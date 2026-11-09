package com.spaceclipboard.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "paired_devices",
    indices = [Index(value = ["inboundTokenHash"], unique = true)],
)
data class DeviceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val protocol: Int,
    /** Token que usamos para chamar o outro dispositivo, cifrado pelo Android Keystore. */
    val outboundTokenEncrypted: String,
    /** SHA-256 do token que o outro dispositivo usa para nos chamar. */
    val inboundTokenHash: String,
    val lastSeen: Long?,
    val createdAt: Long,
)
