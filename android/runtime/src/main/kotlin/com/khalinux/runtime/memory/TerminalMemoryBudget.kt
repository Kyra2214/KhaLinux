package com.khalinux.runtime.memory

/**
 * Orçamento de memória do terminal (8.2). O custo de um terminal cresce com linhas × colunas × (histórico + tela) × abas;
 * em 400 colunas o histórico padrão de 1000 linhas seria dezenas de MB por aba. O histórico por aba é calculado para
 * que o pior caso (todas as abas cheias) caiba em [TOTAL_BUDGET_BYTES].
 *
 * `BYTES_PER_CELL` é uma estimativa conservadora (objeto da célula, referência na lista e `String` do caractere),
 * **não medida em aparelho**: serve para limitar o crescimento, não como medida exata.
 */
object TerminalMemoryBudget {
    const val BYTES_PER_CELL = 72L
    const val TOTAL_BUDGET_BYTES = 24L * 1024 * 1024
    const val MIN_SCROLLBACK_LINES = 100
    const val MAX_SCROLLBACK_LINES = 1_000

    fun estimateBytes(rows: Int, columns: Int, scrollbackLines: Int): Long =
        (rows.toLong() + scrollbackLines.toLong()) * columns.toLong() * BYTES_PER_CELL

    /** Linhas de histórico por aba para [tabs] abas com [rows]×[columns]; sempre em [MIN_SCROLLBACK_LINES]..[MAX_SCROLLBACK_LINES]. */
    fun scrollbackFor(rows: Int, columns: Int, tabs: Int, totalBudgetBytes: Long = TOTAL_BUDGET_BYTES): Int {
        require(rows > 0 && columns > 0 && tabs > 0 && totalBudgetBytes > 0) { "Parâmetros inválidos" }
        val perTab = totalBudgetBytes / tabs
        val totalLines = perTab / (columns.toLong() * BYTES_PER_CELL)
        return (totalLines - rows).coerceIn(MIN_SCROLLBACK_LINES.toLong(), MAX_SCROLLBACK_LINES.toLong()).toInt()
    }
}
