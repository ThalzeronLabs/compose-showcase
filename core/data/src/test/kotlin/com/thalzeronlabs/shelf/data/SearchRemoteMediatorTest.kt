package com.thalzeronlabs.shelf.data

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import androidx.paging.RemoteMediator.MediatorResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thalzeronlabs.shelf.data.local.SearchRow
import com.thalzeronlabs.shelf.data.remote.DocDto
import com.thalzeronlabs.shelf.data.remote.OpenLibraryApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The RemoteMediator against a real (in-memory) Room database, so the SQL, the transactions and the remote
 * keys are exercised too. Each test drives it page by page, the way Paging would, including connections
 * that drop between pages.
 */
@OptIn(ExperimentalPagingApi::class)
@RunWith(AndroidJUnit4::class)
class SearchRemoteMediatorTest {

    private val db = inMemoryDatabase()
    private val dao = db.searchCache()
    private val api = FakeApi()
    private val clock = TestClock()
    private val pageSize = OpenLibraryApi.PAGE_SIZE

    @After fun tearDown() = db.close()

    private fun mediator(query: String = "dune") = SearchRemoteMediator(query, db, api, clock)

    private val state = PagingState<Int, SearchRow>(emptyList(), null, PagingConfig(pageSize), 0)

    private suspend fun SearchRemoteMediator.refresh() = load(LoadType.REFRESH, state)
    private suspend fun SearchRemoteMediator.append() = load(LoadType.APPEND, state)

    private suspend fun cached(query: String = "dune") = dao.results(query)

    private fun MediatorResult.ended() = (this as MediatorResult.Success).endOfPaginationReached

    @Test
    fun `refresh stores the first page in order and remembers where the next one starts`() = runTest {
        api.catalogue["dune"] = docs(45)
        val result = mediator().refresh()

        assertEquals(false, result.ended())
        assertEquals(ApiRequest("dune", pageSize, 0), api.requests.single())
        val rows = cached()
        assertEquals((0 until 20).toList(), rows.map { it.position })
        assertEquals((1..20).map { "OL${it}W" }, rows.map { it.id })
        assertEquals("https://covers.openlibrary.org/b/id/1-M.jpg", rows.first().coverUrl)
        assertEquals(20, dao.remoteKey("dune")?.nextOffset)
    }

    @Test
    fun `append continues from the saved offset until Open Library has no more`() = runTest {
        api.catalogue["dune"] = docs(45)
        val m = mediator()
        m.refresh()
        assertEquals(false, m.append().ended())
        assertEquals(true, m.append().ended())

        assertEquals(listOf(0, 20, 40), api.requests.map { it.offset })
        assertEquals(45, cached().size)
        assertEquals((0 until 45).toList(), cached().map { it.position })
        assertNull(dao.remoteKey("dune")?.nextOffset)

        // At the end, appending again does not even ask.
        assertEquals(true, m.append().ended())
        assertEquals(3, api.requests.size)
    }

    @Test
    fun `the connection dropping between pages keeps what was loaded and resumes at the same page`() = runTest {
        api.catalogue["dune"] = docs(45)
        val m = mediator()
        m.refresh()

        val error = offline()
        api.failWith = { if (it.offset == 20) error else null }
        val failed = m.append()
        assertSame(error, (failed as MediatorResult.Error).throwable)
        assertEquals(20, cached().size) // page one is intact
        assertEquals(20, dao.remoteKey("dune")?.nextOffset) // and the next page is still the one to fetch

        api.goOnline()
        m.append()
        assertEquals(listOf(0, 20, 20), api.requests.map { it.offset })
        val rows = cached()
        assertEquals((0 until 40).toList(), rows.map { it.position }) // no gap
        assertEquals(40, rows.map { it.id }.toSet().size) // no duplicate
    }

    @Test
    fun `a failed refresh leaves the cached results exactly as they were`() = runTest {
        api.catalogue["dune"] = docs(45)
        val m = mediator()
        m.refresh()
        m.append()

        api.goOffline()
        val result = mediator().refresh()

        assertTrue(result is MediatorResult.Error)
        assertEquals(40, cached().size)
        assertEquals(40, dao.remoteKey("dune")?.nextOffset)
    }

    @Test
    fun `an HTTP error is an error, not an empty result`() = runTest {
        api.goOffline(httpError(503))
        val result = mediator().refresh()
        assertTrue((result as MediatorResult.Error).throwable is retrofit2.HttpException)
        assertEquals(emptyList<Any>(), cached())
        assertNull(dao.remoteKey("dune"))
    }

    @Test
    fun `nothing is appended before a refresh has succeeded`() = runTest {
        api.goOffline()
        mediator().refresh()
        assertEquals(true, mediator().append().ended())
        assertEquals(1, api.requests.size)
    }

    @Test
    fun `a successful refresh replaces the old rows, so a shorter list leaves nothing stale`() = runTest {
        api.catalogue["dune"] = docs(45)
        val m = mediator()
        m.refresh()
        m.append()

        api.catalogue["dune"] = docs(5, title = "New")
        clock.now = 9_000L
        assertEquals(true, m.refresh().ended())

        val rows = cached()
        assertEquals((1..5).map { "New $it" }, rows.map { it.title })
        assertEquals(setOf(9_000L), rows.map { it.cachedAt }.toSet())
        assertNull(dao.remoteKey("dune")?.nextOffset)
    }

    @Test
    fun `a book Open Library repeats on a later page is kept once`() = runTest {
        val first = docs(20)
        api.catalogue["dune"] = first + first[3] + docs(5, prefix = "X")
        val m = mediator()
        m.refresh()
        m.append()

        val ids = cached().map { it.id }
        assertEquals(25, ids.size)
        assertEquals(ids.size, ids.toSet().size)
        assertEquals((0 until 25).toList(), cached().map { it.position })
    }

    @Test
    fun `documents without a title are skipped but still count towards the offset`() = runTest {
        api.catalogue["dune"] = listOf(DocDto("/works/OL0W", title = " ")) + docs(24)
        val m = mediator()
        m.refresh()
        assertEquals(19, cached().size)
        assertEquals(20, dao.remoteKey("dune")?.nextOffset)
        m.append()
        assertEquals(24, cached().size)
    }

    @Test
    fun `the cache key ignores case and surrounding spaces, the API gets the trimmed query`() = runTest {
        api.catalogue["Dune"] = docs(25)
        mediator(" Dune ").refresh()
        assertEquals("Dune", api.requests.single().query)
        assertEquals(20, cached("dune").size)
        assertEquals(20, dao.remoteKey(searchKey("  dUNE"))?.nextOffset)
    }
}
