package com.toimg.app.core.util

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.toimg.app.data.local.dao.ImageDao
import com.toimg.app.data.local.entity.ImageEntity
import com.toimg.app.data.local.ToImgDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 跨 SAF / MediaStore / Photo Picker 的保守身份解析器。
 * 文件名或大小绝不单独作为去重依据；只有官方 URI 映射命中，或唯一候选的修改时间与 SHA-256 均一致才复用索引。
 */
object ImageIdentityResolver {
    // Keep identity lookup + canonical upsert serialized across scanner and picker coroutines.
    // This closes the read-then-insert race that could otherwise create two IDs for one asset.
    private val ingestMutex = Mutex()

    suspend fun resolveAndPersist(
        context: Context,
        db: ToImgDatabase,
        incoming: ImageEntity
    ): ImageEntity = ingestMutex.withLock {
        val resolved = resolve(context, db.imageDao(), incoming)
        db.withTransaction {
            // Re-check canonical URI after acquiring Room's writer transaction. Another writer may
            // have inserted it while this resolver was calculating the content hash.
            db.imageDao().getImageByUri(resolved.uriString)?.let { existing ->
                return@withTransaction existing
            }
            db.imageDao().insertImages(listOf(resolved))
            resolved
        }
    }


    fun canonicalMediaUri(context: Context, uri: Uri): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val authority = uri.authority.orEmpty()
        if (authority != "com.android.externalstorage.documents" &&
            authority != "com.android.providers.media.documents") return null
        return runCatching { MediaStore.getMediaUri(context, uri) }.getOrNull()
    }

    suspend fun resolve(
        context: Context,
        imageDao: ImageDao,
        incoming: ImageEntity
    ): ImageEntity {
        val incomingUri = Uri.parse(incoming.uriString)
        val canonical = canonicalMediaUri(context, incomingUri)
        if (canonical != null) {
            imageDao.getImageByUri(canonical.toString())?.let { return it }
        }
        imageDao.getImageByUri(incoming.uriString)?.let { return it }

        if (incoming.size <= 0L || incoming.dateModified <= 0L || incoming.name.isBlank()) return incoming
        val candidates = imageDao.findIdentityCandidates(
            name = incoming.name,
            size = incoming.size,
            dateModified = incoming.dateModified,
            mimeType = incoming.mimeType
        ).filter { candidate -> ImageIdentityPolicy.isMetadataCandidate(incoming, candidate) }
        // 多个同元数据候选时拒绝自动合并，避免把不同目录中的真实副本误认为同一个文件。
        val candidate = ImageIdentityPolicy.uniqueCandidate(candidates) ?: return incoming
        val incomingHash = Sha256Util.calculateSha256(context, incomingUri) ?: return incoming
        val candidateHash = candidate.sha256
            ?: Sha256Util.calculateSha256(context, Uri.parse(candidate.uriString))?.also {
                imageDao.updateSha256(candidate.id, it)
            }
        if (candidateHash != null && candidateHash == incomingHash) {
            val incomingIsStableMediaUri = incomingUri.authority == "media" &&
                !incomingUri.path.orEmpty().contains("/picker/")
            val preferredUri = when {
                canonical != null -> canonical.toString()
                incomingIsStableMediaUri -> incoming.uriString
                else -> candidate.uriString
            }
            return candidate.copy(
                uriString = preferredUri,
                name = incoming.name,
                path = incoming.path,
                size = incoming.size,
                dateModified = incoming.dateModified,
                mimeType = incoming.mimeType,
                folderName = incoming.folderName,
                folderPath = incoming.folderPath,
                sha256 = candidateHash
            )
        }
        return incoming.copy(sha256 = incomingHash)
    }
}
