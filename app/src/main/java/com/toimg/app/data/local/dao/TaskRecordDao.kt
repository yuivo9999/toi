package com.toimg.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.toimg.app.data.local.entity.TaskRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskRecordDao {

    @Insert
    suspend fun insert(item: TaskRecordEntity): Long

    @Query("SELECT * FROM task_records WHERE type = :type ORDER BY finishedAt DESC LIMIT 200")
    fun observeByType(type: String): Flow<List<TaskRecordEntity>>

    @Query("DELETE FROM task_records")
    suspend fun clearAll(): Int
}
