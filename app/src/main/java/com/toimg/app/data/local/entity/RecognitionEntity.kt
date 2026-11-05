package com.toimg.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recognition_results")
data class RecognitionEntity(
    @PrimaryKey val imageId: String,
    val keywords: String, // Comma-separated list of keywords, e.g. "猫咪, 宠物, 室内, 窗台"
    val description: String, // Short summary description
    val modelName: String,
    val status: String = "SUCCESS", // "SUCCESS", "FAILED"
    val ocrText: String? = null, // Extracted key text (invoices, receipts, code snippets, etc.)
    val isSensitive: Boolean = false, // Flag for ID cards, bank cards, personal credentials
    val errorMessage: String? = null,
    val recognizedAt: Long = System.currentTimeMillis()
)
