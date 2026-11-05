package com.toimg.app.domain.model

data class RecognitionResult(
    val imageId: String,
    val keywords: List<String>,
    val description: String,
    val modelName: String,
    val isSuccess: Boolean = true,
    val ocrText: String? = null,
    val isSensitive: Boolean = false,
    val errorMessage: String? = null,
    val recognizedAt: Long = System.currentTimeMillis(),
    /** 失败原因是暂时性的（限流/5xx/超时/网络），值得稍后自动重试。 */
    val retryable: Boolean = false,
    /** 429 已完成请求内冷却重试，不进入任务级短间隔队列，也不立即切备用云端。 */
    val rateLimited: Boolean = false
) {
    val keywordsSummary: String
        get() = keywords.joinToString(", ")
}

data class BatchProgressState(
    val totalCount: Int = 0,
    val completedCount: Int = 0,
    val failedCount: Int = 0,
    val currentBatchIndex: Int = 0,
    val totalBatches: Int = 0,
    val batchSize: Int = 0,
    val currentImageName: String = "",
    val statusText: String = "",
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val isCancelled: Boolean = false,
    val isFinished: Boolean = false
) {
    val pendingCount: Int
        get() = (totalCount - completedCount - failedCount).coerceAtLeast(0)
}
