package com.toimg.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.toimg.app.data.local.entity.MoveHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MoveHistoryDao {

    @Query("SELECT * FROM move_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<MoveHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(items: List<MoveHistoryEntity>): List<Long>

    @Query("SELECT * FROM move_history WHERE id = :id LIMIT 1")
    suspend fun getHistoryById(id: Long): MoveHistoryEntity?

    @Query("UPDATE move_history SET status = :status WHERE id = :id")
    suspend fun updateHistoryStatus(id: Long, status: String): Int

    @Query("DELETE FROM move_history")
    suspend fun clearHistory(): Int
}

