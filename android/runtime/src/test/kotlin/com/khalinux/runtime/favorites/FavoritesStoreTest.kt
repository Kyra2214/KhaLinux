package com.khalinux.runtime.favorites

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesStoreTest {
    private fun tempFile(): File = File.createTempFile("favorites", ".tsv").also { it.delete(); it.deleteOnExit() }

    @Test
    fun addsPersistsAndListsNewestFirst() {
        val file = tempFile()
        val store = FavoritesStore(file)
        assertTrue(store.add(FavoriteKind.COMMAND, "ls -la"))
        assertTrue(store.add(FavoriteKind.COMMAND, "uname -a"))
        assertTrue(store.add(FavoriteKind.TOOL, "nmap"))
        val reloaded = FavoritesStore(file)
        assertEquals(listOf("uname -a", "ls -la"), reloaded.list(FavoriteKind.COMMAND).map { it.value })
        assertEquals(listOf("nmap"), reloaded.list(FavoriteKind.TOOL).map { it.value })
    }

    @Test
    fun rejectsDuplicatesBlankControlAndTooLong() {
        val store = FavoritesStore(tempFile())
        assertTrue(store.add(FavoriteKind.COMMAND, "  id  "))
        assertFalse(store.add(FavoriteKind.COMMAND, "id"))
        assertFalse(store.add(FavoriteKind.COMMAND, "   "))
        assertFalse(store.add(FavoriteKind.COMMAND, "a\nb"))
        assertFalse(store.add(FavoriteKind.COMMAND, "a\tb"))
        assertFalse(store.add(FavoriteKind.COMMAND, "x".repeat(FavoritesStore.MAX_VALUE_LENGTH + 1)))
        assertTrue(store.add(FavoriteKind.TOOL, "id"))
    }

    @Test
    fun enforcesLimitPerKind() {
        val store = FavoritesStore(tempFile(), maxPerKind = 2)
        assertTrue(store.add(FavoriteKind.COMMAND, "a"))
        assertTrue(store.add(FavoriteKind.COMMAND, "b"))
        assertFalse(store.add(FavoriteKind.COMMAND, "c"))
        assertTrue(store.add(FavoriteKind.TOOL, "c"))
    }

    @Test
    fun toggleAndRemove() {
        val store = FavoritesStore(tempFile())
        assertTrue(store.toggle(FavoriteKind.TOOL, "whois"))
        assertTrue(store.contains(FavoriteKind.TOOL, "whois"))
        assertFalse(store.toggle(FavoriteKind.TOOL, "whois"))
        assertFalse(store.remove(FavoriteKind.TOOL, "whois"))
    }

    @Test
    fun ignoresCorruptLines() {
        val file = tempFile()
        file.writeText("lixo\nOUTRO\tx\nCOMMAND\tok\n\tvazio\n", Charsets.UTF_8)
        assertEquals(listOf(Favorite(FavoriteKind.COMMAND, "ok")), FavoritesStore(file).list())
    }
}
