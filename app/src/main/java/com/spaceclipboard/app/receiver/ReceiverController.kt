package com.spaceclipboard.app.receiver

import android.content.Context
import android.util.Log
import com.spaceclipboard.app.clipboard.ClipboardWriter
import com.spaceclipboard.app.data.DeviceRepository
import com.spaceclipboard.app.data.HistoryRepository
import com.spaceclipboard.app.data.SettingsRepository
import com.spaceclipboard.app.domain.model.AppSettings
import com.spaceclipboard.app.domain.model.ContentType
import com.spaceclipboard.app.domain.model.HistoryItem
import com.spaceclipboard.app.domain.model.NetworkSnapshot
import com.spaceclipboard.app.domain.model.PeerIdentity
import com.spaceclipboard.app.domain.model.ReceiveFeedback
import com.spaceclipboard.app.domain.model.ReceiverPhase
import com.spaceclipboard.app.domain.model.ReceiverUiState
import com.spaceclipboard.app.network.ClipboardPayload
import com.spaceclipboard.app.network.NetworkInfoProvider
import com.spaceclipboard.app.network.PairRequest
import com.spaceclipboard.app.network.Protocol
import com.spaceclipboard.app.network.ReceiveResponse
import com.spaceclipboard.app.network.discovery.NsdRegistration
import com.spaceclipboard.app.network.server.ClipboardHttpServer
import com.spaceclipboard.app.network.server.ClipboardServerHost
import com.spaceclipboard.app.network.server.PairCompletion
import com.spaceclipboard.app.network.server.PairInspection
import com.spaceclipboard.app.network.server.ReceiverInfo
import com.spaceclipboard.app.pairing.PairAttemptLimiter
import com.spaceclipboard.app.pairing.PairingPayloadCodec
import com.spaceclipboard.app.pairing.PairingQrPayload
import com.spaceclipboard.app.pairing.PairingSession
import com.spaceclipboard.app.pairing.QrCodeGenerator
import com.spaceclipboard.app.security.Tokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

class ReceiverController(
    context: Context,
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val historyRepository: HistoryRepository,
    private val deviceRepository: DeviceRepository,
    private val clipboard: ClipboardWriter,
    private val networkInfo: NetworkInfoProvider,
    private val clock: () -> Long = System::currentTimeMillis,
) : ClipboardServerHost {
    private val appContext = context.applicationContext
    private val server = ClipboardHttpServer(
        host = this,
        logger = { message, error -> Log.d(TAG, message, error) },
    )
    private val nsd = NsdRegistration(appContext)
    private val coordinatorMutex = Mutex()
    private val pairingMutex = Mutex()
    private val pairLimiter = PairAttemptLimiter()
    private val _state = MutableStateFlow(ReceiverUiState())
    val state: StateFlow<ReceiverUiState> = _state.asStateFlow()

    @Volatile private var desiredRunning = false
    @Volatile private var currentSettings: AppSettings? = null
    @Volatile private var pairingSession: PairingSession? = null
    private var boundIp: String? = null
    private var boundPort: Int? = null
    private var networkJob: Job? = null
    private var rotationJob: Job? = null
    private var feedbackJob: Job? = null
    private val lifecycleGeneration = AtomicInteger(0)

    fun start() {
        desiredRunning = true
        val generation = lifecycleGeneration.incrementAndGet()
        scope.launch {
            coordinatorMutex.withLock {
                if (generation != lifecycleGeneration.get() || !desiredRunning) return@withLock
                ensureNetworkObserver()
                startSessionLocked(networkInfo.current())
            }
        }
    }

    fun stop() {
        desiredRunning = false
        val generation = lifecycleGeneration.incrementAndGet()
        networkJob?.cancel()
        networkJob = null
        scope.launch {
            coordinatorMutex.withLock {
                if (generation != lifecycleGeneration.get()) return@withLock
                stopServerLocked(
                    phase = ReceiverPhase.STOPPED,
                    message = "Servidor inativo.",
                )
            }
        }
    }

    fun restart() {
        if (!desiredRunning) return
        val generation = lifecycleGeneration.get()
        scope.launch {
            coordinatorMutex.withLock {
                if (generation != lifecycleGeneration.get() || !desiredRunning) return@withLock
                stopServerLocked(
                    phase = ReceiverPhase.STARTING,
                    message = "Reiniciando servidor local...",
                )
                startSessionLocked(networkInfo.current())
            }
        }
    }

    fun retry() {
        if (!desiredRunning) {
            start()
            return
        }
        restart()
    }

    private suspend fun ensureNetworkObserver() {
        if (networkJob?.isActive == true) return
        networkJob = scope.launch {
            networkInfo.observe().collect { snapshot ->
                coordinatorMutex.withLock {
                    onNetworkChangedLocked(snapshot)
                }
            }
        }
    }

    private suspend fun onNetworkChangedLocked(snapshot: NetworkSnapshot) {
        if (!desiredRunning) return
        _state.update { it.copy(network = snapshot) }
        if (!snapshot.canReceive) {
            if (server.isRunning() || boundPort != null) {
                stopServerLocked(
                    phase = ReceiverPhase.NO_NETWORK,
                    message = "Dispositivo não conectado à rede Wi-Fi local.",
                )
            } else {
                _state.update {
                    it.copy(
                        phase = ReceiverPhase.NO_NETWORK,
                        statusMessage = "Dispositivo não conectado à rede Wi-Fi local.",
                        network = snapshot,
                    )
                }
            }
            return
        }
        val ipChanged = boundIp != null && snapshot.ipAddress != boundIp
        if (ipChanged || (!server.isRunning() && _state.value.phase == ReceiverPhase.NO_NETWORK)) {
            if (server.isRunning()) {
                stopServerLocked(ReceiverPhase.STARTING, "Rede alterada. Reiniciando servidor...")
            }
            startSessionLocked(snapshot)
        }
    }

    private suspend fun startSessionLocked(snapshot: NetworkSnapshot) {
        if (!desiredRunning || server.isRunning()) return
        val settings = settingsRepository.current()
        currentSettings = settings
        _state.update {
            it.copy(
                phase = ReceiverPhase.STARTING,
                statusMessage = "Iniciando servidor local...",
                network = snapshot,
                deviceName = settings.deviceName,
                deviceId = settings.deviceId,
                qrPayload = null,
                qrBitmap = null,
                pairCode = null,
            )
        }
        if (!snapshot.canReceive || snapshot.ipAddress.isNullOrBlank()) {
            _state.update {
                it.copy(
                    phase = ReceiverPhase.NO_NETWORK,
                    statusMessage = "Dispositivo não conectado à rede Wi-Fi local.",
                )
            }
            return
        }
        val binding = try {
            // Vincula somente ao IPv4 da rede local, não a todas as interfaces.
            server.start(settings.serverPort, snapshot.ipAddress)
        } catch (error: Exception) {
            Log.e(TAG, "Servidor não iniciou", error)
            _state.update {
                it.copy(
                    phase = ReceiverPhase.ERROR,
                    statusMessage = "Não foi possível iniciar o servidor local.",
                    network = snapshot,
                )
            }
            return
        }
        if (!desiredRunning) {
            server.stop()
            return
        }
        boundIp = snapshot.ipAddress
        boundPort = binding.port
        _state.update {
            it.copy(
                phase = ReceiverPhase.ACTIVE,
                statusMessage = "Servidor ativo. Aguardando novo compartilhamento...",
                network = snapshot,
                port = binding.port,
                deviceName = settings.deviceName,
                deviceId = settings.deviceId,
            )
        }
        rotatePairingSessionLocked(force = true)
        nsd.start(
            deviceId = settings.deviceId,
            deviceName = settings.deviceName,
            port = binding.port,
            onError = { message ->
                _state.update {
                    if (it.phase == ReceiverPhase.ACTIVE) {
                        it.copy(statusMessage = "Servidor ativo. $message")
                    } else {
                        it
                    }
                }
            },
        )
        scope.launch {
            val latest = runCatching { historyRepository.latest() }
                .onFailure { Log.w(TAG, "Não foi possível ler o último item do histórico", it) }
                .getOrNull()
            if (latest != null) {
                _state.update { if (it.lastReceived == null) it.copy(lastReceived = latest) else it }
            }
        }
    }

    private suspend fun stopServerLocked(phase: ReceiverPhase, message: String) {
        nsd.stop()
        rotationJob?.cancel()
        rotationJob = null
        feedbackJob?.cancel()
        feedbackJob = null
        pairingSession = null
        boundIp = null
        boundPort = null
        server.stop()
        _state.update {
            it.copy(
                phase = phase,
                statusMessage = message,
                port = null,
                qrPayload = null,
                qrBitmap = null,
                pairCode = null,
                pairExpiresAt = null,
                feedback = null,
            )
        }
    }

    private suspend fun rotatePairingSessionLocked(force: Boolean = false) {
        if (!desiredRunning || !server.isRunning()) return
        val settings = currentSettings ?: settingsRepository.current()
        val ip = boundIp ?: return
        val port = boundPort ?: return
        val current = pairingSession
        if (!force && current != null && !current.isExpired(clock())) return
        val session = PairingSession.create(clock())
        pairingSession = session
        currentSettings = settings
        val payload = PairingQrPayload(
            v = Protocol.VERSION,
            id = settings.deviceId,
            host = ip,
            port = port,
            token = session.token,
            exp = session.expiresAt,
        )
        val encoded = PairingPayloadCodec.encode(payload)
        val bitmap = withContext(Dispatchers.Default) { QrCodeGenerator.generate(encoded, 512) }
        _state.update {
            it.copy(
                phase = ReceiverPhase.ACTIVE,
                pairCode = session.code,
                pairExpiresAt = session.expiresAt,
                qrPayload = encoded,
                qrBitmap = bitmap,
                network = it.network.copy(ipAddress = ip),
                port = port,
                deviceName = settings.deviceName,
                deviceId = settings.deviceId,
            )
        }
        rotationJob?.cancel()
        rotationJob = scope.launch {
            delay((session.expiresAt - clock()).coerceAtLeast(1_000L))
            if (!desiredRunning) return@launch
            coordinatorMutex.withLock {
                if (pairingSession?.token == session.token) {
                    rotatePairingSessionLocked(force = true)
                }
            }
        }
    }

    override fun receiverInfo(): ReceiverInfo {
        val settings = currentSettings
        return ReceiverInfo(
            id = settings?.deviceId ?: "",
            name = settings?.deviceName ?: "Space Clipboard",
            port = boundPort ?: settings?.serverPort ?: Protocol.DEFAULT_PORT,
        )
    }

    override fun pairingAvailable(): Boolean {
        val session = pairingSession ?: return false
        return !session.isExpired(clock())
    }

    override suspend fun inspectPair(
        token: String?,
        code: String?,
        remoteHost: String,
    ): PairInspection {
        val session = pairingSession
        if (session == null || session.isExpired(clock())) {
            return PairInspection(false, "O código de pareamento expirou.", 0L)
        }
        if (!pairLimiter.allow(remoteHost, clock())) {
            return PairInspection(false, "Muitas tentativas. Aguarde um minuto.", session.expiresAt)
        }
        if (!session.matches(token, code)) {
            return PairInspection(false, "Código de pareamento incorreto.", session.expiresAt)
        }
        return PairInspection(true, "Código válido.", session.expiresAt)
    }

    override suspend fun completePair(request: PairRequest, remoteHost: String): PairCompletion {
        return pairingMutex.withLock {
            val session = pairingSession
            if (session == null || session.isExpired(clock())) {
                return@withLock PairCompletion.Failure(
                    410,
                    "pairing_expired",
                    "O código de pareamento expirou. Gere outro no receptor.",
                )
            }
            if (!pairLimiter.allow(remoteHost, clock())) {
                return@withLock PairCompletion.Failure(
                    429,
                    "too_many_attempts",
                    "Muitas tentativas de pareamento. Aguarde um minuto.",
                )
            }
            if (!session.matches(request.pairToken, request.pairCode)) {
                return@withLock PairCompletion.Failure(
                    410,
                    "pairing_invalid",
                    "Código de pareamento inválido ou já utilizado.",
                )
            }
            val settings = currentSettings ?: settingsRepository.current()
            if (request.senderId == settings.deviceId) {
                return@withLock PairCompletion.Failure(
                    409,
                    "same_device",
                    "Este é o próprio dispositivo.",
                )
            }
            val credential = Tokens.newToken(32)
            val peer = PeerIdentity(
                id = request.senderId,
                name = request.senderName,
                host = remoteHost.substringBefore('%'),
                port = request.senderPort,
            )
            runCatching {
                deviceRepository.savePairing(
                    peer = peer,
                    protocol = Protocol.VERSION,
                    outboundToken = request.senderToken,
                    inboundToken = credential,
                    now = clock(),
                )
            }.onFailure {
                Log.e(TAG, "Falha ao salvar pareamento", it)
                return@withLock PairCompletion.Failure(
                    500,
                    "pairing_store_failed",
                    "Não foi possível salvar o pareamento.",
                )
            }
            pairLimiter.reset(remoteHost)
            val newSession = PairingSession.create(clock())
            pairingSession = newSession
            val receiver = receiverInfo()
            publishPairing(newSession)
            PairCompletion.Success(receiver, credential)
        }
    }

    private suspend fun publishPairing(session: PairingSession) {
        val settings = currentSettings ?: return
        val ip = boundIp ?: return
        val port = boundPort ?: return
        val payload = PairingQrPayload(
            v = Protocol.VERSION,
            id = settings.deviceId,
            host = ip,
            port = port,
            token = session.token,
            exp = session.expiresAt,
        )
        val encoded = PairingPayloadCodec.encode(payload)
        val bitmap = withContext(Dispatchers.Default) { QrCodeGenerator.generate(encoded, 512) }
        _state.update {
            it.copy(
                pairCode = session.code,
                pairExpiresAt = session.expiresAt,
                qrPayload = encoded,
                qrBitmap = bitmap,
            )
        }
        rotationJob?.cancel()
        rotationJob = scope.launch {
            delay((session.expiresAt - clock()).coerceAtLeast(1_000L))
            if (!desiredRunning) return@launch
            coordinatorMutex.withLock {
                if (pairingSession?.token == session.token) rotatePairingSessionLocked(force = true)
            }
        }
    }

    override suspend fun authenticate(tokenHash: String): PeerIdentity? =
        deviceRepository.authenticate(tokenHash)

    override suspend fun receive(
        payload: ClipboardPayload,
        type: ContentType,
        peer: PeerIdentity,
    ): ReceiveResponse {
        val now = clock()
        val item = HistoryItem(
            id = payload.id,
            text = payload.text,
            type = type,
            senderName = peer.name,
            senderId = peer.id,
            receivedAt = now,
        )
        val inserted = historyRepository.insert(item)
        deviceRepository.touch(peer.id, now)
        if (!inserted) {
            return ReceiveResponse(
                success = true,
                message = "Conteúdo já recebido anteriormente.",
                duplicate = true,
            )
        }
        val copyResult = clipboard.copy(item.text)
        val feedback = if (copyResult.isSuccess) {
            ReceiveFeedback(
                message = "Novo conteúdo recebido e copiado!",
                success = true,
                at = now,
            )
        } else {
            ReceiveFeedback(
                message = "Conteúdo recebido, mas não foi possível copiar.",
                success = false,
                at = now,
            )
        }
        _state.update {
            it.copy(
                lastReceived = item,
                feedback = feedback,
                statusMessage = if (copyResult.isSuccess) {
                    "Novo conteúdo recebido e copiado!"
                } else {
                    "Conteúdo recebido. Falha ao copiar."
                },
            )
        }
        feedbackJob?.cancel()
        feedbackJob = scope.launch {
            delay(8_000L)
            _state.update { current ->
                if (current.feedback?.at == now) {
                    current.copy(
                        feedback = null,
                        statusMessage = "Servidor ativo. Aguardando novo compartilhamento...",
                    )
                } else {
                    current
                }
            }
        }
        return ReceiveResponse(
            success = true,
            message = if (copyResult.isSuccess) {
                "Conteúdo recebido e copiado para a área de transferência"
            } else {
                "Conteúdo recebido, mas não foi possível copiar"
            },
            duplicate = false,
        )
    }

    override suspend fun deleteHistory(id: String, peer: PeerIdentity): Boolean {
        val deleted = historyRepository.delete(id)
        if (deleted) {
            _state.update {
                if (it.lastReceived?.id == id) {
                    it.copy(lastReceived = historyRepository.latest())
                } else {
                    it
                }
            }
        }
        return deleted
    }

    private companion object {
        const val TAG = "SpaceReceiver"
    }
}
