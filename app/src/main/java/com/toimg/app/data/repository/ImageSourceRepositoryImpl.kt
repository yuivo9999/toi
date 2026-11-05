package com.toimg.app.data.repository

import androidx.room.withTransaction
import android.content.Context
import android.net.Uri
import android.content.Intent
import com.toimg.app.data.local.ToImgDatabase
import com.toimg.app.data.local.entity.ImageSourceEntity
import com.toimg.app.data.local.entity.ImageSourceSummary
import com.toimg.app.data.local.entity.SourceScanStatEntity
import com.toimg.app.data.local.entity.SourceLifecycleEventEntity
import com.toimg.app.domain.repository.ImageSourceRepository
import com.toimg.app.domain.repository.RemoveSourceResult
import kotlinx.coroutines.flow.Flow

/** 来源数据唯一入口。数据库为 SSOT，UI 不直接访问 DAO。 */
class ImageSourceRepositoryImpl(private val context: Context, private val db: ToImgDatabase) : ImageSourceRepository {
    private val sourceDao = db.imageSourceDao()

    private fun releaseTreePermission(uri: Uri) {
        val grant = context.contentResolver.persistedUriPermissions.firstOrNull { it.uri == uri } ?: return
        var flags = 0
        if (grant.isReadPermission) flags = flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (grant.isWritePermission) flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        if (flags != 0) runCatching { context.contentResolver.releasePersistableUriPermission(uri, flags) }
    }

    override fun observeSources(): Flow<List<ImageSourceSummary>> = sourceDao.observeAll()
    override suspend fun getSource(sourceId: String) = sourceDao.getById(sourceId)
    override suspend fun registerSource(source: ImageSourceEntity) { sourceDao.insert(source) }
    override suspend fun updateSource(source: ImageSourceEntity) {
        sourceDao.update(source)
        // 来源显示名称是项目级名称；同步到扫描范围旧版授权清单，避免两个页面各显示一套名称。
        val prefs = context.getSharedPreferences("toimg_authorized_folders", Context.MODE_PRIVATE)
        val entries = prefs.getStringSet("folders_list", emptySet())?.toMutableSet() ?: mutableSetOf()
        val matched = entries.filter { it.substringBefore('|') == source.uriString || it.substringBefore('|') == source.treeUriString }
        if (matched.isNotEmpty()) {
            entries.removeAll(matched.toSet())
            entries.add("${source.uriString}|${source.displayName}")
            prefs.edit().putStringSet("folders_list", entries).apply()
        }
    }
    override fun observeScanStats(sourceId: String): Flow<List<SourceScanStatEntity>> = sourceDao.observeScanStats(sourceId)
    override fun observeScanStatsForSession(sessionId: String): Flow<List<SourceScanStatEntity>> = sourceDao.observeScanStatsForSession(sessionId)
    override fun observeRecentLifecycleEvents(limit: Int): Flow<List<SourceLifecycleEventEntity>> = db.sourceLifecycleEventDao().observeRecent(limit)

    /** 保留稳定 sourceId 和既有图片/识别索引，只替换失效的 SAF tree URI。 */
    override suspend fun reauthorizeSource(sourceId: String, newTreeUri: String, displayName: String): Boolean {
        val source = sourceDao.getById(sourceId) ?: return false
        if (sourceDao.activeScanCountForSource(sourceId) > 0) return false
        val newUri = Uri.parse(newTreeUri)
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(newUri, flags)
        } catch (_: SecurityException) {
            try {
                context.contentResolver.takePersistableUriPermission(newUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) { return false }
        } catch (_: Exception) { return false }

        val prefs = context.getSharedPreferences("toimg_authorized_folders", Context.MODE_PRIVATE)
        val entries = prefs.getStringSet("folders_list", emptySet())?.toMutableSet() ?: mutableSetOf()
        entries.removeAll { it.substringBefore('|') == source.uriString || it.substringBefore('|') == source.treeUriString }
        entries.add("$newTreeUri|${displayName.ifBlank { source.displayName }}")
        if (!prefs.edit().putStringSet("folders_list", entries).commit()) {
            releaseTreePermission(newUri)
            return false
        }

        try {
            sourceDao.update(source.copy(
                uriString = newTreeUri,
                treeUriString = newTreeUri,
                isAuthorized = true,
                lifecycleStatus = ImageSourceEntity.UNSCANNED,
                errorMessage = null
            ))
        } catch (error: Exception) {
            val rollback = prefs.getStringSet("folders_list", emptySet())?.toMutableSet() ?: mutableSetOf()
            rollback.removeAll { it.substringBefore('|') == newTreeUri }
            rollback.add("${source.uriString}|${source.displayName}")
            prefs.edit().putStringSet("folders_list", rollback).commit()
            releaseTreePermission(newUri)
            throw error
        }
        if (source.uriString != newTreeUri) {
            releaseTreePermission(Uri.parse(source.uriString))
        }
        return true
    }

    override suspend fun removeSourceFromProject(sourceId: String): RemoveSourceResult {
        val source = sourceDao.getById(sourceId) ?: return RemoveSourceResult(0, 0)
        val result = db.withTransaction {
            val current = sourceDao.getById(sourceId) ?: return@withTransaction RemoveSourceResult(0, 0)
            if (sourceDao.activeScanCountForSource(sourceId) > 0) {
                return@withTransaction RemoveSourceResult(0, 0, blockedByActiveScan = true)
            }
            val links = sourceDao.linksForSource(sourceId)
            // 只把实际存在的图片行计入清理/共享统计；孤立 link 会随来源关联一起清理，但不能冒充删除了图片。
            val validImageIds = if (links.isEmpty()) emptyList() else
                db.imageDao().getImagesByIds(links.map { it.imageId }.distinct()).map { it.id }
            val exclusiveIds = validImageIds.filter { sourceDao.sourceReferenceCount(it) <= 1 }
            val sharedCount = validImageIds.size - exclusiveIds.size
            // 审计事件与业务清理处于同一 Room 事务：要么两者都提交，要么都回滚。
            // 设备文件删除数固定为 0；此用例绝不调用 ContentResolver.delete / DocumentFile.delete。
            sourceDao.deleteLinksForSource(sourceId)
            if (exclusiveIds.isNotEmpty()) {
                db.recognitionDao().deleteByImageIds(exclusiveIds)
                db.imageFtsDao().deleteFtsByImageIds(exclusiveIds)
                db.imageDao().deleteImagesByIds(exclusiveIds)
            }
            db.sourceLifecycleEventDao().insert(SourceLifecycleEventEntity(
                sourceId = current.id,
                sourceName = current.displayName,
                eventType = SourceLifecycleEventEntity.SOURCE_REMOVED,
                status = SourceLifecycleEventEntity.COMPLETED,
                linkedImageCount = validImageIds.size,
                deletedImageCount = exclusiveIds.size,
                retainedSharedImageCount = sharedCount,
                deviceFilesDeleted = 0,
                detail = "来源已从项目移出；独占索引及识别结果已清理，共享图片保留；清理孤立关联 ${links.size - validImageIds.size} 条；设备原图未删除。"
            ))
            sourceDao.deleteById(sourceId)
            RemoveSourceResult(exclusiveIds.size, sharedCount)
        }
        if (!result.blockedByActiveScan) {
            val prefs = context.getSharedPreferences("toimg_authorized_folders", Context.MODE_PRIVATE)
            val entries = prefs.getStringSet("folders_list", emptySet())?.toMutableSet() ?: mutableSetOf()
            entries.removeAll { it.substringBefore('|') == source.uriString || it.substringBefore('|') == source.treeUriString }
            prefs.edit().putStringSet("folders_list", entries).apply()
            if (source.id != ImageSourceEntity.MEDIASTORE_SOURCE_ID && source.id != ImageSourceEntity.PICKER_SOURCE_ID) {
                releaseTreePermission(Uri.parse(source.treeUriString))
            }
        }
        return result
    }
}
