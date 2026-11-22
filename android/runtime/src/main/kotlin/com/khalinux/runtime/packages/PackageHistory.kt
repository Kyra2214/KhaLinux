package com.khalinux.runtime.packages

import java.io.File

enum class PackageAction(val id: String) { INSTALL("install"), REMOVE("remove"), UPDATE("update") }

/** Entrada imutável do histórico de operações do gerenciador. */
data class PackageHistoryEntry(
    val epochMillis: Long,
    val action: PackageAction,
    val packageName: String,
    val version: String?,
    val success: Boolean,
    /** Mensagem de falha (truncada); vazia em sucesso. Nunca deve conter segredos. */
    val detail: String
)

/**
 * Histórico auditável: arquivo de texto append-only (TAB), uma linha por operação, inclusive falhas.
 * Entradas existentes nunca são reescritas; linhas corrompidas são ignoradas na leitura.
 * Mantém no máximo [maxEntries] linhas: ao exceder, a leitura devolve só as mais recentes e
 * o arquivo é compactado atomicamente (as mais antigas são descartadas).
 */
class PackageHistoryStore(private val file: File, private val maxEntries: Int = 1_000) {
    init { require(maxEntries > 0) { "maxEntries deve ser positivo" } }

    @Synchronized
    fun append(entry: PackageHistoryEntry) {
        file.parentFile?.mkdirs()
        file.appendText(format(entry), Charsets.UTF_8)
        if (readAll().size > maxEntries + maxEntries / 4) compact()
    }

    /** Mais recentes primeiro. */
    @Synchronized
    fun recent(limit: Int = 50): List<PackageHistoryEntry> = readAll().asReversed().take(limit)

    @Synchronized
    fun forPackage(name: String): List<PackageHistoryEntry> = readAll().asReversed().filter { it.packageName == name }

    private fun readAll(): List<PackageHistoryEntry> {
        if (!file.isFile) return emptyList()
        return file.readLines(Charsets.UTF_8).mapNotNull(::parse)
    }

    private fun compact() {
        val keep = readAll().takeLast(maxEntries)
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(keep.joinToString("") { format(it) }, Charsets.UTF_8)
        if (!temporary.renameTo(file)) {
            file.delete()
            check(temporary.renameTo(file)) { "Não foi possível compactar ${file.name}" }
        }
    }

    private fun format(e: PackageHistoryEntry): String {
        val detail = e.detail.replace(Regex("[\\t\\r\\n]+"), " ").take(300)
        return "${e.epochMillis}\t${e.action.id}\t${e.packageName}\t${e.version ?: "-"}\t${if (e.success) "ok" else "fail"}\t$detail\n"
    }

    private fun parse(line: String): PackageHistoryEntry? {
        val f = line.split('\t', limit = 6)
        if (f.size != 6) return null
        val at = f[0].toLongOrNull() ?: return null
        val action = PackageAction.values().firstOrNull { it.id == f[1] } ?: return null
        val success = when (f[4]) { "ok" -> true; "fail" -> false; else -> return null }
        return PackageHistoryEntry(at, action, f[2], f[3].takeIf { it != "-" }, success, f[5])
    }
}
