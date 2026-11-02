package com.thalzeronlabs.shelf.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.thalzeronlabs.shelf.data.local.BookEntity
import com.thalzeronlabs.shelf.data.local.SearchRow
import com.thalzeronlabs.shelf.data.local.ShelfDatabase
import com.thalzeronlabs.shelf.data.remote.OpenLibraryApi
import com.thalzeronlabs.shelf.data.sync.SyncRequester
import com.thalzeronlabs.shelf.model.Book
import com.thalzeronlabs.shelf.model.Clock
import com.thalzeronlabs.shelf.model.ReadingStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The only way the UI reaches data. Room is the single source of truth: everything the UI observes is a Room
 * query, and the network only ever writes into Room (search pages through the RemoteMediator, the shelf
 * through the background sync). That is what makes the app work offline without special cases.
 */
interface ShelfRepository {
    /** Paged results for [query], each with its shelf status. Cached pages show at once, also offline. */
    fun search(query: String): Flow<PagingData<Book>>
    fun shelf(): Flow<List<Book>>
    suspend fun save(book: Book, status: ReadingStatus)
    suspend fun setStatus(id: String, status: ReadingStatus)
    suspend fun remove(id: String)
}

class DefaultShelfRepository(
    private val db: ShelfDatabase,
    private val api: OpenLibraryApi,
    private val clock: Clock,
    private val sync: SyncRequester,
) : ShelfRepository {

    private val books = db.books()

    @OptIn(ExperimentalPagingApi::class)
    override fun search(query: String): Flow<PagingData<Book>> {
        val key = searchKey(query)
        return Pager(
            config = PagingConfig(
                pageSize = OpenLibraryApi.PAGE_SIZE,
                initialLoadSize = OpenLibraryApi.PAGE_SIZE,
                prefetchDistance = OpenLibraryApi.PAGE_SIZE / 2,
                enablePlaceholders = false,
            ),
            remoteMediator = SearchRemoteMediator(query, db, api, clock),
            pagingSourceFactory = { db.searchCache().pagingSource(key) },
        ).flow.map { page -> page.map(SearchRow::toBook) }
    }

    override fun shelf(): Flow<List<Book>> = books.observeAll().map { rows ->
        rows.map { Book(it.id, it.title, it.authors, it.year, it.coverUrl, it.status) }
    }

    /** Saved locally first; the sync then fills in fresh catalogue data once there is a network. */
    override suspend fun save(book: Book, status: ReadingStatus) {
        books.upsert(BookEntity(book.id, book.title, book.authors, book.year, book.coverUrl, status, clock.now()))
        sync.requestSync()
    }

    override suspend fun setStatus(id: String, status: ReadingStatus) = books.setStatus(id, status)

    override suspend fun remove(id: String) = books.delete(id)
}

internal fun SearchRow.toBook() = Book(
    id = id,
    title = title,
    authors = authors,
    year = year,
    coverUrl = coverUrl,
    status = status?.let { name -> ReadingStatus.entries.firstOrNull { it.name == name } },
)
