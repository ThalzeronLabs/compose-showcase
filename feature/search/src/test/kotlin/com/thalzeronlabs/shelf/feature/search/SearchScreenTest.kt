package com.thalzeronlabs.shelf.feature.search

import android.os.Looper
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.paging.LoadState
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thalzeronlabs.sheets.SheetHost
import com.thalzeronlabs.sheets.SheetHostState
import com.thalzeronlabs.shelf.model.Book
import com.thalzeronlabs.shelf.model.ReadingStatus
import com.thalzeronlabs.shelf.testing.FakeShelfRepository
import com.thalzeronlabs.shelf.testing.assertAccessible
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
// Native graphics: the accessibility checks measure real bounds and colours.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
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
        // Book 2's badge is drawn; a screen reader hears it as the row's state.
        compose.onNodeWithText("Finished", useUnmergedTree = true).assertExists()

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

    @Test
    fun `the results, the saved-results banner and the error state pass the accessibility checks`() {
        repo.network = LoadState.Error(IOException("offline"))
        repo.resultsFor["dune"] = listOf(book("1"), book("2", ReadingStatus.READING))
        repo.resultsFor["foundation"] = emptyList()
        showSearch()
        compose.assertAccessible() // the idle hint
        type("dune")
        compose.assertAccessible() // results under the banner
        compose.onNodeWithTag(SearchTags.FIELD).performTextClearance()
        type("foundation")
        compose.assertAccessible() // the error with "Try again"
    }

    @Test
    fun `the add-to-shelf sheet passes the accessibility checks`() {
        repo.resultsFor["dune"] = listOf(book("1"))
        showSearch()
        type("dune")
        compose.onNodeWithText("Book 1").performClick()
        compose.waitForIdle()
        compose.assertAccessible()
    }

    @Test
    fun `a screen reader hears each result as one item with its state and what a tap does`() {
        repo.resultsFor["dune"] = listOf(
            Book("1", "Dune", "Frank Herbert", 1965, "https://covers.example/1.jpg"),
            Book("2", "Dune Messiah", "Frank Herbert", 1969, null, ReadingStatus.READING),
        )
        showSearch()
        compose.onNodeWithTag(SearchTags.FIELD).assert(hasContentDescription("Search books"))
        type("dune")

        compose.onNodeWithText("Dune")
            .assert(hasStateDescription("Not on your shelf"))
            .assert(hasClickLabel("add to your shelf"))
            // Title and byline, spoken without the middle dot.
            .assert(hasContentDescription("Dune, Frank Herbert, 1965"))
            .assert(hasNoContentDescriptionContaining("Cover")) // the cover is decorative: the title follows it
        compose.onNodeWithText("Dune Messiah")
            .assert(hasStateDescription("Reading"))
            .assert(hasClickLabel("change its list"))
        // The badges are drawn, but not read twice.
        compose.onNodeWithText("+ Add").assertDoesNotExist()
    }

    @Test
    fun `the error and the saved-results banner are announced when they appear`() {
        repo.network = LoadState.Error(IOException("offline"))
        repo.resultsFor["dune"] = listOf(book("1"))
        repo.resultsFor["foundation"] = emptyList()
        showSearch()
        type("dune")
        compose.onNode(hasText("Couldn't refresh", substring = true).and(isPoliteLiveRegion()), useUnmergedTree = false)
            .assertExists()
        compose.onNodeWithTag(SearchTags.FIELD).performTextClearance()
        type("foundation")
        compose.onNode(hasText(NETWORK_ERROR).and(isPoliteLiveRegion())).assertExists()
    }

    private fun hasStateDescription(value: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value)

    private fun hasClickLabel(label: String) = SemanticsMatcher("click label is '$label'") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == label
    }

    private fun hasNoContentDescriptionContaining(text: String) =
        SemanticsMatcher("no description containing '$text'") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().none { it.contains(text) }
        }

    private fun isPoliteLiveRegion() =
        SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
}
