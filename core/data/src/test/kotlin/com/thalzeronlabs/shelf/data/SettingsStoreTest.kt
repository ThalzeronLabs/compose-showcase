package com.thalzeronlabs.shelf.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.thalzeronlabs.shelf.model.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The theme setting against a real Preferences DataStore file (plain JVM, no Android needed). */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val file: File by lazy { File(folder.root, "settings.preferences_pb") }

    /** Opens the DataStore file like the app does, in its own scope so a test can "restart the app". */
    private fun open(scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) { file }

    @Test
    fun `with nothing saved the theme follows the system`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        assertEquals(ThemeMode.SYSTEM, SettingsStore(open(scope)).themeMode.first())
        scope.coroutineContext[Job]!!.cancelAndJoin()
    }

    @Test
    fun `a chosen theme is saved and survives a restart`() = runTest {
        val first = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        val settings = SettingsStore(open(first))
        settings.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, settings.themeMode.first())
        first.coroutineContext[Job]!!.cancelAndJoin() // the process goes away

        val second = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        assertEquals(ThemeMode.DARK, SettingsStore(open(second)).themeMode.first())
        second.coroutineContext[Job]!!.cancelAndJoin()
    }

    @Test
    fun `a value this version does not know falls back to the system theme instead of crashing`() = runTest {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())
        val store = open(scope)
        // What a newer (or older) version of the app could have written.
        store.edit { it[stringPreferencesKey("theme_mode")] = "NEON" }
        assertEquals(ThemeMode.SYSTEM, SettingsStore(store).themeMode.first())
        scope.coroutineContext[Job]!!.cancelAndJoin()
    }
}
