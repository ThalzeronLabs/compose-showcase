package com.thalzeronlabs.shelf

import com.thalzeronlabs.shelf.data.ShelfRepository
import com.thalzeronlabs.shelf.data.local.BookDao
import com.thalzeronlabs.shelf.data.local.BookEntity
import com.thalzeronlabs.shelf.data.remote.DocDto
import com.thalzeronlabs.shelf.data.remote.OpenLibraryApi
import com.thalzeronlabs.shelf.data.remote.SearchResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

internal class FakeApi(var docs: List<DocDto> = emptyList(), var fail: Boolean = false) : OpenLibraryApi {
    val queries = mutableListOf<String>()
    override suspend fun search(query: String, limit: Int, fields: String): SearchResponse {
        queries += query
        if (fail) error("offline")
        return SearchResponse(docs)
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
    val searches = mutableListOf<String>()
    val shelf = MutableStateFlow<List<Book>>(emptyList())

    override suspend fun search(query: String): List<Book> {
        searches += query
        if (fail) error("offline")
        return results
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
