package com.thalzeronlabs.shelf.data.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thalzeronlabs.shelf.data.FakeApi
import com.thalzeronlabs.shelf.data.TestClock
import com.thalzeronlabs.shelf.data.inMemoryDatabase
import com.thalzeronlabs.shelf.data.local.BookEntity
import com.thalzeronlabs.shelf.data.offline
import com.thalzeronlabs.shelf.data.remote.DocDto
import com.thalzeronlabs.shelf.model.ReadingStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/** The background sync against a real (in-memory) Room database, including a network that drops halfway. */
@RunWith(AndroidJUnit4::class)
class ShelfSyncerTest {

    private val db = inMemoryDatabase()
    private val books = db.books()
    private val api = FakeApi()
    private val clock = TestClock(now = 10 * ShelfSyncer.MAX_AGE_MS)
    private val syncer = ShelfSyncer(db, api, clock)

    @After fun tearDown() = db.close()

    private suspend fun shelve(id: String, title: String = "Old $id", status: ReadingStatus = ReadingStatus.WANT, addedAt: Long = 1) =
        books.upsert(BookEntity(id, title, "Old author", 1900, "https://old/$id.jpg", status, addedAt))

    private fun publish(id: String, title: String = "New $id", year: Int? = 1965, cover: Long? = 7L) {
        api.works[id] = DocDto("/works/$id", title, listOf("Frank Herbert"), year, cover)
    }

    @Test
    fun `stale books get fresh catalogue data and keep the user's status`() = runTest {
        shelve("OL1W", status = ReadingStatus.READING)
        publish("OL1W", title = "Dune")

        syncer.sync()

        val book = books.get("OL1W")!!
        assertEquals("Dune", book.title)
        assertEquals("Frank Herbert", book.authors)
        assertEquals(1965, book.year)
        assertEquals("https://covers.openlibrary.org/b/id/7-M.jpg", book.coverUrl)
        assertEquals(ReadingStatus.READING, book.status)
        assertEquals(clock.now, book.syncedAt)
    }

    @Test
    fun `a book synced in the last day is not asked for again`() = runTest {
        shelve("OL1W")
        publish("OL1W")
        syncer.sync()
        clock.now += ShelfSyncer.MAX_AGE_MS - 1
        syncer.sync()
        assertEquals(1, api.requests.size)

        clock.now += 2
        syncer.sync()
        assertEquals(2, api.requests.size)
    }

    @Test
    fun `the connection dropping halfway keeps the batches already done, and the retry does only the rest`() = runTest {
        val ids = (1..45).map { "OL${it}W" }
        ids.forEachIndexed { i, id ->
            shelve(id, addedAt = i.toLong())
            publish(id)
        }
        val error = offline()
        api.failWith = { if (api.requests.size == 2) error else null }

        try {
            syncer.sync()
            fail("the dropped connection should reach the worker")
        } catch (e: IOException) {
            assertSame(error, e)
        }
        val synced = ids.filter { books.get(it)!!.syncedAt != null }
        assertEquals(ids.take(ShelfSyncer.BATCH_SIZE), synced)
        assertEquals("New OL1W", books.get("OL1W")!!.title)
        assertEquals("Old OL21W", books.get("OL21W")!!.title)

        api.goOnline()
        api.requests.clear()
        syncer.sync()
        assertEquals(2, api.requests.size) // 25 books left: one batch of 20, one of 5
        assertEquals(ids.map { "New $it" }, ids.map { books.get(it)!!.title })
    }

    @Test
    fun `a status change made while the sync is in flight is kept`() = runTest {
        shelve("OL1W", status = ReadingStatus.WANT)
        publish("OL1W", title = "Dune")
        api.whileInFlight = { books.setStatus("OL1W", ReadingStatus.DONE) }

        syncer.sync()

        val book = books.get("OL1W")!!
        assertEquals("Dune", book.title)
        assertEquals(ReadingStatus.DONE, book.status)
    }

    @Test
    fun `a book removed while the sync is in flight does not come back`() = runTest {
        shelve("OL1W")
        publish("OL1W")
        api.whileInFlight = { books.delete("OL1W") }

        syncer.sync()

        assertNull(books.get("OL1W"))
    }

    @Test
    fun `what Open Library no longer returns is kept as it was`() = runTest {
        shelve("OL1W")
        shelve("OL2W")
        publish("OL2W", year = null, cover = null) // OL1W is gone (merged or removed)

        syncer.sync()

        val gone = books.get("OL1W")!!
        assertEquals("Old OL1W", gone.title)
        assertEquals(clock.now, gone.syncedAt) // not asked for again until it is stale
        val partial = books.get("OL2W")!!
        assertEquals("New OL2W", partial.title)
        assertEquals(1900, partial.year)
        assertEquals("https://old/OL2W.jpg", partial.coverUrl)
    }
}
