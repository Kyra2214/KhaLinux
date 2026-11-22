package com.khalinux.runtime.packages

import java.io.File

/** Registro de um pacote instalado. */
data class InstalledPackage(
    val name: String,
    val version: String,
    val sourceType: PackageSourceType,
    val sha256: String?,
    val installedAtEpochMillis: Long
)

/**
 * Pacotes instalados, persistidos em arquivo de texto (uma linha por pacote, campos separados por TAB)
 * com gravação atômica (temporário + rename). Linhas corrompidas são ignoradas na leitura.
 */
class InstalledPackageStore(private val file: File) {
    @Synchronized
    fun all(): Map<String, InstalledPackage> {
        if (!file.isFile) return emptyMap()
        val result = LinkedHashMap<String, InstalledPackage>()
        file.readLines(Charsets.UTF_8).forEach { line ->
            val f = line.split('\t')
            if (f.size != 5) return@forEach
            val source = runCatching { PackageSourceType.fromId(f[3]) }.getOrNull() ?: return@forEach
            val at = f[4].toLongOrNull() ?: return@forEach
            if (!PackageManifest.NAME.matches(f[0]) || !PackageManifest.VERSION.matches(f[1])) return@forEach
            result[f[0]] = InstalledPackage(f[0], f[1], source, f[2].takeIf { it != "-" }, at)
        }
        return result
    }

    /** Mapa nome → versão, no formato esperado por [DependencyResolver]. */
    fun versions(): Map<String, String> = all().mapValues { it.value.version }

    @Synchronized
    fun put(pkg: InstalledPackage) {
        val current = LinkedHashMap(all())
        current[pkg.name] = pkg
        write(current)
    }

    @Synchronized
    fun remove(name: String): Boolean {
        val current = LinkedHashMap(all())
        if (current.remove(name) == null) return false
        write(current)
        return true
    }

    private fun write(entries: Map<String, InstalledPackage>) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(
            entries.values.sortedBy { it.name }.joinToString("") {
                "${it.name}\t${it.version}\t${it.sha256 ?: "-"}\t${it.sourceType.id}\t${it.installedAtEpochMillis}\n"
            },
            Charsets.UTF_8
        )
        if (!temporary.renameTo(file)) {
            file.delete()
            check(temporary.renameTo(file)) { "Não foi possível gravar ${file.name}" }
        }
    }
}
