package com.thalzeronlabs.shelf

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class Tab(val route: String, val label: String) { SEARCH("search", "Search"), SHELF("shelf", "My shelf") }

/**
 * The app chrome (top bar with the offline indicator, bottom navigation) around a tab's content.
 * Stateless, so screenshot and UI tests can render it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun ShelfScaffold(
    selected: Tab,
    offline: Boolean,
    onSelect: (Tab) -> Unit,
    onThemeClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        // Test tags double as resource ids, so the Macrobenchmark journeys can find views with UiAutomator.
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Shelf", fontWeight = FontWeight.Bold)
                        AnimatedVisibility(offline, enter = fadeIn(), exit = fadeOut()) {
                            OfflineIndicator(Modifier.padding(start = 12.dp))
                        }
                    }
                },
                actions = { TextButton(onClick = onThemeClick) { Text("Theme") } },
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selected,
                        onClick = { onSelect(tab) },
                        icon = { Text(if (tab == Tab.SEARCH) "⌕" else "▤", style = MaterialTheme.typography.titleLarge) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) { content() }
    }
}

/** A small pill in the top bar while the device has no network. Everything on screen still works from Room. */
@Composable
fun OfflineIndicator(modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
        Spacer(Modifier.width(6.dp))
        Text("Offline", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
