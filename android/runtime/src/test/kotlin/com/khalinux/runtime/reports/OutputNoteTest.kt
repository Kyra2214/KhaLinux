package com.khalinux.runtime.reports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputNoteTest {
    @Test
    fun blankOutputProducesNoNote() {
        assertNull(OutputNote.build(0L, "x", null, "  \n\n"))
    }

    @Test
    fun wrapsOutputInFenceLongerThanAnyBacktickRun() {
        val note = OutputNote.build(0L, "Teste", "ls `x`", "a ```` b")!!
        assertTrue(note.contains("`````text\n"))
        assertTrue(note.contains("Comando: `ls 'x'`"))
        assertTrue(note.startsWith("# Teste\n"))
    }

    @Test
    fun truncatesKeepingTheEnd() {
        val big = "x".repeat(OutputNote.MAX_OUTPUT_CHARS) + "FIM"
        val note = OutputNote.build(0L, "t", null, big)!!
        assertTrue(note.contains("Saída truncada"))
        assertTrue(note.contains("FIM"))
        assertEquals(OutputNote.MAX_OUTPUT_CHARS, note.substringAfter("text\n").substringBeforeLast("\n```").length)
    }
}
