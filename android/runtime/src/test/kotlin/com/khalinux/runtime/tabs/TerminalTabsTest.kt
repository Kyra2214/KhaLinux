package com.khalinux.runtime.tabs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalTabsTest {
    @Test
    fun startsWithOneActiveTab() {
        val tabs = TerminalTabs()
        assertEquals(1, tabs.infos().size)
        assertEquals("Terminal 1", tabs.active().title)
    }

    @Test
    fun createsUpToLimitAndActivatesNewTab() {
        val tabs = TerminalTabs(maxTabs = 2)
        val second = tabs.create()
        assertNotNull(second)
        assertEquals(second!!.id, tabs.activeId())
        assertNull(tabs.create())
        assertFalse(tabs.canCreate())
    }

    @Test
    fun closeKeepsAtLeastOneTabAndReturnsSessionToCancel() {
        val tabs = TerminalTabs()
        val first = tabs.active()
        assertFalse(tabs.close(first.id).closed)
        val second = tabs.create()!!
        second.sessionId = "s-2"
        val result = tabs.close(second.id)
        assertTrue(result.closed)
        assertEquals("s-2", result.sessionToCancel)
        assertEquals(first.id, tabs.activeId())
    }

    @Test
    fun closingActiveMiddleTabSelectsLeftNeighbour() {
        val tabs = TerminalTabs()
        val a = tabs.active()
        val b = tabs.create()!!
        val c = tabs.create()!!
        tabs.select(b.id)
        tabs.close(b.id)
        assertEquals(a.id, tabs.activeId())
        assertEquals(listOf(a.id, c.id), tabs.infos().map { it.id })
    }

    @Test
    fun routesBySessionAndKeepsDraftsAndOutputPerTab() {
        val tabs = TerminalTabs()
        val first = tabs.active()
        val second = tabs.create()!!
        first.sessionId = "s-1"
        second.sessionId = "s-2"
        first.draft = "ls"
        first.emulator.appendText("um")
        second.emulator.appendText("dois")
        assertEquals(first.id, tabs.findBySession("s-1")!!.id)
        assertEquals(second.id, tabs.findBySession("s-2")!!.id)
        assertNull(tabs.findBySession("x"))
        assertEquals("ls", first.draft)
        assertTrue(first.emulator.plainText().contains("um"))
        assertFalse(first.emulator.plainText().contains("dois"))
    }

    @Test
    fun renameSanitizesAndRejectsBlank() {
        val tabs = TerminalTabs()
        val id = tabs.active().id
        assertFalse(tabs.rename(id, "   "))
        assertTrue(tabs.rename(id, "  redes\u0007  "))
        assertEquals("redes", tabs.active().title)
        assertTrue(tabs.rename(id, "x".repeat(100)))
        assertEquals(MAX_TAB_TITLE_LENGTH, tabs.active().title.length)
        assertFalse(tabs.rename("inexistente", "a"))
    }

    @Test
    fun clearSessionsReportsRunningTabs() {
        val tabs = TerminalTabs()
        val first = tabs.active()
        val second = tabs.create()!!
        first.sessionId = "s-1"
        second.starting = true
        assertTrue(tabs.anyRunning())
        assertEquals(2, tabs.clearSessions().size)
        assertFalse(tabs.anyRunning())
    }

    @Test
    fun resizeAppliesToAllEmulatorsAndNewTabs() {
        val tabs = TerminalTabs()
        tabs.create()
        tabs.resizeAll(30, 100)
        assertTrue(tabs.all().all { it.emulator.snapshot().rows == 30 && it.emulator.snapshot().columns == 100 })
        assertEquals(30, tabs.create()!!.emulator.snapshot().rows)
    }
}
