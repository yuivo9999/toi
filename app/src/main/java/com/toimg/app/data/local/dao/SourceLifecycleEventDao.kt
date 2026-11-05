package com.toimg.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.toimg.app.data.local.entity.SourceLifecycleEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceLifecycleEventDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: SourceLifecycleEventEntity): Long

    @Query("SELECT * FROM source_lifecycle_events ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<SourceLifecycleEventEntity>>

    @Query("SELECT * FROM source_lifecycle_events WHERE sourceId = :sourceId ORDER BY createdAt DESC")
    fun observeForSource(sourceId: String): Flow<List<SourceLifecycleEventEntity>>

    @Query("SELECT COUNT(*) FROM source_lifecycle_events")
    suspend fun countAll(): Int
}
