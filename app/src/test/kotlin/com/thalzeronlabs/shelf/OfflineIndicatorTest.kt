package com.thalzeronlabs.shelf

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thalzeronlabs.shelf.ui.theme.ShelfTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The top bar: its title is a heading, and the "Offline" indicator follows connectivity and is announced. */
@RunWith(AndroidJUnit4::class)
class OfflineIndicatorTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the indicator appears and is announced when the network goes, and disappears when it is back`() {
        var offline by mutableStateOf(false)
        compose.setContent {
            ShelfTheme(darkTheme = true) {
                ShelfScaffold(selected = Tab.SEARCH, offline = offline, onSelect = {}, onThemeClick = {}) {
                    Text("Search results")
                }
            }
        }
        compose.onNodeWithText("Offline").assertDoesNotExist()

        compose.onNode(isHeading() and hasText("Shelf")).assertExists() // the app title is a heading

        offline = true
        // A live region, so a screen reader announces it instead of the user having to find it.
        compose.onNodeWithText("Offline")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithText("Search results").assertExists()

        offline = false
        compose.waitForIdle()
        compose.onNodeWithText("Offline").assertDoesNotExist()
    }
}
