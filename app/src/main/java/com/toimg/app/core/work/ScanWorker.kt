package com.toimg.app.core.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.toimg.app.MainActivity
import com.toimg.app.R
import com.toimg.app.core.di.AppContainer
import com.toimg.app.core.util.ScanManager
import com.toimg.app.core.util.ScanMode
import com.toimg.app.core.util.ScanCheckpointStore
import com.toimg.app.data.local.entity.ScanSessionEntity
import com.toimg.app.data.local.entity.ImageSourceEntity
import com.toimg.app.data.local.entity.SourceScanStatEntity
import androidx.documentfile.provider.DocumentFile
import com.toimg.app.navigation.AppRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/** WorkManager 持久化扫描执行器。进程被系统回收后，WorkManager 可重新调度该任务。 */
class ScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = coroutineScope {
        val sessionId = inputData.getString(KEY_SESSION_ID) ?: return@coroutineScope Result.failure()
        val includeMediaStore = inputData.getBoolean(KEY_MEDIA_STORE, false)
        val folderUris = inputData.getStringArray(KEY_FOLDER_URIS).orEmpty()
        val includeSubfolders = inputData.getBoolean(KEY_INCLUDE_SUBFOLDERS, false)
        val requestedScanMode = ScanMode.fromPersisted(inputData.getString(KEY_SCAN_MODE))
        val db = AppContainer.database(applicationContext)
        val dao = db.scanSessionDao()
        val startedAt = System.currentTimeMillis()
        var mediaStoreSourceId: String? = null
        var sessionSnapshot: ScanSessionEntity? = null

        try {
            setForeground(createForegroundInfo("正在准备扫描…"))
            val session = dao.getById(sessionId) ?: ScanSessionEntity(
                id = sessionId,
                status = ScanSessionEntity.RUNNING,
                includeMediaStore = includeMediaStore,
                folderUris = folderUris.joinToString("\n"),
                includeSubfolders = includeSubfolders,
                startedAt = startedAt,
                updatedAt = startedAt,
                statusText = "正在启动扫描",
                scanMode = requestedScanMode.persistedValue
            )
            sessionSnapshot = session
            var currentSession = session
            if (currentSession.status in listOf(
                    ScanSessionEntity.SUCCESS,
                    ScanSessionEntity.PARTIAL,
                    ScanSessionEntity.FAILED,
                    ScanSessionEntity.CANCELLED
                )) {
                ScanCheckpointStore.clearSession(applicationContext, sessionId)
                ScanTaskScheduler.markFinished(applicationContext, sessionId)
                return@coroutineScope Result.success()
            }
            while (currentSession.status == ScanSessionEntity.PAUSED) {
                delay(1_000)
                currentSession = dao.getById(sessionId) ?: currentSession
                if (currentSession.status == ScanSessionEntity.CANCELLED) {
                    ScanTaskScheduler.markFinished(applicationContext, sessionId)
                    return@coroutineScope Result.success()
                }
            }
            if (currentSession.status == ScanSessionEntity.CANCELLED) {
                ScanTaskScheduler.markFinished(applicationContext, sessionId)
                return@coroutineScope Result.success()
            }
            val sourceDao = db.imageSourceDao()
            val priorStatsBySource = sourceDao.scanStatsForSession(sessionId).associateBy { it.sourceId }
            // Resume progress from durable counters instead of resetting the visible count to zero.
            val restoredDiscoveredCount = priorStatsBySource.values.sumOf { it.discoveredCount }
            val restoredSkippedCount = priorStatsBySource.values.sumOf { it.skippedCount }
            val restoredSourceSkippedCounts = priorStatsBySource.mapValues { it.value.skippedCount }
            val sourceIdsByUri = linkedMapOf<String, String>()
            val sourceNamesById = linkedMapOf<String, String>()
            if (includeMediaStore) {
                val id = ImageSourceEntity.MEDIASTORE_SOURCE_ID
                val existing = sourceDao.getById(id)
                val mediaSource = ImageSourceEntity(
                    id = id, displayName = "系统媒体库", uriString = ImageSourceEntity.MEDIASTORE_URI,
                    treeUriString = ImageSourceEntity.MEDIASTORE_URI, isAuthorized = true,
                    includeSubfolders = true, lifecycleStatus = ImageSourceEntity.SCANNING,
                    addedAt = existing?.addedAt ?: startedAt
                )
                if (existing == null) sourceDao.insert(mediaSource) else sourceDao.update(mediaSource)
                mediaStoreSourceId = id
                sourceNamesById[id] = mediaSource.displayName
                val previousStat = priorStatsBySource[id]
                sourceDao.upsertScanStat(previousStat?.copy(
                    status = "RUNNING", startedAt = previousStat.startedAt ?: startedAt,
                    finishedAt = null, errorMessage = null
                ) ?: SourceScanStatEntity(
                    scanSessionId = sessionId, sourceId = id, status = "RUNNING", startedAt = startedAt
                ))
            }
            folderUris.forEach { rawUri ->
                val uri = android.net.Uri.parse(rawUri)
                val existing = sourceDao.getByUri(rawUri) ?: sourceDao.getById(rawUri)
                val sourceId = existing?.id ?: rawUri
                val name = runCatching { DocumentFile.fromTreeUri(applicationContext, uri)?.name }.getOrNull() ?: existing?.displayName ?: "SAF 目录"
                if (existing == null) {
                    sourceDao.insert(ImageSourceEntity(
                        id = sourceId,
                        displayName = name,
                        uriString = rawUri,
                        treeUriString = rawUri,
                        isAuthorized = true,
                        includeSubfolders = includeSubfolders,
                        lifecycleStatus = ImageSourceEntity.SCANNING
                    ))
                } else {
                    sourceDao.update(existing.copy(
                        displayName = name,
                        uriString = rawUri,
                        treeUriString = rawUri,
                        isAuthorized = true,
                        includeSubfolders = includeSubfolders,
                        lifecycleStatus = ImageSourceEntity.SCANNING,
                        errorMessage = null
                    ))
                }
                sourceIdsByUri[rawUri] = sourceId
                sourceNamesById[sourceId] = name.replace("\n", " ").replace("\r", " ")
                // 旧版本只保存了图片索引和文件夹 URI，没有 image_source_links。
                // 通过 SAF 子文档 URI 前缀进行一次性回填，避免旧 DCIM 6000 张图片在来源管理中显示为 0。
                val currentLinkCount = sourceDao.countLinksForSource(sourceId)
                if (currentLinkCount < (existing?.imageCount ?: 0) || currentLinkCount == 0) {
                    val legacyImages = db.imageDao().getImagesByUriPrefix(rawUri.trimEnd('/') + "/document/")
                    val now = System.currentTimeMillis()
                    legacyImages.chunked(300).forEach { batch ->
                        sourceDao.addImageSourceLinks(batch.map { image ->
                            com.toimg.app.data.local.entity.ImageSourceLinkEntity(image.id, sourceId, now, now)
                        })
                    }
                }
                val previousStat = priorStatsBySource[sourceId]
                sourceDao.upsertScanStat(previousStat?.copy(
                    status = "RUNNING", startedAt = previousStat.startedAt ?: startedAt,
                    finishedAt = null, errorMessage = null
                ) ?: SourceScanStatEntity(
                    scanSessionId = sessionId,
                    sourceId = sourceId,
                    status = "RUNNING",
                    startedAt = startedAt
                ))
            }
            dao.upsert(currentSession.copy(
                status = ScanSessionEntity.RUNNING,
                updatedAt = startedAt,
                selectedSourceIds = (sourceIdsByUri.values + listOfNotNull(mediaStoreSourceId)).joinToString("\n"),
                selectedSourceNames = (sourceIdsByUri.values + listOfNotNull(mediaStoreSourceId)).distinct().mapNotNull { sourceNamesById[it] }.joinToString("\n"),
                statusText = "正在扫描；若进程被系统回收，将由任务调度器尝试恢复"
            ))

            ScanManager.start(
                context = applicationContext,
                includeMediaStore = includeMediaStore,
                folderUris = folderUris.map(android.net.Uri::parse),
                includeSubfolders = includeSubfolders,
                sourceIdsByUri = sourceIdsByUri,
                scanSessionId = sessionId,
                mediaStoreSourceId = mediaStoreSourceId,
                initialDiscoveredCount = restoredDiscoveredCount,
                initialSkippedCount = restoredSkippedCount,
                initialSourceSkippedCounts = restoredSourceSkippedCounts,
                scanMode = ScanMode.fromPersisted(currentSession.scanMode.ifBlank { requestedScanMode.persistedValue })
            )
            val progressJob = launch {
                ScanManager.progress.collect { progress ->
                    if (!progress.hasStarted) return@collect
                    val old = dao.getById(sessionId) ?: session
                    val persistedStatusText = (listOf(progress.statusText) + progress.warnings)
                        .filter { it.isNotBlank() }.distinct().joinToString("；")
                    val updated = old.copy(
                        status = when {
                            progress.isCancelled -> ScanSessionEntity.CANCELLED
                            progress.isFinished && progress.errorMessage != null -> ScanSessionEntity.FAILED
                            progress.isFinished && progress.skippedCount > 0 -> ScanSessionEntity.PARTIAL
                            progress.isFinished -> ScanSessionEntity.SUCCESS
                            progress.isPaused -> ScanSessionEntity.PAUSED
                            else -> ScanSessionEntity.RUNNING
                        },
                        updatedAt = System.currentTimeMillis(),
                        finishedAt = if (progress.isFinished) System.currentTimeMillis() else null,
                        phase = progress.phase,
                        currentSource = progress.currentSource,
                        currentDirectory = progress.currentDirectory,
                        discoveredCount = progress.discoveredCount,
                        skippedCount = progress.skippedCount,
                        unavailableCount = progress.unavailableCount,
                        sourcesDone = progress.sourcesDone,
                        sourcesTotal = progress.sourcesTotal,
                        statusText = persistedStatusText,
                        errorMessage = progress.errorMessage
                    )
                    dao.upsert(updated)
                    if (progress.sourceSkippedCounts.isNotEmpty()) {
                        progress.sourceSkippedCounts.forEach { (sourceId, skipped) ->
                            sourceDao.setSourceSkippedCount(sessionId, sourceId, skipped)
                        }
                    }
                    if (progress.completedSourceIds.isNotEmpty()) {
                        val currentStats = sourceDao.scanStatsForSession(sessionId).associateBy { it.sourceId }
                        progress.completedSourceIds.forEach { sourceId ->
                            val stat = currentStats[sourceId] ?: return@forEach
                            if (stat.status !in listOf("RUNNING", "QUEUED")) return@forEach
                            val skipped = progress.sourceSkippedCounts[sourceId] ?: 0
                            val sourceError = progress.sourceErrors[sourceId]
                            sourceDao.upsertScanStat(stat.copy(
                                status = when {
                                    sourceError != null -> "FAILED"
                                    skipped > 0 -> "PARTIAL"
                                    else -> "COMPLETED"
                                },
                                skippedCount = skipped,
                                finishedAt = System.currentTimeMillis(),
                                errorMessage = sourceError
                            ))
                        }
                    }
                    setProgress(workDataOfProgress(progress.discoveredCount, progress.sourcesDone, progress.sourcesTotal, progress.statusText))
                    if (!progress.isFinished) setForeground(createForegroundInfo(progress.statusText))
                }
            }
            val finalProgress = ScanManager.progress.first { it.hasStarted && it.isFinished }
            progressJob.cancel()

            val finalStatus = when {
                finalProgress.isCancelled -> ScanSessionEntity.CANCELLED
                finalProgress.sourceErrors.isNotEmpty() && sourceIdsByUri.isNotEmpty() -> ScanSessionEntity.PARTIAL
                finalProgress.errorMessage != null -> ScanSessionEntity.FAILED
                finalProgress.skippedCount > 0 -> ScanSessionEntity.PARTIAL
                else -> ScanSessionEntity.SUCCESS
            }
            val scanFinishedAt = System.currentTimeMillis()
            (sourceIdsByUri.values + listOfNotNull(mediaStoreSourceId)).forEach { sourceId ->
                val count = sourceDao.countLinksForSource(sourceId)
                val sourceError = finalProgress.sourceErrors[sourceId]
                val sourceSkipped = finalProgress.sourceSkippedCounts[sourceId] ?: 0
                val sourceStatus = when {
                    finalProgress.isCancelled -> "CANCELLED"
                    sourceError != null -> "FAILED"
                    sourceSkipped > 0 -> "PARTIAL"
                    else -> "COMPLETED"
                }
                val unavailableForSource = sourceDao.countUnavailableLinksForSource(sourceId)
                sourceDao.finalizeSourceScan(
                    sourceId = sourceId,
                    count = count,
                    scannedAt = scanFinishedAt,
                    sessionId = sessionId,
                    status = when (sourceStatus) {
                        "COMPLETED" -> ImageSourceEntity.COMPLETED
                        "PARTIAL", "CANCELLED" -> ImageSourceEntity.PARTIAL
                        "FAILED" -> ImageSourceEntity.FAILED
                        else -> ImageSourceEntity.UNSCANNED
                    },
                    error = sourceError
                )
                if (sourceError != null && (
                        sourceError.contains("无法打开所选文件夹") ||
                        sourceError.contains("所选文件夹不可读取") ||
                        sourceError.contains("FileNotFoundException") ||
                        sourceError.contains("SecurityException") ||
                        sourceError.contains("授权已失效")
                    )) {
                    sourceDao.markAuthorizationExpired(sourceId, sourceError)
                }
                val previousStat = sourceDao.scanStatsForSession(sessionId).firstOrNull { it.sourceId == sourceId }
                sourceDao.upsertScanStat(SourceScanStatEntity(
                    scanSessionId = sessionId,
                    sourceId = sourceId,
                    status = sourceStatus,
                    discoveredCount = previousStat?.discoveredCount ?: 0,
                    addedCount = previousStat?.addedCount ?: 0,
                    updatedCount = previousStat?.updatedCount ?: 0,
                    existingCount = previousStat?.existingCount ?: 0,
                    unavailableCount = unavailableForSource,
                    skippedCount = sourceSkipped,
                    failedCount = if (sourceError != null) 1 else 0,
                    startedAt = startedAt,
                    finishedAt = scanFinishedAt,
                    errorMessage = sourceError
                ))
            }
            val current = dao.getById(sessionId) ?: session
            dao.upsert(current.copy(
                status = finalStatus,
                updatedAt = System.currentTimeMillis(),
                finishedAt = System.currentTimeMillis(),
                statusText = (listOf(finalProgress.statusText) + finalProgress.warnings)
                    .filter { it.isNotBlank() }.distinct().joinToString("；"),
                errorMessage = finalProgress.errorMessage,
                discoveredCount = sourceDao.scanStatsForSession(sessionId).sumOf { it.discoveredCount },
                skippedCount = finalProgress.skippedCount,
                unavailableCount = finalProgress.unavailableCount,
                phase = finalProgress.phase
            ))
            ScanCheckpointStore.clearSession(applicationContext, sessionId)
            ScanTaskScheduler.markFinished(applicationContext, sessionId)
            Result.success()
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { runCatching { withTimeout(5_000) { ScanManager.cancelAndJoin() } } }
            runCatching {
                val sourceDao = db.imageSourceDao()
                val current = dao.getById(sessionId)
                val explicitlyCancelled = current?.status == ScanSessionEntity.CANCELLED
                if (explicitlyCancelled) {
                    val sourceIds = (folderUris.mapNotNull { rawUri -> sourceDao.getByUri(rawUri)?.id ?: rawUri } + listOfNotNull(mediaStoreSourceId)).distinct()
                    sourceIds.forEach { sourceId ->
                        val count = sourceDao.countLinksForSource(sourceId)
                        sourceDao.finalizeSourceScan(sourceId, count, System.currentTimeMillis(), sessionId, ImageSourceEntity.PARTIAL, "扫描已取消，已建立的图片索引予以保留")
                    }
                    sourceDao.cancelActiveStatsForSession(sessionId, System.currentTimeMillis(), "扫描已取消，已建立的图片索引予以保留")
                    sourceDao.markSessionSourcesPartial(sessionId, "扫描已取消；已建立的图片索引予以保留")
                    val base = current ?: sessionSnapshot ?: ScanSessionEntity(
                        id = sessionId, status = ScanSessionEntity.CANCELLED,
                        includeMediaStore = includeMediaStore, folderUris = folderUris.joinToString("\n"),
                        includeSubfolders = includeSubfolders, startedAt = startedAt, updatedAt = System.currentTimeMillis()
                    )
                    dao.upsert(base.copy(
                        status = ScanSessionEntity.CANCELLED, updatedAt = System.currentTimeMillis(),
                        finishedAt = System.currentTimeMillis(), statusText = "扫描已取消；已建立的图片索引予以保留"
                    ))
                    ScanCheckpointStore.clearSession(applicationContext, sessionId)
                    ScanTaskScheduler.markFinished(applicationContext, sessionId)
                } else {
                    // 系统抢占/进程任务停止不是用户取消：保留会话并让 WorkManager 重试。
                    sourceDao.requeueInterruptedStatsForSession(sessionId, "系统暂时中断，等待后台恢复")
                    val base = current ?: sessionSnapshot ?: ScanSessionEntity(
                        id = sessionId, status = ScanSessionEntity.QUEUED,
                        includeMediaStore = includeMediaStore, folderUris = folderUris.joinToString("\n"),
                        includeSubfolders = includeSubfolders, startedAt = startedAt, updatedAt = System.currentTimeMillis()
                    )
                    dao.upsert(base.copy(
                        status = ScanSessionEntity.QUEUED, updatedAt = System.currentTimeMillis(),
                        finishedAt = null, statusText = "扫描被系统暂时中断，后台将尝试恢复"
                    ))
                }
            }
            throw cancelled
        } catch (error: Exception) {
            ScanManager.cancel()
            val current = dao.getById(sessionId)
            if (current != null) dao.upsert(current.copy(
                status = ScanSessionEntity.FAILED,
                updatedAt = System.currentTimeMillis(),
                finishedAt = System.currentTimeMillis(),
                statusText = "扫描任务执行失败",
                errorMessage = error.localizedMessage ?: error.javaClass.simpleName
            ))
            ScanCheckpointStore.clearSession(applicationContext, sessionId)
            ScanTaskScheduler.markFinished(applicationContext, sessionId)
            Result.failure()
        }
    }

    private fun createForegroundInfo(text: String): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "扫描进度", NotificationManager.IMPORTANCE_LOW).apply {
                description = "扫描图片目录时显示任务进度"
                setShowBadge(false)
            })
        }
        val openIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("toimg_open_route", AppRoute.SCAN_PROGRESS)
        }
        val pending = PendingIntent.getActivity(
            applicationContext, 771, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_toimg)
            .setContentTitle("ToImg 正在扫描图片")
            .setContentText(text.ifBlank { "正在建立本地图片索引" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(pending)
            .build()
        return if (Build.VERSION.SDK_INT >= 34) {
            ForegroundInfo(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun workDataOfProgress(discovered: Int, done: Int, total: Int, status: String) =
        androidx.work.workDataOf("discovered" to discovered, "sources_done" to done, "sources_total" to total, "status" to status)

    companion object {
        const val KEY_SESSION_ID = "scan_session_id"
        const val KEY_MEDIA_STORE = "scan_media_store"
        const val KEY_FOLDER_URIS = "scan_folder_uris"
        const val KEY_INCLUDE_SUBFOLDERS = "scan_include_subfolders"
        const val KEY_SCAN_MODE = "scan_mode"
        const val UNIQUE_WORK_NAME = "toimg_scan_session"
        const val CHANNEL_ID = "scan_progress"
        const val NOTIFICATION_ID = 1801
    }
}
