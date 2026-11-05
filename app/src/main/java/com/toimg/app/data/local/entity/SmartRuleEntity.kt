package com.toimg.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "smart_rules")
data class SmartRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val keywordTrigger: String, // Comma-separated, e.g. "发票, 收据, 票据, 报销"
    val targetFolderName: String, // e.g. "Pictures/财务报销/"
    val targetTreeUri: String,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
