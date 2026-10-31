package com.thalzeronlabs.shelf.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thalzeronlabs.shelf.Book
import com.thalzeronlabs.shelf.ReadingStatus
import com.thalzeronlabs.shelf.data.ShelfRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Results(val books: List<Book>) : SearchState
    data class Empty(val query: String) : SearchState
    data class Error(val message: String) : SearchState
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(private val repository: ShelfRepository) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Bumped by [retry] so the same query runs again. */
    private val attempt = MutableStateFlow(0)

    private val remote = combine(_query.debounce(DEBOUNCE_MS).map { it.trim() }.distinctUntilChanged(), attempt) { q, _ -> q }
        .flatMapLatest { q ->
            if (q.length < MIN_QUERY) {
                flowOf<SearchState>(SearchState.Idle)
            } else {
                flow<SearchState> {
                    emit(SearchState.Loading)
                    emit(
                        runCatching { repository.search(q) }.fold(
                            onSuccess = { if (it.isEmpty()) SearchState.Empty(q) else SearchState.Results(it) },
                            onFailure = { SearchState.Error("Couldn't reach Open Library. Check your connection.") },
                        )
                    )
                }
            }
        }

    /** Search results, each marked with its status when the book is already on the shelf. */
    val state: StateFlow<SearchState> = combine(remote, repository.shelf()) { result, shelf ->
        if (result !is SearchState.Results) return@combine result
        val saved = shelf.associate { it.id to it.status }
        SearchState.Results(result.books.map { it.copy(status = saved[it.id]) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchState.Idle)

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun retry() {
        attempt.value++
    }

    fun save(book: Book, status: ReadingStatus) {
        viewModelScope.launch { repository.save(book, status) }
    }

    companion object {
        const val DEBOUNCE_MS = 400L
        const val MIN_QUERY = 2
    }
}
