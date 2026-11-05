package com.toimg.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

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
