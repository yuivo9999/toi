package com.toimg.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.toimg.app.data.local.entity.ScanSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: ScanSessionEntity)

    @Update
    suspend fun update(session: ScanSessionEntity)

    @Query("SELECT * FROM scan_sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ScanSessionEntity?

    @Query("SELECT * FROM scan_sessions WHERE status IN ('QUEUED','RUNNING','PAUSED') ORDER BY updatedAt DESC LIMIT 1")
    fun observeActive(): Flow<ScanSessionEntity?>

    @Query("SELECT * FROM scan_sessions ORDER BY updatedAt DESC LIMIT 1")
    fun observeLatest(): Flow<ScanSessionEntity?>

    /** 完整扫描会话历史；与 TaskRecord 摘要并存，保留来源 ID/名称快照。 */
    @Query("SELECT * FROM scan_sessions ORDER BY startedAt DESC LIMIT 200")
    fun observeHistory(): Flow<List<ScanSessionEntity>>

    @Query("UPDATE scan_sessions SET status = :status, updatedAt = :updatedAt, finishedAt = :finishedAt, statusText = :statusText WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long, finishedAt: Long?, statusText: String)

    /** Photo Picker 导入没有 WorkManager；进程死亡后由来源管理页清理超过阈值的孤立活动会话。 */
    @Query("""UPDATE scan_sessions SET status = 'FAILED', updatedAt = :now, finishedAt = :now,
        statusText = '图片选择器导入中断', errorMessage = '应用进程中断，未完成的选取登记已结束'
        WHERE status IN ('QUEUED','RUNNING','PAUSED') AND startedAt < :cutoff
        AND id IN (SELECT scanSessionId FROM source_scan_stats WHERE sourceId = 'system:photo-picker-selections')""")
    suspend fun failStalePickerSessions(cutoff: Long, now: Long): Int
}
