package com.khalinux.runtime.memory

import com.khalinux.runtime.TerminalEmulator
import com.khalinux.runtime.tabs.TerminalTabs
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Escrito em 8.4; **não executado** (sem `kotlinc`/rede): entra na validação final. */
class MemoryPressureTest {
    @Test fun onlyCriticalLevelsTrim() {
        assertTrue(MemoryPressure.shouldTrimBackgroundTabs(15))
        assertTrue(MemoryPressure.shouldTrimBackgroundTabs(60))
        assertTrue(MemoryPressure.shouldTrimBackgroundTabs(80))
        listOf(5, 10, 20, 40).forEach { assertFalse(MemoryPressure.shouldTrimBackgroundTabs(it), "nível $it") }
    }

    @Test fun trimBackgroundKeepsActiveTabUntouched() {
        val tabs = TerminalTabs(maxTabs = 4, rows = 2, columns = 10)
        val first = tabs.active()
        repeat(500) { first.emulator.append("linha $it\r\n".toByteArray()) }
        val second = requireNotNull(tabs.create()) // a nova aba fica ativa; a primeira vai para segundo plano
        repeat(500) { second.emulator.append("linha $it\r\n".toByteArray()) }
        assertEquals(1, tabs.trimBackground())
        assertTrue(first.emulator.snapshot().lines.size <= TerminalMemoryBudget.MIN_SCROLLBACK_LINES + 2)
        assertTrue(second.emulator.snapshot().lines.size > TerminalMemoryBudget.MIN_SCROLLBACK_LINES + 2)
    }

    @Test fun selectingTabRestoresNormalLimit() {
        val tabs = TerminalTabs(maxTabs = 4, rows = 2, columns = 10)
        val first = tabs.active()
        tabs.create()
        tabs.trimBackground()
        tabs.select(first.id)
        repeat(500) { first.emulator.append("x $it\r\n".toByteArray()) }
        assertTrue(first.emulator.snapshot().lines.size > TerminalMemoryBudget.MIN_SCROLLBACK_LINES + 2)
    }
}
