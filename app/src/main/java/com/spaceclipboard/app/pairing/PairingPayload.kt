package com.spaceclipboard.app.pairing

import com.spaceclipboard.app.network.Protocol
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64
import java.util.UUID

@Serializable
data class PairingQrPayload(
    @SerialName("v") val v: Int = Protocol.VERSION,
    @SerialName("i") val id: String,
    @SerialName("h") val host: String,
    @SerialName("p") val port: Int,
    @SerialName("t") val token: String,
    @SerialName("e") val exp: Long,
) {
    fun isExpired(now: Long): Boolean = exp <= now
}

sealed interface PairingDecodeResult {
    data class Success(val payload: PairingQrPayload) : PairingDecodeResult
    data class Error(val message: String) : PairingDecodeResult
}

object PairingPayloadCodec {
    private const val PREFIX = "SCP1:"
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = false
    }

    fun encode(payload: PairingQrPayload): String {
        val raw = json.encodeToString(PairingQrPayload.serializer(), payload)
        return PREFIX + Base64.getUrlEncoder().withoutPadding()
            .encodeToString(raw.toByteArray(Charsets.UTF_8))
    }

    fun decode(value: String?, now: Long = System.currentTimeMillis()): PairingDecodeResult {
        if (value.isNullOrBlank()) return PairingDecodeResult.Error("QR Code vazio.")
        val trimmed = value.trim()
        if (!trimmed.startsWith(PREFIX)) {
            return PairingDecodeResult.Error("Este QR Code não pertence ao Space Clipboard.")
        }
        val payload = runCatching {
            val data = Base64.getUrlDecoder().decode(trimmed.removePrefix(PREFIX))
            json.decodeFromString(PairingQrPayload.serializer(), data.toString(Charsets.UTF_8))
        }.getOrNull() ?: return PairingDecodeResult.Error("QR Code inválido ou corrompido.")

        if (payload.v != Protocol.VERSION) {
            return PairingDecodeResult.Error("Versão de protocolo incompatível.")
        }
        if (payload.id.isBlank() || runCatching { UUID.fromString(payload.id) }.isFailure) {
            return PairingDecodeResult.Error("QR Code sem identificação válida do dispositivo.")
        }
        if (payload.host.isBlank() || payload.host.length > 253 || payload.port !in 1..65535) {
            return PairingDecodeResult.Error("Endereço de rede inválido no QR Code.")
        }
        if (payload.token.length < 16) {
            return PairingDecodeResult.Error("Credenciais de pareamento inválidas.")
        }
        if (payload.isExpired(now)) {
            return PairingDecodeResult.Error("Este QR Code expirou. Gere um novo no receptor.")
        }
        return PairingDecodeResult.Success(payload)
    }
}
