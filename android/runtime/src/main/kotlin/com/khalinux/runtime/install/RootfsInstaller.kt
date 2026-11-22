package com.khalinux.runtime.install

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.LinkOption
import java.nio.file.attribute.PosixFilePermission
import java.util.EnumSet
import java.security.MessageDigest
import java.util.Locale

/**
 * Instala um RootFS tar.gz fornecido por uma origem HTTPS confiável.
 *
 * O SHA-256 esperado deve vir de um manifesto imutável/revisado pelo aplicativo,
 * não de um valor obtido no mesmo download do arquivo sem validação independente.
 */
class RootfsInstaller(
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 30_000,
    private val maxDownloadBytes: Long = 1_500_000_000L,
    private val maxExtractedBytes: Long = 4_000_000_000L
) {
    data class Request(
        val archiveUrl: String,
        val expectedSha256: String,
        val destination: File
    )

    data class Result(val installedDirectory: File, val sha256: String, val extractedBytes: Long)

    fun install(
        request: Request,
        onPhase: (InstallPhase) -> Unit = {},
        onExtractProgress: (extracted: Long) -> Unit = {},
        isCancelled: () -> Boolean = { false },
        onProgress: (downloaded: Long, total: Long?) -> Unit = { _, _ -> }
    ): Result {
        require(request.expectedSha256.matches(Regex("[a-fA-F0-9]{64}"))) { "SHA-256 inválido" }
        val url = URL(request.archiveUrl)
        require(url.protocol.equals("https", ignoreCase = true)) { "O download do RootFS exige HTTPS" }
        require(connectTimeoutMillis > 0 && readTimeoutMillis > 0) { "Timeout inválido" }
        require(maxDownloadBytes > 0 && maxExtractedBytes > 0) { "Limites de tamanho inválidos" }

        val destination = request.destination.absoluteFile
        val parent = destination.parentFile ?: error("Destino sem diretório pai")
        require(destination.name.isNotBlank() && destination.name != "." && destination.name != "..")
        if (!parent.exists()) check(parent.mkdirs()) { "Não foi possível criar diretório de instalação" }
        require(parent.isDirectory) { "Diretório pai inválido" }

        val staging = File(parent, ".${destination.name}.staging")
        val archive = File(parent, ".${destination.name}.download")
        val backup = File(parent, ".${destination.name}.previous")
        deleteTree(staging)
        if (backup.exists()) deleteTree(backup)

        // O arquivo parcial (.download) é mantido entre tentativas para permitir retomada.
        var downloadComplete = false
        try {
            val digest = ResumableDownloader(connectTimeoutMillis, readTimeoutMillis, maxDownloadBytes)
                .download(url, archive, isCancelled, onProgress)
            downloadComplete = true
            onPhase(InstallPhase.VERIFYING)
            val actual = digest.joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }
            require(actual.equals(request.expectedSha256, ignoreCase = true)) {
                "SHA-256 divergente: esperado ${request.expectedSha256}, recebido $actual"
            }
            check(staging.mkdirs()) { "Não foi possível criar diretório temporário" }
            onPhase(InstallPhase.EXTRACTING)
            val extracted = extractSafely(archive, staging, onExtractProgress, isCancelled)
            RootfsValidator.problem(staging)?.let { problem ->
                throw IOException("RootFS extraído inválido: $problem")
            }

            require(!Files.isSymbolicLink(destination.toPath())) { "Destino de instalação não pode ser link simbólico" }
            if (destination.exists()) {
                try {
                    Files.move(destination.toPath(), backup.toPath(), StandardCopyOption.ATOMIC_MOVE)
                } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                    Files.move(destination.toPath(), backup.toPath())
                }
            }
            try {
                try {
                    Files.move(staging.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
                } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                    Files.move(staging.toPath(), destination.toPath())
                }
            } catch (failure: Throwable) {
                if (backup.exists() && !destination.exists()) {
                    Files.move(backup.toPath(), destination.toPath())
                }
                throw failure
            }
            runCatching { deleteTree(backup) }
            return Result(destination, actual, extracted)
        } finally {
            if (downloadComplete) archive.delete()
            deleteTree(staging)
        }
    }

    internal fun extractSafely(
        archive: File,
        root: File,
        onExtractProgress: (extracted: Long) -> Unit = {},
        isCancelled: () -> Boolean = { false }
    ): Long {
        val canonicalRoot = root.canonicalFile.toPath()
        var extractedBytes = 0L
        val directoryModes = mutableListOf<Pair<java.nio.file.Path, Int>>()
        TarArchiveInputStream(GzipCompressorInputStream(BufferedInputStream(FileInputStream(archive)))).use { tar ->
            while (true) {
                val entry = tar.nextTarEntry ?: break
                if (isCancelled()) throw InstallCancelledException()
                val target = resolveEntry(canonicalRoot, entry.name)
                ensureNoSymlinkParents(canonicalRoot, if (target == canonicalRoot) canonicalRoot else target.parent)
                val alreadyExists = Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                // ATENÇÃO À ORDEM: no commons-compress, TarArchiveEntry.isFile() também é true para links
                // simbólicos, hard links e dispositivos. Esses tipos precisam ser tratados ANTES de isFile,
                // senão /bin, /lib etc. viram arquivos vazios e o RootFS fica inutilizável.
                when {
                    entry.isDirectory -> {
                        require(!alreadyExists || (Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(target))) {
                            "Diretório TAR colide com entrada existente: " + entry.name
                        }
                        Files.createDirectories(target)
                        directoryModes += target to entry.mode
                    }
                    entry.isSymbolicLink -> {
                        require(!alreadyExists) { "Entrada TAR duplicada: " + entry.name }
                        val link = resolveLink(canonicalRoot, target.parent, entry.linkName)
                        Files.createDirectories(target.parent)
                        val relative = target.parent.relativize(link).toString().ifEmpty { "." }
                        Files.createSymbolicLink(target, target.fileSystem.getPath(relative))
                    }
                    entry.isLink -> {
                        require(!alreadyExists) { "Entrada TAR duplicada: " + entry.name }
                        val link = resolveEntry(canonicalRoot, entry.linkName)
                        require(Files.isRegularFile(link)) { "Hard link aponta para arquivo ausente ou não regular: ${entry.name}" }
                        Files.createDirectories(target.parent)
                        Files.copy(link, target)
                        applyMode(target, entry.mode)
                    }
                    entry.isCharacterDevice || entry.isBlockDevice || entry.isFIFO -> Unit
                    entry.isFile -> {
                        require(!alreadyExists) { "Entrada TAR duplicada: " + entry.name }
                        require(entry.size >= 0) { "Tamanho de arquivo inválido no TAR" }
                        extractedBytes += entry.size
                        require(extractedBytes <= maxExtractedBytes) { "RootFS excede o limite de extração" }
                        Files.createDirectories(target.parent)
                        BufferedOutputStream(Files.newOutputStream(target)).use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var remaining = entry.size
                            while (remaining > 0) {
                                val read = tar.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                                if (read < 0) throw IOException("TAR truncado em ${entry.name}")
                                output.write(buffer, 0, read)
                                remaining -= read
                            }
                        }
                        applyMode(target, entry.mode)
                        onExtractProgress(extractedBytes)
                    }
                    else -> throw IOException("Tipo de entrada TAR não permitido: ${entry.name}")
                }
            }
        }
        directoryModes.sortedByDescending { it.first.nameCount }.forEach { (path, mode) -> applyMode(path, mode) }
        return extractedBytes
    }

    private fun applyMode(path: java.nio.file.Path, mode: Int) {
        val permissions = EnumSet.noneOf(PosixFilePermission::class.java)
        if (mode and 0b100000000 != 0) permissions.add(PosixFilePermission.OWNER_READ)
        if (mode and 0b010000000 != 0) permissions.add(PosixFilePermission.OWNER_WRITE)
        if (mode and 0b001000000 != 0) permissions.add(PosixFilePermission.OWNER_EXECUTE)
        if (mode and 0b000100000 != 0) permissions.add(PosixFilePermission.GROUP_READ)
        if (mode and 0b000010000 != 0) permissions.add(PosixFilePermission.GROUP_WRITE)
        if (mode and 0b000001000 != 0) permissions.add(PosixFilePermission.GROUP_EXECUTE)
        if (mode and 0b000000100 != 0) permissions.add(PosixFilePermission.OTHERS_READ)
        if (mode and 0b000000010 != 0) permissions.add(PosixFilePermission.OTHERS_WRITE)
        if (mode and 0b000000001 != 0) permissions.add(PosixFilePermission.OTHERS_EXECUTE)
        Files.setPosixFilePermissions(path, permissions)
    }

    internal fun resolveEntry(root: java.nio.file.Path, name: String): java.nio.file.Path {
        require(name.isNotBlank() && !name.startsWith("/")) { "Caminho TAR absoluto/vazio recusado: $name" }
        val resolved = root.resolve(name).normalize()
        require(resolved.startsWith(root) && (resolved != root || name.trim('/') == ".")) { "Path traversal recusado: $name" }
        return resolved
    }

    internal fun resolveLink(root: java.nio.file.Path, parent: java.nio.file.Path, name: String): java.nio.file.Path {
        require(name.isNotBlank()) { "Link simbólico vazio" }
        val raw = if (name.startsWith("/")) root.resolve(name.removePrefix("/")) else parent.resolve(name)
        val resolved = raw.normalize()
        require(resolved.startsWith(root)) { "Link simbólico escapa do RootFS: $name" }
        return resolved
    }

    private fun ensureNoSymlinkParents(root: java.nio.file.Path, parent: java.nio.file.Path?) {
        var current = parent ?: root
        require(current.startsWith(root)) { "Diretório pai fora do RootFS" }
        while (current != root) {
            require(!Files.isSymbolicLink(current)) { "Entrada TAR atravessaria link simbólico: $current" }
            current = current.parent ?: root
        }
    }

    private fun deleteTree(file: File) {
        if (!file.exists()) return
        if (file.isDirectory && !Files.isSymbolicLink(file.toPath())) {
            file.listFiles()?.forEach(::deleteTree)
        }
        Files.deleteIfExists(file.toPath())
    }
}
