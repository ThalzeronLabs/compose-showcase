package com.thalzeronlabs.shelf.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.thalzeronlabs.shelf.data.ShelfRepository
import com.thalzeronlabs.shelf.model.Book
import com.thalzeronlabs.shelf.model.ReadingStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(private val repository: ShelfRepository) : ViewModel() {

    private val _query = MutableStateFlow("")

    /** Exactly what is in the search field. */
    val query: StateFlow<String> = _query.asStateFlow()

    /** The query being searched: debounced and trimmed, or null while it is too short to search. */
    val activeQuery: StateFlow<String?> = _query
        .debounce(DEBOUNCE_MS)
        .map { it.trim().takeIf { q -> q.length >= MIN_QUERY } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * The results for [activeQuery], paged from Room. `flatMapLatest` drops the previous query's pager (and
     * its network request) as soon as the query changes; `cachedIn` keeps the loaded pages across
     * configuration changes. Shelf badges come from the repository's join, so saving a book updates its row.
     */
    val results: Flow<PagingData<Book>> = activeQuery
        .flatMapLatest { q -> if (q == null) flowOf(PagingData.empty()) else repository.search(q) }
        .cachedIn(viewModelScope)

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun save(book: Book, status: ReadingStatus) {
        viewModelScope.launch { repository.save(book, status) }
    }

    companion object {
        const val DEBOUNCE_MS = 400L
        const val MIN_QUERY = 2
    }
}
