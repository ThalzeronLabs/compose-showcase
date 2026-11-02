package com.thalzeronlabs.shelf.data.sync

import androidx.room.withTransaction
import com.thalzeronlabs.shelf.data.local.ShelfDatabase
import com.thalzeronlabs.shelf.data.remote.OpenLibraryApi
import com.thalzeronlabs.shelf.data.remote.toBook
import com.thalzeronlabs.shelf.model.Clock
import javax.inject.Inject

/**
 * Keeps the shelf in step with the Open Library catalogue: corrected titles and authors, covers added
 * after the book was saved. Only the catalogue fields change; the user's status and order never do.
 *
 * Books are refreshed in batches of [BATCH_SIZE], one request each, and every batch is committed on its own.
 * If the connection drops halfway, the batches already done stay done (they are stamped `syncedAt`), and
 * the retry picks up only the books that are still stale.
 */
class ShelfSyncer @Inject constructor(
    private val db: ShelfDatabase,
    private val api: OpenLibraryApi,
    private val clock: Clock,
) {
    /** Throws the network error (IOException or HttpException) of the batch that failed, if any. */
    suspend fun sync() {
        val books = db.books()
        val stale = books.idsToSync(before = clock.now() - MAX_AGE_MS)
        for (batch in stale.chunked(BATCH_SIZE)) {
            val found = api.search(OpenLibraryApi.worksQuery(batch), limit = batch.size, offset = 0)
                .docs.mapNotNull { it.toBook() }
                .associateBy { it.id }
            val now = clock.now()
            db.withTransaction {
                for (id in batch) {
                    val book = found[id]
                    if (book == null) {
                        books.markSynced(id, now)
                    } else {
                        books.updateCatalogue(id, book.title, book.authors, book.year, book.coverUrl, now)
                    }
                }
            }
        }
    }

    companion object {
        const val BATCH_SIZE = 20

        /** A book refreshed less than a day ago is left alone. */
        const val MAX_AGE_MS = 24 * 60 * 60 * 1000L
    }
}
