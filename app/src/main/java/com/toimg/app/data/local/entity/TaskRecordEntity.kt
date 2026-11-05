package com.toimg.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 任务历史记录：扫描、AI 识别两类任务各自一条汇总记录。
 * 移动任务沿用 move_history 逐项记录。
 */
@Entity(tableName = "task_records")
data class TaskRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String, // SCAN, RECOGNITION
    val status: String, // SUCCESS, PARTIAL, CANCELLED, FAILED
    val title: String,
    val totalCount: Int,
    val successCount: Int,
    val failedCount: Int,
    val skippedCount: Int,
    val detail: String,
    val startedAt: Long,
    val finishedAt: Long
) {
    companion object {
        const val TYPE_SCAN = "SCAN"
        const val TYPE_RECOGNITION = "RECOGNITION"
    }
}
