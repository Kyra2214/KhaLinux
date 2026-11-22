package com.khalinux.android.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "command_history",
    indices = [Index(value = ["createdAt"])]
)
data class CommandHistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val command: String,
    val createdAt: Long
)

@Dao
interface CommandHistoryDao {
    @Insert
    suspend fun insert(entry: CommandHistoryEntry)

    @Query("SELECT * FROM command_history ORDER BY createdAt DESC, id DESC LIMIT 12")
    fun observeRecent(): Flow<List<CommandHistoryEntry>>

    @Query(
        "DELETE FROM command_history WHERE id NOT IN " +
            "(SELECT id FROM command_history ORDER BY createdAt DESC, id DESC LIMIT :maxEntries)"
    )
    suspend fun prune(maxEntries: Int)
}

@Database(entities = [CommandHistoryEntry::class], version = 1, exportSchema = false)
abstract class CommandHistoryDatabase : RoomDatabase() {
    abstract fun commandHistoryDao(): CommandHistoryDao

    companion object {
        @Volatile
        private var instance: CommandHistoryDatabase? = null

        fun getInstance(context: Context): CommandHistoryDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CommandHistoryDatabase::class.java,
                    "khalinux-local.db"
                ).build().also { instance = it }
            }
    }
}

class CommandHistoryRepository(private val dao: CommandHistoryDao) {
    suspend fun record(command: String) {
        val normalized = command.trim()
        if (normalized.isEmpty()) return
        dao.insert(CommandHistoryEntry(command = normalized, createdAt = System.currentTimeMillis()))
        dao.prune(maxEntries = 100)
    }

    fun observeRecent(): Flow<List<CommandHistoryEntry>> = dao.observeRecent()
}
