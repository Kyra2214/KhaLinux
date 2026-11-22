package com.khalinux.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalEmulatorTest {
    @Test
    fun rendersPlainTextAndCarriageReturnProgressUpdates() {
        val terminal = TerminalEmulator(rows = 3, columns = 12)
        terminal.appendText("hello\rworld")

        assertEquals("world", terminal.plainText())
    }

    @Test
    fun parsesAnsiForegroundBoldAndReset() {
        val terminal = TerminalEmulator(rows = 2, columns = 20)
        terminal.appendText("\u001B[1;31mERROR\u001B[0m normal")

        val line = terminal.snapshot().lines[0]
        assertEquals(0xffcd0000.toInt(), line[0].style.foreground)
        assertTrue(line[0].style.bold)
        assertEquals(TerminalStyle(), line[5].style)
        assertTrue(terminal.plainText().contains("ERROR normal"))
    }

    @Test
    fun parsesTrueColorAndCursorPosition() {
        val terminal = TerminalEmulator(rows = 3, columns = 10)
        terminal.appendText("abc\u001B[1;2H\u001B[38;2;1;2;3mXY")

        val snapshot = terminal.snapshot()
        assertEquals("aXY", snapshot.lines[0].take(3).joinToString("") { it.text })
        assertEquals(0xff010203.toInt(), snapshot.lines[0][1].style.foreground)
    }

    @Test
    fun erasesLineFromCurrentCursorPosition() {
        val terminal = TerminalEmulator(rows = 2, columns = 12)
        terminal.appendText("abcdef\r\u001B[3C\u001B[K")

        assertEquals("abc", terminal.plainText())
    }

    @Test
    fun decodesUtf8AcrossSeparatePtyChunks() {
        val terminal = TerminalEmulator(rows = 2, columns = 20)
        val bytes = "ação".toByteArray(Charsets.UTF_8)
        terminal.append(bytes.copyOfRange(0, 2))
        terminal.append(bytes.copyOfRange(2, bytes.size))
        terminal.finishInput()

        assertEquals("ação", terminal.plainText())
    }

    @Test
    fun ignoresOscWindowTitleSequences() {
        val terminal = TerminalEmulator(rows = 2, columns = 20)
        terminal.appendText("before\u001B]0;ignored title\u0007after")

        assertEquals("beforeafter", terminal.plainText())
    }

    @Test
    fun keepsScrollbackBounded() {
        val terminal = TerminalEmulator(rows = 2, columns = 12, maxScrollbackLines = 2)
        terminal.appendText("one\r\ntwo\r\nthree\r\nfour\r\nfive")

        assertTrue(terminal.snapshot().lines.size <= 4)
        assertTrue(terminal.plainText().contains("five"))
    }

    @Test
    fun supportsCursorVisibilityAndResize() {
        val terminal = TerminalEmulator(rows = 2, columns = 8)
        terminal.appendText("\u001B[?25l")
        assertFalse(terminal.snapshot().cursorVisible)

        terminal.resize(rows = 4, columns = 16)
        assertEquals(4, terminal.snapshot().rows)
        assertEquals(16, terminal.snapshot().columns)
    }

    @Test
    fun clearScreenRemovesVisibleTextAndAnsiControls() {
        val terminal = TerminalEmulator(rows = 2, columns = 12)
        terminal.appendText("secret\u001B[2J")

        assertEquals("", terminal.plainText())
    }
}
