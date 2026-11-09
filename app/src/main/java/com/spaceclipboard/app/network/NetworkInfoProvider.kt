package com.spaceclipboard.app.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.spaceclipboard.app.domain.model.NetworkSnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.net.Inet4Address

class NetworkInfoProvider(context: Context) {
    private val connectivity = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @Suppress("DEPRECATION")
    fun current(): NetworkSnapshot {
        val active = connectivity.activeNetwork
        val candidates = buildList {
            if (active != null) add(active)
            connectivity.allNetworks.forEach { network ->
                if (network != active) add(network)
            }
        }
        val snapshots = candidates.mapNotNull { readSnapshot(it) }
        return snapshots.firstOrNull { it.localTransport && it.ipAddress != null }
            ?: snapshots.firstOrNull { it.ipAddress != null }
            ?: snapshots.firstOrNull()
            ?: NetworkSnapshot()
    }

    fun observe(): Flow<NetworkSnapshot> = callbackFlow {
        trySend(current())
        val callbacks = mutableListOf<ConnectivityManager.NetworkCallback>()
        fun emit() {
            trySend(current())
        }
        val defaultCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = emit()
            override fun onLost(network: Network) = emit()
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = emit()
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) = emit()
        }
        callbacks += defaultCallback
        runCatching { connectivity.registerDefaultNetworkCallback(defaultCallback) }

        // Wi-Fi local sem internet pode não ser a rede padrão. Observamos Wi-Fi separadamente.
        val wifiCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = emit()
            override fun onLost(network: Network) = emit()
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = emit()
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) = emit()
        }
        val wifiRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        if (runCatching { connectivity.registerNetworkCallback(wifiRequest, wifiCallback) }.isSuccess) {
            callbacks += wifiCallback
        }
        awaitClose {
            callbacks.forEach { callback ->
                runCatching { connectivity.unregisterNetworkCallback(callback) }
            }
        }
    }.distinctUntilChanged()

    private fun readSnapshot(network: Network): NetworkSnapshot? {
        val caps = connectivity.getNetworkCapabilities(network) ?: return null
        val link = connectivity.getLinkProperties(network) ?: return null
        return snapshot(caps, link)
    }

    private fun snapshot(caps: NetworkCapabilities, link: LinkProperties): NetworkSnapshot {
        val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val isEthernet = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        val label = when {
            isWifi -> "Wi-Fi"
            isEthernet -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Rede móvel"
            else -> "Rede local"
        }
        val ipv4 = selectIpv4(link)
        return NetworkSnapshot(
            connected = ipv4 != null,
            localTransport = isWifi || isEthernet,
            transportLabel = label,
            interfaceName = link.interfaceName,
            ipAddress = ipv4?.hostAddress?.substringBefore('%'),
        )
    }

    private fun selectIpv4(link: LinkProperties): Inet4Address? {
        val addresses = link.linkAddresses.map { it.address }
        return addresses.filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
            ?: addresses.filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            ?: addresses.filterIsInstance<Inet4Address>()
                .firstOrNull { !it.isLoopbackAddress }
    }
}
