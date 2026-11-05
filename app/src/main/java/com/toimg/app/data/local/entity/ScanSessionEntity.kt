package com.toimg.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

/** 当前及最近一次扫描的持久化快照。配置 URI 用换行分隔保存，来源 URI 本身不会包含换行。 */
@Entity(tableName = "scan_sessions")
data class ScanSessionEntity(
    @PrimaryKey val id: String,
    val status: String,
    val includeMediaStore: Boolean,
    val folderUris: String,
    val includeSubfolders: Boolean,
    val startedAt: Long,
    val updatedAt: Long,
    val finishedAt: Long? = null,
    val phase: String = "准备扫描",
    val currentSource: String = "",
    val currentDirectory: String = "",
    val discoveredCount: Int = 0,
    val skippedCount: Int = 0,
    val unavailableCount: Int = 0,
    val sourcesDone: Int = 0,
    val sourcesTotal: Int = 0,
    val statusText: String = "等待扫描任务启动",
    val errorMessage: String? = null,
    /** 本次任务来源 ID 快照；旧会话为空时回退解析 folderUris。 */
    @ColumnInfo(defaultValue = "''") val selectedSourceIds: String = "",
    /** 来源名称快照；即使来源之后被移出，历史记录仍可读。 */
    @ColumnInfo(defaultValue = "''") val selectedSourceNames: String = "",
    /** 扫描策略：新会话默认快速增量；旧会话迁移后采用核对模式以安全恢复。 */
    @ColumnInfo(defaultValue = "'VERIFY_EXISTING'") val scanMode: String = "FAST_INCREMENTAL"
) {
    companion object {
        const val QUEUED = "QUEUED"
        const val RUNNING = "RUNNING"
        const val PAUSED = "PAUSED"
        const val SUCCESS = "SUCCESS"
        const val PARTIAL = "PARTIAL"
        const val CANCELLED = "CANCELLED"
        const val FAILED = "FAILED"
        val ACTIVE = listOf(QUEUED, RUNNING, PAUSED)
    }

    fun folderUriList(): List<String> = folderUris.split('\n').filter { it.isNotBlank() }
}
