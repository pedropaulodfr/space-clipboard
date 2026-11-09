package com.spaceclipboard.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY receivedAt DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history ORDER BY receivedAt DESC, id DESC LIMIT :limit")
    suspend fun recent(limit: Int = 20): List<HistoryEntity>

    @Query("SELECT * FROM history WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): HistoryEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM history WHERE id = :id)")
    suspend fun exists(id: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: HistoryEntity): Long

    @Query(
        "DELETE FROM history WHERE id NOT IN (" +
            "SELECT id FROM history ORDER BY receivedAt DESC, id DESC LIMIT :limit" +
            ")",
    )
    suspend fun trimTo(limit: Int)

    @Transaction
    suspend fun insertAndTrim(entity: HistoryEntity, limit: Int = 20): Boolean {
        val rowId = insert(entity)
        trimTo(limit)
        return rowId != -1L
    }

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("DELETE FROM history")
    suspend fun clear()
}
