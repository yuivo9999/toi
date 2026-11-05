package com.toimg.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "images")
data class ImageEntity(
    @PrimaryKey val id: String,
    val uriString: String,
    val name: String,
    val path: String,
    val size: Long,
    val dateModified: Long,
    val mimeType: String,
    val folderName: String,
    val folderPath: String = "",
    val sha256: String? = null,
    val status: String = "SCANNED",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val uriString: String = "",
    val path: String = "",
    val imageCount: Int = 0,
    val isAuthorized: Boolean = true,
    val lastScannedAt: Long = System.currentTimeMillis()
)
