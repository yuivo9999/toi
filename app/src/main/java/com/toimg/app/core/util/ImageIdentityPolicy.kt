package com.toimg.app.core.util

import com.toimg.app.data.local.entity.ImageEntity

/** 不依赖 Android API 的保守候选规则，供单元测试和 URI resolver 共用。 */
object ImageIdentityPolicy {
    fun isMetadataCandidate(incoming: ImageEntity, candidate: ImageEntity): Boolean =
        incoming.id != candidate.id &&
            incoming.name.isNotBlank() &&
            incoming.size > 0L &&
            incoming.dateModified > 0L &&
            candidate.name == incoming.name &&
            candidate.size == incoming.size &&
            candidate.dateModified == incoming.dateModified &&
            candidate.mimeType.substringBefore(';').equals(
                incoming.mimeType.substringBefore(';'), ignoreCase = true
            )

    fun uniqueCandidate(candidates: List<ImageEntity>): ImageEntity? =
        candidates.singleOrNull()
}
