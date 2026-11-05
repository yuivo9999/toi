package com.toimg.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/** 图片与来源多对多关联；不把来源 URI 当作图片主键。 */
@Entity(tableName = "image_source_links", primaryKeys = ["imageId", "sourceId"], indices = [Index(value = ["sourceId"]), Index(value = ["imageId"])])
data class ImageSourceLinkEntity(
    val imageId: String,
    val sourceId: String,
    val firstSeenAt: Long = System.currentTimeMillis(),
    val lastSeenAt: Long = System.currentTimeMillis()
)
