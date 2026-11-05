package com.toimg.app.domain.model

import android.net.Uri

enum class ConflictStrategy(val displayName: String, val description: String) {
    RENAME_NEW("自动重命名 (推荐)", "碰撞时在文件名后追加 _1, _2 后缀，保留两份文件"),
    SKIP("跳过不移动", "碰撞时跳过该文件，保持源文件留在原位置")
}

data class MovePreviewItem(
    val imageItem: ImageItem,
    val sourcePathDisplay: String,
    val targetDirName: String,
    val targetDirUri: Uri,
    val proposedTargetName: String,
    val hasConflict: Boolean = false,
    val isValid: Boolean = true,
    val invalidReason: String? = null
)

data class MovePlan(
    val items: List<MovePreviewItem> = emptyList(),
    val targetDirName: String = "",
    val targetDirUri: Uri? = null,
    val conflictStrategy: ConflictStrategy = ConflictStrategy.RENAME_NEW
) {
    val totalCount: Int
        get() = items.size

    val validCount: Int
        get() = items.count { it.isValid }

    val conflictCount: Int
        get() = items.count { it.hasConflict }

    val invalidCount: Int
        get() = items.count { !it.isValid }

    val canExecute: Boolean
        get() = targetDirUri != null && validCount > 0
}
