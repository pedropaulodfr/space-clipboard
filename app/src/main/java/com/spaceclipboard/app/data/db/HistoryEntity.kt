package com.spaceclipboard.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "history",
    indices = [Index("receivedAt")],
)
data class HistoryEntity(
    @PrimaryKey val id: String,
    val text: String,
    val type: String,
    val senderName: String,
    val senderId: String,
    val receivedAt: Long,
)
