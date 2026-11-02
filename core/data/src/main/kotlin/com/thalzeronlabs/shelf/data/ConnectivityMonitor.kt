package com.thalzeronlabs.shelf.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

/** Whether the device has a network that can reach the internet. Drives the app-wide "Offline" indicator. */
interface ConnectivityMonitor {
    val isOnline: Flow<Boolean>
}

/**
 * Follows the default network with [ConnectivityManager.registerDefaultNetworkCallback]. The flow starts with
 * the current state, so the indicator is right on the first frame, and stops listening when nobody collects.
 */
class AndroidConnectivityMonitor @Inject constructor(@ApplicationContext private val context: Context) : ConnectivityMonitor {

    override val isOnline: Flow<Boolean> = callbackFlow {
        val manager = context.getSystemService<ConnectivityManager>()
        if (manager == null) {
            trySend(true) // Nothing to observe: never claim to be offline.
            close()
            return@callbackFlow
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }

            override fun onLost(network: Network) {
                trySend(false)
            }
        }
        trySend(manager.isOnline())
        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged().conflate()

    private fun ConnectivityManager.isOnline(): Boolean =
        getNetworkCapabilities(activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}
