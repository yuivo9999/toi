package com.toimg.app.core.util

/**
 * Default scan exclusions. These are directory-name/path rules only: files are never deleted.
 * A user who explicitly selects an excluded directory as the SAF root may scan that selected
 * tree for this run; excluded descendants inside it remain excluded.
 */
object ScanDirectoryFilter {
    private val excludedDirectoryNames = setOf(
        ".thumbnails", "thumbnails",
        ".cache", "cache",
        ".trash", "trash",
        "temp", "tmp"
    )

    fun isExcludedDirectoryName(name: String?): Boolean {
        val normalized = name?.trim()?.trimEnd('/', '\\')?.substringAfterLast('/')?.lowercase()
        return normalized != null && normalized in excludedDirectoryNames
    }

    /** Detect excluded path components in MediaStore RELATIVE_PATH or legacy absolute DATA path. */
    fun isExcludedPath(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        val normalized = path.replace('\\', '/').trim('/')
        val parts = normalized.split('/').filter { it.isNotBlank() }
        if (parts.any { it.lowercase() in excludedDirectoryNames }) return true
        // Android app-private storage locations are not useful image-library sources.
        return parts.zipWithNext().any { (parent, child) ->
            parent.equals("Android", ignoreCase = true) &&
                (child.equals("data", ignoreCase = true) || child.equals("obb", ignoreCase = true))
        }
    }

    /** Explicit selection only exempts the selected root, never its excluded child folders. */
    fun shouldSkipDirectory(name: String?, isSelectedRoot: Boolean): Boolean =
        isExcludedDirectoryName(name) && !isSelectedRoot
}
