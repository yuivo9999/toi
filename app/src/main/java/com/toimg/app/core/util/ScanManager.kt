package com.toimg.app.core.util

import com.toimg.app.core.di.AppContainer

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.toimg.app.data.local.entity.TaskRecordEntity
import com.toimg.app.data.repository.LocalImageRepositoryImpl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ScanProgress(
    val hasStarted: Boolean = false,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val isFinished: Boolean = false,
    val isCancelled: Boolean = false,
    val phase: String = "",
    val currentSource: String = "",
    val currentSourceId: String = "",
    val sourceSkippedCounts: Map<String, Int> = emptyMap(),
    val completedSourceIds: Set<String> = emptySet(),
    val currentDirectory: String = "",
    val discoveredCount: Int = 0,
    val skippedCount: Int = 0,
    val skipReasons: Map<String, Int> = emptyMap(),
    val unavailableCount: Int = 0,
    val sourcesDone: Int = 0,
    val sourcesTotal: Int = 0,
    val statusText: String = "",
    val errorMessage: String? = null,
    val sourceErrors: Map<String, String> = emptyMap(),
    val warnings: List<String> = emptyList()
)

/**
 * 全 App 唯一的扫描任务管理器。
 * - 扫描只建立本地索引，不复制、不修改、不上传任何图片。
 * - 支持暂停 / 继续 / 取消，并实时报告当前目录、已发现数量、跳过数量及原因。
 * - 由 WorkManager Worker 驱动，离开页面后任务继续执行；进度由 Worker 持久化到 Room。
 */
object ScanManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private val repository = LocalImageRepositoryImpl()

    @Volatile
    private var paused = false

    private val _progress = MutableStateFlow(ScanProgress())
    val progress: StateFlow<ScanProgress> = _progress.asStateFlow()

    fun isRunning(): Boolean = _progress.value.isRunning

    // ---------- 供 Repository 扫描过程调用的上报钩子 ----------

    fun reportDirectory(name: String) {
        if (_progress.value.isRunning && _progress.value.currentDirectory != name) {
            _progress.update { it.copy(currentDirectory = name) }
        }
    }

    fun reportWarning(message: String) {
        if (message.isBlank()) return
        _progress.update { current ->
            val next = (current.warnings + message).distinct()
            current.copy(warnings = next, statusText = if (current.isRunning) "${current.statusText}（注意：$message）" else current.statusText)
        }
    }

    fun reportExisting(count: Int) {
        if (count <= 0) return
        _progress.update { it.copy(discoveredCount = it.discoveredCount + count) }
    }

    fun reportSkipped(reason: String) {
        if (!_progress.value.isRunning) return
        _progress.update {
            val map = it.skipReasons.toMutableMap()
            map[reason] = (map[reason] ?: 0) + 1
            val sourceSkips = it.sourceSkippedCounts.toMutableMap()
            if (it.currentSourceId.isNotBlank()) {
                sourceSkips[it.currentSourceId] = (sourceSkips[it.currentSourceId] ?: 0) + 1
            }
            it.copy(skippedCount = it.skippedCount + 1, skipReasons = map, sourceSkippedCounts = sourceSkips)
        }
    }

    /** 扫描循环中调用：暂停时在此挂起，取消时抛出 CancellationException。 */
    suspend fun awaitIfPaused() {
        while (paused) {
            currentCoroutineContext().ensureActive()
            delay(200)
        }
        currentCoroutineContext().ensureActive()
    }

    // ---------- 控制 ----------

    fun start(context: Context, includeMediaStore: Boolean, folderUris: List<Uri>, includeSubfolders: Boolean = true, sourceIdsByUri: Map<String, String> = emptyMap(), scanSessionId: String? = null, mediaStoreSourceId: String? = null, initialDiscoveredCount: Int = 0, initialSkippedCount: Int = 0, initialSourceSkippedCounts: Map<String, Int> = emptyMap(), scanMode: ScanMode = ScanMode.FAST_INCREMENTAL) {
        if (isRunning()) return
        if (!includeMediaStore && folderUris.isEmpty()) return

        val app = context.applicationContext
        val startedAt = System.currentTimeMillis()
        paused = false
        _progress.value = ScanProgress(
            hasStarted = true,
            isRunning = true,
            phase = "扫描图片",
            sourcesTotal = (if (includeMediaStore) 1 else 0) + folderUris.size,
            discoveredCount = initialDiscoveredCount.coerceAtLeast(0),
            skippedCount = initialSkippedCount.coerceAtLeast(0),
            sourceSkippedCounts = initialSourceSkippedCounts,
            statusText = "正在准备扫描…"
        )

        job = scope.launch {
            // Restored from the session's persisted per-source counters. A resumed worker must not
            // visually reset progress to zero merely because checkpointed files are skipped.
            var failedSources = 0
            try {
                if (includeMediaStore) {
                    _progress.update { it.copy(currentSource = "系统媒体库", currentSourceId = mediaStoreSourceId.orEmpty(), currentDirectory = "", statusText = "正在扫描系统媒体库…") }
                    try {
                        var last = 0
                        repository.scanMediaStoreImagesWithMode(app, mediaStoreSourceId, scanSessionId, scanMode).collect { list ->
                            val delta = (list.size - last).coerceAtLeast(0)
                            last = list.size
                            if (delta > 0) _progress.update { it.copy(discoveredCount = it.discoveredCount + delta) }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failedSources++
                        if (!mediaStoreSourceId.isNullOrBlank()) {
                            _progress.update { it.copy(sourceErrors = it.sourceErrors + (mediaStoreSourceId to (e.localizedMessage ?: e.javaClass.simpleName))) }
                        }
                        reportSkipped("来源读取失败：系统媒体库（${e.javaClass.simpleName}: ${e.localizedMessage ?: "无详细信息"}）")
                    }
                    _progress.update { it.copy(
                        sourcesDone = it.sourcesDone + 1,
                        completedSourceIds = it.completedSourceIds + listOfNotNull(mediaStoreSourceId)
                    ) }
                }

                for (treeUri in folderUris) {
                    val name = try {
                        DocumentFile.fromTreeUri(app, treeUri)?.name ?: "SAF 目录"
                    } catch (e: Exception) {
                        "SAF 目录"
                    }
                    val sourceId = sourceIdsByUri[treeUri.toString()]
                    _progress.update { it.copy(currentSource = name, currentSourceId = sourceId.orEmpty(), currentDirectory = name, statusText = "正在扫描：$name") }
                    try {
                        var last = 0
                        repository.scanFolderUriWithMode(app, treeUri, includeSubfolders, sourceId, scanSessionId, scanMode).collect { list ->
                            val delta = (list.size - last).coerceAtLeast(0)
                            last = list.size
                            if (delta > 0) _progress.update { it.copy(discoveredCount = it.discoveredCount + delta) }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failedSources++
                        val detail = e.localizedMessage ?: e.javaClass.simpleName
                        if (!sourceId.isNullOrBlank()) _progress.update { it.copy(sourceErrors = it.sourceErrors + (sourceId to detail)) }
                        reportSkipped("来源读取失败：$name（授权可能已失效）")
                    }
                    _progress.update { it.copy(
                        sourcesDone = it.sourcesDone + 1,
                        completedSourceIds = if (sourceId.isNullOrBlank()) it.completedSourceIds else it.completedSourceIds + sourceId
                    ) }
                }

                val allSourceIds = (sourceIdsByUri.values + listOfNotNull(mediaStoreSourceId)).toSet()
                var unavailable = 0
                if (scanMode == ScanMode.VERIFY_EXISTING) {
                    // 核对模式：只对缺少指纹/元数据变化的记录补算 SHA-256，并检查旧图片可用性。
                    _progress.update { it.copy(phase = "核对旧图片", currentSourceId = "", currentDirectory = "", statusText = "正在核对已入库图片…") }
                    if (allSourceIds.isNotEmpty()) {
                        repository.computeMissingHashesForSources(app, allSourceIds) { current, total ->
                            _progress.update { it.copy(statusText = "核对图片指纹：$current / $total") }
                        }
                    } else if (includeMediaStore) {
                        repository.computeMissingHashes(app) { current, total ->
                            _progress.update { it.copy(statusText = "核对图片指纹：$current / $total") }
                        }
                    }
                    _progress.update { it.copy(phase = "检查文件可用性", statusText = "正在检查旧图片是否仍可读取…") }
                    val sourceImageIds = if (allSourceIds.isNotEmpty()) {
                        AppContainer.database(app).imageSourceDao().imageIdsForSources(allSourceIds.toList()).toSet()
                    } else null
                    unavailable = checkAvailability(app, sourceImageIds) { current, total ->
                        _progress.update { it.copy(statusText = "检查旧图片：$current / $total") }
                    }
                } else {
                    _progress.update { it.copy(phase = "完成增量入库", statusText = "快速模式：旧图片已跳过，不进行旧图片指纹与可用性核对") }
                }

                _progress.update {
                    it.copy(
                        isRunning = false,
                        isPaused = false,
                        isFinished = true,
                        phase = "完成",
                        currentDirectory = "",
                        unavailableCount = unavailable,
                        errorMessage = if (failedSources > 0) "有 $failedSources 个扫描来源读取失败，请检查授权后重试" else null,
                        statusText = "扫描完成：发现 ${it.discoveredCount} 张，跳过 ${it.skippedCount} 项，不可用 $unavailable 张"
                    )
                }
                val done = _progress.value
                saveRecord(app, startedAt, done, if (failedSources > 0) "PARTIAL" else "SUCCESS")
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    _progress.update {
                        it.copy(
                            isRunning = false,
                            isPaused = false,
                            isFinished = true,
                            isCancelled = true,
                            statusText = "扫描已取消：已发现 ${it.discoveredCount} 张（已入库的索引会保留）"
                        )
                    }
                    saveRecord(app, startedAt, _progress.value, "CANCELLED")
                }
                throw e
            } catch (e: Exception) {
                withContext(NonCancellable) {
                    _progress.update {
                        it.copy(
                            isRunning = false,
                            isFinished = true,
                            errorMessage = e.localizedMessage ?: "扫描异常终止",
                            statusText = "扫描异常终止"
                        )
                    }
                    saveRecord(app, startedAt, _progress.value, "FAILED")
                }
            }
        }
    }

    fun pause() {
        if (!isRunning()) return
        paused = true
        _progress.update { it.copy(isPaused = true, statusText = "扫描已暂停") }
    }

    fun resume() {
        if (!isRunning()) return
        paused = false
        _progress.update { it.copy(isPaused = false, statusText = "继续扫描…") }
    }

    fun cancel() {
        if (!isRunning()) return
        paused = false
        job?.cancel()
    }

    /** Worker 收到停止信号后等待扫描协程完成清理，避免同进程重试与旧任务重叠。 */
    suspend fun cancelAndJoin() {
        paused = false
        job?.cancelAndJoin()
    }

    fun reset() {
        if (!isRunning()) _progress.value = ScanProgress()
    }

    // ---------- 文件可用性 ----------

    private fun isReadable(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 逐张检查图片是否仍可读取：不可读标记为 UNAVAILABLE（保留索引与识别结果，不会悄悄消失）；
     * 之前不可用、现在恢复的图片，按识别结果还原状态。返回当前不可用数量。
     */
    suspend fun checkAvailability(
        context: Context,
        imageIds: Set<String>? = null,
        onProgress: ((Int, Int) -> Unit)? = null
    ): Int = withContext(Dispatchers.IO) {
        val db = AppContainer.database(context)
        val all = if (imageIds == null) db.imageDao().getAllImagesOnce() else
            imageIds.toList().chunked(500).flatMap { db.imageDao().getImagesByIds(it) }
        var unavailable = 0
        all.forEachIndexed { index, entity ->
            awaitIfPaused()
            val ok = isReadable(context, Uri.parse(entity.uriString))
            if (!ok) {
                unavailable++
                if (entity.status != "UNAVAILABLE") db.imageDao().updateStatus(entity.id, "UNAVAILABLE")
            } else if (entity.status == "UNAVAILABLE") {
                val rec = db.recognitionDao().getRecognitionByImageId(entity.id)
                val restored = when (rec?.status) {
                    "SUCCESS" -> "RECOGNIZED"
                    "FAILED" -> "FAILED"
                    else -> "SCANNED"
                }
                db.imageDao().updateStatus(entity.id, restored)
            }
            if (onProgress != null && (index % 25 == 0 || index == all.lastIndex)) {
                onProgress(index + 1, all.size)
            }
        }
        unavailable
    }

    private suspend fun saveRecord(context: Context, startedAt: Long, p: ScanProgress, status: String) {
        try {
            val reasons = if (p.skipReasons.isEmpty()) "" else "；跳过原因：" +
                p.skipReasons.entries.joinToString("、") { "${it.key}×${it.value}" }
            AppContainer.database(context).taskRecordDao().insert(
                TaskRecordEntity(
                    type = TaskRecordEntity.TYPE_SCAN,
                    status = status,
                    title = "扫描 ${p.sourcesTotal} 个来源",
                    totalCount = p.discoveredCount,
                    successCount = p.discoveredCount,
                    failedCount = p.unavailableCount,
                    skippedCount = p.skippedCount,
                    detail = "已完成 ${p.sourcesDone}/${p.sourcesTotal} 个来源$reasons",
                    startedAt = startedAt,
                    finishedAt = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
