package com.thalzeronlabs.shelf.feature.search

import android.os.Looper
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.paging.LoadState
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thalzeronlabs.sheets.SheetHost
import com.thalzeronlabs.sheets.SheetHostState
import com.thalzeronlabs.shelf.model.ReadingStatus
import com.thalzeronlabs.shelf.testing.FakeShelfRepository
import com.thalzeronlabs.shelf.testing.book
import com.thalzeronlabs.shelf.ui.theme.ShelfTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.io.IOException
import java.time.Duration

/**
 * The search screen as a user drives it: type, wait for the debounce, read the results, add a book through the
 * bottom sheet. The real screen, ViewModel, Paging and sheets run under Robolectric; only the repository is a fake.
 */
@RunWith(AndroidJUnit4::class)
class SearchScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val repo = FakeShelfRepository()

    private fun showSearch() {
        val sheets = SheetHostState()
        compose.setContent {
            ShelfTheme(darkTheme = false) {
                SearchScreen(SearchViewModel(repo), sheets)
                SheetHost(sheets)
            }
        }
    }

    /** Types into the search field and lets the debounce (a delay on the main looper) run out. */
    private fun type(text: String) {
        compose.onNodeWithTag(SearchTags.FIELD).performTextInput(text)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(SearchViewModel.DEBOUNCE_MS + 100))
        compose.waitForIdle()
    }

    @Test
    fun `before typing, and for a one-letter query, the screen only invites a search`() {
        showSearch()
        compose.onNodeWithText("Search millions of books by title or author.").assertExists()
        type("d")
        compose.onNodeWithText("Search millions of books by title or author.").assertExists()
        assertTrue(repo.searches.isEmpty())
    }

    @Test
    fun `typing shows the results, and choosing a list in the sheet saves the book there`() {
        repo.resultsFor["dune"] = listOf(book("1"), book("2", ReadingStatus.DONE))
        showSearch()
        type("dune")

        assertEquals(listOf("dune"), repo.searches)
        compose.onNodeWithText("Book 1").assertExists()
        compose.onNodeWithText("Finished").assertExists() // the shelf badge of book 2

        compose.onNodeWithText("Book 1").performClick()
        compose.onNodeWithText("Reading").performClick()
        compose.waitForIdle()

        assertEquals(listOf("1" to ReadingStatus.READING), repo.shelf.value.map { it.id to it.status })
    }

    @Test
    fun `a search with no results says so`() {
        repo.resultsFor["zzzz"] = emptyList()
        showSearch()
        type("zzzz")
        compose.onNodeWithText("Nothing found for \"zzzz\".").assertExists()
    }

    @Test
    fun `offline with nothing saved shows the error and a way to try again`() {
        repo.network = LoadState.Error(IOException("offline"))
        repo.resultsFor["foundation"] = emptyList()
        showSearch()
        type("foundation")
        compose.onNodeWithText(NETWORK_ERROR).assertExists()
        compose.onNodeWithText("Try again").assertExists()
    }

    @Test
    fun `offline with saved results shows them under a banner that says so`() {
        repo.network = LoadState.Error(IOException("offline"))
        repo.resultsFor["dune"] = listOf(book("1"))
        showSearch()
        type("dune")
        compose.onNodeWithText("Book 1").assertExists()
        compose.onNodeWithText("Couldn't refresh. Showing results saved on this device.").assertExists()
    }
}
