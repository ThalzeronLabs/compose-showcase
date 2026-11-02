package com.thalzeronlabs.shelf.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetworkCapabilities

/** The monitor behind the "Offline" indicator, driven through Robolectric's ConnectivityManager. */
@RunWith(AndroidJUnit4::class)
class ConnectivityMonitorTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val monitor = AndroidConnectivityMonitor(context)

    private fun capabilities(internet: Boolean): NetworkCapabilities = ShadowNetworkCapabilities.newInstance().also {
        if (internet) shadowOf(it).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun callback() = shadowOf(manager).networkCallbacks.single()

    @Test
    fun `starts with the current state, then follows the default network`() = runTest {
        val network = manager.activeNetwork!!
        shadowOf(manager).setNetworkCapabilities(network, capabilities(internet = true))

        monitor.isOnline.test {
            assertEquals(true, awaitItem())

            callback().onLost(network)
            assertEquals(false, awaitItem())

            callback().onCapabilitiesChanged(network, capabilities(internet = true))
            assertEquals(true, awaitItem())

            // A network without internet access (a captive Wi-Fi, say) still counts as offline.
            callback().onCapabilitiesChanged(network, capabilities(internet = false))
            assertEquals(false, awaitItem())

            // Repeats are dropped, so the indicator does not flicker.
            callback().onLost(network)
            expectNoEvents()
        }
    }

    @Test
    fun `no network at start is offline, and nobody collecting means nobody listening`() = runTest {
        shadowOf(manager).setDefaultNetworkActive(false)
        shadowOf(manager).setActiveNetworkInfo(null)

        monitor.isOnline.test {
            assertEquals(false, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(shadowOf(manager).networkCallbacks.isEmpty())
    }
}
