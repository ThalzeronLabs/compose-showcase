package com.thalzeronlabs.shelf.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.thalzeronlabs.shelf.model.ReadingStatus
import com.thalzeronlabs.shelf.data.local.ShelfDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Upgrading the app must never lose the user's shelf. These tests build a database from an exported old
 * schema, fill it the way that version of the app would have, and migrate it.
 */
@RunWith(AndroidJUnit4::class)
class ShelfMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), ShelfDatabase::class.java)

    @Test
    fun `1 to 2 keeps every book and adds an empty search cache`() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO books (id, title, authors, year, coverUrl, status, addedAt) VALUES " +
                    "('OL1W', 'Dune', 'Frank Herbert', 1965, 'https://covers.openlibrary.org/b/id/42-M.jpg', 'READING', 1000), " +
                    "('OL2W', 'Neuromancer', 'William Gibson', NULL, NULL, 'WANT', 2000)",
            )
        }

        // Also checks that the migrated schema matches 2.json exactly.
        val db = helper.runMigrationsAndValidate(DB, 2, true)

        db.query("SELECT id, title, year, status FROM books ORDER BY addedAt").use { c ->
            assertEquals(2, c.count)
            c.moveToFirst()
            assertEquals("OL1W", c.getString(0))
            assertEquals("Dune", c.getString(1))
            assertEquals(1965, c.getInt(2))
            assertEquals("READING", c.getString(3))
            c.moveToNext()
            assertEquals("Neuromancer", c.getString(1))
            assertEquals(true, c.isNull(2))
        }
        db.query("SELECT COUNT(*) FROM search_cache").use { c ->
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
        }
    }

    @Test
    fun `2 to 3 keeps the shelf and the offline results, and adds paging keys and the sync stamp`() {
        helper.createDatabase(DB, 2).use { db ->
            db.execSQL(
                "INSERT INTO books (id, title, authors, year, coverUrl, status, addedAt) " +
                    "VALUES ('OL1W', 'Dune', 'Frank Herbert', 1965, NULL, 'READING', 1000)",
            )
            db.execSQL(
                "INSERT INTO search_cache (`query`, position, id, title, authors, year, coverUrl, cachedAt) " +
                    "VALUES ('dune', 0, 'OL1W', 'Dune', 'Frank Herbert', 1965, NULL, 500)",
            )
        }

        // Also checks that the migrated schema matches 3.json exactly.
        val db = helper.runMigrationsAndValidate(DB, 3, true)

        db.query("SELECT title, status, syncedAt FROM books").use { c ->
            c.moveToFirst()
            assertEquals("Dune", c.getString(0))
            assertEquals("READING", c.getString(1))
            assertEquals(true, c.isNull(2)) // never synced: the first sync will refresh it
        }
        db.query("SELECT `query`, title FROM search_cache").use { c ->
            c.moveToFirst()
            assertEquals("dune", c.getString(0))
            assertEquals("Dune", c.getString(1))
        }
        db.query("SELECT COUNT(*) FROM search_remote_keys").use { c ->
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
        }
    }

    @Test
    fun `the real database opens a version-1 file and reads the shelf through the DAO`() = runTest {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO books (id, title, authors, year, coverUrl, status, addedAt) " +
                    "VALUES ('OL1W', 'Dune', 'Frank Herbert', 1965, NULL, 'DONE', 1000)",
            )
        }

        // The same builder the app uses (1 → 2 → 3), so a missing migration would fail here instead of on a user's phone.
        val room = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), ShelfDatabase::class.java, DB)
            .allowMainThreadQueries()
            .build()
        try {
            val shelf = room.books().observeAll().first()
            assertEquals(listOf("Dune"), shelf.map { it.title })
            assertEquals(ReadingStatus.DONE, shelf.single().status)
            assertEquals(emptyList<Any>(), room.searchCache().results("dune"))
            assertEquals(listOf("OL1W"), room.books().idsToSync(before = Long.MAX_VALUE))
        } finally {
            room.close()
        }
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
