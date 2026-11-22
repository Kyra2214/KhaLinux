package com.khalinux.runtime.storage

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption

/** Área de armazenamento do app, para exibição. [preserved] = dados do usuário ou do ambiente: a limpeza nunca toca. */
data class StorageEntry(val label: String, val path: String, val bytes: Long, val preserved: Boolean)

data class StorageReport(val entries: List<StorageEntry>) {
    val totalBytes: Long get() = entries.sumOf { it.bytes }
    val reclaimableBytes: Long get() = entries.filter { !it.preserved }.sumOf { it.bytes }
}

data class CleanupResult(val removed: List<String>, val freedBytes: Long, val skipped: List<String>)

/**
 * Higiene de armazenamento (8.1): remove somente resíduos de instalação interrompida e temporários
 * antigos; nunca toca no RootFS instalado, no PRoot instalado, no home do usuário nem em relatórios/notas.
 *
 * Regras de segurança:
 * - Nada é removido enquanto uma instalação estiver em andamento ([installRunning]).
 * - `.download` parcial só é removido após [maxPartialAgeMillis] (preserva a retomada do download).
 * - `.previous` (cópia de reversão) só é removida quando o destino correspondente existe; se o destino
 *   não existe, a cópia pode ser a única versão utilizável e é mantida (restauração é o submódulo 8.5).
 * - Links simbólicos não são seguidos nem apagados recursivamente por seu alvo.
 */
class StorageHygiene(
    private val runtimeDirectory: File,
    private val homeDirectory: File? = null,
    private val packageTempDirectory: File? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxPartialAgeMillis: Long = DAY * 7,
    private val maxTempAgeMillis: Long = DAY
) {
    init {
        require(maxPartialAgeMillis >= 0 && maxTempAgeMillis >= 0) { "Idade máxima inválida" }
    }

    fun report(): StorageReport {
        val items = mutableListOf<StorageEntry>()
        fun add(label: String, file: File, preserved: Boolean) {
            if (exists(file)) items += StorageEntry(label, file.path, sizeOf(file), preserved)
        }
        add("RootFS instalado", File(runtimeDirectory, ROOTFS), preserved = true)
        add("PRoot instalado", File(runtimeDirectory, PROOT), preserved = true)
        homeDirectory?.let { add("Home do usuário", it, preserved = true) }
        for (stale in staleCandidates()) items += StorageEntry("Resíduo: ${stale.name}", stale.path, sizeOf(stale), preserved = false)
        packageTempDirectory?.takeIf { exists(it) }?.let { dir ->
            val bytes = dir.listFiles().orEmpty().sumOf { sizeOf(it) }
            if (bytes > 0) items += StorageEntry("Temporários de pacotes", dir.path, bytes, preserved = false)
        }
        return StorageReport(items)
    }

    /** Remove resíduos elegíveis; seguro para chamar na inicialização do serviço (em segundo plano). */
    fun cleanup(installRunning: Boolean): CleanupResult {
        if (installRunning) return CleanupResult(emptyList(), 0L, listOf("instalação em andamento"))
        val removed = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        var freed = 0L
        for (file in staleCandidates()) {
            val bytes = sizeOf(file)
            if (deleteTree(file)) {
                removed += file.path
                freed += bytes
            } else {
                skipped += file.path
            }
        }
        packageTempDirectory?.takeIf { exists(it) && it.isDirectory }?.listFiles().orEmpty().forEach { entry ->
            if (age(entry) >= maxTempAgeMillis) {
                val bytes = sizeOf(entry)
                if (deleteTree(entry)) {
                    removed += entry.path
                    freed += bytes
                } else {
                    skipped += entry.path
                }
            }
        }
        return CleanupResult(removed, freed, skipped)
    }

    /** Resíduos elegíveis hoje (sem removê-los). */
    internal fun staleCandidates(): List<File> {
        val found = mutableListOf<File>()
        found += residuesIn(runtimeDirectory)
        // Pacotes: `/opt/khalinux/.<nome>.staging|previous` dentro do RootFS.
        val packages = File(File(runtimeDirectory, ROOTFS), "opt/khalinux")
        if (packages.isDirectory) found += residuesIn(packages)
        return found
    }

    /**
     * Procura `.<nome>.staging`, `.<nome>.download` e `.<nome>.previous` em [parent] (inclusive órfãos, sem destino
     * correspondente), aplicando as regras de segurança descritas na classe.
     */
    private fun residuesIn(parent: File): List<File> {
        if (!parent.isDirectory) return emptyList()
        val result = mutableListOf<File>()
        for (entry in parent.listFiles().orEmpty()) {
            val match = RESIDUE.matchEntire(entry.name) ?: continue
            val base = match.groupValues[1]
            val kind = match.groupValues[2]
            val destinationExists = exists(File(parent, base))
            when (kind) {
                "staging" -> result += entry // área de trabalho: nunca é utilizável, apagar
                "download" -> if (age(entry) >= maxPartialAgeMillis) result += entry
                "previous" -> if (destinationExists) result += entry
            }
        }
        return result
    }

    private fun age(file: File): Long = (clock() - file.lastModified()).coerceAtLeast(0L)

    private fun exists(file: File): Boolean = Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)

    private fun sizeOf(file: File): Long {
        if (!exists(file)) return 0L
        if (Files.isSymbolicLink(file.toPath())) return 0L
        if (file.isFile) return file.length()
        return file.listFiles().orEmpty().sumOf { sizeOf(it) }
    }

    private fun deleteTree(file: File): Boolean = try {
        if (Files.isDirectory(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            file.listFiles().orEmpty().forEach { deleteTree(it) }
        }
        Files.deleteIfExists(file.toPath())
        !exists(file)
    } catch (_: IOException) {
        false
    }

    companion object {
        const val DAY = 24L * 60 * 60 * 1000
        const val ROOTFS = "rootfs-arm64"
        const val PROOT = "proot"
        /** `.<base>.<staging|download|previous>` */
        private val RESIDUE = Regex("\\.([A-Za-z0-9._-]+)\\.(staging|download|previous)")
    }
}
