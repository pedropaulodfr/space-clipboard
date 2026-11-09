package com.spaceclipboard.app.network.server

import com.spaceclipboard.app.domain.ClipboardValidator
import com.spaceclipboard.app.domain.TextValidation
import com.spaceclipboard.app.domain.model.ContentType
import com.spaceclipboard.app.domain.model.PeerIdentity
import com.spaceclipboard.app.network.ApiError
import com.spaceclipboard.app.network.ApiErrorResponse
import com.spaceclipboard.app.network.AuthCheckResponse
import com.spaceclipboard.app.network.ClipboardPayload
import com.spaceclipboard.app.network.DeleteResponse
import com.spaceclipboard.app.network.DeviceInfoDto
import com.spaceclipboard.app.network.PairInspectionResponse
import com.spaceclipboard.app.network.PairRequest
import com.spaceclipboard.app.network.PairResponse
import com.spaceclipboard.app.network.Protocol
import com.spaceclipboard.app.network.ReceiveResponse
import com.spaceclipboard.app.network.ReceiverInfoDto
import com.spaceclipboard.app.network.RootResponse
import com.spaceclipboard.app.network.StatusResponse
import com.spaceclipboard.app.security.Tokens
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.server.plugins.bodylimit.RequestBodyLimit
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import java.net.InetSocketAddress
import java.net.ServerSocket

data class ReceiverInfo(
    val id: String,
    val name: String,
    val port: Int,
)

data class PairInspection(
    val valid: Boolean,
    val reason: String,
    val expiresAt: Long,
)

sealed interface PairCompletion {
    data class Success(val receiver: ReceiverInfo, val credential: String) : PairCompletion
    data class Failure(val status: Int, val code: String, val message: String) : PairCompletion
}

interface ClipboardServerHost {
    fun receiverInfo(): ReceiverInfo
    fun pairingAvailable(): Boolean
    suspend fun inspectPair(token: String?, code: String?, remoteHost: String): PairInspection
    suspend fun completePair(request: PairRequest, remoteHost: String): PairCompletion
    suspend fun authenticate(tokenHash: String): PeerIdentity?
    suspend fun receive(
        payload: ClipboardPayload,
        type: ContentType,
        peer: PeerIdentity,
    ): ReceiveResponse
    suspend fun deleteHistory(id: String, peer: PeerIdentity): Boolean
}

data class ServerBinding(val port: Int)

class ClipboardHttpServer(
    private val host: ClipboardServerHost,
    private val logger: (String, Throwable?) -> Unit = { _, _ -> },
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    },
) {
    private var engine: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private var boundPort: Int? = null

    suspend fun start(requestedPort: Int, bindHost: String = "0.0.0.0"): ServerBinding {
        engine?.let { return ServerBinding(boundPort ?: requestedPort) }
        val candidates = candidatePorts(requestedPort, bindHost)
        var lastError: Throwable? = null
        for (port in candidates) {
            val candidate = embeddedServer(
                factory = CIO,
                host = bindHost,
                port = port,
                module = { module() },
            )
            try {
                // Não envolva start() em withContext: o engine CIO mantém jobs filhos
                // no contexto atual e o withContext só retornaria quando o servidor parasse.
                candidate.start(wait = false)
                engine = candidate
                boundPort = port
                logger("Servidor Space Clipboard ativo na porta $port", null)
                return ServerBinding(port)
            } catch (error: Throwable) {
                lastError = error
                runCatching { candidate.stop(200, 1_000) }
                logger("Falha ao abrir a porta $port", error)
            }
        }
        throw IllegalStateException(
            "Não foi possível iniciar o servidor local.",
            lastError,
        )
    }

    suspend fun stop() {
        val running = engine ?: return
        engine = null
        boundPort = null
        runCatching { running.stop(250, 2_500) }
            .onFailure { logger("Falha ao encerrar o servidor", it) }
    }

    fun isRunning(): Boolean = engine != null

    private fun candidatePorts(requestedPort: Int, bindHost: String): List<Int> {
        if (requestedPort == 0) return listOf(findEphemeralPort())
        val preferred = (requestedPort..minOf(65535, requestedPort + 9)).toMutableList()
        if (preferred.none { isPortAvailable(it, bindHost) }) {
            preferred += findEphemeralPort()
        }
        return preferred.filter { isPortAvailable(it, bindHost) }.distinct().ifEmpty {
            listOf(findEphemeralPort())
        }
    }

    private fun isPortAvailable(port: Int, bindHost: String): Boolean = runCatching {
        ServerSocket().use { socket ->
            socket.reuseAddress = false
            socket.bind(InetSocketAddress(bindHost, port))
        }
        true
    }.getOrDefault(false)

    private fun findEphemeralPort(): Int = ServerSocket(0).use { it.localPort }

    private fun Application.module() {
        install(ContentNegotiation) { json(json) }
        install(RequestBodyLimit) {
            bodyLimit { Protocol.MAX_BODY_BYTES }
        }
        install(StatusPages) {
            exception<PayloadTooLargeException> { call, _ ->
                call.respondError(
                    HttpStatusCode.PayloadTooLarge,
                    "content_too_large",
                    "O corpo da requisição excede o limite de 96 KB.",
                )
            }
            exception<BadRequestException> { call, cause ->
                logger("Requisição inválida", cause)
                call.respondError(
                    HttpStatusCode.BadRequest,
                    "invalid_request",
                    "JSON inválido ou incompleto.",
                )
            }
            exception<Throwable> { call, cause ->
                logger("Erro não tratado no servidor", cause)
                if (!call.response.isCommitted) {
                    call.respondError(
                        HttpStatusCode.InternalServerError,
                        "internal_error",
                        "Erro interno no receptor.",
                    )
                }
            }
        }
        routing {
            get("/") {
                call.respond(RootResponse())
            }
            get("/api/status") {
                val info = host.receiverInfo()
                call.respond(
                    StatusResponse(
                        deviceId = info.id,
                        deviceName = info.name,
                        port = info.port,
                        pairingAvailable = host.pairingAvailable(),
                        timestamp = System.currentTimeMillis(),
                    ),
                )
            }
            get("/api/pair") {
                val token = call.request.queryParameters["token"]
                val code = call.request.queryParameters["code"]
                if (token.isNullOrBlank() && code.isNullOrBlank()) {
                    call.respondError(
                        HttpStatusCode.BadRequest,
                        "pairing_required",
                        "Informe o QR Code ou o código de pareamento.",
                    )
                    return@get
                }
                val remote = call.request.origin.remoteHost
                val inspection = host.inspectPair(token, code, remote)
                if (!inspection.valid) {
                    call.respondError(
                        HttpStatusCode.Gone,
                        "pairing_invalid",
                        inspection.reason,
                    )
                    return@get
                }
                val info = host.receiverInfo()
                call.respond(
                    PairInspectionResponse(
                        valid = true,
                        receiver = ReceiverInfoDto(
                            id = info.id,
                            name = info.name,
                            protocol = Protocol.VERSION,
                            port = info.port,
                        ),
                        expiresAt = inspection.expiresAt,
                    ),
                )
            }
            post("/api/pair") {
                val request = call.receive<PairRequest>()
                val senderId = ClipboardValidator.validateId(request.senderId)
                if (senderId == null) {
                    call.respondError(HttpStatusCode.BadRequest, "invalid_device", "Identificador inválido.")
                    return@post
                }
                val senderName = ClipboardValidator.sanitizeName(request.senderName, "Dispositivo Android")
                if (request.senderPort !in 1..65535) {
                    call.respondError(HttpStatusCode.BadRequest, "invalid_port", "Porta inválida.")
                    return@post
                }
                if (request.senderToken.length !in 20..256) {
                    call.respondError(HttpStatusCode.BadRequest, "invalid_token", "Credencial inválida.")
                    return@post
                }
                when (val result = host.completePair(
                    request.copy(senderId = senderId, senderName = senderName),
                    call.request.origin.remoteHost,
                )) {
                    is PairCompletion.Success -> call.respond(
                        PairResponse(
                            receiver = ReceiverInfoDto(
                                id = result.receiver.id,
                                name = result.receiver.name,
                                protocol = Protocol.VERSION,
                                port = result.receiver.port,
                            ),
                            credential = result.credential,
                        ),
                    )
                    is PairCompletion.Failure -> call.respondError(
                        HttpStatusCode.fromValue(result.status),
                        result.code,
                        result.message,
                    )
                }
            }
            get("/api/auth/check") {
                val peer = call.authenticatedPeer()
                if (peer == null) {
                    call.respondError(HttpStatusCode.Unauthorized, "unauthorized", "Credencial inválida.")
                    return@get
                }
                call.respond(
                    AuthCheckResponse(device = DeviceInfoDto(id = peer.id, name = peer.name)),
                )
            }
            post("/api/clipboard") {
                val peer = call.authenticatedPeer()
                if (peer == null) {
                    call.respondError(HttpStatusCode.Unauthorized, "unauthorized", "Credencial inválida.")
                    return@post
                }
                val payload = call.receive<ClipboardPayload>()
                val id = ClipboardValidator.validateId(payload.id)
                if (id == null) {
                    call.respondError(HttpStatusCode.BadRequest, "invalid_id", "UUID inválido.")
                    return@post
                }
                when (val validation = ClipboardValidator.validate(payload.text)) {
                    is TextValidation.Invalid -> {
                        call.respondError(HttpStatusCode.BadRequest, validation.code, validation.message)
                        return@post
                    }
                    is TextValidation.Valid -> {
                        val declaredType = payload.type?.let { ContentType.fromWire(it) }
                        if (payload.type != null && declaredType == null) {
                            call.respondError(
                                HttpStatusCode.BadRequest,
                                "invalid_type",
                                "Tipo de conteúdo inválido.",
                            )
                            return@post
                        }
                        val effectiveType = declaredType ?: validation.type
                        val response = host.receive(
                            payload.copy(id = id, text = validation.text, type = effectiveType.wireValue()),
                            effectiveType,
                            peer,
                        )
                        val status = if (response.duplicate) {
                            HttpStatusCode.OK
                        } else {
                            HttpStatusCode.Created
                        }
                        call.respond(status, response)
                    }
                }
            }
            delete("/api/history/{id}") {
                val peer = call.authenticatedPeer()
                if (peer == null) {
                    call.respondError(HttpStatusCode.Unauthorized, "unauthorized", "Credencial inválida.")
                    return@delete
                }
                val id = ClipboardValidator.validateId(call.parameters["id"])
                if (id == null) {
                    call.respondError(HttpStatusCode.BadRequest, "invalid_id", "UUID inválido.")
                    return@delete
                }
                val deleted = host.deleteHistory(id, peer)
                if (!deleted) {
                    call.respondError(HttpStatusCode.NotFound, "not_found", "Registro não encontrado.")
                } else {
                    call.respond(DeleteResponse(deleted = true))
                }
            }
        }
    }

    private suspend fun ApplicationCall.authenticatedPeer(): PeerIdentity? {
        val header = request.headers[HttpHeaders.Authorization] ?: return null
        if (!header.startsWith("Bearer ", ignoreCase = true)) return null
        val token = header.substring(7).trim()
        if (token.length !in 20..256) return null
        return host.authenticate(Tokens.sha256(token))
    }

    private suspend fun ApplicationCall.respondError(
        status: HttpStatusCode,
        code: String,
        message: String,
    ) {
        respond(status, ApiErrorResponse(error = ApiError(code = code, message = message)))
    }
}
