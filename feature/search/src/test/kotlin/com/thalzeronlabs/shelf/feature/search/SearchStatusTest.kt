package com.thalzeronlabs.shelf.feature.search

import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.LoadStates
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/** How Paging's two load states (Room and the network) become what the search screen shows. */
class SearchStatusTest {

    private val idle = LoadState.NotLoading(endOfPaginationReached = false)
    private val done = LoadState.NotLoading(endOfPaginationReached = true)
    private val loading = LoadState.Loading
    private val failed = LoadState.Error(IOException("offline"))

    private fun states(network: LoadState?, room: LoadState = idle, append: LoadState = idle): CombinedLoadStates {
        val source = LoadStates(refresh = room, prepend = done, append = append)
        val mediator = network?.let { LoadStates(refresh = it, prepend = done, append = append) }
        return CombinedLoadStates(
            refresh = network ?: room,
            prepend = done,
            append = append,
            source = source,
            mediator = mediator,
        )
    }

    @Test
    fun `no query is idle whatever Paging says`() {
        assertEquals(SearchStatus.Idle, searchStatus(null, states(failed), itemCount = 3))
    }

    @Test
    fun `nothing on the device yet while the network loads is loading`() {
        assertEquals(SearchStatus.Loading, searchStatus("dune", states(loading), itemCount = 0))
        assertEquals(SearchStatus.Loading, searchStatus("dune", states(idle, room = loading), itemCount = 0))
    }

    @Test
    fun `a pager that has not reported yet is loading, not empty`() {
        assertEquals(SearchStatus.Loading, searchStatus("dune", states(network = null, room = done), itemCount = 0))
    }

    @Test
    fun `cached rows show while fresh ones load`() {
        assertEquals(SearchStatus.Results(refreshing = true), searchStatus("dune", states(loading), itemCount = 20))
    }

    @Test
    fun `cached rows stay when the refresh fails, marked as saved on the device`() {
        assertEquals(SearchStatus.Results(refreshFailed = true), searchStatus("dune", states(failed), itemCount = 20))
    }

    @Test
    fun `a failed refresh with nothing cached is an error`() {
        assertEquals(SearchStatus.Error(NETWORK_ERROR), searchStatus("dune", states(failed), itemCount = 0))
    }

    @Test
    fun `a finished search with no rows is empty`() {
        assertEquals(SearchStatus.Empty("zzzz"), searchStatus("zzzz", states(done, room = done), itemCount = 0))
    }

    @Test
    fun `a failed next page does not hide the rows already loaded`() {
        assertEquals(SearchStatus.Results(), searchStatus("dune", states(done, append = failed), itemCount = 20))
    }
}
