package com.spaceclipboard.app.domain.model

enum class ContentType {
    TEXT,
    URL;

    companion object {
        fun fromWire(value: String?): ContentType? = when (value?.lowercase()) {
            "text" -> TEXT
            "url", "link" -> URL
            else -> null
        }
    }

    fun wireValue(): String = name.lowercase()
}

data class ClipboardText(
    val id: String,
    val text: String,
    val type: ContentType,
    val senderName: String,
    val senderId: String,
    val timestamp: Long,
)

data class HistoryItem(
    val id: String,
    val text: String,
    val type: ContentType,
    val senderName: String,
    val senderId: String,
    val receivedAt: Long,
)

data class PairedDevice(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val protocol: Int,
    val lastSeen: Long?,
    val createdAt: Long,
)

data class PeerIdentity(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
)

data class AppSettings(
    val deviceId: String,
    val deviceName: String,
    val serverPort: Int,
)

data class NetworkSnapshot(
    val connected: Boolean = false,
    val localTransport: Boolean = false,
    val transportLabel: String = "Sem conexão",
    val interfaceName: String? = null,
    val ipAddress: String? = null,
) {
    val canReceive: Boolean get() = connected && localTransport && !ipAddress.isNullOrBlank()
}

enum class ReceiverPhase {
    STOPPED,
    STARTING,
    ACTIVE,
    NO_NETWORK,
    ERROR,
}

data class ReceiveFeedback(
    val message: String,
    val success: Boolean,
    val at: Long,
)

data class ReceiverUiState(
    val phase: ReceiverPhase = ReceiverPhase.STOPPED,
    val statusMessage: String = "Servidor inativo.",
    val network: NetworkSnapshot = NetworkSnapshot(),
    val port: Int? = null,
    val deviceName: String = "",
    val deviceId: String = "",
    val pairCode: String? = null,
    val pairExpiresAt: Long? = null,
    val qrPayload: String? = null,
    val qrBitmap: android.graphics.Bitmap? = null,
    val lastReceived: HistoryItem? = null,
    val feedback: ReceiveFeedback? = null,
)

data class DiscoveredPeer(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val protocol: Int,
)

sealed interface SendOutcome {
    data class Success(val deviceName: String, val duplicate: Boolean = false) : SendOutcome
    data class Failure(val message: String, val retryable: Boolean = true) : SendOutcome
}
