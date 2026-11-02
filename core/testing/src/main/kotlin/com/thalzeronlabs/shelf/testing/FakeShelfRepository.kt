package com.thalzeronlabs.shelf.testing

import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import com.thalzeronlabs.shelf.data.ShelfRepository
import com.thalzeronlabs.shelf.model.Book
import com.thalzeronlabs.shelf.model.ReadingStatus
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

/** An in-memory [ShelfRepository] for ViewModel tests: no Room, no network, every call recorded. */
class FakeShelfRepository(var results: List<Book> = emptyList()) : ShelfRepository {
    /** Per-query results; queries not listed here get [results]. */
    val resultsFor = mutableMapOf<String, List<Book>>()

    /** Every query a search flow was started for, in order. */
    val searches = mutableListOf<String>()

    /** Queries whose search flow was collected and then cancelled (the user typed something else). */
    val cancelled = mutableListOf<String>()

    val shelf = MutableStateFlow<List<Book>>(emptyList())

    /**
     * What the network reports for every search, as the real repository's RemoteMediator would: by default the
     * refresh finished and there is nothing more to load; a [LoadState.Error] plays "offline".
     */
    var network: LoadState = LoadState.NotLoading(endOfPaginationReached = true)

    override fun search(query: String): Flow<PagingData<Book>> = flow {
        searches += query
        try {
            val done = LoadState.NotLoading(endOfPaginationReached = true)
            emit(
                PagingData.from(
                    resultsFor[query] ?: results,
                    sourceLoadStates = LoadStates(refresh = done, prepend = done, append = done),
                    mediatorLoadStates = LoadStates(refresh = network, prepend = done, append = done),
                ),
            )
            awaitCancellation()
        } finally {
            cancelled += query
        }
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

fun book(id: String, status: ReadingStatus? = null) =
    Book(id = id, title = "Book $id", authors = "Author", year = 2020, coverUrl = null, status = status)
