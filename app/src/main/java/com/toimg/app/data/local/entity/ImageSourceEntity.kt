package com.toimg.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 项目图片来源（SAF 文件夹、系统媒体库或图片选择器）；授权、登记、扫描状态彼此独立。 */
@Entity(tableName = "image_sources")
data class ImageSourceEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val uriString: String,
    val treeUriString: String = uriString,
    val isAuthorized: Boolean = true,
    val includeSubfolders: Boolean = true,
    val lifecycleStatus: String = UNSCANNED,
    val addedAt: Long = System.currentTimeMillis(),
    val lastScannedAt: Long? = null,
    val lastScanSessionId: String? = null,
    val imageCount: Int = 0,
    val errorMessage: String? = null
) {
    companion object {
        const val UNSCANNED = "UNSCANNED"
        const val SCANNING = "SCANNING"
        const val COMPLETED = "COMPLETED"
        const val PARTIAL = "PARTIAL"
        const val FAILED = "FAILED"
        const val AUTH_EXPIRED = "AUTH_EXPIRED"
        const val MEDIASTORE_SOURCE_ID = "system:mediastore:external-images"
        const val MEDIASTORE_URI = "toimg://source/system-mediastore-external-images"
        const val PICKER_SOURCE_ID = "system:photo-picker-selections"
        const val PICKER_URI = "toimg://source/photo-picker-selections"
    }
}
