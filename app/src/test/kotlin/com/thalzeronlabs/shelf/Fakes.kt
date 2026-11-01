package com.thalzeronlabs.shelf

import com.thalzeronlabs.shelf.data.SearchResult
import com.thalzeronlabs.shelf.data.ShelfRepository
import com.thalzeronlabs.shelf.data.local.BookDao
import com.thalzeronlabs.shelf.data.local.BookEntity
import com.thalzeronlabs.shelf.data.local.CachedBookEntity
import com.thalzeronlabs.shelf.data.local.SearchCacheDao
import com.thalzeronlabs.shelf.data.remote.DocDto
import com.thalzeronlabs.shelf.data.remote.OpenLibraryApi
import com.thalzeronlabs.shelf.data.remote.SearchResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

internal class FakeApi(var docs: List<DocDto> = emptyList()) : OpenLibraryApi {
    /** Thrown instead of answering, e.g. an [IOException] for "no connection". */
    var failure: Throwable? = null
    val queries = mutableListOf<String>()
    override suspend fun search(query: String, limit: Int, fields: String): SearchResponse {
        queries += query
        failure?.let { throw it }
        return SearchResponse(docs)
    }
}

internal fun offline() = IOException("Unable to resolve host openlibrary.org")
internal fun httpError(code: Int) = HttpException(Response.error<SearchResponse>(code, "".toResponseBody()))

internal class FakeSearchCacheDao : SearchCacheDao {
    val rows = mutableListOf<CachedBookEntity>()
    override suspend fun results(query: String) = rows.filter { it.query == query }.sortedBy { it.position }
    override suspend fun insertAll(rows: List<CachedBookEntity>) {
        this.rows += rows
    }
    override suspend fun clear(query: String) {
        rows.removeAll { it.query == query }
    }
    override suspend fun replace(query: String, rows: List<CachedBookEntity>) {
        clear(query)
        insertAll(rows)
    }
}

internal class FakeDao : BookDao {
    val rows = MutableStateFlow<List<BookEntity>>(emptyList())
    override fun observeAll(): Flow<List<BookEntity>> = rows.map { list -> list.sortedByDescending { it.addedAt } }
    override suspend fun upsert(book: BookEntity) {
        rows.value = rows.value.filter { it.id != book.id } + book
    }
    override suspend fun setStatus(id: String, status: ReadingStatus) {
        rows.value = rows.value.map { if (it.id == id) it.copy(status = status) else it }
    }
    override suspend fun delete(id: String) {
        rows.value = rows.value.filter { it.id != id }
    }
}

internal class FakeRepository(results: List<Book> = emptyList()) : ShelfRepository {
    var results = results
    var fail = false
    /** When set, searches answer as if offline with results cached at this time. */
    var cachedAt: Long? = null
    /** Simulated network time, in virtual (test dispatcher) milliseconds. */
    var latency = 0L
    /** Per-query answers; queries not listed here get [results]. */
    val resultsFor = mutableMapOf<String, List<Book>>()
    val searches = mutableListOf<String>()
    val shelf = MutableStateFlow<List<Book>>(emptyList())

    override suspend fun search(query: String): SearchResult {
        searches += query
        delay(latency)
        if (fail) throw offline()
        return SearchResult(resultsFor[query] ?: results, cachedAt)
    }
    override fun shelf(): Flow<List<Book>> = shelf
    override suspend fun save(book: Book, status: ReadingStatus) {
        shelf.value = shelf.value.filter { it.id != book.id } + book.copy(status = status)
    }
    override suspend fun setStatus(id: String, status: ReadingStatus) {
        shelf.value = shelf.value.map { if (it.id == id) it.copy(status = status) else it }
    }
    override suspend fun remove(id: String) {
        shelf.value = shelf.value.filter { it.id != id }
    }
}

internal fun book(id: String, status: ReadingStatus? = null) =
    Book(id = id, title = "Book $id", authors = "Author", year = 2020, coverUrl = null, status = status)
