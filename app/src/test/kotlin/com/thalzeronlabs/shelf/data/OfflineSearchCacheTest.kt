package com.thalzeronlabs.shelf.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thalzeronlabs.shelf.FakeApi
import com.thalzeronlabs.shelf.data.local.ShelfDatabase
import com.thalzeronlabs.shelf.data.remote.DocDto
import com.thalzeronlabs.shelf.httpError
import com.thalzeronlabs.shelf.offline
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * The repository's offline cache, against a real (in-memory) Room database rather than a fake DAO,
 * so the SQL, the primary key and the replace transaction are exercised too.
 */
@RunWith(AndroidJUnit4::class)
class OfflineSearchCacheTest {

    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ShelfDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val api = FakeApi()
    private var now = 1_000L
    private val repo = DefaultShelfRepository(api, db.books(), db.searchCache()) { now }

    @After fun tearDown() = db.close()

    private val dune = listOf(
        DocDto("/works/OL1W", "Dune", listOf("Frank Herbert"), 1965, 42L),
        DocDto("/works/OL2W", "Dune Messiah", listOf("Frank Herbert"), 1969),
    )

    @Test
    fun `fresh results are not marked as cached`() = runTest {
        api.docs = dune
        val result = repo.search("dune")
        assertFalse(result.fromCache)
        assertNull(result.cachedAt)
        assertEquals(listOf("OL1W", "OL2W"), result.books.map { it.id })
    }

    @Test
    fun `offline, the last results for the query come back in order with when they were saved`() = runTest {
        api.docs = dune
        repo.search("dune")

        now = 5_000L
        api.failure = offline()
        val result = repo.search("dune")

        assertTrue(result.fromCache)
        assertEquals(1_000L, result.cachedAt)
        assertEquals(listOf("Dune", "Dune Messiah"), result.books.map { it.title })
        assertEquals("https://covers.openlibrary.org/b/id/42-M.jpg", result.books.first().coverUrl)
        assertNull(result.books.first().status) // shelf status is merged in by the ViewModel, not cached
    }

    @Test
    fun `the cache key ignores case and surrounding spaces`() = runTest {
        api.docs = dune
        repo.search("Dune")
        api.failure = offline()
        assertEquals(2, repo.search("  dUNE ").books.size)
    }

    @Test
    fun `new results replace old ones, so a shorter list leaves no stale rows`() = runTest {
        api.docs = dune
        repo.search("dune")
        api.docs = dune.take(1)
        now = 2_000L
        repo.search("dune")

        api.failure = offline()
        val result = repo.search("dune")
        assertEquals(listOf("OL1W"), result.books.map { it.id })
        assertEquals(2_000L, result.cachedAt)
    }

    @Test
    fun `an HTTP error also falls back to the cache`() = runTest {
        api.docs = dune
        repo.search("dune")
        api.failure = httpError(503)
        assertTrue(repo.search("dune").fromCache)
    }

    @Test
    fun `offline with nothing cached rethrows the network error`() = runTest {
        api.docs = dune
        repo.search("dune")
        val error = offline()
        api.failure = error
        try {
            repo.search("foundation")
            fail("expected the network error")
        } catch (e: IOException) {
            assertSame(error, e)
        }
    }

    @Test
    fun `a successful empty search is cached as empty, not as the previous results`() = runTest {
        api.docs = dune
        repo.search("zzzz")
        api.docs = emptyList()
        repo.search("zzzz")
        api.failure = offline()
        try {
            repo.search("zzzz")
            fail("nothing should be cached for a query that now has no results")
        } catch (_: IOException) {
        }
    }
}
