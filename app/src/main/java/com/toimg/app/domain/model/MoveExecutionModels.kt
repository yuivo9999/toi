package com.toimg.app.domain.model

import android.net.Uri

enum class MoveItemStatus(val displayName: String) {
    PENDING("等待中"),
    COPYING("复制中"),
    COPIED("已复制"),
    VERIFYING("校验完整性"),
    SUCCESS("移动成功"),
    PARTIALLY_COMPLETED("部分完成 (源文件保留)"),
    SKIPPED("已跳过"),
    FAILED("移动失败")
}

data class MoveItemResult(
    val imageId: String,
    val originalName: String,
    val sourceUri: Uri,
    val targetUri: Uri?,
    val finalName: String,
    val status: MoveItemStatus,
    val sourceHash: String? = null,
    val targetHash: String? = null,
    val errorMessage: String? = null,
    val executionMethod: String = "COPY_VERIFY_DELETE",
    val timestamp: Long = System.currentTimeMillis()
)

data class MoveSessionReport(
    val sessionId: String = System.currentTimeMillis().toString(),
    val targetDirName: String = "",
    val totalCount: Int = 0,
    val successCount: Int = 0,
    val partialCount: Int = 0,
    val skippedCount: Int = 0,
    val failedCount: Int = 0,
    val items: List<MoveItemResult> = emptyList(),
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long = System.currentTimeMillis()
)
