package com.spaceclipboard.app.network.discovery

import android.content.Context
import com.spaceclipboard.app.data.SettingsRepository
import com.spaceclipboard.app.domain.model.DiscoveredPeer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference

class DiscoveryController(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val scope: CoroutineScope,
) {
    private val discoveryRef = AtomicReference<NsdDiscovery?>(null)
    private val _peers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val peers: StateFlow<List<DiscoveredPeer>> = _peers.asStateFlow()
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private var timeoutJob: Job? = null
    private var observerJob: Job? = null

    fun startScan(timeoutMillis: Long = 12_000L) {
        _error.value = null
        _scanning.value = true
        timeoutJob?.cancel()
        scope.launch {
            val settings = settingsRepository.current()
            val discovery = ensureDiscovery(settings.deviceId)
            observerJob?.cancel()
            observerJob = scope.launch {
                discovery.peers.collect { _peers.value = it }
            }
            discovery.start()
            timeoutJob = scope.launch {
                delay(timeoutMillis)
                _scanning.value = false
                discovery.stop()
            }
        }
    }

    fun stop() {
        timeoutJob?.cancel()
        timeoutJob = null
        _scanning.value = false
        discoveryRef.get()?.stop()
    }

    suspend fun findPeer(id: String, timeoutMillis: Long = 5_000L): DiscoveredPeer? {
        startScan(timeoutMillis)
        val found = withTimeoutOrNull(timeoutMillis) {
            peers.first { values -> values.any { it.id == id } }
                .firstOrNull { it.id == id }
        }
        if (found != null) {
            _scanning.value = false
            discoveryRef.get()?.stop()
        }
        return found
    }

    private fun ensureDiscovery(ownDeviceId: String): NsdDiscovery {
        discoveryRef.get()?.let {
            it.ownDeviceId = ownDeviceId
            return it
        }
        return NsdDiscovery(context, ownDeviceId).also { discoveryRef.compareAndSet(null, it) }
    }
}
