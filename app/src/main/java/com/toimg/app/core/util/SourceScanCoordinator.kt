package com.toimg.app.core.util

import com.toimg.app.data.local.dao.ImageSourceDao
import com.toimg.app.data.local.entity.SourceScanStatEntity

/** F2 扫描编排契约：一个会话内每个来源独立建账，具体文件枚举仍由现有 ScanWorker 承担。 */
class SourceScanCoordinator(private val sourceDao: ImageSourceDao) {
    suspend fun initializeSession(sessionId: String, sourceIds: List<String>, startedAt: Long) {
        sourceIds.distinct().forEach { sourceId ->
            sourceDao.upsertScanStat(SourceScanStatEntity(
                scanSessionId = sessionId,
                sourceId = sourceId,
                status = "QUEUED",
                startedAt = startedAt
            ))
        }
    }

    suspend fun update(stat: SourceScanStatEntity) = sourceDao.upsertScanStat(stat)
}
