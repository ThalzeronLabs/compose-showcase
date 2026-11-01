package com.thalzeronlabs.shelf

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
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
import com.thalzeronlabs.shelf.data.SettingsStore
import com.thalzeronlabs.shelf.ui.search.SearchScreen
import com.thalzeronlabs.shelf.ui.shelf.ShelfScreen
import com.thalzeronlabs.shelf.ui.theme.ShelfTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ShelfApp : Application()

@HiltViewModel
class AppViewModel @Inject constructor(private val settings: SettingsStore) : ViewModel() {
    val themeMode = settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }
}

enum class Tab(val route: String, val label: String) { SEARCH("search", "Search"), SHELF("shelf", "My shelf") }

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app: AppViewModel = hiltViewModel()
            val theme by app.themeMode.collectAsStateWithLifecycle()
            ShelfTheme(theme) { ShelfRoot(onTheme = app::setThemeMode) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShelfRoot(onTheme: (ThemeMode) -> Unit) {
    val nav = rememberNavController()
    val sheets = remember { SheetHostState() }
    val scope = rememberCoroutineScope()
    val current = nav.currentBackStackEntryAsState().value?.destination?.route

    ShelfScaffold(
        selected = Tab.entries.firstOrNull { it.route == current } ?: Tab.SEARCH,
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

/** The app chrome (top bar, bottom navigation) around a tab's content. Stateless, so screenshot tests can render it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfScaffold(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    onThemeClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shelf", fontWeight = FontWeight.Bold) },
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
