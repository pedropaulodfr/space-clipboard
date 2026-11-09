package com.spaceclipboard.app.pairing

import com.spaceclipboard.app.data.DeviceRepository
import com.spaceclipboard.app.data.SettingsRepository
import com.spaceclipboard.app.domain.model.PairedDevice
import com.spaceclipboard.app.domain.model.PeerIdentity
import com.spaceclipboard.app.network.ApiException
import com.spaceclipboard.app.network.PairRequest
import com.spaceclipboard.app.network.Protocol
import com.spaceclipboard.app.network.client.ClientConnectionException
import com.spaceclipboard.app.network.client.ClipboardClient
import com.spaceclipboard.app.security.Tokens

sealed interface PairingResult {
    data class Success(val device: PairedDevice) : PairingResult
    data class Failure(val message: String) : PairingResult
}

class PairingManager(
    private val settings: SettingsRepository,
    private val devices: DeviceRepository,
    private val client: ClipboardClient,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun pairFromQr(payload: PairingQrPayload): PairingResult {
        if (payload.isExpired(clock())) {
            return PairingResult.Failure("O QR Code expirou. Atualize o código no receptor.")
        }
        if (!NetworkAddressValidator.isLocalHost(payload.host)) {
            return PairingResult.Failure("O endereço do QR Code não pertence a uma rede local.")
        }
        return pair(
            host = payload.host.removePrefix("[").removeSuffix("]"),
            port = payload.port,
            token = payload.token,
            code = null,
            expectedReceiverId = payload.id,
        )
    }

    suspend fun pairManually(host: String, port: Int, code: String): PairingResult {
        val cleanHost = host.trim().removePrefix("[").removeSuffix("]")
        if (!NetworkAddressValidator.isLocalHost(cleanHost)) {
            return PairingResult.Failure("Use um endereço IP da rede local.")
        }
        if (port !in 1..65535) return PairingResult.Failure("Porta inválida.")
        if (code.length != 6 || code.any { !it.isDigit() }) {
            return PairingResult.Failure("O código deve ter 6 dígitos.")
        }
        return pair(cleanHost, port, token = null, code = code, expectedReceiverId = null)
    }

    private suspend fun pair(
        host: String,
        port: Int,
        token: String?,
        code: String?,
        expectedReceiverId: String?,
    ): PairingResult {
        return try {
            val inspection = client.inspectPair(host, port, token = token, code = code)
            if (!inspection.valid) {
                return PairingResult.Failure("O código de pareamento não é válido ou expirou.")
            }
            if (expectedReceiverId != null && inspection.receiver.id != expectedReceiverId) {
                return PairingResult.Failure("O QR Code não corresponde ao dispositivo esperado.")
            }
            val local = settings.current()
            val senderToken = Tokens.newToken(32)
            val response = client.pair(
                host,
                port,
                PairRequest(
                    pairToken = token,
                    pairCode = code,
                    senderId = local.deviceId,
                    senderName = local.deviceName,
                    senderPort = local.serverPort,
                    senderToken = senderToken,
                ),
            )
            if (response.receiver.id != inspection.receiver.id) {
                return PairingResult.Failure("O receptor mudou durante o pareamento. Tente novamente.")
            }
            val peer = PeerIdentity(
                id = response.receiver.id,
                name = response.receiver.name,
                host = host,
                port = response.receiver.port,
            )
            val saved = devices.savePairing(
                peer = peer,
                protocol = Protocol.VERSION,
                outboundToken = response.credential,
                inboundToken = senderToken,
                now = clock(),
            )
            PairingResult.Success(saved)
        } catch (error: ApiException) {
            PairingResult.Failure(
                when (error.statusCode) {
                    401, 410 -> "O código de pareamento expirou ou já foi usado."
                    409 -> error.message
                    else -> error.message
                },
            )
        } catch (error: ClientConnectionException) {
            PairingResult.Failure(error.message)
        } catch (error: Exception) {
            PairingResult.Failure("Não foi possível concluir o pareamento.")
        }
    }
}
