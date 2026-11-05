package com.toimg.app.core.util

import com.toimg.app.data.local.dao.ImageSourceDao
import com.toimg.app.data.local.entity.ImageSourceLinkEntity

/** F3 统一图片-来源关联写入入口；图片主记录仍由 ImageDao 管理。 */
class ImageSourceIndexCoordinator(private val sourceDao: ImageSourceDao) {
    suspend fun linkImagesToSource(
        imageIds: List<String>,
        sourceId: String,
        observedAt: Long = System.currentTimeMillis()
    ): Int {
        if (imageIds.isEmpty()) return 0
        val links = imageIds.distinct().map { imageId ->
            ImageSourceLinkEntity(imageId, sourceId, observedAt, observedAt)
        }
        val insertedRows = sourceDao.addImageSourceLinks(links)
        links.forEach { sourceDao.touchImageSourceLink(it.imageId, sourceId, observedAt) }
        return insertedRows.count { it != -1L }
    }

    suspend fun linkExistingImageToSource(imageId: String, sourceId: String, observedAt: Long = System.currentTimeMillis()) {
        linkImagesToSource(listOf(imageId), sourceId, observedAt)
    }
}
