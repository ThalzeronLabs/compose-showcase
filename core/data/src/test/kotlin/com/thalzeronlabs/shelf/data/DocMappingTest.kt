package com.thalzeronlabs.shelf.data

import com.thalzeronlabs.shelf.data.remote.DocDto
import com.thalzeronlabs.shelf.data.remote.OpenLibraryApi
import com.thalzeronlabs.shelf.data.remote.toBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DocMappingTest {

    @Test
    fun `API documents map to clean books`() {
        val dune = DocDto("/works/OL1W", " Dune ", listOf("Frank Herbert", "Someone", "Third"), 1965, 42L).toBook()!!
        assertEquals("OL1W", dune.id)
        assertEquals("Dune", dune.title)
        assertEquals("Frank Herbert, Someone", dune.authors)
        assertEquals("Frank Herbert, Someone · 1965", dune.byline)
        assertEquals("https://covers.openlibrary.org/b/id/42-M.jpg", dune.coverUrl)

        val bare = DocDto("/works/OL3W", "No author").toBook()!!
        assertEquals("Unknown author", bare.authors)
        assertEquals("Unknown author", bare.byline)
        assertNull(bare.coverUrl)
    }

    @Test
    fun `documents without a title are dropped`() {
        assertNull(DocDto("/works/OL2W", null).toBook())
        assertNull(DocDto("/works/OL2W", "  ").toBook())
    }

    @Test
    fun `the sync asks for many works in one query`() {
        assertEquals("key:(\"/works/OL1W\" OR \"/works/OL2W\")", OpenLibraryApi.worksQuery(listOf("OL1W", "OL2W")))
    }
}
