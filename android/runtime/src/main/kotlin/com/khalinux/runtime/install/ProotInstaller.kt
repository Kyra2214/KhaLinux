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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest
import java.util.EnumSet
import java.util.Locale

/** Instala um único executável PRoot Android ARM64 a partir de release HTTPS fixada. */
class ProotInstaller(
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 30_000,
    private val maxDownloadBytes: Long = 64L * 1024 * 1024,
    private val maxBinaryBytes: Long = 32L * 1024 * 1024
) {
    data class Request(
        val archiveUrl: String,
        val expectedArchiveSha256: String,
        val expectedBinarySha256: String,
        val expectedBinaryBytes: Long,
        val destination: File
    )

    data class Result(
        val installedFile: File,
        val archiveSha256: String,
        val binarySha256: String,
        val binaryBytes: Long
    )

    fun install(
        request: Request,
        onProgress: (downloaded: Long, total: Long?) -> Unit = { _, _ -> }
    ): Result {
        requireSha256(request.expectedArchiveSha256, "SHA-256 do pacote")
        requireSha256(request.expectedBinarySha256, "SHA-256 do executável")
        require(request.expectedBinaryBytes >= 64L && request.expectedBinaryBytes <= maxBinaryBytes) {
            "Tamanho esperado do executável PRoot inválido"
        }
        val url = URL(request.archiveUrl)
        require(url.protocol.equals("https", ignoreCase = true)) { "O download do PRoot exige HTTPS" }
        require(connectTimeoutMillis > 0 && readTimeoutMillis > 0) { "Timeout inválido" }
        require(maxDownloadBytes > 0 && maxBinaryBytes >= 64) { "Limites de tamanho inválidos" }

        val destination = request.destination.absoluteFile
        val parent = destination.parentFile ?: error("Destino PRoot sem diretório pai")
        if (!parent.exists()) check(parent.mkdirs()) { "Não foi possível criar diretório do PRoot" }
        require(parent.isDirectory) { "Diretório pai do PRoot inválido" }
        require(destination.name == "proot") { "Nome de destino PRoot inesperado" }

        val staging = File(parent, ".proot.staging")
        val archive = File(parent, ".proot.download")
        val backup = File(parent, ".proot.previous")
        deleteFile(staging)
        deleteFile(archive)
        deleteFile(backup)

        try {
            val archiveDigest = download(url, archive, onProgress)
            val actualArchiveSha = archiveDigest.toHex()
            require(actualArchiveSha.equals(request.expectedArchiveSha256, ignoreCase = true)) {
                "SHA-256 do pacote PRoot divergente"
            }

            val binaryBytes = extractBinarySafely(archive, staging)
            require(binaryBytes == request.expectedBinaryBytes) { "Tamanho do executável PRoot divergente do manifesto" }
            val actualBinarySha = sha256(staging).toHex()
            require(actualBinarySha.equals(request.expectedBinarySha256, ignoreCase = true)) {
                "SHA-256 do executável PRoot divergente"
            }
            setExecutablePermissions(staging)

            require(!Files.isSymbolicLink(destination.toPath())) {
                "Destino PRoot não pode ser link simbólico"
            }
            if (destination.exists()) move(destination, backup)
            try {
                move(staging, destination)
            } catch (failure: Throwable) {
                if (backup.exists() && !destination.exists()) move(backup, destination)
                throw failure
            }
            runCatching { deleteFile(backup) }
            return Result(destination, actualArchiveSha, actualBinarySha, binaryBytes)
        } finally {
            deleteFile(archive)
            deleteFile(staging)
        }
    }

    private fun download(url: URL, output: File, onProgress: (Long, Long?) -> Unit): ByteArray {
        val connection = GitHubHttpsConnection.open(url, connectTimeoutMillis, readTimeoutMillis)
        try {
            val total = connection.contentLengthLong.takeIf { it >= 0 }
            require(total == null || total in 1..maxDownloadBytes) { "Pacote PRoot excede o limite de download" }
            val digest = MessageDigest.getInstance("SHA-256")
            var count = 0L
            connection.inputStream.use { input ->
                FileOutputStream(output).use { raw ->
                    BufferedOutputStream(raw).use { sink ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            count += read
                            require(count <= maxDownloadBytes) { "Download PRoot excedeu o limite permitido" }
                            digest.update(buffer, 0, read)
                            sink.write(buffer, 0, read)
                            onProgress(count, total)
                        }
                    }
                }
            }
            require(count > 0) { "Download PRoot vazio" }
            return digest.digest()
        } finally {
            connection.disconnect()
        }
    }

    internal fun extractBinarySafely(archive: File, output: File): Long {
        require(!Files.isSymbolicLink(output.toPath())) { "Arquivo temporário PRoot não pode ser link simbólico" }
        var extractedBytes = 0L
        TarArchiveInputStream(
            GzipCompressorInputStream(BufferedInputStream(FileInputStream(archive)))
        ).use { tar ->
            val entry = tar.nextTarEntry ?: throw IOException("Pacote PRoot vazio")
            require(entry.name == "proot" && entry.isFile && !entry.isSymbolicLink && !entry.isLink) {
                "Pacote PRoot deve conter somente um arquivo regular chamado proot"
            }
            require(entry.size >= 64L && entry.size <= maxBinaryBytes) { "Tamanho do executável PRoot inválido" }
            extractedBytes = entry.size
            Files.newOutputStream(
                output.toPath(),
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
            ).use { sink ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var remaining = entry.size
                while (remaining > 0) {
                    val read = tar.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (read < 0) throw IOException("Pacote PRoot truncado")
                    sink.write(buffer, 0, read)
                    remaining -= read
                }
            }
            require(tar.nextTarEntry == null) { "Pacote PRoot contém entradas adicionais não permitidas" }
        }
        validateAndroidArm64Elf(output)
        return extractedBytes
    }

    internal fun validateAndroidArm64Elf(file: File) {
        java.io.RandomAccessFile(file, "r").use { input ->
            val header = ByteArray(64)
            input.readFully(header)
            require(
                header[0] == 0x7f.toByte() &&
                    header[1] == 'E'.code.toByte() &&
                    header[2] == 'L'.code.toByte() &&
                    header[3] == 'F'.code.toByte()
            ) { "Executável PRoot não é ELF" }
            require(header[4].toInt() == 2 && header[5].toInt() == 1 && header[6].toInt() == 1) {
                "Executável PRoot deve ser ELF64 little-endian com versão ELF atual"
            }
            val elf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            require((elf.getShort(16).toInt() and 0xffff) == 3) { "Executável PRoot deve ser PIE (ET_DYN)" }
            require((elf.getShort(18).toInt() and 0xffff) == 183) { "Executável PRoot não é AArch64" }

            val programHeaderOffset = elf.getLong(32)
            val programHeaderSize = elf.getShort(54).toInt() and 0xffff
            val programHeaderCount = elf.getShort(56).toInt() and 0xffff
            require(programHeaderOffset >= 64 && programHeaderSize in 56..128 && programHeaderCount in 1..128) {
                "Tabela de segmentos ELF inválida"
            }

            var interpreter: String? = null
            repeat(programHeaderCount) { index ->
                val offset = programHeaderOffset + index.toLong() * programHeaderSize
                val ph = ByteArray(programHeaderSize)
                input.seek(offset)
                input.readFully(ph)
                val programHeader = ByteBuffer.wrap(ph).order(ByteOrder.LITTLE_ENDIAN)
                if (programHeader.getInt(0) == 3) { // PT_INTERP
                    val stringOffset = programHeader.getLong(8)
                    val stringSize = programHeader.getLong(32)
                    require(stringSize >= 2L && stringSize <= 256L) { "PT_INTERP inválido" }
                    val bytes = ByteArray(stringSize.toInt())
                    input.seek(stringOffset)
                    input.readFully(bytes)
                    interpreter = bytes.takeWhile { it != 0.toByte() }.toByteArray().toString(Charsets.UTF_8)
                }
            }
            require(interpreter == "/system/bin/linker64") {
                "Executável PRoot não usa o linker64 do Android"
            }
        }
    }

    private fun setExecutablePermissions(file: File) {
        val permissions = EnumSet.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE,
            PosixFilePermission.GROUP_READ,
            PosixFilePermission.GROUP_EXECUTE,
            PosixFilePermission.OTHERS_READ,
            PosixFilePermission.OTHERS_EXECUTE
        )
        Files.setPosixFilePermissions(file.toPath(), permissions)
    }

    private fun move(source: File, destination: File) {
        try {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), destination.toPath())
        }
    }

    private fun sha256(file: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest()
    }

    private fun requireSha256(value: String, label: String) {
        require(value.matches(Regex("[a-fA-F0-9]{64}"))) { "$label inválido" }
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }

    private fun deleteFile(file: File) {
        if (Files.isSymbolicLink(file.toPath())) {
            Files.deleteIfExists(file.toPath())
        } else if (file.exists()) {
            require(file.isFile) { "Arquivo temporário PRoot não é regular" }
            Files.deleteIfExists(file.toPath())
        }
    }

}
