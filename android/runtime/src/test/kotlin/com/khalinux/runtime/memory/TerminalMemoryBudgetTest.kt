package com.khalinux.runtime.memory

import com.khalinux.runtime.TerminalEmulator
import com.khalinux.runtime.TerminalStyle
import com.khalinux.runtime.tabs.TerminalTabs
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Escrito em 8.2; **não executado** (sem `kotlinc`/rede): entra na validação final. */
class TerminalMemoryBudgetTest {
    @Test fun phoneSizedTerminalKeepsDefaultHistory() {
        assertEquals(1000, TerminalMemoryBudget.scrollbackFor(24, 80, 4))
    }

    @Test fun wideTerminalGetsShorterHistoryWithinBudget() {
        val lines = TerminalMemoryBudget.scrollbackFor(50, 400, 4)
        assertTrue(lines in TerminalMemoryBudget.MIN_SCROLLBACK_LINES until 1000)
        val total = 4 * TerminalMemoryBudget.estimateBytes(50, 400, lines)
        assertTrue(total <= TerminalMemoryBudget.TOTAL_BUDGET_BYTES + 4 * 400 * TerminalMemoryBudget.BYTES_PER_CELL * TerminalMemoryBudget.MIN_SCROLLBACK_LINES)
    }

    @Test fun historyNeverBelowMinimum() {
        assertEquals(TerminalMemoryBudget.MIN_SCROLLBACK_LINES, TerminalMemoryBudget.scrollbackFor(200, 400, 16, totalBudgetBytes = 1024))
    }

    @Test fun emulatorTrimsWhenLimitLowered() {
        val terminal = TerminalEmulator(rows = 2, columns = 10, maxScrollbackLines = 50)
        repeat(30) { terminal.append("linha $it\r\n".toByteArray()) }
        terminal.setScrollbackLimit(3)
        assertTrue(terminal.snapshot().lines.size <= 5)
    }

    @Test fun resizeAllAppliesBudgetToEveryTab() {
        val tabs = TerminalTabs(maxTabs = 4, rows = 24, columns = 80)
        tabs.create()
        tabs.resizeAll(50, 400)
        assertTrue(tabs.all().all { it.emulator.snapshot().columns == 400 })
    }

    @Test fun blankCellsShareStyle() {
        val terminal = TerminalEmulator(rows = 2, columns = 4)
        val lines = terminal.snapshot().lines
        assertSame(TerminalStyle.DEFAULT, lines[0][0].style)
        assertSame(lines[0][0], lines[1][3])
    }
}
