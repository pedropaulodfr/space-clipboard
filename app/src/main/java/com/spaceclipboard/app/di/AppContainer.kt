package com.spaceclipboard.app.di

import android.content.Context
import android.util.Log
import com.spaceclipboard.app.clipboard.AndroidClipboardWriter
import com.spaceclipboard.app.clipboard.ClipboardWriter
import com.spaceclipboard.app.data.DeviceRepository
import com.spaceclipboard.app.data.HistoryRepository
import com.spaceclipboard.app.data.SettingsRepository
import com.spaceclipboard.app.data.db.AppDatabase
import com.spaceclipboard.app.network.NetworkInfoProvider
import com.spaceclipboard.app.network.client.ClipboardClient
import com.spaceclipboard.app.network.discovery.DiscoveryController
import com.spaceclipboard.app.pairing.PairingManager
import com.spaceclipboard.app.receiver.ReceiverController
import com.spaceclipboard.app.security.AndroidTokenCipher
import com.spaceclipboard.app.security.TokenCipher
import com.spaceclipboard.app.sender.DeviceService
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val exceptionHandler = CoroutineExceptionHandler { _, error ->
        Log.e("SpaceClipboard", "Erro em coroutine de aplicação", error)
    }
    val applicationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + exceptionHandler,
    )
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val database = AppDatabase.get(appContext)
    val settingsRepository = SettingsRepository(appContext)
    val historyRepository = HistoryRepository(database.historyDao())
    private val tokenCipher: TokenCipher by lazy { AndroidTokenCipher(appContext) }
    val deviceRepository: DeviceRepository by lazy {
        DeviceRepository(database.deviceDao(), tokenCipher)
    }
    val clipboardWriter: ClipboardWriter = AndroidClipboardWriter(appContext)
    val networkInfoProvider = NetworkInfoProvider(appContext)
    val clipboardClient: ClipboardClient by lazy { ClipboardClient(json) }
    val discoveryController: DiscoveryController by lazy {
        DiscoveryController(
            context = appContext,
            settingsRepository = settingsRepository,
            scope = applicationScope,
        )
    }
    val receiverController: ReceiverController by lazy {
        ReceiverController(
            context = appContext,
            scope = applicationScope,
            settingsRepository = settingsRepository,
            historyRepository = historyRepository,
            deviceRepository = deviceRepository,
            clipboard = clipboardWriter,
            networkInfo = networkInfoProvider,
        )
    }
    val deviceService: DeviceService by lazy {
        DeviceService(
            client = clipboardClient,
            devices = deviceRepository,
            settings = settingsRepository,
            discovery = discoveryController,
        )
    }
    val pairingManager: PairingManager by lazy {
        PairingManager(
            settings = settingsRepository,
            devices = deviceRepository,
            client = clipboardClient,
        )
    }

    init {
        applicationScope.launch { settingsRepository.ensureInitialized() }
    }
}
