package com.thalzeronlabs.shelf.feature.search

import androidx.paging.testing.asSnapshot
import app.cash.turbine.test
import com.thalzeronlabs.shelf.model.ReadingStatus
import com.thalzeronlabs.shelf.testing.FakeShelfRepository
import com.thalzeronlabs.shelf.testing.book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repo = FakeShelfRepository()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    /** Collects the paged results like the screen does, so the ViewModel actually starts searches. */
    private fun TestScope.collectResults(vm: SearchViewModel) {
        backgroundScope.launch { vm.results.collect {} }
    }

    /** The rows of the PagingData the screen would show right now. */
    private suspend fun snapshot(vm: SearchViewModel) = flowOf(vm.results.first()).asSnapshot()

    @Test
    fun `typing is debounced into a single search`() = runTest(dispatcher) {
        val vm = SearchViewModel(repo)
        collectResults(vm)
        vm.onQueryChange("d")
        vm.onQueryChange("du")
        vm.onQueryChange("dune")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MS - 1)
        assertTrue(repo.searches.isEmpty())
        advanceTimeBy(2)
        advanceUntilIdle()
        assertEquals(listOf("dune"), repo.searches)
        assertEquals("dune", vm.activeQuery.value)
    }

    @Test
    fun `short queries do not search`() = runTest(dispatcher) {
        val vm = SearchViewModel(repo)
        collectResults(vm)
        vm.onQueryChange("a")
        advanceUntilIdle()
        assertNull(vm.activeQuery.value)
        assertTrue(repo.searches.isEmpty())
    }

    @Test
    fun `the query is trimmed before searching, and spaces alone do not search again`() = runTest(dispatcher) {
        val vm = SearchViewModel(repo)
        collectResults(vm)
        vm.onQueryChange("  dune ")
        advanceUntilIdle()
        vm.onQueryChange("dune   ")
        advanceUntilIdle()
        assertEquals(listOf("dune"), repo.searches)
        assertEquals("dune   ", vm.query.value) // the field keeps exactly what was typed
    }

    @Test
    fun `a new query stops the previous search`() = runTest(dispatcher) {
        val vm = SearchViewModel(repo)
        collectResults(vm)
        vm.onQueryChange("dune")
        advanceUntilIdle()
        vm.onQueryChange("foundation")
        advanceUntilIdle()
        assertEquals(listOf("dune", "foundation"), repo.searches)
        // flatMapLatest cancelled the first pager, and with it any page request still in flight.
        assertEquals(listOf("dune"), repo.cancelled)
    }

    @Test
    fun `results are the repository's pages for the active query`() = runTest(dispatcher) {
        repo.resultsFor["dune"] = listOf(book("1", ReadingStatus.DONE), book("2"))
        val vm = SearchViewModel(repo)
        vm.activeQuery.test {
            assertNull(awaitItem())
            vm.onQueryChange("dune")
            advanceUntilIdle()
            assertEquals("dune", awaitItem())
        }
        val books = snapshot(vm)
        assertEquals(listOf("1", "2"), books.map { it.id })
        assertEquals(listOf(ReadingStatus.DONE, null), books.map { it.status })
    }

    @Test
    fun `clearing the query goes back to idle with no results`() = runTest(dispatcher) {
        repo.results = listOf(book("1"))
        val vm = SearchViewModel(repo)
        collectResults(vm)
        vm.onQueryChange("dune")
        advanceUntilIdle()
        vm.onQueryChange("")
        advanceUntilIdle()
        assertNull(vm.activeQuery.value)
        assertEquals(emptyList<Any>(), snapshot(vm))
    }

    @Test
    fun `saving goes to the repository`() = runTest(dispatcher) {
        val vm = SearchViewModel(repo)
        vm.save(book("1"), ReadingStatus.READING)
        advanceUntilIdle()
        assertEquals(ReadingStatus.READING, repo.shelf.value.single().status)
    }
}
