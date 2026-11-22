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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

@Entity(tableName = "terminal_settings")
data class TerminalSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val fontSizeSp: Float = 12f,
    val rows: Int = 24,
    val columns: Int = 80
)

@Dao
interface TerminalSettingsDao {
    @Query("SELECT * FROM terminal_settings WHERE id = 1 LIMIT 1")
    fun observeSettings(): Flow<TerminalSettingsEntity?>

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun save(settings: TerminalSettingsEntity)
}

@Database(entities = [CommandHistoryEntry::class, TerminalSettingsEntity::class], version = 2, exportSchema = false)
abstract class CommandHistoryDatabase : RoomDatabase() {
    abstract fun commandHistoryDao(): CommandHistoryDao
    abstract fun terminalSettingsDao(): TerminalSettingsDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS terminal_settings (" +
                        "id INTEGER NOT NULL PRIMARY KEY, fontSizeSp REAL NOT NULL, " +
                        "rows INTEGER NOT NULL, columns INTEGER NOT NULL)"
                )
                database.execSQL(
                    "INSERT INTO terminal_settings (id, fontSizeSp, rows, columns) VALUES (1, 12.0, 24, 80)"
                )
            }
        }

        @Volatile
        private var instance: CommandHistoryDatabase? = null

        fun getInstance(context: Context): CommandHistoryDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CommandHistoryDatabase::class.java,
                    "khalinux-local.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
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

class TerminalSettingsRepository(private val dao: TerminalSettingsDao) {
    fun observeSettings(): Flow<TerminalSettingsEntity?> = dao.observeSettings()

    suspend fun save(settings: TerminalSettingsEntity) = dao.save(settings)
}
