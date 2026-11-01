package com.thalzeronlabs.shelf.data.local

import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import com.thalzeronlabs.shelf.ReadingStatus
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val authors: String,
    val year: Int?,
    val coverUrl: String?,
    val status: ReadingStatus,
    val addedAt: Long,
)

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Upsert
    suspend fun upsert(book: BookEntity)

    @Query("UPDATE books SET status = :status WHERE id = :id")
    suspend fun setStatus(id: String, status: ReadingStatus)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: String)
}

/**
 * One row of a cached search result, so the last results for a query are still there offline.
 * [query] is the normalized query (trimmed, lowercase) and [position] keeps the API's ranking.
 */
@Entity(tableName = "search_cache", primaryKeys = ["query", "position"])
data class CachedBookEntity(
    val query: String,
    val position: Int,
    val id: String,
    val title: String,
    val authors: String,
    val year: Int?,
    val coverUrl: String?,
    val cachedAt: Long,
)

@Dao
interface SearchCacheDao {
    @Query("SELECT * FROM search_cache WHERE `query` = :query ORDER BY position")
    suspend fun results(query: String): List<CachedBookEntity>

    @Insert
    suspend fun insertAll(rows: List<CachedBookEntity>)

    @Query("DELETE FROM search_cache WHERE `query` = :query")
    suspend fun clear(query: String)

    /** Swaps the cached results for [query] atomically, so a shorter new list never leaves stale rows behind. */
    @Transaction
    suspend fun replace(query: String, rows: List<CachedBookEntity>) {
        clear(query)
        insertAll(rows)
    }
}

class Converters {
    @TypeConverter fun fromStatus(status: ReadingStatus): String = status.name
    @TypeConverter fun toStatus(value: String): ReadingStatus = ReadingStatus.valueOf(value)
}

/**
 * Version history (schemas are exported to `app/schemas` and checked by `ShelfMigrationTest`):
 * 1. `books`, the user's shelf.
 * 2. adds `search_cache` for offline search. Additive, so Room generates the migration.
 */
@Database(
    entities = [BookEntity::class, CachedBookEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@TypeConverters(Converters::class)
abstract class ShelfDatabase : RoomDatabase() {
    abstract fun books(): BookDao
    abstract fun searchCache(): SearchCacheDao

    companion object {
        const val NAME = "shelf.db"
    }
}
