package com.khalinux.runtime.files

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFileManagerTest {
    private fun root(): File = Files.createTempDirectory("home").toFile().also { it.deleteOnExit() }

    @Test
    fun writesReadsListsAndSortsDirectoriesFirst() {
        val m = HomeFileManager(root())
        assertTrue(m.mkdir("docs"))
        assertTrue(m.writeText("b.txt", "oi"))
        assertTrue(m.writeText("docs/a.txt", "dentro"))
        assertEquals(listOf("docs", "b.txt"), m.list()!!.map { it.name })
        assertEquals(listOf("docs/a.txt"), m.list("docs")!!.map { it.relativePath })
        assertEquals("dentro", m.readText("docs/a.txt"))
    }

    @Test
    fun rejectsTraversalAndInvalidSegments() {
        val m = HomeFileManager(root())
        listOf("../x", "a/../../x", "a/./b", "a\u0000b").forEach {
            assertNull(it, m.resolve(it))
            assertFalse(it, m.writeText(it, "x"))
        }
        assertFalse(m.delete(""))
        assertFalse(m.mkdir(""))
    }

    @Test
    fun symlinkEscapingRootIsRejectedAndNotFollowedOnDelete() {
        val outside = Files.createTempDirectory("fora").toFile()
        File(outside, "segredo.txt").writeText("s")
        val r = root()
        Files.createSymbolicLink(File(r, "link").toPath(), outside.toPath())
        val m = HomeFileManager(r)
        assertNull(m.resolve("link"))
        assertNull(m.readText("link/segredo.txt"))
        assertTrue(m.delete("link", recursive = true))
        assertTrue(File(outside, "segredo.txt").exists())
    }

    @Test
    fun deleteRequiresRecursiveForNonEmptyDirectories() {
        val m = HomeFileManager(root())
        m.mkdir("d")
        m.writeText("d/x", "1")
        assertFalse(m.delete("d"))
        assertTrue(m.delete("d", recursive = true))
        assertNull(m.list("d"))
    }

    @Test
    fun renameStaysInFolderAndRefusesOverwrite() {
        val m = HomeFileManager(root())
        m.writeText("a", "1")
        m.writeText("b", "2")
        assertFalse(m.rename("a", "b"))
        assertFalse(m.rename("a", "../c"))
        assertTrue(m.rename("a", "c"))
        assertEquals("1", m.readText("c"))
    }

    @Test
    fun refusesBinaryAndOversizedFiles() {
        val r = root()
        val m = HomeFileManager(r, maxTextBytes = 10)
        File(r, "bin").writeBytes(byteArrayOf(65, 0, 66))
        assertNull(m.readText("bin"))
        File(r, "grande").writeText("x".repeat(11))
        assertNull(m.readText("grande"))
        assertFalse(m.writeText("novo", "x".repeat(11)))
    }
}
