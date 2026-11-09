package com.spaceclipboard.app.sender

import com.spaceclipboard.app.data.DeviceRepository
import com.spaceclipboard.app.data.SettingsRepository
import com.spaceclipboard.app.domain.ClipboardValidator
import com.spaceclipboard.app.domain.TextValidation
import com.spaceclipboard.app.domain.model.ClipboardText
import com.spaceclipboard.app.domain.model.ContentType
import com.spaceclipboard.app.domain.model.PairedDevice
import com.spaceclipboard.app.domain.model.SendOutcome
import com.spaceclipboard.app.network.ApiException
import com.spaceclipboard.app.network.ClipboardPayload
import com.spaceclipboard.app.network.client.ClientConnectionException
import com.spaceclipboard.app.network.client.ClipboardClient
import com.spaceclipboard.app.network.discovery.DiscoveryController
import kotlinx.coroutines.flow.first
import java.util.UUID

sealed interface ConnectionTest {
    data class Online(val device: PairedDevice, val relocated: Boolean) : ConnectionTest
    data class Failure(val message: String, val needsRepair: Boolean = false) : ConnectionTest
}

class DeviceService(
    private val client: ClipboardClient,
    private val devices: DeviceRepository,
    private val settings: SettingsRepository,
    private val discovery: DiscoveryController,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun test(device: PairedDevice): ConnectionTest {
        return try {
            var relocated = false
            val working = withRediscovery(device) { candidate ->
                val token = devices.outboundToken(candidate.id)
                    ?: throw ApiException(401, "missing_credential", "Credencial local ausente. Pareie novamente.")
                val status = client.status(candidate.host, candidate.port)
                if (status.deviceId != candidate.id) {
                    throw ApiException(
                        409,
                        "wrong_device",
                        "O endereço responde, mas é outro dispositivo.",
                    )
                }
                client.checkAuth(candidate.host, candidate.port, token)
                relocated = candidate.host != device.host || candidate.port != device.port
                candidate
            }
            val now = clock()
            devices.touch(working.id, now)
            val updated = devices.find(working.id) ?: working
            ConnectionTest.Online(updated, relocated)
        } catch (error: ApiException) {
            ConnectionTest.Failure(
                message = error.message,
                needsRepair = error.statusCode == 401 || error.statusCode == 403,
            )
        } catch (error: ClientConnectionException) {
            ConnectionTest.Failure(error.message)
        } catch (error: Exception) {
            ConnectionTest.Failure("Não foi possível testar a conexão.")
        }
    }

    suspend fun send(device: PairedDevice, text: String, type: ContentType? = null): SendOutcome {
        val validation = ClipboardValidator.validate(text)
        if (validation is TextValidation.Invalid) return SendOutcome.Failure(validation.message, false)
        val valid = validation as TextValidation.Valid
        val actualType = type ?: valid.type
        val settingsNow = settings.settings.first()
        val payload = ClipboardPayload(
            id = UUID.randomUUID().toString(),
            text = valid.text,
            type = actualType.wireValue(),
            sender = settingsNow.deviceName,
            timestamp = clock(),
        )
        return try {
            var working = device
            val token = devices.outboundToken(device.id)
                ?: return SendOutcome.Failure("Credencial local ausente. Pareie novamente.", false)
            try {
                val response = client.send(working.host, working.port, token, payload)
                devices.touch(working.id, clock())
                SendOutcome.Success(working.name, response.duplicate)
            } catch (error: ClientConnectionException) {
                val peer = discovery.findPeer(device.id, 5_000L)
                    ?: return SendOutcome.Failure(
                        "O dispositivo está indisponível. Verifique se o app está aberto no receptor.",
                    )
                devices.updateEndpoint(working, peer.host, peer.port, clock())
                working = devices.find(working.id) ?: working
                val retryToken = devices.outboundToken(working.id)
                    ?: return SendOutcome.Failure("Credencial local ausente. Pareie novamente.", false)
                val response = client.send(working.host, working.port, retryToken, payload)
                devices.touch(working.id, clock())
                SendOutcome.Success(working.name, response.duplicate)
            }
        } catch (error: ApiException) {
            when (error.statusCode) {
                401, 403 -> SendOutcome.Failure("Pareamento inválido. Refaga o pareamento.", false)
                409 -> SendOutcome.Failure("O receptor recusou o conteúdo: ${error.message}", false)
                else -> SendOutcome.Failure(error.message)
            }
        } catch (error: ClientConnectionException) {
            SendOutcome.Failure(error.message)
        } catch (error: Exception) {
            SendOutcome.Failure("Não foi possível enviar o conteúdo.")
        }
    }

    suspend fun rename(device: PairedDevice, newName: String) = devices.rename(device.id, newName)

    suspend fun remove(device: PairedDevice) = devices.remove(device.id)

    suspend fun updateFromDiscovery(device: PairedDevice, peerHost: String, peerPort: Int): PairedDevice {
        devices.updateEndpoint(device, peerHost, peerPort, clock())
        return devices.find(device.id) ?: device
    }

    private suspend fun <T> withRediscovery(
        device: PairedDevice,
        block: suspend (PairedDevice) -> T,
    ): T {
        return try {
            block(device)
        } catch (error: ClientConnectionException) {
            val peer = discovery.findPeer(device.id, 5_000L) ?: throw error
            devices.updateEndpoint(device, peer.host, peer.port, clock())
            val updated = devices.find(device.id) ?: device
            block(updated)
        }
    }
}
