package com.spaceclipboard.app.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM paired_devices ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM paired_devices ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(): List<DeviceEntity>

    @Query("SELECT * FROM paired_devices WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): DeviceEntity?

    @Query("SELECT * FROM paired_devices WHERE inboundTokenHash = :hash LIMIT 1")
    suspend fun findByInboundHash(hash: String): DeviceEntity?

    @Upsert
    suspend fun upsert(entity: DeviceEntity)

    @Query("UPDATE paired_devices SET lastSeen = :at WHERE id = :id")
    suspend fun touch(id: String, at: Long)

    @Query("UPDATE paired_devices SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("UPDATE paired_devices SET host = :host, port = :port, lastSeen = :at WHERE id = :id")
    suspend fun updateEndpoint(id: String, host: String, port: Int, at: Long)

    @Query("DELETE FROM paired_devices WHERE id = :id")
    suspend fun delete(id: String)
}
