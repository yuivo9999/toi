package com.toimg.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.toimg.app.data.local.entity.ImageSourceEntity
import com.toimg.app.data.local.entity.ImageSourceLinkEntity
import com.toimg.app.data.local.entity.ImageSourceSummary
import com.toimg.app.data.local.entity.SourceScanStatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageSourceDao {
    @Query("""SELECT s.*,
        (SELECT COUNT(*) FROM image_source_links l WHERE l.sourceId = s.id) AS linkedImageCount,
        CASE
            WHEN s.id = 'system:photo-picker-selections' THEN NULL
            WHEN s.id = 'system:mediastore:external-images' THEN
                (SELECT COUNT(*) FROM images i WHERE i.uriString LIKE 'content://media/%')
            ELSE
                (SELECT COUNT(*) FROM images i
                 WHERE substr(i.uriString, 1, length(s.treeUriString || '/document/')) = s.treeUriString || '/document/')
        END AS currentLocationCount
        FROM image_sources s ORDER BY s.addedAt DESC""")
    fun observeAll(): Flow<List<ImageSourceSummary>>

    @Query("SELECT * FROM image_sources WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ImageSourceEntity?

    @Query("SELECT * FROM image_sources WHERE uriString = :uri LIMIT 1")
    suspend fun getByUri(uri: String): ImageSourceEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(source: ImageSourceEntity): Long

    @Update
    suspend fun update(source: ImageSourceEntity)

    @Query("UPDATE image_sources SET isAuthorized = 0, lifecycleStatus = 'AUTH_EXPIRED', errorMessage = :message WHERE id = :sourceId")
    suspend fun markAuthorizationExpired(sourceId: String, message: String): Int

    @Query("DELETE FROM image_sources WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addImageSourceLinks(links: List<ImageSourceLinkEntity>): List<Long>

    @Query("UPDATE source_scan_stats SET discoveredCount = discoveredCount + :discovered, addedCount = addedCount + :added, updatedCount = updatedCount + :updated, existingCount = existingCount + :existing WHERE scanSessionId = :sessionId AND sourceId = :sourceId")
    suspend fun recordScanBatch(sessionId: String, sourceId: String, discovered: Int, added: Int, updated: Int, existing: Int)

    @Query("UPDATE image_source_links SET lastSeenAt = :observedAt WHERE imageId = :imageId AND sourceId = :sourceId")
    suspend fun touchImageSourceLink(imageId: String, sourceId: String, observedAt: Long)

    @Query("SELECT imageId FROM image_source_links WHERE sourceId = :sourceId AND imageId IN (:imageIds)")
    suspend fun linkedImageIds(sourceId: String, imageIds: List<String>): List<String>

    @Query("SELECT * FROM image_source_links WHERE sourceId = :sourceId")
    suspend fun linksForSource(sourceId: String): List<ImageSourceLinkEntity>

    @Query("SELECT COUNT(*) FROM image_source_links WHERE sourceId = :sourceId")
    fun observeLinkedImageCount(sourceId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM image_source_links WHERE sourceId = :sourceId")
    suspend fun countLinksForSource(sourceId: String): Int

    @Query("SELECT DISTINCT imageId FROM image_source_links WHERE sourceId IN (:sourceIds)")
    suspend fun imageIdsForSources(sourceIds: List<String>): List<String>

    @Query("SELECT COUNT(*) FROM image_source_links l INNER JOIN images i ON i.id = l.imageId WHERE l.sourceId = :sourceId AND i.status = 'UNAVAILABLE'")
    suspend fun countUnavailableLinksForSource(sourceId: String): Int

    @Query("UPDATE image_sources SET imageCount = :count, lastScannedAt = :scannedAt, lastScanSessionId = :sessionId, lifecycleStatus = :status, errorMessage = :error WHERE id = :sourceId")
    suspend fun finalizeSourceScan(sourceId: String, count: Int, scannedAt: Long, sessionId: String, status: String, error: String?)

    @Query("SELECT COUNT(*) FROM image_source_links WHERE imageId = :imageId")
    suspend fun sourceReferenceCount(imageId: String): Int

    @Query("SELECT COUNT(*) FROM image_source_links l LEFT JOIN images i ON i.id = l.imageId LEFT JOIN image_sources s ON s.id = l.sourceId WHERE i.id IS NULL OR s.id IS NULL")
    suspend fun countOrphanSourceLinks(): Int

    @Query("DELETE FROM image_source_links WHERE sourceId = :sourceId")
    suspend fun deleteLinksForSource(sourceId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertScanStat(stat: SourceScanStatEntity)

    @Query("SELECT * FROM source_scan_stats WHERE sourceId = :sourceId ORDER BY startedAt DESC")
    fun observeScanStats(sourceId: String): Flow<List<SourceScanStatEntity>>

    @Query("SELECT * FROM source_scan_stats WHERE scanSessionId = :sessionId ORDER BY startedAt, sourceId")
    fun observeScanStatsForSession(sessionId: String): Flow<List<SourceScanStatEntity>>

    @Query("SELECT * FROM source_scan_stats WHERE scanSessionId = :sessionId ORDER BY sourceId")
    suspend fun scanStatsForSession(sessionId: String): List<SourceScanStatEntity>

    @Query("UPDATE source_scan_stats SET skippedCount = :count WHERE scanSessionId = :sessionId AND sourceId = :sourceId")
    suspend fun setSourceSkippedCount(sessionId: String, sourceId: String, count: Int)

    @Query("""SELECT COUNT(*) FROM source_scan_stats st
        INNER JOIN scan_sessions ss ON ss.id = st.scanSessionId
        WHERE st.sourceId = :sourceId
          AND (st.status IN ('QUEUED', 'RUNNING', 'PAUSED') OR ss.status IN ('QUEUED', 'RUNNING', 'PAUSED'))""")
    suspend fun activeScanCountForSource(sourceId: String): Int

    /** 扫描批次写入前的来源栅栏：来源已移出/不存在时禁止重新建立关联。 */
    @Query("SELECT COUNT(*) FROM image_sources WHERE id = :sourceId AND lifecycleStatus != 'REMOVING'")
    suspend fun writableSourceCount(sourceId: String): Int

    @Query("UPDATE source_scan_stats SET status = 'CANCELLED', finishedAt = :finishedAt, errorMessage = :message WHERE scanSessionId = :sessionId AND status IN ('QUEUED', 'RUNNING', 'PAUSED')")
    suspend fun cancelActiveStatsForSession(sessionId: String, finishedAt: Long, message: String): Int

    @Query("UPDATE source_scan_stats SET status = 'QUEUED', finishedAt = NULL, errorMessage = :message WHERE scanSessionId = :sessionId AND status IN ('RUNNING', 'PAUSED')")
    suspend fun requeueInterruptedStatsForSession(sessionId: String, message: String): Int

    @Query("""UPDATE source_scan_stats SET status = 'FAILED', finishedAt = :now,
        errorMessage = '图片选择器导入中断'
        WHERE sourceId = 'system:photo-picker-selections' AND status IN ('QUEUED','RUNNING','PAUSED')
        AND scanSessionId IN (SELECT id FROM scan_sessions WHERE startedAt < :cutoff AND status = 'FAILED')""")
    suspend fun failStalePickerStats(cutoff: Long, now: Long): Int

    @Query("UPDATE image_sources SET lifecycleStatus = 'PARTIAL', errorMessage = '上一次图片选择器登记被进程中断' WHERE id = 'system:photo-picker-selections' AND EXISTS (SELECT 1 FROM source_scan_stats WHERE sourceId = 'system:photo-picker-selections' AND status = 'FAILED' AND errorMessage = '图片选择器导入中断')")
    suspend fun markPickerSourceInterrupted(): Int

    @Query("UPDATE image_sources SET lifecycleStatus = 'PARTIAL', errorMessage = :message WHERE id IN (SELECT sourceId FROM source_scan_stats WHERE scanSessionId = :sessionId)")
    suspend fun markSessionSourcesPartial(sessionId: String, message: String): Int
}
