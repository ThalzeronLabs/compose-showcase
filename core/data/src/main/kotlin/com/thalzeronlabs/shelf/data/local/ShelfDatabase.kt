package com.thalzeronlabs.shelf.data.local

import androidx.paging.PagingSource
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import com.thalzeronlabs.shelf.model.ReadingStatus
import kotlinx.coroutines.flow.Flow

/**
 * A book on the user's shelf. [status] and [addedAt] belong to the user; the catalogue fields (title,
 * authors, year, cover) are refreshed from Open Library by the sync, which stamps [syncedAt].
 */
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val authors: String,
    val year: Int?,
    val coverUrl: String?,
    val status: ReadingStatus,
    val addedAt: Long,
    /** When the catalogue fields were last refreshed from Open Library; null means never. */
    val syncedAt: Long? = null,
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

    /** Books whose catalogue data was never refreshed, or not since [before], oldest first. */
    @Query("SELECT id FROM books WHERE syncedAt IS NULL OR syncedAt < :before ORDER BY addedAt")
    suspend fun idsToSync(before: Long): List<String>

    /**
     * Writes fresh catalogue data for one book. It is an UPDATE, never an upsert: the user's status is not
     * touched, and a book removed while the sync was in flight stays removed. A year or cover that Open
     * Library no longer returns keeps its old value.
     */
    @Query(
        "UPDATE books SET title = :title, authors = :authors, year = COALESCE(:year, year), " +
            "coverUrl = COALESCE(:coverUrl, coverUrl), syncedAt = :syncedAt WHERE id = :id",
    )
    suspend fun updateCatalogue(id: String, title: String, authors: String, year: Int?, coverUrl: String?, syncedAt: Long)

    /** For a book Open Library did not return (merged or removed works): keep the local data, stop asking. */
    @Query("UPDATE books SET syncedAt = :syncedAt WHERE id = :id")
    suspend fun markSynced(id: String, syncedAt: Long)

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: String): BookEntity?
}

/**
 * One row of a search result list. The rows for a query are what the search screen pages through, so
 * results already loaded are still there offline. [query] is the normalized query (trimmed, lowercase)
 * and [position] keeps the API's ranking.
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

/** Where the next page of a query starts. [nextOffset] is null once Open Library has no more results. */
@Entity(tableName = "search_remote_keys")
data class SearchRemoteKey(
    @PrimaryKey val query: String,
    val nextOffset: Int?,
)

/** A search result joined with the shelf, so the list shows each book's status without a second query. */
data class SearchRow(
    val position: Int,
    val id: String,
    val title: String,
    val authors: String,
    val year: Int?,
    val coverUrl: String?,
    /** The book's [ReadingStatus] name when it is on the shelf, else null. */
    val status: String?,
)

@Dao
interface SearchCacheDao {
    /**
     * The paged list the UI shows. Room invalidates it when either table changes, so saving a book updates
     * its badge and every page the RemoteMediator writes appears without any manual refresh.
     */
    @Query(
        "SELECT c.position, c.id, c.title, c.authors, c.year, c.coverUrl, b.status FROM search_cache c " +
            "LEFT JOIN books b ON b.id = c.id WHERE c.`query` = :query ORDER BY c.position",
    )
    fun pagingSource(query: String): PagingSource<Int, SearchRow>

    @Query("SELECT * FROM search_cache WHERE `query` = :query ORDER BY position")
    suspend fun results(query: String): List<CachedBookEntity>

    @Query("SELECT id FROM search_cache WHERE `query` = :query")
    suspend fun ids(query: String): List<String>

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM search_cache WHERE `query` = :query")
    suspend fun nextPosition(query: String): Int

    @Insert
    suspend fun insertAll(rows: List<CachedBookEntity>)

    @Query("DELETE FROM search_cache WHERE `query` = :query")
    suspend fun clear(query: String)

    @Query("SELECT * FROM search_remote_keys WHERE `query` = :query")
    suspend fun remoteKey(query: String): SearchRemoteKey?

    @Upsert
    suspend fun upsertRemoteKey(key: SearchRemoteKey)
}

class Converters {
    @TypeConverter fun fromStatus(status: ReadingStatus): String = status.name
    @TypeConverter fun toStatus(value: String): ReadingStatus = ReadingStatus.valueOf(value)
}

/**
 * Version history (schemas are exported to `core/data/schemas` and checked by `ShelfMigrationTest`):
 * 1. `books`, the user's shelf.
 * 2. adds `search_cache` for offline search.
 * 3. adds `search_remote_keys` for paging and `books.syncedAt` for the background sync.
 * Every step is additive, so Room generates the migrations.
 */
@Database(
    entities = [BookEntity::class, CachedBookEntity::class, SearchRemoteKey::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
@TypeConverters(Converters::class)
abstract class ShelfDatabase : RoomDatabase() {
    abstract fun books(): BookDao
    abstract fun searchCache(): SearchCacheDao

    companion object {
        const val NAME = "shelf.db"
    }
}
