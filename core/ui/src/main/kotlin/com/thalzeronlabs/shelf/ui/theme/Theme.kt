package com.thalzeronlabs.shelf.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Thalzeron Labs palette: near-black surfaces with a single bright yellow accent. */
private val Yellow = Color(0xFFFFD600)
private val Black = Color(0xFF121212)

private val Dark = darkColorScheme(
    primary = Yellow,
    onPrimary = Black,
    primaryContainer = Color(0xFF3A3200),
    onPrimaryContainer = Yellow,
    secondaryContainer = Color(0xFF2C2C2E),
    onSecondaryContainer = Color(0xFFF2F2F7),
    background = Black,
    surface = Black,
    surfaceContainer = Color(0xFF1C1C1E),
    surfaceContainerHigh = Color(0xFF2C2C2E),
    surfaceContainerLow = Color(0xFF161618),
    onSurfaceVariant = Color(0xFF8E8E93),
    outline = Color(0xFF3A3A3C),
)

private val Light = lightColorScheme(
    primary = Color(0xFF1C1C1E),
    onPrimary = Yellow,
    primaryContainer = Yellow,
    onPrimaryContainer = Black,
    secondaryContainer = Color(0xFFE5E5EA),
    background = Color(0xFFF7F7F9),
    surface = Color(0xFFF7F7F9),
    surfaceContainer = Color(0xFFEDEDF0),
    surfaceContainerHigh = Color(0xFFE5E5EA),
    surfaceContainerLow = Color(0xFFF2F2F5),
    // 5.3:1 or more on every light surface (WCAG AA asks 4.5:1 for small text); 0xFF6C6C70 was 4.47:1.
    onSurfaceVariant = Color(0xFF5C5C61),
)

/** The app theme. The caller decides light or dark (from the user's setting or the system). */
@Composable
fun ShelfTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) Dark else Light, content = content)
}
