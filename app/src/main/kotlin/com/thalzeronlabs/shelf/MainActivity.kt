package com.thalzeronlabs.shelf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.thalzeronlabs.sheets.SheetHost
import com.thalzeronlabs.sheets.SheetHostState
import com.thalzeronlabs.sheets.SheetOption
import com.thalzeronlabs.shelf.data.ConnectivityMonitor
import com.thalzeronlabs.shelf.data.SettingsStore
import com.thalzeronlabs.shelf.feature.search.SearchScreen
import com.thalzeronlabs.shelf.feature.shelf.ShelfScreen
import com.thalzeronlabs.shelf.model.ThemeMode
import com.thalzeronlabs.shelf.ui.theme.ShelfTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    private val settings: SettingsStore,
    connectivity: ConnectivityMonitor,
) : ViewModel() {
    val themeMode = settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    /** Drives the "Offline" indicator in the top bar. Starts as online so a cold start never flashes it. */
    val offline = connectivity.isOnline.map { !it }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app: AppViewModel = hiltViewModel()
            val theme by app.themeMode.collectAsStateWithLifecycle()
            val offline by app.offline.collectAsStateWithLifecycle()
            val dark = when (theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            ShelfTheme(dark) { ShelfRoot(offline, onTheme = app::setThemeMode) }
        }
    }
}

@Composable
private fun ShelfRoot(offline: Boolean, onTheme: (ThemeMode) -> Unit) {
    val nav = rememberNavController()
    val sheets = remember { SheetHostState() }
    val scope = rememberCoroutineScope()
    val current = nav.currentBackStackEntryAsState().value?.destination?.route

    ShelfScaffold(
        selected = Tab.entries.firstOrNull { it.route == current } ?: Tab.SEARCH,
        offline = offline,
        onSelect = { tab ->
            nav.navigate(tab.route) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
        onThemeClick = {
            scope.launch {
                sheets.choose("Appearance", ThemeMode.entries.map { SheetOption(it.label, it) })?.let(onTheme)
            }
        },
    ) {
        NavHost(nav, startDestination = Tab.SEARCH.route) {
            composable(Tab.SEARCH.route) { SearchScreen(hiltViewModel(), sheets) }
            composable(Tab.SHELF.route) { ShelfScreen(hiltViewModel(), sheets) }
        }
    }
    SheetHost(sheets)
}
