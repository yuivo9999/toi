package com.toimg.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/** 一个扫描会话内、单个来源的独立统计快照。 */
@Entity(tableName = "source_scan_stats", primaryKeys = ["scanSessionId", "sourceId"], indices = [Index(value = ["sourceId"])])
data class SourceScanStatEntity(
    val scanSessionId: String,
    val sourceId: String,
    val status: String = "QUEUED",
    val discoveredCount: Int = 0,
    val addedCount: Int = 0,
    val updatedCount: Int = 0,
    val existingCount: Int = 0,
    val skippedCount: Int = 0,
    val unavailableCount: Int = 0,
    val failedCount: Int = 0,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val errorMessage: String? = null
)
