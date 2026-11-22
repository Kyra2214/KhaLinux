package com.khalinux.runtime.tabs

import com.khalinux.runtime.TerminalEmulator

/** Limite padrão de abas; igual ao limite de sessões ativas do `SessionManager`. */
const val DEFAULT_MAX_TABS = 4
const val MAX_TAB_TITLE_LENGTH = 24

/**
 * Uma aba de terminal: emulador próprio, título, rascunho de entrada e a sessão do runtime associada.
 * Os campos mutáveis só devem ser alterados por quem detém o lock externo (o ViewModel) ou pelo [TerminalTabs].
 */
class TerminalTab internal constructor(
    val id: String,
    title: String,
    rows: Int,
    columns: Int,
    scrollbackLines: Int = TerminalEmulator.DEFAULT_SCROLLBACK_LINES
) {
    val emulator: TerminalEmulator = TerminalEmulator(rows = rows, columns = columns, maxScrollbackLines = scrollbackLines)
    var title: String = title
        internal set
    /** Sessão do runtime em execução nesta aba; nulo quando não há sessão vinculada. */
    var sessionId: String? = null
    var interactive: Boolean = false
    var starting: Boolean = false
    var sendingInput: Boolean = false
    /** Texto digitado e ainda não enviado; preservado ao trocar de aba. */
    var draft: String = ""

    val isRunning: Boolean get() = starting || sessionId != null

    fun info(): TerminalTabInfo = TerminalTabInfo(id, title, sessionId, interactive, isRunning)
}

/** Visão imutável de uma aba para a UI. */
data class TerminalTabInfo(
    val id: String,
    val title: String,
    val sessionId: String?,
    val interactive: Boolean,
    val running: Boolean
)

/** Resultado de [TerminalTabs.close]: `sessionToCancel` é a sessão que o chamador deve cancelar no runtime. */
data class TabCloseResult(val closed: Boolean, val sessionToCancel: String?)

/**
 * Gerencia múltiplas abas de terminal. Sempre existe ao menos uma aba e exatamente uma está ativa.
 * Thread-safe; não conhece Android nem o runtime (apenas guarda os identificadores de sessão).
 */
class TerminalTabs(
    private val maxTabs: Int = DEFAULT_MAX_TABS,
    private var rows: Int = 24,
    private var columns: Int = 80
) {
    private val lock = Any()
    private val tabs = mutableListOf<TerminalTab>()
    private var activeId: String = ""
    private var counter = 0

    init {
        require(maxTabs > 0) { "maxTabs deve ser maior que zero" }
        synchronized(lock) { activeId = newTabLocked(null).id }
    }

    /** Cria e ativa uma nova aba; nulo se o limite foi atingido. */
    fun create(title: String? = null): TerminalTab? = synchronized(lock) {
        if (tabs.size >= maxTabs) return null
        newTabLocked(title).also { activeId = it.id }
    }

    fun canCreate(): Boolean = synchronized(lock) { tabs.size < maxTabs }

    fun active(): TerminalTab = synchronized(lock) { tabs.first { it.id == activeId } }

    fun get(id: String): TerminalTab? = synchronized(lock) { tabs.firstOrNull { it.id == id } }

    fun findBySession(sessionId: String): TerminalTab? = synchronized(lock) {
        tabs.firstOrNull { it.sessionId == sessionId }
    }

    fun select(id: String): Boolean = synchronized(lock) {
        val tab = tabs.firstOrNull { it.id == id } ?: return false
        activeId = id
        // 8.4: a aba que volta a ser usada recupera o limite normal de histórico (o que foi descartado não volta).
        tab.emulator.setScrollbackLimit(budgetedScrollback())
        true
    }

    /**
     * Sob pressão crítica de memória (8.4): reduz ao mínimo o histórico das abas em segundo plano, descartando as linhas
     * mais antigas delas. A aba ativa não é alterada. Devolve quantas abas foram reduzidas.
     */
    fun trimBackground(): Int = synchronized(lock) {
        val minimum = com.khalinux.runtime.memory.TerminalMemoryBudget.MIN_SCROLLBACK_LINES
        val background = tabs.filter { it.id != activeId }
        background.forEach { it.emulator.setScrollbackLimit(minimum) }
        background.size
    }

    /** Fecha a aba; a última aba não pode ser fechada. Se estava ativa, a vizinha à esquerda (ou a primeira) assume. */
    fun close(id: String): TabCloseResult = synchronized(lock) {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0 || tabs.size == 1) return TabCloseResult(false, null)
        val removed = tabs.removeAt(index)
        if (activeId == id) activeId = tabs[(index - 1).coerceAtLeast(0)].id
        TabCloseResult(true, removed.sessionId)
    }

    fun rename(id: String, title: String): Boolean = synchronized(lock) {
        val clean = sanitizeTitle(title) ?: return false
        val tab = tabs.firstOrNull { it.id == id } ?: return false
        tab.title = clean
        true
    }

    fun infos(): List<TerminalTabInfo> = synchronized(lock) { tabs.map { it.info() } }

    fun all(): List<TerminalTab> = synchronized(lock) { tabs.toList() }

    fun activeId(): String = synchronized(lock) { activeId }

    fun anyRunning(): Boolean = synchronized(lock) { tabs.any { it.isRunning } }

    /** Redimensiona o emulador de todas as abas (o tamanho é uma preferência global). */
    fun resizeAll(newRows: Int, newColumns: Int) = synchronized(lock) {
        rows = newRows
        columns = newColumns
        val scrollback = budgetedScrollback()
        tabs.forEach {
            it.emulator.resize(newRows, newColumns)
            it.emulator.setScrollbackLimit(scrollback)
        }
    }

    /** Desvincula todas as sessões (ex.: Service reconectado); devolve as abas que estavam em execução. */
    fun clearSessions(): List<TerminalTab> = synchronized(lock) {
        val running = tabs.filter { it.isRunning }
        tabs.forEach {
            it.sessionId = null
            it.interactive = false
            it.starting = false
            it.sendingInput = false
        }
        running
    }

    /** Histórico por aba dentro do orçamento de memória, considerando o pior caso (todas as abas abertas). */
    private fun budgetedScrollback(): Int = com.khalinux.runtime.memory.TerminalMemoryBudget.scrollbackFor(rows, columns, maxTabs)

    private fun newTabLocked(title: String?): TerminalTab {
        counter++
        val tab = TerminalTab("tab-$counter", sanitizeTitle(title) ?: "Terminal $counter", rows, columns, budgetedScrollback())
        tabs += tab
        return tab
    }

    companion object {
        fun sanitizeTitle(raw: String?): String? {
            val clean = raw?.filter { !it.isISOControl() }?.trim().orEmpty()
            if (clean.isEmpty()) return null
            return clean.take(MAX_TAB_TITLE_LENGTH)
        }
    }
}
