package com.khalinux.runtime.files

import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

data class FileEntry(
    val name: String,
    /** Caminho relativo à raiz, com `/`; vazio é a raiz. */
    val relativePath: String,
    val directory: Boolean,
    val size: Long,
    val modifiedMillis: Long
)

/**
 * Gerenciador de arquivos confinado a uma raiz (o home persistente do RootFS, `/home/khalinux`).
 * Todo caminho é relativo à raiz; segmentos `.`/`..`/vazios/NUL são recusados e o caminho canônico
 * (que resolve links simbólicos) precisa permanecer dentro da raiz.
 */
class HomeFileManager(
    root: File,
    private val maxTextBytes: Long = MAX_TEXT_BYTES
) {
    private val rootDir: File = root.canonicalFile

    /** Normaliza um caminho relativo; nulo se algum segmento é inválido. */
    fun normalize(relative: String): String? {
        val segments = relative.split('/').filter { it.isNotEmpty() }
        if (segments.any { !isValidSegment(it) }) return null
        return segments.joinToString("/")
    }

    fun resolve(relative: String): File? {
        val clean = normalize(relative) ?: return null
        val target = if (clean.isEmpty()) rootDir else File(rootDir, clean)
        val canonical = try { target.canonicalFile } catch (_: java.io.IOException) { return null }
        val rootPath = rootDir.path
        val inside = canonical.path == rootPath || canonical.path.startsWith(rootPath + File.separator)
        return if (inside) canonical else null
    }

    /** Lista o diretório (pastas primeiro, ordem alfabética); nulo se inválido ou não é diretório. */
    fun list(relative: String = ""): List<FileEntry>? {
        val dir = resolve(relative) ?: return null
        if (!dir.isDirectory) return null
        val base = normalize(relative) ?: return null
        val children = dir.listFiles() ?: return emptyList()
        return children
            .filter { isValidSegment(it.name) }
            .map {
                FileEntry(
                    name = it.name,
                    relativePath = if (base.isEmpty()) it.name else "$base/${it.name}",
                    directory = it.isDirectory,
                    size = if (it.isDirectory) 0L else it.length(),
                    modifiedMillis = it.lastModified()
                )
            }
            .sortedWith(compareBy<FileEntry>({ !it.directory }, { it.name.lowercase() }))
    }

    /** Lê um arquivo de texto UTF-8; nulo se não é arquivo, é grande demais ou parece binário (contém NUL). */
    fun readText(relative: String): String? {
        val file = resolve(relative) ?: return null
        if (!file.isFile || file.length() > maxTextBytes) return null
        val bytes = try { file.readBytes() } catch (_: java.io.IOException) { return null }
        if (bytes.any { it == 0.toByte() }) return null
        return String(bytes, Charsets.UTF_8)
    }

    /** Cria/substitui um arquivo de texto numa pasta existente. */
    fun writeText(relative: String, content: String): Boolean {
        if (normalize(relative).isNullOrEmpty()) return false
        if (content.toByteArray(Charsets.UTF_8).size > maxTextBytes || content.contains('\u0000')) return false
        val file = resolve(relative) ?: return false
        val parent = file.parentFile ?: return false
        if (!parent.isDirectory || file.isDirectory) return false
        return try { file.writeText(content, Charsets.UTF_8); true } catch (_: java.io.IOException) { false }
    }

    fun mkdir(relative: String): Boolean {
        if (normalize(relative).isNullOrEmpty()) return false
        val dir = resolve(relative) ?: return false
        return !dir.exists() && dir.mkdir()
    }

    /** Remove arquivo ou link; pasta só vazia, a menos que [recursive]. Nunca remove a raiz; não segue links. */
    fun delete(relative: String, recursive: Boolean = false): Boolean {
        val clean = normalize(relative) ?: return false
        if (clean.isEmpty()) return false
        // Resolve só o pai: se o alvo for um link, remove o link e não o destino.
        val parentRelative = clean.substringBeforeLast('/', "")
        val parent = resolve(parentRelative) ?: return false
        val target = File(parent, clean.substringAfterLast('/'))
        val path: Path = target.toPath()
        if (!Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return false
        return try {
            if (Files.isDirectory(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                if (!recursive) {
                    Files.delete(path)
                } else {
                    Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
                        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                            Files.delete(file)
                            return FileVisitResult.CONTINUE
                        }

                        override fun postVisitDirectory(dir: Path, exc: java.io.IOException?): FileVisitResult {
                            if (exc != null) throw exc
                            Files.delete(dir)
                            return FileVisitResult.CONTINUE
                        }
                    })
                }
            } else {
                Files.delete(path)
            }
            true
        } catch (_: java.io.IOException) {
            false
        }
    }

    /** Renomeia dentro da mesma pasta; [newName] é um único segmento válido. */
    fun rename(relative: String, newName: String): Boolean {
        val clean = normalize(relative) ?: return false
        if (clean.isEmpty() || !isValidSegment(newName)) return false
        val source = resolve(clean) ?: return false
        if (!source.exists()) return false
        val parentRelative = clean.substringBeforeLast('/', "")
        val destination = resolve(if (parentRelative.isEmpty()) newName else "$parentRelative/$newName") ?: return false
        if (destination.exists()) return false
        return source.renameTo(destination)
    }

    companion object {
        const val MAX_TEXT_BYTES = 256L * 1024L

        fun isValidSegment(name: String): Boolean =
            name.isNotEmpty() && name != "." && name != ".." && name.length <= 255 &&
                name.none { it == '/' || it == '\u0000' || it.isISOControl() }
    }
}
