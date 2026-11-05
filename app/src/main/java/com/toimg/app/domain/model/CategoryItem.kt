package com.toimg.app.domain.model

import android.net.Uri

data class CategoryItem(
    val tag: String,
    val displayName: String,
    val imageCount: Int,
    val coverUri: Uri? = null,
    val imageIds: List<String> = emptyList()
)
