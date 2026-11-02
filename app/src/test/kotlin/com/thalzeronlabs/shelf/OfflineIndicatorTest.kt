package com.thalzeronlabs.shelf

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.thalzeronlabs.shelf.ui.theme.ShelfTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The top bar's "Offline" indicator follows connectivity, without changing what the tab shows. */
@RunWith(AndroidJUnit4::class)
class OfflineIndicatorTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `the indicator appears when the network goes and disappears when it is back`() {
        var offline by mutableStateOf(false)
        compose.setContent {
            ShelfTheme(darkTheme = true) {
                ShelfScaffold(selected = Tab.SEARCH, offline = offline, onSelect = {}, onThemeClick = {}) {
                    Text("Search results")
                }
            }
        }
        compose.onNodeWithText("Offline").assertDoesNotExist()

        offline = true
        compose.onNodeWithText("Offline").assertExists()
        compose.onNodeWithText("Search results").assertExists()

        offline = false
        compose.waitForIdle()
        compose.onNodeWithText("Offline").assertDoesNotExist()
    }
}
