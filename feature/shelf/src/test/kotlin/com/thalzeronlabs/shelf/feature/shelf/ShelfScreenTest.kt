package com.thalzeronlabs.shelf.feature.shelf

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thalzeronlabs.sheets.SheetHost
import com.thalzeronlabs.sheets.SheetHostState
import com.thalzeronlabs.shelf.model.ReadingStatus
import com.thalzeronlabs.shelf.testing.FakeShelfRepository
import com.thalzeronlabs.shelf.testing.book
import com.thalzeronlabs.shelf.ui.theme.ShelfTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The shelf as a user drives it: tap a book, answer the bottom sheets, see the list change. The real screen,
 * ViewModel and sheets run under Robolectric; only the repository is a fake.
 */
@RunWith(AndroidJUnit4::class)
class ShelfScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val repo = FakeShelfRepository()

    private fun showShelf(vararg books: com.thalzeronlabs.shelf.model.Book) {
        repo.shelf.value = books.toList()
        val sheets = SheetHostState()
        compose.setContent {
            ShelfTheme(darkTheme = false) {
                ShelfScreen(ShelfViewModel(repo), sheets)
                SheetHost(sheets)
            }
        }
    }

    @Test
    fun `an empty shelf says how to fill it`() {
        showShelf()
        compose.onNodeWithText("Your shelf is empty.", substring = true).assertExists()
    }

    @Test
    fun `moving a book through the sheet puts it in the new section`() {
        showShelf(book("a", ReadingStatus.WANT))
        compose.onNodeWithText("Want to read · 1").assertExists()

        compose.onNodeWithText("Book a").performClick()
        // The sheet offers the other two lists, not the current one.
        compose.onNodeWithText("Move to Want to read").assertDoesNotExist()
        compose.onNodeWithText("Move to Reading").performClick()
        compose.waitForIdle()

        assertEquals(ReadingStatus.READING, repo.shelf.value.single().status)
        compose.onNodeWithText("Reading · 1").assertExists()
        compose.onNodeWithText("Want to read · 1").assertDoesNotExist()
    }

    @Test
    fun `removing asks first, and cancelling keeps the book`() {
        showShelf(book("a", ReadingStatus.DONE))

        compose.onNodeWithText("Book a").performClick()
        compose.onNodeWithText("Remove from shelf").performClick()
        compose.onNodeWithText("Remove \"Book a\"?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.waitForIdle()

        assertEquals(listOf("a"), repo.shelf.value.map { it.id })
        compose.onNodeWithText("Finished · 1").assertExists()
    }

    @Test
    fun `confirming the removal empties the shelf`() {
        showShelf(book("a", ReadingStatus.DONE))

        compose.onNodeWithText("Book a").performClick()
        compose.onNodeWithText("Remove from shelf").performClick()
        compose.onNodeWithText("Remove").performClick()
        compose.waitForIdle()

        assertEquals(emptyList<Any>(), repo.shelf.value)
        compose.onNodeWithText("Your shelf is empty.", substring = true).assertExists()
    }
}
