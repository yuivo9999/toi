package com.toimg.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

@Entity(tableName = "image_search_fts")
@Fts4
data class ImageSearchFtsEntity(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowid: Int = 0,
    val imageId: String,
    val keywords: String,
    val description: String,
    val ocrText: String,
    val name: String,
    val folderName: String
)
