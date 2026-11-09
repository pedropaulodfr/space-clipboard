package com.spaceclipboard.app.network.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import com.spaceclipboard.app.domain.model.DiscoveredPeer
import com.spaceclipboard.app.network.Protocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NsdDiscovery(
    context: Context,
    var ownDeviceId: String,
) {
    private val appContext = context.applicationContext
    private val nsd = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val _peers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val peers: StateFlow<List<DiscoveredPeer>> = _peers.asStateFlow()
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val byServiceName = mutableMapOf<String, DiscoveredPeer>()
    private val resolveQueue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var running = false
    private var listener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    @Suppress("DEPRECATION")
    fun start() {
        if (running) return
        running = true
        _error.value = null
        _scanning.value = true
        acquireMulticastLock()
        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.i(TAG, "Descoberta NSD iniciada")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "Falha ao iniciar descoberta: $errorCode")
                _error.value = "Não foi possível procurar dispositivos na rede."
                _scanning.value = false
                stop()
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!running) return
                if (serviceInfo.serviceType != Protocol.SERVICE_TYPE) return
                enqueueResolve(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val peer = synchronized(byServiceName) {
                    byServiceName.remove(serviceInfo.serviceName)
                }
                if (peer != null) publish()
            }

            override fun onDiscoveryStopped(serviceType: String) {
                _scanning.value = false
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "Falha ao encerrar descoberta: $errorCode")
                _scanning.value = false
            }
        }
        listener = discoveryListener
        runCatching {
            nsd.discoverServices(Protocol.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        }.onFailure {
            _error.value = "Descoberta de rede indisponível neste dispositivo."
            _scanning.value = false
            stop()
        }
    }

    fun stop() {
        running = false
        listener?.let { current ->
            runCatching { nsd.stopServiceDiscovery(current) }
                .onFailure { Log.d(TAG, "Descoberta já estava parada") }
        }
        listener = null
        synchronized(byServiceName) { byServiceName.clear() }
        resolveQueue.clear()
        resolving = false
        _peers.value = emptyList()
        _scanning.value = false
        releaseMulticastLock()
    }

    @Suppress("DEPRECATION")
    private fun enqueueResolve(info: NsdServiceInfo) {
        synchronized(resolveQueue) { resolveQueue.addLast(info) }
        resolveNext()
    }

    @Suppress("DEPRECATION")
    private fun resolveNext() {
        val next: NsdServiceInfo?
        synchronized(resolveQueue) {
            if (resolving || !running) return
            next = resolveQueue.removeFirstOrNull()
            if (next == null) return
            resolving = true
        }
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.d(TAG, "Resolução NSD falhou: $errorCode")
                synchronized(resolveQueue) { resolving = false }
                resolveNext()
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                handleResolved(serviceInfo)
                synchronized(resolveQueue) { resolving = false }
                resolveNext()
            }
        }
        runCatching { nsd.resolveService(next!!, listener) }
            .onFailure {
                synchronized(resolveQueue) { resolving = false }
                resolveNext()
            }
    }

    @Suppress("DEPRECATION")
    private fun handleResolved(info: NsdServiceInfo) {
        if (!running) return
        val id = runCatching { info.attributes["id"]?.toString(Charsets.UTF_8) }.getOrNull()
            ?: return
        if (id == ownDeviceId) return
        val host = info.host?.hostAddress?.substringBefore('%') ?: return
        val port = info.port
        if (port !in 1..65535) return
        val name = runCatching { info.attributes["name"]?.toString(Charsets.UTF_8) }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: info.serviceName
        val protocol = runCatching {
            info.attributes["v"]?.toString(Charsets.UTF_8)?.toIntOrNull()
        }.getOrNull() ?: Protocol.VERSION
        if (protocol != Protocol.VERSION) return
        val peer = DiscoveredPeer(id, name, host, port, protocol)
        synchronized(byServiceName) { byServiceName[info.serviceName] = peer }
        publish()
    }

    private fun publish() {
        val values = synchronized(byServiceName) {
            byServiceName.values.distinctBy { it.id }.sortedBy { it.name.lowercase() }
        }
        _peers.value = values
    }

    private fun acquireMulticastLock() {
        if (multicastLock?.isHeld == true) return
        runCatching {
            multicastLock = wifi.createMulticastLock("space-clipboard-discovery").apply {
                setReferenceCounted(false)
                acquire()
            }
        }.onFailure { Log.d(TAG, "Multicast lock indisponível", it) }
    }

    private fun releaseMulticastLock() {
        runCatching { if (multicastLock?.isHeld == true) multicastLock?.release() }
        multicastLock = null
    }

    private companion object {
        const val TAG = "SpaceNsdDiscovery"
    }
}
