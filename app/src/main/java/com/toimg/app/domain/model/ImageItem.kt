package com.toimg.app.domain.model

import android.net.Uri

data class ImageItem(
    val id: String,
    val uri: Uri,
    val name: String,
    val path: String,
    val size: Long,
    val dateModified: Long,
    val mimeType: String,
    val folderName: String,
    val folderPath: String = "",
    val sha256: String? = null,
    val status: String = "SCANNED"
) {
    val formattedSize: String
        get() {
            return when {
                size >= 1024 * 1024 -> String.format("%.2f MB", size / (1024.0 * 1024.0))
                size >= 1024 -> String.format("%.1f KB", size / 1024.0)
                else -> "$size B"
            }
        }
}

data class FolderItem(
    val id: String,
    val name: String,
    val uri: Uri? = null,
    val path: String = "",
    val imageCount: Int = 0,
    val isAuthorized: Boolean = false
)

sealed class ScanStatus {
    data object Idle : ScanStatus()
    data class Scanning(val scannedCount: Int, val currentFolder: String = "") : ScanStatus()
    data class Success(val totalImages: Int, val foldersCount: Int) : ScanStatus()
    data class Error(val message: String) : ScanStatus()
}
