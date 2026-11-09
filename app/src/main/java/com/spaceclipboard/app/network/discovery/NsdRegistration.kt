package com.spaceclipboard.app.network.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import com.spaceclipboard.app.network.Protocol

class NsdRegistration(context: Context) {
    private val appContext = context.applicationContext
    private val nsd = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var listener: NsdManager.RegistrationListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var running = false

    fun start(
        deviceId: String,
        deviceName: String,
        port: Int,
        onRegistered: (String) -> Unit = {},
        onError: (String) -> Unit = {},
    ) {
        if (running) return
        running = true
        acquireMulticastLock()
        val serviceName = "SpaceClipboard-${deviceId.take(8)}"
        val info = NsdServiceInfo().apply {
            this.serviceName = serviceName
            serviceType = Protocol.SERVICE_TYPE
            this.port = port
            setAttribute("id", deviceId)
            setAttribute("name", deviceName.take(48))
            setAttribute("v", Protocol.VERSION.toString())
        }
        val registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(registered: NsdServiceInfo) {
                Log.i(TAG, "NSD registrado como ${registered.serviceName}")
                onRegistered(registered.serviceName)
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "Falha ao registrar NSD: $errorCode")
                running = false
                releaseMulticastLock()
                onError("Não foi possível anunciar o dispositivo na rede (NSD $errorCode).")
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                Log.i(TAG, "NSD removido")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "Falha ao remover NSD: $errorCode")
            }
        }
        listener = registrationListener
        runCatching {
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        }.onFailure {
            running = false
            releaseMulticastLock()
            onError("NSD indisponível neste dispositivo.")
        }
    }

    fun stop() {
        if (!running && listener == null) {
            releaseMulticastLock()
            return
        }
        running = false
        listener?.let { current ->
            runCatching { nsd.unregisterService(current) }
                .onFailure { Log.d(TAG, "NSD já estava inativo") }
        }
        listener = null
        releaseMulticastLock()
    }

    private fun acquireMulticastLock() {
        if (multicastLock?.isHeld == true) return
        runCatching {
            multicastLock = wifi.createMulticastLock("space-clipboard-nsd").apply {
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
        const val TAG = "SpaceNsdRegistration"
    }
}
