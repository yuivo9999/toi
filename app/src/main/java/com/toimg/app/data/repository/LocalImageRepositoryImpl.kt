package com.toimg.app.data.repository

import com.toimg.app.core.di.AppContainer

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.toimg.app.core.util.FileMoveUtil
import com.toimg.app.core.util.ScanManager
import com.toimg.app.core.util.ScanDirectoryFilter
import com.toimg.app.core.util.Sha256Util
import com.toimg.app.core.util.ImageIdentityResolver
import com.toimg.app.core.util.ScanCheckpointStore
import com.toimg.app.core.util.ScanMode
import com.toimg.app.data.local.ToImgDatabase
import com.toimg.app.data.local.entity.ImageSourceEntity
import com.toimg.app.data.local.entity.ImageEntity
import com.toimg.app.data.local.entity.RecognitionEntity
import com.toimg.app.data.local.entity.SmartRuleEntity
import com.toimg.app.domain.model.FolderItem
import com.toimg.app.domain.model.ImageItem
import com.toimg.app.domain.model.RecognitionResult
import com.toimg.app.domain.repository.ImageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class LocalImageRepositoryImpl : ImageRepository {

    private companion object {
        const val MAX_SCAN_DEPTH = 12
    }

    private class FastExistingCounter(var value: Int = 0, val imageIds: MutableList<String> = mutableListOf())

    private suspend fun ensureSourceLinksForKnownImages(db: ToImgDatabase, sourceId: String?, imageIds: List<String>) {
        if (sourceId.isNullOrBlank() || imageIds.isEmpty()) return
        val sourceDao = db.imageSourceDao()
        imageIds.distinct().chunked(400).forEach { batch ->
            db.withTransaction {
                if (sourceDao.writableSourceCount(sourceId) == 0) return@withTransaction
                val linked = sourceDao.linkedImageIds(sourceId, batch).toSet()
                val missing = batch.filterNot { it in linked }
                if (missing.isNotEmpty()) {
                    val now = System.currentTimeMillis()
                    sourceDao.addImageSourceLinks(missing.map {
                        com.toimg.app.data.local.entity.ImageSourceLinkEntity(it, sourceId, now, now)
                    })
                }
            }
        }
    }

    private val prefName = "toimg_authorized_folders"
    private val keyFolderList = "folders_list"

    override fun observeImagesFromDb(context: Context): Flow<List<ImageItem>> {
        val db = AppContainer.database(context)
        return db.imageDao().getAllImages().map { entities ->
            entities.map { entity ->
                ImageItem(
                    id = entity.id,
                    uri = Uri.parse(entity.uriString),
                    name = entity.name,
                    path = entity.path,
                    size = entity.size,
                    dateModified = entity.dateModified,
                    mimeType = entity.mimeType,
                    folderName = entity.folderName,
                    folderPath = entity.folderPath,
                    sha256 = entity.sha256,
                    status = entity.status
                )
            }
        }.flowOn(Dispatchers.IO)
    }

    override fun getPagedImages(context: Context): Flow<PagingData<ImageItem>> {
        val db = AppContainer.database(context)
        return Pager(
            config = PagingConfig(
                pageSize = 30,
                prefetchDistance = 10,
                enablePlaceholders = false
            ),
            pagingSourceFactory = { db.imageDao().getPagedImages() }
        ).flow.map { pagingData ->
            pagingData.map { entity ->
                ImageItem(
                    id = entity.id,
                    uri = Uri.parse(entity.uriString),
                    name = entity.name,
                    path = entity.path,
                    size = entity.size,
                    dateModified = entity.dateModified,
                    mimeType = entity.mimeType,
                    folderName = entity.folderName,
                    folderPath = entity.folderPath,
                    sha256 = entity.sha256,
                    status = entity.status
                )
            }
        }.flowOn(Dispatchers.IO)
    }

    override fun observeDatabaseStats(context: Context): Flow<Pair<Int, Int>> {
        val db = AppContainer.database(context)
        return combine(db.imageDao().getImageCount(), db.imageDao().getHashedCount()) { total, hashed ->
            Pair(total, hashed)
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun computeMissingHashes(context: Context, onProgress: (Int, Int) -> Unit) {
        withContext(Dispatchers.IO) {
            val db = AppContainer.database(context)
            val unhashed = db.imageDao().getImagesWithoutHash()
            val total = unhashed.size

            unhashed.forEachIndexed { index, entity ->
                ScanManager.awaitIfPaused()
                val uri = Uri.parse(entity.uriString)
                val hash = Sha256Util.calculateSha256(context, uri)
                if (hash != null) {
                    db.imageDao().updateSha256(entity.id, hash)
                }
                onProgress(index + 1, total)
            }
        }
    }

    /** 仅处理本次扫描来源关联的图片，避免添加新文件夹时重算整个项目的历史图片。 */
    suspend fun computeMissingHashesForSources(context: Context, sourceIds: Set<String>, onProgress: (Int, Int) -> Unit) {
        if (sourceIds.isEmpty()) return
        withContext(Dispatchers.IO) {
            val db = AppContainer.database(context)
            val ids = db.imageSourceDao().imageIdsForSources(sourceIds.toList())
            val images = ids.chunked(500).flatMap { db.imageDao().getImagesByIds(it) }.filter { it.sha256 == null }
            val total = images.size
            images.forEachIndexed { index, entity ->
                ScanManager.awaitIfPaused()
                val hash = Sha256Util.calculateSha256(context, Uri.parse(entity.uriString))
                if (hash != null) db.imageDao().updateSha256(entity.id, hash)
                onProgress(index + 1, total)
            }
        }
    }

    /**
     * 重新扫描时保留已有记录的指纹与状态：文件大小和修改时间都没变 => 内容视为未变化，
     * 沿用 sha256 / 识别状态（避免重复计算与重复识别）；变化了则清空指纹并回到 SCANNED，
     * 提示该图片需要重新处理。
     */
    private suspend fun mergeWithExisting(db: ToImgDatabase, fresh: List<ImageEntity>): List<ImageEntity> {
        if (fresh.isEmpty()) return fresh
        val existing = HashMap<String, ImageEntity>()
        fresh.map { it.id }.chunked(500).forEach { ids ->
            db.imageDao().getImagesByIds(ids).forEach { existing[it.id] = it }
        }
        return fresh.map { n ->
            val old = existing[n.id] ?: return@map n
            if (old.size == n.size && old.dateModified == n.dateModified) {
                n.copy(sha256 = old.sha256, status = old.status, createdAt = old.createdAt)
            } else {
                // 元数据变化代表旧内容可能已被替换：清除旧指纹并回到待识别状态。
                n.copy(sha256 = null, status = "SCANNED", createdAt = old.createdAt)
            }
        }
    }

    private suspend fun invalidateChangedRecognition(
        db: ToImgDatabase,
        previous: Map<String, ImageEntity>,
        incoming: List<ImageEntity>
    ) {
        val changedIds = incoming.distinctBy { it.id }.filter { item ->
            val old = previous[item.id]
            old != null && (old.size != item.size || old.dateModified != item.dateModified)
        }.map { it.id }
        if (changedIds.isNotEmpty()) {
            db.recognitionDao().deleteByImageIds(changedIds)
            changedIds.forEach { db.imageFtsDao().syncFtsForImage(it) }
        }
    }

    override fun scanMediaStoreImages(context: Context, sourceId: String?, scanSessionId: String?): Flow<List<ImageItem>> =
        scanMediaStoreImagesWithMode(context, sourceId, scanSessionId, ScanMode.VERIFY_EXISTING)

    fun scanMediaStoreImagesWithMode(context: Context, sourceId: String?, scanSessionId: String?, scanMode: ScanMode): Flow<List<ImageItem>> = flow {
        val imageList = mutableListOf<ImageItem>()
        val entityList = mutableListOf<ImageEntity>()
        val contentResolver: ContentResolver = context.contentResolver
        val db = AppContainer.database(context)
        val checkpointedUris = ScanCheckpointStore.load(context, scanSessionId, sourceId)
        val pendingCheckpointUris = mutableListOf<String>()
        var checkpointWritesEnabled = true
        var fastExistingCount = 0
        val fastExistingIds = mutableListOf<String>()
        fun appendCheckpoint(tokens: Collection<String>) {
            if (!checkpointWritesEnabled || tokens.isEmpty()) return
            if (!ScanCheckpointStore.append(context, scanSessionId, sourceId, tokens)) {
                checkpointWritesEnabled = false
                ScanManager.reportWarning("设备存储空间不足或断点文件不可写；本次扫描继续，但中断后可能需要重新核对该来源")
            }
        }

        suspend fun persistMediaBatch(batch: List<ImageEntity>) {
            if (batch.isEmpty()) return
            db.withTransaction {
                if (!sourceId.isNullOrBlank() && db.imageSourceDao().writableSourceCount(sourceId) == 0) {
                    throw IllegalStateException("图片来源已从项目移出，停止写入：$sourceId")
                }
                val unique = batch.distinctBy { it.id }
                val previous = db.imageDao().getImagesByIds(unique.map { it.id }).associateBy { it.id }
                val merged = mergeWithExisting(db, unique)
                db.imageDao().insertImages(merged)
                invalidateChangedRecognition(db, previous, unique)
                if (!sourceId.isNullOrBlank()) {
                    val now = System.currentTimeMillis()
                    val added = com.toimg.app.core.util.ImageSourceIndexCoordinator(db.imageSourceDao())
                        .linkImagesToSource(unique.map { it.id }, sourceId, now)
                    if (!scanSessionId.isNullOrBlank()) {
                        val updated = unique.count { item ->
                            val old = previous[item.id]
                            old != null && (old.uriString != item.uriString || old.name != item.name || old.path != item.path ||
                                    old.size != item.size || old.dateModified != item.dateModified || old.mimeType != item.mimeType ||
                                    old.folderName != item.folderName || old.folderPath != item.folderPath)
                        }.coerceAtMost((unique.size - added).coerceAtLeast(0))
                        val existing = (unique.size - added - updated).coerceAtLeast(0)
                        db.imageSourceDao().recordScanBatch(scanSessionId, sourceId, unique.size, added, updated, existing)
                    }
                }
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Images.Media.RELATIVE_PATH else MediaStore.Images.Media.DATA
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

        try {
            contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val bucketColumn = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                } else {
                    cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                }

                while (cursor.moveToNext()) {
                    ScanManager.awaitIfPaused()
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "IMG_$id.jpg"
                    val size = cursor.getLong(sizeColumn)
                    val dateModified = cursor.getLong(dateColumn)
                    val mimeType = cursor.getString(mimeColumn) ?: "image/*"
                    val folderName = if (bucketColumn >= 0) cursor.getString(bucketColumn) ?: "图库" else "图库"
                    val path = if (pathColumn >= 0) cursor.getString(pathColumn) ?: "" else ""
                    ScanManager.reportDirectory(folderName)
                    if (ScanDirectoryFilter.isExcludedPath(path)) {
                        ScanManager.reportSkipped("默认排除目录（${path.substringAfterLast('/', path)}）")
                        continue
                    }
                    if (size <= 0L) {
                        ScanManager.reportSkipped("空文件或大小未知")
                        continue
                    }

                    // 必须使用与 query 相同的 collection（Android 10+ 可能是聚合 volume URI）。
                    val contentUri: Uri = ContentUris.withAppendedId(collection, id)
                    val rawUriString = contentUri.toString()
                    if (scanMode == ScanMode.FAST_INCREMENTAL && !sourceId.isNullOrBlank()) {
                        val known = db.imageDao().getImageByUri(rawUriString)
                        if (known != null) {
                            fastExistingCount++
                            fastExistingIds += known.id
                            continue
                        }
                    }
                    val reliableMetadata = ScanCheckpointStore.hasReliableMetadata(size, dateModified)
                    val checkpointToken = ScanCheckpointStore.token(rawUriString, size, dateModified)
                    if (reliableMetadata && checkpointToken in checkpointedUris) continue

                    val rawEntity = ImageEntity(
                        id = contentUri.toString(),
                        uriString = contentUri.toString(),
                        name = name,
                        path = path,
                        size = size,
                        dateModified = dateModified,
                        mimeType = mimeType,
                        folderName = folderName,
                        folderPath = path
                    )
                    val existingByUri = db.imageDao().getImageByUri(rawUriString)
                    val entity = if (existingByUri != null) {
                        rawEntity.copy(id = existingByUri.id, createdAt = existingByUri.createdAt)
                    } else ImageIdentityResolver.resolveAndPersist(context, db, rawEntity)
                    val item = ImageItem(
                        id = entity.id,
                        uri = contentUri,
                        name = name,
                        path = path,
                        size = size,
                        dateModified = dateModified,
                        mimeType = mimeType,
                        folderName = folderName,
                        folderPath = path
                    )

                    imageList.add(item)
                    entityList.add(entity)
                    if (reliableMetadata) pendingCheckpointUris.add(checkpointToken)

                    if (entityList.size >= 20) {
                        persistMediaBatch(entityList.toList())
                        appendCheckpoint(pendingCheckpointUris)
                        checkpointedUris.addAll(pendingCheckpointUris)
                        pendingCheckpointUris.clear()
                        entityList.clear()
                        emit(imageList.toList())
                    }
                }
            }

            if (entityList.isNotEmpty()) {
                persistMediaBatch(entityList.toList())
                appendCheckpoint(pendingCheckpointUris)
                checkpointedUris.addAll(pendingCheckpointUris)
                pendingCheckpointUris.clear()
                entityList.clear()
            }
            ensureSourceLinksForKnownImages(db, sourceId, fastExistingIds)
            if (fastExistingCount > 0 && !scanSessionId.isNullOrBlank() && !sourceId.isNullOrBlank()) {
                db.imageSourceDao().recordScanBatch(scanSessionId, sourceId, fastExistingCount, 0, 0, fastExistingCount)
            }
            ScanManager.reportExisting(fastExistingCount)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // 交由 ScanManager 统一记录具体来源与异常类型，避免吞掉真正的故障原因。
            throw IllegalStateException("系统媒体库读取失败：${e.javaClass.simpleName}: ${e.localizedMessage ?: "无详细信息"}", e)
        }

        emit(imageList)
    }.flowOn(Dispatchers.IO)

    override fun scanFolderUri(context: Context, treeUri: Uri, includeSubfolders: Boolean, sourceId: String?, scanSessionId: String?): Flow<List<ImageItem>> =
        scanFolderUriWithMode(context, treeUri, includeSubfolders, sourceId, scanSessionId, ScanMode.VERIFY_EXISTING)

    fun scanFolderUriWithMode(context: Context, treeUri: Uri, includeSubfolders: Boolean, sourceId: String?, scanSessionId: String?, scanMode: ScanMode): Flow<List<ImageItem>> = flow {
        val imageList = mutableListOf<ImageItem>()
        val entityList = mutableListOf<ImageEntity>()
        val db = AppContainer.database(context)
        val checkpointedUris = ScanCheckpointStore.load(context, scanSessionId, sourceId)
        val pendingCheckpointUris = mutableListOf<String>()
        var checkpointWritesEnabled = true
        var fastExistingCount = 0
        fun appendCheckpoint(tokens: Collection<String>) {
            if (!checkpointWritesEnabled || tokens.isEmpty()) return
            if (!ScanCheckpointStore.append(context, scanSessionId, sourceId, tokens)) {
                checkpointWritesEnabled = false
                ScanManager.reportWarning("设备存储空间不足或断点文件不可写；本次扫描继续，但中断后可能需要重新核对该来源")
            }
        }
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IllegalStateException("无法打开所选文件夹，可能是授权已失效")

        if (!rootDoc.exists() || !rootDoc.canRead()) {
            throw IllegalStateException("所选文件夹不可读取，可能是授权已失效")
        }
        val folderName = rootDoc.name ?: "SAF目录"
        // C 策略：只有用户把被排除目录本身选为授权根目录时，才允许本次扫描该根目录。
        val explicitlySelectedExcludedRoot = ScanDirectoryFilter.isExcludedDirectoryName(folderName)
        suspend fun persistBatch(batchEntities: List<ImageEntity>) {
            if (batchEntities.isEmpty()) return
            val uniqueBatch = batchEntities.distinctBy { it.id }
            db.withTransaction {
                if (!sourceId.isNullOrBlank() && db.imageSourceDao().writableSourceCount(sourceId) == 0) {
                    throw IllegalStateException("图片来源已从项目移出，停止写入：$sourceId")
                }
                val previous = db.imageDao().getImagesByIds(uniqueBatch.map { it.id }).associateBy { it.id }
                db.imageDao().insertImages(mergeWithExisting(db, uniqueBatch))
                invalidateChangedRecognition(db, previous, uniqueBatch)
                if (!sourceId.isNullOrBlank()) {
                    val now = System.currentTimeMillis()
                    val uniqueCount = uniqueBatch.size
                    val added = com.toimg.app.core.util.ImageSourceIndexCoordinator(db.imageSourceDao())
                        .linkImagesToSource(uniqueBatch.map { it.id }, sourceId, now)
                    if (!scanSessionId.isNullOrBlank()) {
                        val updated = uniqueBatch.count { item ->
                            val old = previous[item.id]
                            old != null && (old.uriString != item.uriString || old.name != item.name || old.path != item.path ||
                                old.size != item.size || old.dateModified != item.dateModified || old.mimeType != item.mimeType ||
                                old.folderName != item.folderName || old.folderPath != item.folderPath)
                        }.coerceAtMost((uniqueCount - added).coerceAtLeast(0))
                        val existing = (uniqueCount - added - updated).coerceAtLeast(0)
                        db.imageSourceDao().recordScanBatch(scanSessionId, sourceId, uniqueCount, added, updated, existing)
                    }
                }
            }
        }

        val fastExistingCounter = FastExistingCounter()
        traverseDocumentTree(context, rootDoc, folderName, imageList, entityList, pendingCheckpointUris, checkpointedUris, 0, mutableSetOf(), includeSubfolders, explicitlySelectedExcludedRoot, scanMode, sourceId, fastExistingCounter) { batchItems, batchEntities, batchUris ->
            persistBatch(batchEntities)
            appendCheckpoint(batchUris)
            checkpointedUris.addAll(batchUris)
            pendingCheckpointUris.removeAll(batchUris.toSet())
            emit(batchItems)
        }

        if (entityList.isNotEmpty()) {
            persistBatch(entityList.toList())
            appendCheckpoint(pendingCheckpointUris)
            checkpointedUris.addAll(pendingCheckpointUris)
            pendingCheckpointUris.clear()
            entityList.clear()
        }
        fastExistingCount = fastExistingCounter.value
        ensureSourceLinksForKnownImages(db, sourceId, fastExistingCounter.imageIds)
        ScanManager.reportExisting(fastExistingCount)
        if (fastExistingCount > 0 && !scanSessionId.isNullOrBlank() && !sourceId.isNullOrBlank()) {
            db.imageSourceDao().recordScanBatch(scanSessionId, sourceId, fastExistingCount, 0, 0, fastExistingCount)
        }
        emit(imageList)
    }.flowOn(Dispatchers.IO)

    private suspend fun traverseDocumentTree(
        context: Context,
        dir: DocumentFile,
        folderName: String,
        itemAccumulator: MutableList<ImageItem>,
        entityAccumulator: MutableList<ImageEntity>,
        pendingCheckpointUris: MutableList<String>,
        checkpointedUris: MutableSet<String>,
        depth: Int,
        visitedDirs: MutableSet<String>,
        includeSubfolders: Boolean,
        isSelectedRoot: Boolean,
        scanMode: ScanMode,
        sourceIdForCurrentTraversal: String?,
        fastExistingCounter: FastExistingCounter,
        onProgress: suspend (List<ImageItem>, List<ImageEntity>, List<String>) -> Unit
    ) {
        val db = AppContainer.database(context)
        // 防止无限递归 / 循环目录：限制深度并记录已访问目录
        if (depth > MAX_SCAN_DEPTH) {
            ScanManager.reportSkipped("目录层级过深")
            return
        }
        if (!visitedDirs.add(dir.uri.toString())) {
            ScanManager.reportSkipped("重复/循环目录")
            return
        }
        if (ScanDirectoryFilter.shouldSkipDirectory(dir.name, isSelectedRoot)) {
            ScanManager.reportSkipped("默认排除目录：${dir.name ?: folderName}")
            return
        }

        ScanManager.reportDirectory(folderName)
        val files: Array<DocumentFile> = try {
            dir.listFiles()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (depth == 0) throw IllegalStateException("来源根目录无法读取：${e.localizedMessage ?: e.javaClass.simpleName}", e)
            ScanManager.reportSkipped("目录无法读取")
            return
        }

        for (file in files) {
            ScanManager.awaitIfPaused()
            try {
                if (file.isDirectory) {
                    // 用户选择“仅当前目录”时，只读取所选目录的直接图片，不进入任何下级目录。
                    if (includeSubfolders) {
                        traverseDocumentTree(
                            context, file, file.name ?: folderName, itemAccumulator, entityAccumulator,
                            pendingCheckpointUris, checkpointedUris, depth + 1, visitedDirs, includeSubfolders, false, scanMode, sourceIdForCurrentTraversal, fastExistingCounter, onProgress
                        )
                    }
                } else if (file.isFile && (file.type?.startsWith("image/") == true || isImageExtension(file.name))) {
                    val rawFileUri = file.uri.toString()
                    if (scanMode == ScanMode.FAST_INCREMENTAL) {
                        val known = db.imageDao().getImageByUri(rawFileUri)
                        if (known != null) {
                            fastExistingCounter.value++
                            fastExistingCounter.imageIds += known.id
                            continue
                        }
                    }
                    val fileSize = file.length()
                    val fileModifiedAt = file.lastModified()
                    val reliableMetadata = ScanCheckpointStore.hasReliableMetadata(fileSize, fileModifiedAt)
                    val checkpointToken = ScanCheckpointStore.token(rawFileUri, fileSize, fileModifiedAt)
                    if (reliableMetadata && checkpointToken in checkpointedUris) continue
                    val item = ImageItem(
                        id = file.uri.toString(),
                        uri = file.uri,
                        name = file.name ?: "Image",
                        path = file.uri.path ?: "",
                        size = file.length(),
                        dateModified = file.lastModified() / 1000,
                        mimeType = file.type ?: "image/jpeg",
                        folderName = folderName,
                        folderPath = dir.uri.path ?: ""
                    )
                    val rawEntity = ImageEntity(
                        id = file.uri.toString(),
                        uriString = file.uri.toString(),
                        name = file.name ?: "Image",
                        path = file.uri.path ?: "",
                        size = file.length(),
                        dateModified = file.lastModified() / 1000,
                        mimeType = file.type ?: "image/jpeg",
                        folderName = folderName,
                        folderPath = dir.uri.path ?: ""
                    )
                    val existingByUri = db.imageDao().getImageByUri(rawFileUri)
                    val entity = if (existingByUri != null) {
                        rawEntity.copy(id = existingByUri.id, createdAt = existingByUri.createdAt)
                    } else ImageIdentityResolver.resolveAndPersist(context, db, rawEntity)
                    itemAccumulator.add(item.copy(id = entity.id, uri = file.uri))
                    entityAccumulator.add(entity)
                    if (reliableMetadata) pendingCheckpointUris.add(checkpointToken)

                    if (entityAccumulator.size >= 10) {
                        val batchUris = pendingCheckpointUris.toList()
                        onProgress(itemAccumulator.toList(), entityAccumulator.toList(), batchUris)
                        entityAccumulator.clear()
                    }
                } else if (file.isFile) {
                    ScanManager.reportSkipped("非图片文件")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                ScanManager.reportSkipped("文件读取失败")
            }
        }
    }

    private fun isImageExtension(fileName: String?): Boolean {
        if (fileName == null) return false
        val lower = fileName.lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                lower.endsWith(".png") || lower.endsWith(".webp") ||
                lower.endsWith(".heic") || lower.endsWith(".bmp")
    }

    override fun getSavedFolders(context: Context): List<FolderItem> {
        val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
        val rawSet = prefs.getStringSet(keyFolderList, emptySet()) ?: emptySet()
        val persistedPermissions = context.contentResolver.persistedUriPermissions

        return rawSet.mapNotNull { entry ->
            val parts = entry.split("|", limit = 2)
            if (parts.size == 2) {
                val uriString = parts[0]
                val name = parts[1]
                val uri = Uri.parse(uriString)
                val hasGrant = persistedPermissions.any { it.uri == uri && it.isReadPermission }
                val isAuth = hasGrant && runCatching {
                    val root = DocumentFile.fromTreeUri(context, uri)
                    root != null && root.exists() && root.canRead()
                }.getOrDefault(false)
                FolderItem(
                    id = uriString,
                    name = name,
                    uri = uri,
                    isAuthorized = isAuth
                )
            } else null
        }
    }

    override fun saveAuthorizedFolder(context: Context, uri: Uri, folderName: String) {
        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val persisted = runCatching {
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            true
        }.getOrElse {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                true
            }.getOrDefault(false)
        }
        if (!persisted) return

        val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
        val currentSet = prefs.getStringSet(keyFolderList, emptySet())?.toMutableSet() ?: mutableSetOf()
        val preservedName = currentSet.firstOrNull { it.substringBefore('|') == uri.toString() }
            ?.substringAfter('|')?.takeIf { it.isNotBlank() } ?: folderName
        currentSet.removeAll { it.substringBefore('|') == uri.toString() }
        currentSet.add("${uri}|$preservedName")
        prefs.edit().putStringSet(keyFolderList, currentSet).apply()

        // 新增来源时立即登记到统一来源表；即使用户尚未开始扫描，管理页也能看到 UNSCANNED。
        val db = AppContainer.database(context)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val existing = db.imageSourceDao().getByUri(uri.toString()) ?: db.imageSourceDao().getById(uri.toString())
            if (existing == null) {
                db.imageSourceDao().insert(ImageSourceEntity(
                    id = uri.toString(), displayName = preservedName, uriString = uri.toString(),
                    treeUriString = uri.toString(), isAuthorized = true,
                    lifecycleStatus = ImageSourceEntity.UNSCANNED
                ))
            } else {
                db.imageSourceDao().update(existing.copy(isAuthorized = true, errorMessage = null,
                    lifecycleStatus = if (existing.lifecycleStatus == ImageSourceEntity.AUTH_EXPIRED) ImageSourceEntity.UNSCANNED else existing.lifecycleStatus))
            }
        }
    }

    override fun observeRecognitions(context: Context): Flow<List<RecognitionResult>> {
        val db = AppContainer.database(context)
        return db.recognitionDao().getAllRecognitions().map { entities ->
            entities.map { entity ->
                val kwList = entity.keywords.split(",", "，")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                RecognitionResult(
                    imageId = entity.imageId,
                    keywords = kwList,
                    description = entity.description,
                    modelName = entity.modelName,
                    isSuccess = entity.status == "SUCCESS",
                    ocrText = entity.ocrText,
                    isSensitive = entity.isSensitive,
                    errorMessage = entity.errorMessage,
                    recognizedAt = entity.recognizedAt
                )
            }
        }.flowOn(Dispatchers.IO)
    }

    override fun observeSuccessfulRecognitions(context: Context): Flow<List<RecognitionResult>> {
        val db = AppContainer.database(context)
        return db.recognitionDao().getSuccessfulRecognitions().map { entities ->
            entities.map { entity ->
                val kwList = entity.keywords.split(",", "，")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                RecognitionResult(
                    imageId = entity.imageId,
                    keywords = kwList,
                    description = entity.description,
                    modelName = entity.modelName,
                    isSuccess = true,
                    ocrText = entity.ocrText,
                    isSensitive = entity.isSensitive,
                    errorMessage = null,
                    recognizedAt = entity.recognizedAt
                )
            }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun saveRecognitions(context: Context, results: List<RecognitionResult>) {
        withContext(Dispatchers.IO) {
            val db = AppContainer.database(context)
            db.withTransaction {
                // Protect existing cloud success rows and keep result/status/FTS in one commit.
                // A source may have been removed while a cloud request was in flight. Never
                // recreate orphan recognition rows after its exclusive image index was deleted.
                val safeResults = results.filter { res ->
                    if (db.imageDao().getImageById(res.imageId) == null) return@filter false
                    val existing = db.recognitionDao().getRecognitionByImageId(res.imageId)
                    val existingIsCloudSuccess = existing?.status == "SUCCESS" && existing.modelName != "MLKit-OnDevice"
                    val incomingIsOffline = res.modelName == "MLKit-OnDevice"
                    !(existingIsCloudSuccess && (!res.isSuccess || incomingIsOffline))
                }
                val entities = safeResults.map { res ->
                    RecognitionEntity(
                        imageId = res.imageId,
                        keywords = res.keywords.joinToString(", "),
                        description = res.description,
                        modelName = res.modelName,
                        status = if (res.isSuccess) "SUCCESS" else "FAILED",
                        ocrText = res.ocrText,
                        isSensitive = res.isSensitive,
                        errorMessage = res.errorMessage,
                        recognizedAt = res.recognizedAt
                    )
                }
                if (entities.isNotEmpty()) db.recognitionDao().insertRecognitions(entities)

                // These derived flags and search rows must never lag behind the recognition row.
                safeResults.forEach { res ->
                    val status = if (res.isSuccess) "RECOGNIZED" else "FAILED"
                    db.imageDao().updateStatus(res.imageId, status)
                    db.imageFtsDao().syncFtsForImage(res.imageId)
                }
            }
        }
    }

    override suspend fun getRecognition(context: Context, imageId: String): RecognitionResult? {
        return withContext(Dispatchers.IO) {
            val db = AppContainer.database(context)
            val entity = db.recognitionDao().getRecognitionByImageId(imageId) ?: return@withContext null
            val kwList = entity.keywords.split(",", "，")
                .map { it.trim() }
                .filter { it.isNotBlank() }
            RecognitionResult(
                imageId = entity.imageId,
                keywords = kwList,
                description = entity.description,
                modelName = entity.modelName,
                isSuccess = entity.status == "SUCCESS",
                ocrText = entity.ocrText,
                isSensitive = entity.isSensitive,
                errorMessage = entity.errorMessage,
                recognizedAt = entity.recognizedAt
            )
        }
    }

    override suspend fun recordMoveSuccess(
        context: Context,
        imageId: String,
        newUriString: String,
        newName: String,
        newFolderName: String
    ) {
        withContext(Dispatchers.IO) {
            val db = AppContainer.database(context)
            db.withTransaction {
                db.imageDao().updateImageLocation(
                    id = imageId,
                    uriString = newUriString,
                    name = newName,
                    path = newUriString,
                    folderName = newFolderName,
                    folderPath = newFolderName,
                    status = "MOVED"
                )
                db.imageFtsDao().syncFtsForImage(imageId)
            }
        }
    }

    override suspend fun recordMoveHistory(
        context: Context,
        results: List<com.toimg.app.domain.model.MoveItemResult>,
        targetDirName: String
    ) {
        withContext(Dispatchers.IO) {
            val db = AppContainer.database(context)
            val historyEntities = results.map { item ->
                com.toimg.app.data.local.entity.MoveHistoryEntity(
                    imageId = item.imageId,
                    fileName = item.originalName,
                    sourcePath = item.sourceUri.toString(),
                    targetPath = item.targetUri?.toString() ?: "目标: $targetDirName",
                    status = item.status.name,
                    sha256 = item.targetHash ?: item.sourceHash,
                    executionMethod = item.executionMethod,
                    errorMessage = item.errorMessage,
                    timestamp = item.timestamp
                )
            }
            db.moveHistoryDao().insertHistory(historyEntities)
        }
    }

    override fun observeMoveHistory(context: Context): Flow<List<com.toimg.app.data.local.entity.MoveHistoryEntity>> {
        val db = AppContainer.database(context)
        return db.moveHistoryDao().getAllHistory().flowOn(Dispatchers.IO)
    }

    override suspend fun rollbackMove(context: Context, historyId: Long): Result<String> {
        return withContext(Dispatchers.IO) {
            val db = AppContainer.database(context)
            val historyItem = db.moveHistoryDao().getHistoryById(historyId)
                ?: return@withContext Result.failure(IllegalArgumentException("未找到移动历史记录: ID=$historyId"))
            FileMoveUtil.executeRollback(context, historyItem)
        }
    }

    override suspend fun updateKeywords(context: Context, imageId: String, newKeywords: List<String>) {
        withContext(Dispatchers.IO) {
            val db = AppContainer.database(context)
            val kwString = newKeywords.map { it.trim() }.filter { it.isNotBlank() }.distinct().joinToString(", ")
            db.recognitionDao().updateKeywords(imageId, kwString)
            try {
                db.imageFtsDao().syncFtsForImage(imageId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    override suspend fun mergeCategory(context: Context, oldCategory: String, targetCategory: String) {
        withContext(Dispatchers.IO) {
            val db = AppContainer.database(context)
            val recognitions = db.recognitionDao().getRecognitionsWithKeyword(oldCategory)
            recognitions.forEach { rec ->
                val keywords = rec.keywords.split(",", "，")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toMutableList()

                if (keywords.contains(oldCategory)) {
                    keywords.remove(oldCategory)
                    if (!keywords.contains(targetCategory)) {
                        keywords.add(targetCategory)
                    }
                    val updated = keywords.joinToString(", ")
                    db.recognitionDao().updateKeywords(rec.imageId, updated)
                }
            }
        }
    }

    override fun observeSmartRules(context: Context): Flow<List<SmartRuleEntity>> {
        val db = AppContainer.database(context)
        return db.smartRuleDao().getAllRules().flowOn(Dispatchers.IO)
    }

    override suspend fun saveSmartRule(context: Context, rule: SmartRuleEntity): Long {
        return withContext(Dispatchers.IO) {
            val db = AppContainer.database(context)
            db.smartRuleDao().insertRule(rule)
        }
    }

    override suspend fun deleteSmartRule(context: Context, id: Long) {
        withContext(Dispatchers.IO) {
            val db = AppContainer.database(context)
            db.smartRuleDao().deleteRuleById(id)
        }
    }

    override suspend fun updateRuleStatus(context: Context, id: Long, isEnabled: Boolean) {
        withContext(Dispatchers.IO) {
            val db = AppContainer.database(context)
            db.smartRuleDao().updateRuleStatus(id, isEnabled)
        }
    }

    override suspend fun seedPresetSmartRulesIfEmpty(context: Context) {
        withContext(Dispatchers.IO) {
            val db = AppContainer.database(context)
            val currentRules = db.smartRuleDao().getAllRules().first()
            if (currentRules.isEmpty()) {
                val presets = listOf(
                    SmartRuleEntity(
                        name = "财务报销归档",
                        keywordTrigger = "发票, 收据, 账单, 报销, 票据",
                        targetFolderName = "Pictures/财务报销",
                        targetTreeUri = "",
                        isEnabled = true
                    ),
                    SmartRuleEntity(
                        name = "私密证件标记与保护",
                        keywordTrigger = "身份证, 银行卡, 驾驶证, 护照, 户口本, 证件",
                        targetFolderName = "Pictures/证件专存",
                        targetTreeUri = "",
                        isEnabled = true
                    ),
                    SmartRuleEntity(
                        name = "旅行风光合辑",
                        keywordTrigger = "风景, 山川, 海洋, 日落, 建筑, 旅行, 景区",
                        targetFolderName = "Pictures/旅行风光",
                        targetTreeUri = "",
                        isEnabled = true
                    ),
                    SmartRuleEntity(
                        name = "美食日常记录",
                        keywordTrigger = "美食, 菜肴, 咖啡, 甜点, 聚餐, 餐厅",
                        targetFolderName = "Pictures/美食相册",
                        targetTreeUri = "",
                        isEnabled = true
                    )
                )
                db.smartRuleDao().insertRules(presets)
            }
        }
    }

    override fun searchImagesWithFilters(
        context: Context,
        criteria: com.toimg.app.domain.model.SearchFilterCriteria
    ): Flow<List<ImageItem>> {
        val db = AppContainer.database(context)
        val rawQuery = criteria.query.trim()
        val useFts = if (rawQuery.isNotBlank()) 1 else 0

        // Build FTS query supporting tokenized prefix queries e.g. token*
        val ftsQuery = if (rawQuery.isNotBlank()) {
            val tokens = rawQuery.split(Regex("[\\s,，]+")).filter { it.isNotBlank() }
            if (tokens.size > 1) {
                tokens.joinToString(" AND ") { "$it*" }
            } else {
                "$rawQuery*"
            }
        } else {
            ""
        }

        val (startDate, endDate) = criteria.dateRange.getStartAndEndTimestamp()
        val (minSize, maxSize) = criteria.sizeRange.getMinAndMaxBytes()
        val folderName = criteria.folderName.orEmpty()
        val status = criteria.status.orEmpty()
        val onlySensitive = if (criteria.onlySensitive) 1 else 0

        return db.imageFtsDao().searchWithFilters(
            useFts = useFts,
            ftsQuery = ftsQuery,
            rawKeyword = rawQuery,
            folderName = folderName,
            startDate = startDate,
            endDate = endDate,
            minSize = minSize,
            maxSize = maxSize,
            status = status,
            onlySensitive = onlySensitive
        ).map { entities ->
            entities.map { entity ->
                ImageItem(
                    id = entity.id,
                    uri = Uri.parse(entity.uriString),
                    name = entity.name,
                    path = entity.path,
                    size = entity.size,
                    dateModified = entity.dateModified,
                    mimeType = entity.mimeType,
                    folderName = entity.folderName,
                    folderPath = entity.folderPath,
                    sha256 = entity.sha256,
                    status = entity.status
                )
            }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun rebuildSearchIndex(context: Context) {
        withContext(Dispatchers.IO) {
            val db = AppContainer.database(context)
            db.imageFtsDao().rebuildAllFts()
        }
    }
}


