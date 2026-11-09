package com.spaceclipboard.app.network

import kotlinx.serialization.Serializable

object Protocol {
    const val VERSION = 1
    const val SERVICE_TYPE = "_spaceclipboard._tcp."
    const val DEFAULT_PORT = 8765
    const val MAX_BODY_BYTES = 96 * 1024L
    const val MAX_TEXT_BYTES = 64 * 1024
    const val HEADER_DEVICE_ID = "X-Space-Device-Id"
    const val HEADER_PROTOCOL = "X-Space-Protocol"
    const val PAIRING_TTL_MS = 10 * 60 * 1000L
}

@Serializable
data class ClipboardPayload(
    val id: String,
    val text: String,
    val type: String? = null,
    val sender: String = "",
    val timestamp: Long = 0L,
)

@Serializable
data class ReceiveResponse(
    val success: Boolean = true,
    val message: String = "Conteúdo recebido com sucesso",
    val duplicate: Boolean = false,
)

@Serializable
data class ApiError(
    val code: String,
    val message: String,
)

@Serializable
data class ApiErrorResponse(
    val success: Boolean = false,
    val error: ApiError,
)

@Serializable
data class DeviceInfoDto(
    val id: String,
    val name: String,
)

@Serializable
data class ReceiverInfoDto(
    val id: String,
    val name: String,
    val protocol: Int,
    val port: Int,
)

@Serializable
data class StatusResponse(
    val success: Boolean = true,
    val server: String = "online",
    val deviceId: String,
    val deviceName: String,
    val protocol: Int = Protocol.VERSION,
    val port: Int,
    val pairingAvailable: Boolean,
    val timestamp: Long,
)

@Serializable
data class PairInspectionResponse(
    val success: Boolean = true,
    val valid: Boolean,
    val receiver: ReceiverInfoDto,
    val expiresAt: Long,
)

@Serializable
data class PairRequest(
    val pairToken: String? = null,
    val pairCode: String? = null,
    val senderId: String,
    val senderName: String,
    val senderPort: Int,
    val senderToken: String,
)

@Serializable
data class PairResponse(
    val success: Boolean = true,
    val message: String = "Pareamento concluído",
    val receiver: ReceiverInfoDto,
    val credential: String,
)

@Serializable
data class AuthCheckResponse(
    val success: Boolean = true,
    val device: DeviceInfoDto,
    val protocol: Int = Protocol.VERSION,
)

@Serializable
data class DeleteResponse(
    val success: Boolean = true,
    val deleted: Boolean,
)

@Serializable
data class RootResponse(
    val name: String = "Space Clipboard",
    val protocol: Int = Protocol.VERSION,
    val status: String = "online",
)

class ApiException(
    val statusCode: Int,
    val errorCode: String,
    override val message: String,
) : Exception(message)
