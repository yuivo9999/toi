package com.toimg.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/** 来源生命周期审计。来源被移出后仍保留 sourceId/name 与清理统计，不依赖 image_sources 外键。 */
@Entity(tableName = "source_lifecycle_events", indices = [Index(value = ["sourceId"]), Index(value = ["createdAt"])])
data class SourceLifecycleEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: String,
    val sourceName: String,
    val eventType: String,
    val status: String,
    val linkedImageCount: Int,
    val deletedImageCount: Int,
    val retainedSharedImageCount: Int,
    val deviceFilesDeleted: Int = 0,
    val detail: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
    const val SOURCE_REMOVED = "SOURCE_REMOVED"
    const val COMPLETED = "COMPLETED"
    const val BLOCKED = "BLOCKED"
    }
}
