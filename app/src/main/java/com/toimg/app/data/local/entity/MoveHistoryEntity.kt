package com.toimg.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "move_history")
data class MoveHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val imageId: String,
    val fileName: String,
    val sourcePath: String,
    val targetPath: String,
    val status: String, // SUCCESS, PARTIALLY_COMPLETED, FAILED, SKIPPED
    val sha256: String?,
    val executionMethod: String,
    val errorMessage: String?,
    val timestamp: Long = System.currentTimeMillis()
)
