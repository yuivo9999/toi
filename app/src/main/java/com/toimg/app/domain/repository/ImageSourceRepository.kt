package com.toimg.app.domain.repository

import com.toimg.app.data.local.entity.ImageSourceEntity
import com.toimg.app.data.local.entity.ImageSourceSummary
import com.toimg.app.data.local.entity.SourceScanStatEntity
import com.toimg.app.data.local.entity.SourceLifecycleEventEntity
import kotlinx.coroutines.flow.Flow

interface ImageSourceRepository {
    fun observeSources(): Flow<List<ImageSourceSummary>>
    suspend fun getSource(sourceId: String): ImageSourceEntity?
    suspend fun registerSource(source: ImageSourceEntity)
    suspend fun updateSource(source: ImageSourceEntity)
    fun observeScanStats(sourceId: String): Flow<List<SourceScanStatEntity>>
    fun observeScanStatsForSession(sessionId: String): Flow<List<SourceScanStatEntity>>
    fun observeRecentLifecycleEvents(limit: Int = 30): Flow<List<SourceLifecycleEventEntity>>
    suspend fun reauthorizeSource(sourceId: String, newTreeUri: String, displayName: String): Boolean
    /** A：移出项目会删除该来源独占的索引和识别数据，但绝不删除设备原图。 */
    suspend fun removeSourceFromProject(sourceId: String): RemoveSourceResult
}

data class RemoveSourceResult(val removedImageCount: Int, val retainedSharedImageCount: Int, val blockedByActiveScan: Boolean = false)
