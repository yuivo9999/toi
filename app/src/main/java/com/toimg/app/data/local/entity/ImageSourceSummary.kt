package com.toimg.app.data.local.entity

import androidx.room.Embedded

/**
 * UI 展示用只读投影。
 * linkedImageCount 表示来源发现/关联历史；currentLocationCount 表示当前 URI 仍位于来源位置的数量。
 * Photo Picker 是选取记录来源，没有持续目录位置，因此 currentLocationCount 为 null。
 */
data class ImageSourceSummary(
    @Embedded val source: ImageSourceEntity,
    val linkedImageCount: Int,
    val currentLocationCount: Int?
)
