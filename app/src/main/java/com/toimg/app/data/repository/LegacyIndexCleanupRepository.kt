package com.toimg.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.toimg.app.core.di.AppContainer

/** Only removes ToImg database rows; never touches source files or move history. */
class LegacyIndexCleanupRepository(context: Context) {
    private val db = AppContainer.database(context)
    private val excludedDirectoryNames = setOf(
        ".thumbnails", "thumbnails", ".cache", "cache", ".trash", "trash", "temp", "tmp"
    )

    private fun isLegacyExcluded(image: com.toimg.app.data.local.entity.ImageEntity): Boolean {
        return listOf(image.path, image.folderPath).any { raw ->
            val decoded = Uri.decode(raw).replace('\\', '/')
            decoded.split('/').any { segment -> segment.lowercase() in excludedDirectoryNames }
        }
    }

    data class Preview(
        val imageCount: Int,
        val recognitionCount: Int,
        val folders: List<String>,
        val imageIds: List<String>
    )

    suspend fun preview(): Preview {
        val candidates = db.imageDao().getAllImagesOnce().filter(::isLegacyExcluded)
        val ids = candidates.map { it.id }
        val recognitionCount = if (ids.isEmpty()) 0 else
            db.recognitionDao().getAllRecognitionsSync().count { it.imageId in ids }
        return Preview(
            imageCount = candidates.size,
            recognitionCount = recognitionCount,
            folders = candidates.map { it.folderPath.ifBlank { it.path.substringBeforeLast('/') } }.distinct().sorted(),
            imageIds = ids
        )
    }

    suspend fun clean(preview: Preview): Int {
        if (preview.imageIds.isEmpty()) return 0
        return db.withTransaction {
            // Re-read the candidate set at commit time to avoid deleting stale/unrelated IDs.
            val safeIds = db.imageDao().getAllImagesOnce()
                .filter(::isLegacyExcluded).map { it.id }.toSet()
                .intersect(preview.imageIds.toSet()).toList()
            if (safeIds.isEmpty()) return@withTransaction 0
            db.imageFtsDao().deleteFtsByImageIds(safeIds)
            db.recognitionDao().deleteByImageIds(safeIds)
            db.imageDao().deleteImagesByIds(safeIds)
        }
    }
}
