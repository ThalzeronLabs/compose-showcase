package com.thalzeronlabs.shelf.feature.shelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.thalzeronlabs.shelf.model.Book
import com.thalzeronlabs.shelf.model.ReadingStatus
import com.thalzeronlabs.shelf.data.ShelfRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShelfState(
    val loading: Boolean = true,
    /** Books grouped by status, in the order of [ReadingStatus]; empty groups are left out. */
    val sections: List<Pair<ReadingStatus, List<Book>>> = emptyList(),
) {
    val total: Int get() = sections.sumOf { it.second.size }
    fun count(status: ReadingStatus) = sections.firstOrNull { it.first == status }?.second?.size ?: 0
}

@HiltViewModel
class ShelfViewModel @Inject constructor(private val repository: ShelfRepository) : ViewModel() {

    val state: StateFlow<ShelfState> = repository.shelf()
        .map { books ->
            val byStatus = books.groupBy { it.status ?: ReadingStatus.WANT }
            ShelfState(
                loading = false,
                sections = ReadingStatus.entries.mapNotNull { status -> byStatus[status]?.let { status to it } },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShelfState())

    fun setStatus(book: Book, status: ReadingStatus) {
        viewModelScope.launch { repository.setStatus(book.id, status) }
    }

    fun remove(book: Book) {
        viewModelScope.launch { repository.remove(book.id) }
    }
}
