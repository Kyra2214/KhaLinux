package com.khalinux.runtime.reports

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Nota em Markdown com a saída capturada de um terminal/ferramenta, para anexar a relatórios. */
object OutputNote {
    const val PREFIX = "nota"
    const val MAX_OUTPUT_CHARS = 64 * 1024
    private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC)

    /** Nulo se não há saída. Saída acima do limite é truncada (mantém o final, o mais recente). */
    fun build(generatedAtMillis: Long, title: String, command: String?, output: String): String? {
        val body = output.replace("\r\n", "\n").lines().joinToString("\n") { it.trimEnd() }.trim('\n')
        if (body.isBlank()) return null
        val truncated = body.length > MAX_OUTPUT_CHARS
        val kept = if (truncated) body.takeLast(MAX_OUTPUT_CHARS) else body
        val fence = "`".repeat(maxOf(3, longestBacktickRun(kept) + 1))
        val cleanTitle = title.filter { !it.isISOControl() }.trim().ifEmpty { "Saída do terminal" }.take(80)
        return buildString {
            append("# $cleanTitle\n\n")
            append("Capturada em ${STAMP.format(Instant.ofEpochMilli(generatedAtMillis))}.\n\n")
            command?.filter { !it.isISOControl() }?.trim()?.takeIf { it.isNotEmpty() }?.let {
                append("Comando: `${it.replace("`", "'")}`\n\n")
            }
            if (truncated) append("_Saída truncada: mantidos os últimos $MAX_OUTPUT_CHARS caracteres._\n\n")
            append(fence).append("text\n").append(kept).append("\n").append(fence).append("\n")
        }
    }

    private fun longestBacktickRun(text: String): Int {
        var best = 0
        var run = 0
        for (c in text) {
            if (c == '`') { run++; if (run > best) best = run } else run = 0
        }
        return best
    }
}
