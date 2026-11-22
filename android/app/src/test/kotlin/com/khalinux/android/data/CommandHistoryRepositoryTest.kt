package com.khalinux.android.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandHistoryRepositoryTest {
    @Test
    fun recordsTrimmedCommandsAndKeepsOnlyOneHundred() = runBlocking {
        val dao = FakeCommandHistoryDao()
        val repository = CommandHistoryRepository(dao)

        repeat(105) { repository.record("  echo $it  ") }

        assertEquals(100, dao.entries.size)
        assertEquals("echo 104", dao.entries.maxBy { it.id }.command)
        assertTrue(dao.entries.none { it.command.startsWith("echo 0") })
    }

    @Test
    fun ignoresBlankCommands() = runBlocking {
        val dao = FakeCommandHistoryDao()
        CommandHistoryRepository(dao).record("   ")

        assertTrue(dao.entries.isEmpty())
    }

    @Test
    fun exposesOnlyTwelveMostRecentCommands() = runBlocking {
        val dao = FakeCommandHistoryDao()
        val repository = CommandHistoryRepository(dao)
        repeat(15) { repository.record("cmd-$it") }

        val recent = dao.observeRecentSnapshot()
        assertEquals(12, recent.size)
        assertEquals("cmd-14", recent.first().command)
        assertEquals("cmd-3", recent.last().command)
    }

    private class FakeCommandHistoryDao : CommandHistoryDao {
        val entries = mutableListOf<CommandHistoryEntry>()
        private var nextId = 1L

        override suspend fun insert(entry: CommandHistoryEntry) {
            entries += entry.copy(id = nextId++)
        }

        override fun observeRecent(): Flow<List<CommandHistoryEntry>> =
            flowOf(observeRecentSnapshot())

        fun observeRecentSnapshot(): List<CommandHistoryEntry> =
            entries.sortedWith(compareByDescending<CommandHistoryEntry> { it.createdAt }.thenByDescending { it.id })
                .take(12)

        override suspend fun prune(maxEntries: Int) {
            val keep = entries.sortedWith(
                compareByDescending<CommandHistoryEntry> { it.createdAt }.thenByDescending { it.id }
            ).take(maxEntries).map { it.id }.toSet()
            entries.removeAll { it.id !in keep }
        }
    }
}
