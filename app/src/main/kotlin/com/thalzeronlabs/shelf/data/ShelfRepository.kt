package com.thalzeronlabs.shelf.data

import com.thalzeronlabs.shelf.Book
import com.thalzeronlabs.shelf.ReadingStatus
import com.thalzeronlabs.shelf.data.local.BookDao
import com.thalzeronlabs.shelf.data.local.BookEntity
import com.thalzeronlabs.shelf.data.remote.OpenLibraryApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The single source of truth for books: search goes to the network, the shelf lives in Room. */
interface ShelfRepository {
    suspend fun search(query: String): List<Book>
    fun shelf(): Flow<List<Book>>
    suspend fun save(book: Book, status: ReadingStatus)
    suspend fun setStatus(id: String, status: ReadingStatus)
    suspend fun remove(id: String)
}

class DefaultShelfRepository(
    private val api: OpenLibraryApi,
    private val dao: BookDao,
    private val clock: () -> Long,
) : ShelfRepository {

    override suspend fun search(query: String): List<Book> =
        api.search(query.trim()).docs
            .filter { !it.title.isNullOrBlank() }
            .map { doc ->
                Book(
                    id = doc.key.removePrefix("/works/"),
                    title = doc.title!!.trim(),
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
