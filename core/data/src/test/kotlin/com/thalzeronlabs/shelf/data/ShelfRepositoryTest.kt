package com.thalzeronlabs.shelf.data

import androidx.paging.LoadState
import androidx.paging.PagingDataEvent
import androidx.paging.PagingDataPresenter
import androidx.paging.testing.asSnapshot
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.thalzeronlabs.shelf.model.Book
import com.thalzeronlabs.shelf.model.ReadingStatus
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The repository as the app uses it: Paging, the RemoteMediator and Room together (in-memory), with a fake
 * Open Library. The UI's view of search is exactly what these snapshots contain.
 */
@RunWith(AndroidJUnit4::class)
class ShelfRepositoryTest {

    private val db = inMemoryDatabase()
    private val api = FakeApi()
    private val clock = TestClock()
    private val sync = FakeSyncRequester()
    private val repo = DefaultShelfRepository(db, api, clock, sync)

    @After fun tearDown() = db.close()

    private fun book(id: String) = Book(id, "Book $id", "Author", 2020, null)

    @Test
    fun `search pages from Room, and scrolling down pages more in from the network`() = runTest {
        api.catalogue["dune"] = docs(45)

        // The first page, plus what Paging prefetches ahead of the viewport, in the API's order.
        val first = repo.search("dune").asSnapshot()
        assertTrue(first.size >= 20)
        assertEquals((1..first.size).map { "Dune $it" }, first.map { it.title })

        // Scrolling to the end makes the mediator fetch every page into Room. The snapshot is the window Room
        // reloaded around the scroll position when the last page landed, so check it is a gapless run of the
        // results, and that Room holds all of them.
        val scrolled = repo.search("dune").asSnapshot { scrollTo(index = 44) }
        val numbers = scrolled.map { it.id.removePrefix("OL").removeSuffix("W").toInt() }
        assertTrue(scrolled.size > 20)
        assertEquals((numbers.first()..numbers.last()).toList(), numbers)
        assertEquals(listOf(0, 20, 40), api.requests.map { it.offset }.takeLast(3))
        assertEquals((1..45).map { "OL${it}W" }, db.searchCache().results("dune").map { it.id })
    }

    @Test
    fun `results show which books are on the shelf, through the Room join`() = runTest {
        api.catalogue["dune"] = docs(3)
        repo.save(book("OL2W"), ReadingStatus.READING)

        val results = repo.search("dune").asSnapshot()
        assertEquals(listOf(null, ReadingStatus.READING, null), results.map { it.status })
    }

    /**
     * Collects a search like the screen does and waits until the network refresh has failed and Room has
     * answered: what the user sees when offline.
     */
    private suspend fun TestScope.offlineView(query: String): Pair<List<Book>, LoadState> {
        // The test's own dispatcher instead of Dispatchers.Main (Robolectric's main looper is paused).
        val presenter = object : PagingDataPresenter<Book>(mainContext = UnconfinedTestDispatcher(testScheduler)) {
            override suspend fun presentPagingDataEvent(event: PagingDataEvent<Book>) = Unit
        }
        val job = backgroundScope.launch { repo.search(query).collectLatest(presenter::collectFrom) }
        val states = presenter.loadStateFlow.filterNotNull().first {
            it.mediator?.refresh is LoadState.Error && it.source.refresh is LoadState.NotLoading
        }
        val rows = presenter.snapshot().items
        job.cancel()
        return rows to states.mediator!!.refresh
    }

    @Test
    fun `offline, a query searched before still shows its saved results`() = runTest {
        api.catalogue["dune"] = docs(25)
        repo.search("dune").asSnapshot()

        api.goOffline()
        val (rows, network) = offlineView("dune")
        assertTrue(network is LoadState.Error) // the UI shows the "saved on this device" banner
        assertEquals((1..rows.size).map { "Dune $it" }, rows.map { it.title })
        assertTrue(rows.size >= 20)
    }

    @Test
    fun `offline, a query never searched has no results to show`() = runTest {
        api.goOffline()
        val (rows, network) = offlineView("foundation")
        assertTrue(network is LoadState.Error) // the UI shows the error with "Try again"
        assertTrue(rows.isEmpty())
    }

    @Test
    fun `saved books come back from the shelf, newest first, with their status`() = runTest {
        repo.shelf().test {
            assertEquals(emptyList<Book>(), awaitItem())
            repo.save(book("a"), ReadingStatus.WANT)
            assertEquals(listOf("a"), awaitItem().map { it.id })
            clock.now++
            repo.save(book("b"), ReadingStatus.READING)
            val shelf = awaitItem()
            assertEquals(listOf("b", "a"), shelf.map { it.id })
            assertEquals(ReadingStatus.READING, shelf.first().status)
        }
    }

    @Test
    fun `status changes and removals reach the shelf`() = runTest {
        repo.save(book("a"), ReadingStatus.WANT)
        repo.setStatus("a", ReadingStatus.DONE)
        repo.shelf().test {
            assertEquals(ReadingStatus.DONE, awaitItem().single().status)
            repo.remove("a")
            assertEquals(emptyList<Book>(), awaitItem())
        }
    }

    @Test
    fun `saving a book asks for a sync, changing or removing it does not`() = runTest {
        repo.save(book("a"), ReadingStatus.WANT)
        assertEquals(1, sync.requests)
        repo.setStatus("a", ReadingStatus.DONE)
        repo.remove("a")
        assertEquals(1, sync.requests)
    }
}
