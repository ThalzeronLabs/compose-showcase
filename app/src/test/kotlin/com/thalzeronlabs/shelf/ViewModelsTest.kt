package com.thalzeronlabs.shelf

import app.cash.turbine.test
import com.thalzeronlabs.shelf.ui.search.SearchState
import com.thalzeronlabs.shelf.ui.search.SearchViewModel
import com.thalzeronlabs.shelf.ui.shelf.ShelfViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelsTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `typing is debounced into a single search`() = runTest(dispatcher) {
        val repo = FakeRepository(results = listOf(book("1")))
        val vm = SearchViewModel(repo)
        vm.state.test {
            assertEquals(SearchState.Idle, awaitItem())
            vm.onQueryChange("d")
            vm.onQueryChange("du")
            vm.onQueryChange("dune")
            advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)
            assertEquals(SearchState.Loading, awaitItem())
            assertEquals(listOf("1"), (awaitItem() as SearchState.Results).books.map { it.id })
            assertEquals(listOf("dune"), repo.searches)
        }
    }

    @Test
    fun `short queries do not hit the network`() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = SearchViewModel(repo)
        vm.state.test {
            awaitItem()
            vm.onQueryChange("a")
            advanceUntilIdle()
            expectNoEvents()
            assertTrue(repo.searches.isEmpty())
        }
    }

    @Test
    fun `no results and network errors are distinct states, and retry runs again`() = runTest(dispatcher) {
        val repo = FakeRepository()
        val vm = SearchViewModel(repo)
        vm.state.test {
            awaitItem()
            vm.onQueryChange("zzzz")
            advanceUntilIdle()
            assertEquals(SearchState.Loading, awaitItem())
            assertEquals(SearchState.Empty("zzzz"), awaitItem())

            repo.fail = true
            vm.retry()
            advanceUntilIdle()
            assertEquals(SearchState.Loading, awaitItem())
            assertTrue(awaitItem() is SearchState.Error)
        }
    }

    @Test
    fun `results show which books are already on the shelf`() = runTest(dispatcher) {
        val repo = FakeRepository(results = listOf(book("1"), book("2")))
        repo.shelf.value = listOf(book("2", ReadingStatus.READING))
        val vm = SearchViewModel(repo)
        vm.state.test {
            awaitItem()
            vm.onQueryChange("books")
            advanceUntilIdle()
            awaitItem() // Loading
            val books = (awaitItem() as SearchState.Results).books
            assertEquals(listOf(null, ReadingStatus.READING), books.map { it.status })

            vm.save(books.first(), ReadingStatus.WANT)
            advanceUntilIdle()
            assertEquals(ReadingStatus.WANT, (awaitItem() as SearchState.Results).books.first().status)
        }
    }

    @Test
    fun `a new query cancels the search still in flight`() = runTest(dispatcher) {
        val repo = FakeRepository()
        repo.latency = 1_000
        repo.resultsFor["dune"] = listOf(book("dune"))
        repo.resultsFor["foundation"] = listOf(book("foundation"))
        val vm = SearchViewModel(repo)
        vm.state.test {
            assertEquals(SearchState.Idle, awaitItem())
            vm.onQueryChange("dune")
            advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)
            assertEquals(SearchState.Loading, awaitItem())

            // Halfway through the first request, the user keeps typing.
            advanceTimeBy(500)
            vm.onQueryChange("foundation")
            advanceUntilIdle()

            // The state stays Loading (a StateFlow does not repeat an equal value), and the "dune"
            // answer never reaches the UI: flatMapLatest cancelled that request.
            assertEquals(listOf("foundation"), (awaitItem() as SearchState.Results).books.map { it.id })
            assertEquals(listOf("dune", "foundation"), repo.searches)
            expectNoEvents()
        }
    }

    @Test
    fun `offline results carry when they were saved and still show shelf status`() = runTest(dispatcher) {
        val repo = FakeRepository(results = listOf(book("1"), book("2")))
        repo.cachedAt = 1_234L
        repo.shelf.value = listOf(book("1", ReadingStatus.DONE))
        val vm = SearchViewModel(repo)
        vm.state.test {
            awaitItem()
            vm.onQueryChange("dune")
            advanceUntilIdle()
            assertEquals(SearchState.Loading, awaitItem())
            val results = awaitItem() as SearchState.Results
            assertEquals(1_234L, results.savedAt)
            assertEquals(listOf(ReadingStatus.DONE, null), results.books.map { it.status })

            // Saving from cached results still updates the badge, and keeps the offline marker.
            vm.save(results.books[1], ReadingStatus.READING)
            advanceUntilIdle()
            val updated = awaitItem() as SearchState.Results
            assertEquals(1_234L, updated.savedAt)
            assertEquals(ReadingStatus.READING, updated.books[1].status)
        }
    }

    @Test
    fun `the query is trimmed before searching, and spaces alone do not change it`() = runTest(dispatcher) {
        val repo = FakeRepository(results = listOf(book("1")))
        val vm = SearchViewModel(repo)
        vm.state.test {
            awaitItem()
            vm.onQueryChange("  dune ")
            advanceUntilIdle()
            awaitItem() // Loading
            awaitItem() // Results
            vm.onQueryChange("dune   ")
            advanceUntilIdle()
            expectNoEvents()
            assertEquals(listOf("dune"), repo.searches)
            assertEquals("dune   ", vm.query.value) // the field keeps exactly what was typed
        }
    }

    @Test
    fun `the shelf is grouped by status in a fixed order, empty groups hidden`() = runTest(dispatcher) {
        val repo = FakeRepository()
        repo.shelf.value = listOf(book("a", ReadingStatus.DONE), book("b", ReadingStatus.WANT), book("c", ReadingStatus.DONE))
        val vm = ShelfViewModel(repo)
        vm.state.test {
            var state = awaitItem()
            if (state.loading) state = awaitItem()
            assertEquals(listOf(ReadingStatus.WANT, ReadingStatus.DONE), state.sections.map { it.first })
            assertEquals(3, state.total)
            assertEquals(2, state.count(ReadingStatus.DONE))
            assertEquals(0, state.count(ReadingStatus.READING))
        }
    }

    @Test
    fun `moving and removing books updates the shelf`() = runTest(dispatcher) {
        val repo = FakeRepository()
        repo.shelf.value = listOf(book("a", ReadingStatus.WANT))
        val vm = ShelfViewModel(repo)
        vm.state.test {
            var state = awaitItem()
            if (state.loading) state = awaitItem()
            vm.setStatus(state.sections.single().second.single(), ReadingStatus.READING)
            advanceUntilIdle()
            assertEquals(ReadingStatus.READING, awaitItem().sections.single().first)
            vm.remove(book("a"))
            advanceUntilIdle()
            assertEquals(0, awaitItem().total)
        }
    }
}
