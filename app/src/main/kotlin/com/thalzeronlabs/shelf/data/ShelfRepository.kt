package com.thalzeronlabs.shelf.data

import com.thalzeronlabs.shelf.Book
import com.thalzeronlabs.shelf.ReadingStatus
import com.thalzeronlabs.shelf.data.local.BookDao
import com.thalzeronlabs.shelf.data.local.BookEntity
import com.thalzeronlabs.shelf.data.local.CachedBookEntity
import com.thalzeronlabs.shelf.data.local.SearchCacheDao
import com.thalzeronlabs.shelf.data.remote.DocDto
import com.thalzeronlabs.shelf.data.remote.OpenLibraryApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import java.io.IOException

/** What a search returns. [cachedAt] is set when the network failed and these are the last saved results. */
data class SearchResult(val books: List<Book>, val cachedAt: Long? = null) {
    val fromCache: Boolean get() = cachedAt != null
}

/** The single source of truth for books: search goes to the network (with an offline cache), the shelf lives in Room. */
interface ShelfRepository {
    suspend fun search(query: String): SearchResult
    fun shelf(): Flow<List<Book>>
    suspend fun save(book: Book, status: ReadingStatus)
    suspend fun setStatus(id: String, status: ReadingStatus)
    suspend fun remove(id: String)
}

class DefaultShelfRepository(
    private val api: OpenLibraryApi,
    private val dao: BookDao,
    private val cache: SearchCacheDao,
    private val clock: () -> Long,
) : ShelfRepository {

    /**
     * Network first. Fresh results replace the cached ones for that query; when the network fails
     * (no connection, or an HTTP error), the last saved results come back instead, marked with when
     * they were saved. With nothing cached, the original error is rethrown so the UI can show it.
     */
    override suspend fun search(query: String): SearchResult {
        val q = query.trim()
        val key = q.lowercase()
        val books = try {
            api.search(q).docs.mapNotNull(::toBook)
        } catch (e: IOException) {
            return cached(key) ?: throw e
        } catch (e: HttpException) {
            return cached(key) ?: throw e
        }
        val now = clock()
        cache.replace(
            key,
            books.mapIndexed { i, b -> CachedBookEntity(key, i, b.id, b.title, b.authors, b.year, b.coverUrl, now) },
        )
        return SearchResult(books)
    }

    private suspend fun cached(key: String): SearchResult? {
        val rows = cache.results(key).ifEmpty { return null }
        return SearchResult(
            books = rows.map { Book(it.id, it.title, it.authors, it.year, it.coverUrl) },
            cachedAt = rows.first().cachedAt,
        )
    }

    private fun toBook(doc: DocDto): Book? {
        val title = doc.title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return Book(
            id = doc.key.removePrefix("/works/"),
            title = title,
            authors = doc.authorName?.take(2)?.joinToString(", ") ?: "Unknown author",
            year = doc.firstPublishYear,
            coverUrl = doc.coverId?.let(OpenLibraryApi::coverUrl),
        )
    }

    override fun shelf(): Flow<List<Book>> = dao.observeAll().map { rows ->
        rows.map { Book(it.id, it.title, it.authors, it.year, it.coverUrl, it.status) }
    }

    override suspend fun save(book: Book, status: ReadingStatus) =
        dao.upsert(BookEntity(book.id, book.title, book.authors, book.year, book.coverUrl, status, clock()))

    override suspend fun setStatus(id: String, status: ReadingStatus) = dao.setStatus(id, status)

    override suspend fun remove(id: String) = dao.delete(id)
}
