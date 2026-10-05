package dev.lumbina.tv.platform.system

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.lumbina.tv.core.domain.phone.LocalAddress
import dev.lumbina.tv.core.domain.status.ConnectivityStatus
import dev.lumbina.tv.core.domain.status.NetworkKind
import dev.lumbina.tv.core.domain.status.NetworkState
import java.net.Inet4Address
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** The default network, from ConnectivityManager's callbacks (no location permission: no Wi-Fi name). */
internal class AndroidConnectivityStatus @Inject constructor(@ApplicationContext private val context: Context) :
    ConnectivityStatus {

    override fun observe(): Flow<NetworkState> = callbackFlow {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(stateOf(capabilities))
            }

            override fun onLost(network: Network) {
                trySend(NetworkState.NONE)
            }
        }
        trySend(manager.activeNetwork?.let(manager::getNetworkCapabilities)?.let(::stateOf) ?: NetworkState.NONE)
        manager.registerDefaultNetworkCallback(callback)
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun stateOf(capabilities: NetworkCapabilities): NetworkState {
        val kind = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkKind.Wifi
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkKind.Ethernet
            else -> NetworkKind.Other
        }
        val online = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return NetworkState(kind, if (kind == NetworkKind.Wifi) wifiLevel(capabilities) else null, online)
    }

    @Suppress("DEPRECATION") // calculateSignalLevel(rssi, levels) is the one that exists on API 26–29 too
    private fun wifiLevel(capabilities: NetworkCapabilities): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val rssi = capabilities.signalStrength.takeIf { it != NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED }
            ?: return null
        return WifiManager.calculateSignalLevel(rssi, NetworkState.MAX_SIGNAL + 1)
    }
}

/** The default network's IPv4 address (what a phone on the same Wi-Fi can reach). */
internal class AndroidLocalAddress @Inject constructor(@ApplicationContext private val context: Context) :
    LocalAddress {
    override fun ipv4(): String? {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val link = manager.activeNetwork?.let(manager::getLinkProperties) ?: return null
        return link.linkAddresses
            .map { it.address }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
    }
}
