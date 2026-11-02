package com.thalzeronlabs.shelf.feature.search

import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState

/** What the search screen shows, worked out from the query and Paging's load states. */
sealed interface SearchStatus {
    /** Nothing to search yet. */
    data object Idle : SearchStatus

    /** Searching, with nothing on the device to show meanwhile. */
    data object Loading : SearchStatus

    /** Open Library has no results for [query]. */
    data class Empty(val query: String) : SearchStatus

    /** The search failed and nothing is cached for it. */
    data class Error(val message: String) : SearchStatus

    /**
     * There are rows to show. [refreshing]: fresh results are on their way. [refreshFailed]: they could not be
     * fetched, so these are the results saved on this device.
     */
    data class Results(val refreshing: Boolean = false, val refreshFailed: Boolean = false) : SearchStatus
}

const val NETWORK_ERROR = "Couldn't reach Open Library. Check your connection."

/**
 * With a RemoteMediator, `loadState.mediator` is the network and `loadState.source` is Room. What the user sees
 * depends on both: cached rows are shown whatever the network does, and only with no rows at all does the
 * network state decide between loading, empty and error.
 */
fun searchStatus(activeQuery: String?, loadState: CombinedLoadStates, itemCount: Int): SearchStatus {
    if (activeQuery == null) return SearchStatus.Idle
    val network = loadState.mediator?.refresh
    if (itemCount > 0) {
        return SearchStatus.Results(refreshing = network is LoadState.Loading, refreshFailed = network is LoadState.Error)
    }
    return when {
        network is LoadState.Error -> SearchStatus.Error(NETWORK_ERROR)
        // No mediator state yet: this query's pager has not emitted (the list still holds the previous one).
        network == null || network is LoadState.Loading || loadState.source.refresh is LoadState.Loading -> SearchStatus.Loading
        else -> SearchStatus.Empty(activeQuery)
    }
}
