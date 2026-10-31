package com.thalzeronlabs.shelf.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.thalzeronlabs.shelf.ThemeMode

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
    onSurfaceVariant = Color(0xFF6C6C70),
)

@Composable
fun ShelfTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
