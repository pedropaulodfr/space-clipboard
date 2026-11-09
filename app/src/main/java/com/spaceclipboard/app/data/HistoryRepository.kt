package com.spaceclipboard.app.data

import com.spaceclipboard.app.data.db.HistoryDao
import com.spaceclipboard.app.data.db.HistoryEntity
import com.spaceclipboard.app.domain.model.ContentType
import com.spaceclipboard.app.domain.model.HistoryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HistoryRepository(private val dao: HistoryDao) {
    val recent: Flow<List<HistoryItem>> = dao.observeRecent(LIMIT).map { rows ->
        rows.map { it.toDomain() }
    }

    suspend fun latest(): HistoryItem? = dao.recent(1).firstOrNull()?.toDomain()

    suspend fun insert(item: HistoryItem): Boolean = dao.insertAndTrim(item.toEntity(), LIMIT)

    suspend fun find(id: String): HistoryItem? = dao.findById(id)?.toDomain()

    suspend fun delete(id: String): Boolean = dao.deleteById(id) > 0

    suspend fun clear() = dao.clear()

    private fun HistoryEntity.toDomain() = HistoryItem(
        id = id,
        text = text,
        type = ContentType.fromWire(type) ?: ContentType.TEXT,
        senderName = senderName,
        senderId = senderId,
        receivedAt = receivedAt,
    )

    private fun HistoryItem.toEntity() = HistoryEntity(
        id = id,
        text = text,
        type = type.wireValue(),
        senderName = senderName,
        senderId = senderId,
        receivedAt = receivedAt,
    )

    companion object {
        const val LIMIT = 20
    }
}
