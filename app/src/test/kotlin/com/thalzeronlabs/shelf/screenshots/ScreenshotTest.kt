package com.thalzeronlabs.shelf.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.asImage
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.thalzeronlabs.shelf.Book
import com.thalzeronlabs.shelf.ReadingStatus
import com.thalzeronlabs.shelf.ShelfScaffold
import com.thalzeronlabs.shelf.Tab
import com.thalzeronlabs.shelf.ThemeMode
import com.thalzeronlabs.shelf.ui.search.SearchContent
import com.thalzeronlabs.shelf.ui.search.SearchState
import com.thalzeronlabs.shelf.ui.shelf.ShelfContent
import com.thalzeronlabs.shelf.ui.shelf.ShelfState
import com.thalzeronlabs.shelf.ui.theme.ShelfTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real screens on the JVM (Robolectric native graphics, no emulator) and compares them with the
 * images in `docs/screenshots`, which are also the README's screenshots.
 *
 *   ./gradlew :app:recordRoborazziDebug   # update the images after a UI change
 *   ./gradlew :app:verifyRoborazziDebug   # what CI runs: fails if the UI changed unexpectedly
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xhdpi")
class ScreenshotTest {

    @Test fun searchDark() = snap("search_dark", ThemeMode.DARK, Tab.SEARCH) { search(SearchState.Results(searchResults)) }

    @Test fun shelfDark() = snap("shelf_dark", ThemeMode.DARK, Tab.SHELF) { ShelfContent(shelfState, onBookClick = {}) }

    @Test fun searchOfflineDark() = snap("search_offline_dark", ThemeMode.DARK, Tab.SEARCH) {
        search(SearchState.Results(searchResults, savedAt = 1L))
    }

    @Test fun searchLight() = snap("search_light", ThemeMode.LIGHT, Tab.SEARCH) { search(SearchState.Results(searchResults)) }

    @Test fun shelfLight() = snap("shelf_light", ThemeMode.LIGHT, Tab.SHELF) { ShelfContent(shelfState, onBookClick = {}) }

    @Test fun searchErrorLight() = snap("search_error_light", ThemeMode.LIGHT, Tab.SEARCH) {
        search(SearchState.Error("Couldn't reach Open Library. Check your connection."), query = "foundation")
    }

    @Composable
    private fun search(state: SearchState, query: String = "dune") =
        SearchContent(query, state, onQueryChange = {}, onRetry = {}, onBookClick = {})

    private fun snap(name: String, theme: ThemeMode, tab: Tab, content: @Composable () -> Unit) =
        captureRoboImage("$DIR/$name.png", OPTIONS) {
            // Inspection mode makes Coil use the preview handler below instead of the network.
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalAsyncImagePreviewHandler provides covers,
            ) {
                ShelfTheme(theme) {
                    ShelfScaffold(selected = tab, onSelect = {}, onThemeClick = {}, content = content)
                }
            }
        }

    private companion object {
        /** Relative to the app module, which is where Gradle runs unit tests. */
        const val DIR = "../docs/screenshots"

        /** Tolerate tiny anti-aliasing differences between machines; a real UI change is far above this. */
        val OPTIONS = RoborazziOptions(compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f))

        private fun cover(n: Int) = "https://covers.example/$n.jpg"

        val searchResults = listOf(
            Book("book-1", "Dune", "Frank Herbert", 1965, cover(0), ReadingStatus.DONE),
            Book("book-2", "Dune Messiah", "Frank Herbert", 1969, cover(1), ReadingStatus.READING),
            Book("book-3", "Children of Dune", "Frank Herbert", 1976, cover(2)),
            Book("book-4", "God Emperor of Dune", "Frank Herbert", 1981, cover(3)),
            Book("book-5", "Heretics of Dune", "Frank Herbert", 1984, null),
            Book("book-6", "Chapterhouse: Dune", "Frank Herbert", 1985, cover(4)),
            Book("book-7", "Dune: House Atreides", "Brian Herbert, Kevin J. Anderson", 1999, cover(5)),
        )

        val shelfState = ShelfState(
            loading = false,
            sections = listOf(
                ReadingStatus.WANT to listOf(
                    Book("book-8", "Neuromancer", "William Gibson", 1984, cover(6)),
                    Book("book-9", "Foundation", "Isaac Asimov", 1951, cover(7)),
                ),
                ReadingStatus.READING to listOf(
                    Book("book-10", "The Left Hand of Darkness", "Ursula K. Le Guin", 1969, cover(8)),
                    Book("book-2", "Dune Messiah", "Frank Herbert", 1969, cover(1)),
                ),
                ReadingStatus.DONE to listOf(
                    Book("book-1", "Dune", "Frank Herbert", 1965, cover(0)),
                    Book("book-11", "The Hobbit", "J.R.R. Tolkien", 1937, null),
                ),
            ),
        )

        private val palette = intArrayOf(
            0xFFB5651D.toInt(), 0xFF3D5A80.toInt(), 0xFF6B4E71.toInt(), 0xFF2A6F4E.toInt(), 0xFF9C2F2F.toInt(),
            0xFF4A4E69.toInt(), 0xFF0F4C5C.toInt(), 0xFF7A5C3E.toInt(), 0xFF5B3758.toInt(),
        )

        /** Generated placeholder covers (no real cover art in the repo): a coloured board, a spine and a title block. */
        val covers = AsyncImagePreviewHandler { request ->
            val url = request.data.toString()
            val n = url.substringAfterLast('/').substringBefore('.').toIntOrNull() ?: 0
            drawCover(palette[n % palette.size], variant = n).asImage()
        }

        private fun drawCover(color: Int, variant: Int): Bitmap {
            val w = 216f
            val h = 320f
            val bitmap = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            canvas.drawColor(color)
            canvas.drawRect(0f, 0f, 18f, h, paint.apply { this.color = 0x33000000 })
            canvas.drawRect(18f, 0f, 21f, h, paint.apply { this.color = 0x22FFFFFF })
            paint.color = 0xE6FFFFFF.toInt()
            canvas.drawRoundRect(42f, 48f, 42f + 132f - (variant % 3) * 22f, 64f, 8f, 8f, paint)
            canvas.drawRoundRect(42f, 78f, 42f + 92f + (variant % 2) * 30f, 94f, 8f, 8f, paint)
            paint.color = 0x99FFFFFF.toInt()
            canvas.drawRoundRect(42f, 262f, 132f, 274f, 6f, 6f, paint)
            paint.color = 0x40FFFFFF
            when (variant % 3) {
                0 -> canvas.drawCircle(118f, 176f, 44f, paint)
                1 -> canvas.drawRect(74f, 140f, 162f, 212f, paint)
                else -> canvas.drawOval(60f, 150f, 176f, 202f, paint)
            }
            return bitmap
        }
    }
}
