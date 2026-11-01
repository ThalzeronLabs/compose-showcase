package com.thalzeronlabs.shelf

import app.cash.turbine.test
import com.thalzeronlabs.shelf.data.DefaultShelfRepository
import com.thalzeronlabs.shelf.data.remote.DocDto
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShelfRepositoryTest {

    private val api = FakeApi()
    private val dao = FakeDao()
    private var now = 1_000L
    private val repo = DefaultShelfRepository(api, dao, FakeSearchCacheDao()) { now++ }

    @Test
    fun `search maps the API into clean books`() = runTest {
        api.docs = listOf(
            DocDto("/works/OL1W", " Dune ", listOf("Frank Herbert", "Someone", "Third"), 1965, 42L),
            DocDto("/works/OL2W", null),
            DocDto("/works/OL3W", "No author"),
        )
        val books = repo.search("  dune ").books

        assertEquals("dune", api.queries.single())
        assertEquals(2, books.size) // the one without a title is dropped
        val dune = books.first()
        assertEquals("OL1W", dune.id)
        assertEquals("Dune", dune.title)
        assertEquals("Frank Herbert, Someone", dune.authors)
        assertEquals("https://covers.openlibrary.org/b/id/42-M.jpg", dune.coverUrl)
        assertEquals("Unknown author", books[1].authors)
        assertNull(books[1].coverUrl)
    }

    @Test
    fun `saved books come back from the shelf, newest first, with their status`() = runTest {
        repo.shelf().test {
            assertEquals(emptyList<Book>(), awaitItem())
            repo.save(book("a"), ReadingStatus.WANT)
            assertEquals(listOf("a"), awaitItem().map { it.id })
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
}
