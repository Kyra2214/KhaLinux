package com.khalinux.runtime.scripts

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptStoreTest {
    private fun store(): ScriptStore {
        val dir = File.createTempFile("scripts", "").also { it.delete(); it.mkdirs(); it.deleteOnExit() }
        return ScriptStore(dir)
    }

    @Test
    fun savesReadsListsAndDeletes() {
        val s = store()
        assertEquals("ola.sh", s.save("ola", "echo oi\r\necho tchau"))
        assertEquals("echo oi\necho tchau\n", s.read("ola"))
        assertEquals("b.sh", s.save("b.sh", ""))
        assertEquals(listOf("b.sh", "ola.sh"), s.list())
        assertTrue(s.delete("ola"))
        assertFalse(s.delete("ola"))
        assertEquals(listOf("b.sh"), s.list())
    }

    @Test
    fun rejectsTraversalAndInvalidNames() {
        val s = store()
        listOf("../x", "a/b", ".oculto", "", "   ", "a b", "x;rm -rf", "a..b", "\$(id)").forEach {
            assertNull(it, s.save(it, "echo"))
            assertNull(it, s.command(it))
            assertNull(it, s.read(it))
        }
    }

    @Test
    fun rejectsOversizedOrNulContent() {
        val s = store()
        assertNull(s.save("grande", "x".repeat(ScriptStore.MAX_CONTENT_LENGTH + 1)))
        assertNull(s.save("nul", "a\u0000b"))
    }

    @Test
    fun commandPointsToGuestDirectory() {
        assertEquals("bash /home/khalinux/scripts/ola.sh", store().command("ola"))
    }
}
