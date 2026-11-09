package com.spaceclipboard.app.data

import com.spaceclipboard.app.data.db.DeviceDao
import com.spaceclipboard.app.data.db.DeviceEntity
import com.spaceclipboard.app.domain.model.PairedDevice
import com.spaceclipboard.app.domain.model.PeerIdentity
import com.spaceclipboard.app.security.TokenCipher
import com.spaceclipboard.app.security.Tokens
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DeviceRepository(
    private val dao: DeviceDao,
    private val cipher: TokenCipher,
) {
    val devices: Flow<List<PairedDevice>> = dao.observeAll().map { rows ->
        rows.map { it.toDomain() }
    }

    suspend fun all(): List<PairedDevice> = dao.getAll().map { it.toDomain() }

    suspend fun find(id: String): PairedDevice? = dao.findById(id)?.toDomain()

    /**
     * Salva o resultado do pareamento dos dois lados:
     * - outboundToken: token que este dispositivo usará para chamar o peer.
     * - inboundToken: token que o peer usará para chamar este dispositivo.
     * O token de entrada é persistido somente como hash.
     */
    suspend fun savePairing(
        peer: PeerIdentity,
        protocol: Int,
        outboundToken: String,
        inboundToken: String,
        now: Long,
    ): PairedDevice {
        require(outboundToken.isNotBlank())
        require(inboundToken.isNotBlank())
        val existing = dao.findById(peer.id)
        val entity = DeviceEntity(
            id = peer.id,
            name = peer.name,
            host = peer.host,
            port = peer.port,
            protocol = protocol,
            outboundTokenEncrypted = cipher.encrypt(outboundToken),
            inboundTokenHash = Tokens.sha256(inboundToken),
            lastSeen = now,
            createdAt = existing?.createdAt ?: now,
        )
        dao.upsert(entity)
        return entity.toDomain()
    }

    suspend fun outboundToken(deviceId: String): String? {
        val encrypted = dao.findById(deviceId)?.outboundTokenEncrypted ?: return null
        return cipher.decrypt(encrypted)
    }

    suspend fun authenticate(tokenHash: String): PeerIdentity? {
        val device = dao.findByInboundHash(tokenHash) ?: return null
        return PeerIdentity(device.id, device.name, device.host, device.port)
    }

    suspend fun updateEndpoint(device: PairedDevice, host: String, port: Int, now: Long) {
        dao.updateEndpoint(device.id, host, port, now)
    }

    suspend fun touch(id: String, at: Long) = dao.touch(id, at)

    suspend fun rename(id: String, newName: String) {
        val sanitized = newName.trim().replace(Regex("\\s+"), " ").take(48)
        require(sanitized.isNotBlank()) { "O nome não pode ficar vazio." }
        dao.rename(id, sanitized)
    }

    suspend fun remove(id: String) = dao.delete(id)

    private fun DeviceEntity.toDomain() = PairedDevice(
        id = id,
        name = name,
        host = host,
        port = port,
        protocol = protocol,
        lastSeen = lastSeen,
        createdAt = createdAt,
    )
}
