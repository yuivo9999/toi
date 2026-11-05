package com.toimg.app.core.util

import com.toimg.app.core.di.AppContainer

import android.content.Context
import android.net.Uri
import com.toimg.app.core.service.RecognitionService
import com.toimg.app.data.local.ToImgDatabase
import com.toimg.app.data.local.entity.ImageEntity
import com.toimg.app.data.local.entity.TaskRecordEntity
import com.toimg.app.data.remote.AiVisionClient
import com.toimg.app.data.remote.ImagePayload
import com.toimg.app.data.repository.AiConfigRepository
import com.toimg.app.data.repository.LocalImageRepositoryImpl
import com.toimg.app.domain.model.AiConfig
import com.toimg.app.domain.model.BatchProgressState
import com.toimg.app.domain.model.RecognitionResult
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 全 App 唯一的批量 AI 识别引擎（工作区页面不再自带第二套批次逻辑）。
 *
 * 规则：
 * 1. 按用户设置的批次大小（5/10/20）切分，每一批把整批图片放进「一次」请求（真正的多图批次）。
 * 2. 严格串行：上一批「请求 → 解析校验 → 写入 Room」全部完成后，才开始下一批。
 * 3. 支持暂停（停在批次边界）、继续、取消；取消后未处理的图片会被还原状态。
 * 4. 单张失败不影响同批其他图片的成功结果。
 * 5. 【v3】请求内遇到 429/5xx/超时按指数退避自动重试；全部批次结束后，
 *    对「可重试失败项」再自动排队最多 [RetryPolicy.MAX_AUTO_RETRY_ROUNDS] 轮（每轮前冷却）。
 * 6. 【v3】任务由前台服务 [RecognitionService] 保活（通知栏进度，可暂停/继续/取消）；
 *    进程被杀后，服务被系统重启时会通过 [resumeAfterProcessDeath] 从「处理中」的图片续跑。
 * 7. 结束后写入一条任务历史记录，并发出完成通知。
 */
object BackgroundBatchManager {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var currentBatchJob: Job? = null

    @Volatile
    private var paused = false

    /** 系统强制中断（如前台服务超时）时为 true：保留「处理中」状态，方便稍后恢复。 */
    @Volatile
    private var keepProcessingOnCancel = false

    private val _progressState = MutableStateFlow(BatchProgressState())
    val progressState: StateFlow<BatchProgressState> = _progressState.asStateFlow()

    private val repository = LocalImageRepositoryImpl()

    private const val SESSION_PREFS = "toimg_batch_session"
    private const val KEY_ACTIVE = "active"
    private const val KEY_BATCH_SIZE = "batch_size"

    private class Counters(
        var success: Int = 0,
        var failed: Int = 0,
        var processedBatches: Int = 0
    )

    fun isRunning(): Boolean = _progressState.value.isRunning

    /**
     * 启动识别。batchSize 为空时使用用户保存的批次设置。
     */
    fun startBatchRecognition(
        context: Context,
        imagesToProcess: List<ImageEntity>,
        batchSize: Int? = null,
        onFinished: (() -> Unit)? = null
    ) {
        if (isRunning() || imagesToProcess.isEmpty()) return

        val appContext = context.applicationContext
        val configRepo = AiConfigRepository(appContext)
        val size = (batchSize ?: configRepo.getBatchSize()).coerceAtLeast(1)
        val batches = BatchPlanner.plan(imagesToProcess, size)
        val total = batches.sumOf { it.size }
        val startedAt = System.currentTimeMillis()

        paused = false
        keepProcessingOnCancel = false
        _progressState.value = BatchProgressState(
            totalCount = total,
            totalBatches = batches.size,
            batchSize = size,
            isRunning = true,
            statusText = "识别任务已启动，共 ${batches.size} 批（每批最多 $size 张）"
        )
        setSessionActive(appContext, true, size)
        RecognitionService.start(appContext)

        currentBatchJob = applicationScope.launch {
            val db = AppContainer.database(appContext)
            val aiConfig = configRepo.getAiConfig()
            val aiClient = AiVisionClient()
            val counters = Counters()
            val retryQueue = LinkedHashMap<String, ImageEntity>()

            // 先持久化"处理中"状态，进程被杀后可据此续跑
            batches.flatten().forEach { db.imageDao().updateStatus(it.id, "PROCESSING") }

            try {
                for ((batchIdx, chunk) in batches.withIndex()) {
                    waitWhilePaused()
                    if (!isActive) break

                    val label = "第 ${batchIdx + 1}/${batches.size} 批"
                    _progressState.update {
                        it.copy(
                            currentBatchIndex = batchIdx + 1,
                            currentImageName = chunk.firstOrNull()?.name ?: "",
                            statusText = "$label：压缩 ${chunk.size} 张图片…"
                        )
                    }

                    val results = processChunk(appContext, chunk, aiConfig, aiClient, label)
                    persistChunk(appContext, db, chunk, results, counters, retryQueue, isRetry = false)
                    counters.processedBatches++
                    // NVIDIA 串行节流：本批结果已写入 Room 后，才开始下一批倒计时。
                    if (aiConfig.providerType == AiConfig.PROVIDER_NVIDIA && batchIdx < batches.lastIndex) {
                        waitNvidiaBatchInterval(configRepo, label)
                    }
                    _progressState.update {
                        it.copy(
                            completedCount = counters.success,
                            failedCount = counters.failed,
                            statusText = "$label 完成：成功 ${counters.success}，失败 ${counters.failed}"
                        )
                    }
                }

                // 失败批次自动重试：只重试「暂时性失败」，每轮前冷却；整轮都没救回来就停止，避免空耗额度
                var round = 0
                while (isActive && !_progressState.value.isCancelled &&
                    retryQueue.isNotEmpty() && round < RetryPolicy.MAX_AUTO_RETRY_ROUNDS
                ) {
                    round++
                    val pending = retryQueue.values.toList()
                    retryQueue.clear()
                    cooldown(round, pending.size)

                    val retryBatches = pending.chunked(size)
                    var rescued = 0
                    for ((i, chunk) in retryBatches.withIndex()) {
                        waitWhilePaused()
                        if (!isActive) break
                        val label = "自动重试第 $round/${RetryPolicy.MAX_AUTO_RETRY_ROUNDS} 轮 · 第 ${i + 1}/${retryBatches.size} 批"
                        _progressState.update { it.copy(statusText = "$label：重新识别 ${chunk.size} 张…") }
                        val results = processChunk(appContext, chunk, aiConfig, aiClient, label)
                        rescued += persistChunk(appContext, db, chunk, results, counters, retryQueue, isRetry = true)
                        if (aiConfig.providerType == AiConfig.PROVIDER_NVIDIA && i < retryBatches.lastIndex) {
                            waitNvidiaBatchInterval(configRepo, label)
                        }
                        _progressState.update {
                            it.copy(
                                completedCount = counters.success,
                                failedCount = counters.failed,
                                statusText = "$label 完成：成功 ${counters.success}，失败 ${counters.failed}"
                            )
                        }
                    }
                    if (rescued == 0) break
                }

                val cancelled = !isActive || _progressState.value.isCancelled
                finishTask(
                    appContext, startedAt, total, counters.success, counters.failed,
                    batches.size, counters.processedBatches, size, aiConfig.modelName, cancelled
                )
            } catch (e: CancellationException) {
                withContext(NonCancellable) {
                    finishTask(
                        appContext, startedAt, total, counters.success, counters.failed,
                        batches.size, counters.processedBatches, size, aiConfig.modelName, true
                    )
                }
                throw e
            } catch (e: Exception) {
                withContext(NonCancellable) {
                    resetUnprocessed(appContext)
                    setSessionActive(appContext, false, size)
                    _progressState.update {
                        it.copy(isRunning = false, isFinished = true, statusText = "识别任务异常终止：${e.localizedMessage ?: "未知错误"}")
                    }
                    saveRecord(appContext, startedAt, total, counters.success, counters.failed, "FAILED", batches.size, counters.processedBatches, size, aiConfig.modelName)
                }
            }

            withContext(Dispatchers.Main) { onFinished?.invoke() }
        }
    }

    /** 压缩 → 调用识别引擎（云端 / 本地 / 混合兜底），返回本批所有结果（含无法读取的图片）。 */
    private suspend fun processChunk(
        appContext: Context,
        chunk: List<ImageEntity>,
        aiConfig: AiConfig,
        aiClient: AiVisionClient,
        label: String
    ): List<RecognitionResult> {
        val payloads = mutableListOf<ImagePayload>()
        val unreadable = mutableListOf<RecognitionResult>()
        for (img in chunk) {
            val base64 = try {
                ImageCompressUtil.compressUriToBase64(appContext, Uri.parse(img.uriString), maxDimension = 1024)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (base64 != null) {
                payloads.add(ImagePayload(imageId = img.id, base64Jpeg = base64, fileName = img.name))
            } else {
                unreadable.add(
                    RecognitionResult(
                        imageId = img.id,
                        keywords = emptyList(),
                        description = "文件无法读取或已损坏",
                        modelName = aiConfig.modelName,
                        isSuccess = false,
                        errorMessage = "无法从 URI 解码图片（文件可能已丢失或授权失效）"
                    )
                )
            }
        }

        val aiResults: List<RecognitionResult> = if (aiConfig.isOfflineMode) {
            _progressState.update { it.copy(statusText = "$label：正在使用 ML Kit 本地端侧模型识别 ${chunk.size} 张图片…") }
            MlKitOfflineVisionEngine.processBatch(appContext, chunk)
        } else if (payloads.isNotEmpty()) {
            var cloudResults = if (aiConfig.apiKey.isNotBlank()) {
                _progressState.update { it.copy(statusText = "$label：已向 ${aiConfig.modelName} 发送 ${payloads.size} 张图片，等待响应…") }
                var primaryResults = aiClient.recognizeBatch(aiConfig, payloads) { attempt, maxAttempts, waitMs, reason ->
                    val secs = (waitMs + 999L) / 1000L
                    val why = reason.substringBefore(":").take(30)
                    _progressState.update {
                        it.copy(statusText = "$label：主力服务繁忙（$why），$secs 秒后第 $attempt/$maxAttempts 次重试…")
                    }
                }
                val primaryFailed = primaryResults.isNotEmpty() && primaryResults.all { !it.isSuccess }
                val primaryHasTransientFailure = primaryResults.any { it.retryable || it.rateLimited }
                if (primaryFailed && !primaryHasTransientFailure && aiConfig.backupApiKey.isNotBlank() && aiConfig.backupModelName.isNotBlank()) {
                    val backupConfig = aiConfig.copy(
                        apiKey = aiConfig.backupApiKey,
                        baseUrl = aiConfig.backupBaseUrl,
                        modelName = aiConfig.backupModelName,
                        providerType = aiConfig.backupProviderType
                    )
                    _progressState.update { it.copy(statusText = "$label：主力模型失败，正在切换备用模型 ${backupConfig.modelName}…") }
                    val backupResults = aiClient.recognizeBatch(backupConfig, payloads)
                    if (backupResults.any { it.isSuccess }) primaryResults = backupResults
                }
                primaryResults
            } else {
                emptyList()
            }

            // 混合模式：未配置 Key 或云端（重试用尽后）仍全量失败，无缝触发 ML Kit 本地离线兜底
            val allFailed = cloudResults.isNotEmpty() && cloudResults.all { !it.isSuccess }
            // 可重试/限流失败代表云端请求仍有机会成功（例如 NVIDIA 推理较慢、
            // 响应暂时缺少完整 JSON）；不能因此立刻切换离线模型，避免浪费已发出的云端请求与 Token。
            val hasPendingCloudRetry = cloudResults.any { it.retryable || it.rateLimited }
            val definitiveCloudFailure = allFailed && !hasPendingCloudRetry
            if (aiConfig.isHybridMode && (
                    aiConfig.apiKey.isBlank() ||
                    definitiveCloudFailure ||
                    (cloudResults.isEmpty() && payloads.isNotEmpty())
                )
            ) {
                _progressState.update { it.copy(statusText = "$label：云端请求已明确失败，自动使用 ML Kit 本地离线模型兜底…") }
                cloudResults = MlKitOfflineVisionEngine.processBatch(appContext, chunk)
            }
            cloudResults
        } else {
            emptyList()
        }
        return unreadable + aiResults
    }

    /**
     * 先整批落库，再更新状态与计数（API 返回不等于成功，必须持久化后才计数）。
     * isRetry=true 表示这是对「之前已计为失败」的图片的重试：成功时需把失败数扣回。
     * 返回本批成功张数。
     */
    private suspend fun persistChunk(
        context: Context,
        db: ToImgDatabase,
        chunk: List<ImageEntity>,
        results: List<RecognitionResult>,
        counters: Counters,
        retryQueue: MutableMap<String, ImageEntity>,
        isRetry: Boolean
    ): Int {
        repository.saveRecognitions(context, results)
        val byId = chunk.associateBy { it.id }
        var gained = 0
        for (res in results) {
            val existing = db.recognitionDao().getRecognitionByImageId(res.imageId)
            val protectedCloudSuccess = existing?.status == "SUCCESS" &&
                existing.modelName != "MLKit-OnDevice" &&
                (!res.isSuccess || res.modelName == "MLKit-OnDevice")
            if (protectedCloudSuccess) {
                // 该图片已有云端成功结果；忽略失败/本地兜底结果，保留成功状态与索引。
                counters.success++
                retryQueue.remove(res.imageId)
                continue
            }
            if (res.isSuccess) {
                counters.success++
                if (isRetry) counters.failed = (counters.failed - 1).coerceAtLeast(0)
                gained++
                retryQueue.remove(res.imageId)
            } else {
                if (!isRetry) counters.failed++
                val entity = byId[res.imageId]
                if (res.retryable && entity != null) retryQueue[res.imageId] = entity
            }
            db.imageDao().updateStatus(res.imageId, if (res.isSuccess) "RECOGNIZED" else "FAILED")
        }
        // 本批中没有出现在结果里的图片（理论上不会发生）按失败处理，避免永远停在 PROCESSING
        val returnedIds = results.map { it.imageId }.toSet()
        for (img in chunk) {
            if (img.id !in returnedIds) {
                if (!isRetry) counters.failed++
                db.imageDao().updateStatus(img.id, "FAILED")
            }
        }
        return gained
    }

    /** NVIDIA 正常批次间隔：倒计时在本批入库后启动，支持暂停/取消。 */
    private suspend fun waitNvidiaBatchInterval(configRepo: AiConfigRepository, label: String) {
        var remaining = configRepo.getNvidiaRequestIntervalSeconds()
        while (remaining > 0) {
            currentCoroutineContext().ensureActive()
            waitWhilePaused()
            _progressState.update {
                it.copy(statusText = "$label 已归档；下一批 NVIDIA 请求将在 ${remaining} 秒后发送（可暂停或取消）")
            }
            delay(1_000L)
            remaining--
        }
    }

    /** 自动重试前的冷却等待（可暂停、可取消；取消时抛出 CancellationException）。 */
    private suspend fun cooldown(round: Int, count: Int) {
        val totalSec = (RetryPolicy.ROUND_COOLDOWN_MS * round / 1000L).toInt()
        for (left in totalSec downTo 1) {
            currentCoroutineContext().ensureActive()
            waitWhilePaused()
            _progressState.update {
                it.copy(statusText = "有 $count 张图片因限流/网络失败，$left 秒后自动重试（第 $round/${RetryPolicy.MAX_AUTO_RETRY_ROUNDS} 轮）…")
            }
            delay(1_000L)
        }
    }

    private suspend fun finishTask(
        context: Context,
        startedAt: Long,
        total: Int,
        success: Int,
        failed: Int,
        totalBatches: Int,
        processedBatches: Int,
        batchSize: Int,
        model: String,
        cancelled: Boolean
    ) {
        if (cancelled && !keepProcessingOnCancel) resetUnprocessed(context)
        setSessionActive(context, false, batchSize)
        val status = when {
            cancelled -> "CANCELLED"
            failed > 0 -> "PARTIAL"
            else -> "SUCCESS"
        }
        _progressState.update {
            it.copy(
                completedCount = success,
                failedCount = failed,
                isRunning = false,
                isPaused = false,
                isCancelled = cancelled,
                isFinished = true,
                statusText = if (cancelled) {
                    "识别任务已取消：已完成 $processedBatches/$totalBatches 批，成功 $success 张，失败 $failed 张"
                } else {
                    "识别完成：成功 $success 张，失败 $failed 张"
                }
            )
        }
        saveRecord(context, startedAt, total, success, failed, status, totalBatches, processedBatches, batchSize, model)

        if (!cancelled) {
            val proposals = try {
                SmartProposalEngine.count(context)
            } catch (e: Exception) {
                0
            }
            NotificationHelper.notifyRecognitionFinished(context, _progressState.value, proposals)
        }
    }

    private suspend fun saveRecord(
        context: Context,
        startedAt: Long,
        total: Int,
        success: Int,
        failed: Int,
        status: String,
        totalBatches: Int,
        processedBatches: Int,
        batchSize: Int,
        model: String
    ) {
        try {
            AppContainer.database(context).taskRecordDao().insert(
                TaskRecordEntity(
                    type = TaskRecordEntity.TYPE_RECOGNITION,
                    status = status,
                    title = "AI 识别 $total 张图片",
                    totalCount = total,
                    successCount = success,
                    failedCount = failed,
                    skippedCount = (total - success - failed).coerceAtLeast(0),
                    detail = "模型 $model；批次大小 $batchSize；已完成 $processedBatches/$totalBatches 批",
                    startedAt = startedAt,
                    finishedAt = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** 取消/异常后，把仍停留在 PROCESSING 的图片还原，避免下次启动被当作"中断任务"。 */
    private suspend fun resetUnprocessed(context: Context) {
        try {
            val db = AppContainer.database(context)
            db.imageDao().getImagesByStatus("PROCESSING").forEach {
                db.imageDao().updateStatus(it.id, "SCANNED")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun waitWhilePaused() {
        while (paused && currentBatchJob?.isActive == true) {
            delay(300)
        }
    }

    // ---------- 断点续跑 ----------

    private fun setSessionActive(context: Context, active: Boolean, batchSize: Int) {
        context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ACTIVE, active)
            .putInt(KEY_BATCH_SIZE, batchSize)
            .apply()
    }

    /**
     * 前台服务被系统在进程被杀后重启时调用。
     * 仅当「上次任务是用户主动发起且没有正常结束」才会续跑——图片只来自上次任务里仍处于「处理中」的那批。
     * 返回 true 表示任务正在运行（或已被重新启动）。
     */
    suspend fun resumeAfterProcessDeath(context: Context): Boolean = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        if (isRunning()) return@withContext true
        val prefs = app.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ACTIVE, false)) return@withContext false

        val interrupted = AppContainer.database(app).imageDao().getImagesByStatus("PROCESSING")
        if (interrupted.isEmpty()) {
            prefs.edit().putBoolean(KEY_ACTIVE, false).apply()
            return@withContext false
        }
        val size = prefs.getInt(KEY_BATCH_SIZE, AiConfigRepository.DEFAULT_BATCH_SIZE)
        startBatchRecognition(app, interrupted, size)
        true
    }

    /** 暂停：当前批次完成后停在批次边界，不会中途丢弃已发出的请求。 */
    fun pause() {
        if (!isRunning()) return
        paused = true
        _progressState.update { it.copy(isPaused = true, statusText = "已请求暂停：当前批次完成后暂停") }
    }

    fun resume() {
        if (!isRunning()) return
        paused = false
        _progressState.update { it.copy(isPaused = false, statusText = "已继续识别") }
    }

    /** 取消：不再启动新批次。 */
    fun stopBatch() {
        if (!isRunning()) return
        paused = false
        _progressState.update { it.copy(isCancelled = true, statusText = "正在取消…") }
        currentBatchJob?.cancel()
    }

    fun cancel() = stopBatch()

    /** 系统限制后台时长（如 Android 15 前台服务超时）：中断任务但保留「处理中」状态，可稍后在首页/任务历史恢复。 */
    fun interruptKeepingProgress() {
        if (!isRunning()) return
        keepProcessingOnCancel = true
        paused = false
        _progressState.update { it.copy(isCancelled = true, statusText = "系统限制了后台运行时长，任务已暂存，可稍后恢复") }
        currentBatchJob?.cancel()
    }

    /** 清除上一次已结束任务的进度显示。 */
    fun dismissFinished() {
        if (!isRunning()) _progressState.value = BatchProgressState()
    }

    /**
     * 仅检查是否存在上次被中断的任务并返回数量，**不会自动上传**；
     * 是否继续由用户在首页确认（隐私要求：未经用户操作不得上传图片）。
     * （前台服务被系统重启时的自动续跑见 [resumeAfterProcessDeath]，仅限用户已发起的任务。）
     */
    suspend fun checkAndResumeInterrupted(context: Context): Int = withContext(Dispatchers.IO) {
        if (isRunning()) return@withContext 0
        try {
            AppContainer.database(context).imageDao().getImagesByStatus("PROCESSING").size
        } catch (e: Throwable) {
            0
        }
    }
}
