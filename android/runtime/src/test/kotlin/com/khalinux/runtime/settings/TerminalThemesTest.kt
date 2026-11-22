package com.khalinux.runtime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalThemesTest {
    @Test
    fun indicesAreSequentialAndUnique() {
        assertEquals(TerminalThemes.all.indices.toList(), TerminalThemes.all.map { it.index })
    }

    @Test
    fun invalidIndexFallsBackToDefault() {
        assertEquals(TerminalThemes.all[0], TerminalThemes.byIndex(-1))
        assertEquals(TerminalThemes.all[0], TerminalThemes.byIndex(999))
        assertEquals("Claro", TerminalThemes.byIndex(1).label)
    }

    @Test
    fun foregroundDiffersFromBackgroundAndIsOpaque() {
        TerminalThemes.all.forEach {
            assertNotEquals(it.background, it.foreground)
            assertTrue((it.background ushr 24) == 0xFF && (it.foreground ushr 24) == 0xFF)
        }
    }
}
