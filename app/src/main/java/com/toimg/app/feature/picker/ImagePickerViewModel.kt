package com.toimg.app.feature.picker

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.toimg.app.data.local.entity.ImageSourceLinkEntity
import com.toimg.app.data.local.entity.ImageSourceEntity
import com.toimg.app.data.local.entity.ImageEntity
import com.toimg.app.data.local.entity.ScanSessionEntity
import com.toimg.app.data.local.entity.SourceScanStatEntity
import com.toimg.app.core.di.AppContainer
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.room.withTransaction
import androidx.lifecycle.viewModelScope
import com.toimg.app.data.repository.LocalImageRepositoryImpl
import com.toimg.app.domain.model.FolderItem
import com.toimg.app.core.util.ScanManager
import com.toimg.app.core.util.ImageIdentityResolver
import com.toimg.app.core.util.SelectionHolder
import com.toimg.app.domain.model.ImageItem
import com.toimg.app.domain.model.RecognitionResult
import com.toimg.app.domain.model.ScanStatus
import com.toimg.app.domain.repository.ImageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

private fun ImageItem.stableFolderKey(): String {
    val stored = folderPath.trim()
    return when {
        stored.isBlank() -> path.substringBeforeLast('/', folderName).ifBlank { folderName }
        stored == path -> stored.substringBeforeLast('/', folderName).ifBlank { folderName }
        else -> stored
    }
}

enum class ImageSortOption(val label: String) {
    DATE_DESC("时间：新到旧"),
    DATE_ASC("时间：旧到新"),
    NAME_ASC("名称：A→Z"),
    NAME_DESC("名称：Z→A"),
    SIZE_DESC("大小：大到小"),
    SIZE_ASC("大小：小到大")
}

enum class ImageStatusFilter(val label: String) {
    ALL("全部状态"),
    UNRECOGNIZED("未识别"),
    RECOGNIZED("已识别"),
    FAILED("识别失败"),
    UNAVAILABLE("文件不可用")
}

data class ImagePickerUiState(
    val images: List<ImageItem> = emptyList(),
    val folders: List<FolderItem> = emptyList(),
    val selectedFolder: String? = null,
    val selectedImageIds: Set<String> = emptySet(),
    val scanStatus: ScanStatus = ScanStatus.Idle,
    val permissionGranted: Boolean = false,
    val isScanning: Boolean = false,
    val totalDbCount: Int = 0,
    val hashedDbCount: Int = 0,
    val isHashing: Boolean = false,
    val hashProgress: String = "",
    val filterDuplicatesOnly: Boolean = false,
    val sortOption: ImageSortOption = ImageSortOption.DATE_DESC,
    val statusFilter: ImageStatusFilter = ImageStatusFilter.ALL,
    val recognitions: Map<String, RecognitionResult> = emptyMap()
) {
    private fun statusOf(item: ImageItem): ImageStatusFilter {
        if (item.status == "UNAVAILABLE") return ImageStatusFilter.UNAVAILABLE
        val rec = recognitions[item.id]
        return when {
            rec == null -> ImageStatusFilter.UNRECOGNIZED
            rec.isSuccess -> ImageStatusFilter.RECOGNIZED
            else -> ImageStatusFilter.FAILED
        }
    }

    fun countOf(filter: ImageStatusFilter): Int =
        if (filter == ImageStatusFilter.ALL) images.size else images.count { statusOf(it) == filter }

    fun isUnavailable(item: ImageItem): Boolean = item.status == "UNAVAILABLE"

    val duplicateImageIds: Set<String>
        get() = images.filter { !it.sha256.isNullOrBlank() }
            .groupBy { it.sha256 }
            .filter { it.value.size > 1 }
            .flatMap { entry -> entry.value.map { it.id } }
            .toSet()

    val filteredImages: List<ImageItem>
        get() {
            var list = if (selectedFolder == null) images else images.filter { it.stableFolderKey() == selectedFolder }
            if (filterDuplicatesOnly) {
                list = list.filter { duplicateImageIds.contains(it.id) }
            }
            if (statusFilter != ImageStatusFilter.ALL) {
                list = list.filter { statusOf(it) == statusFilter }
            }
            return when (sortOption) {
                ImageSortOption.DATE_DESC -> list.sortedByDescending { it.dateModified }
                ImageSortOption.DATE_ASC -> list.sortedBy { it.dateModified }
                ImageSortOption.NAME_ASC -> list.sortedBy { it.name.lowercase() }
                ImageSortOption.NAME_DESC -> list.sortedByDescending { it.name.lowercase() }
                ImageSortOption.SIZE_DESC -> list.sortedByDescending { it.size }
                ImageSortOption.SIZE_ASC -> list.sortedBy { it.size }
            }
        }

    val selectedCount: Int
        get() = selectedImageIds.size
}

class ImagePickerViewModel(
    private val repository: ImageRepository = LocalImageRepositoryImpl()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImagePickerUiState())
    val uiState: StateFlow<ImagePickerUiState> = _uiState.asStateFlow()

    private var observing = false

    fun initDbObserver(context: Context) {
        if (observing) return
        observing = true
        viewModelScope.launch {
            repository.observeRecognitions(context).collect { list ->
                _uiState.update { it.copy(recognitions = list.associateBy { r -> r.imageId }) }
            }
        }
        viewModelScope.launch {
            repository.observeImagesFromDb(context).collect { dbImages ->
                val folders = extractFolders(dbImages, repository.getSavedFolders(context))
                _uiState.update { state ->
                    state.copy(
                        images = dbImages,
                        folders = folders
                    )
                }
            }
        }

        viewModelScope.launch {
            repository.observeDatabaseStats(context).collect { (total, hashed) ->
                _uiState.update { state ->
                    state.copy(
                        totalDbCount = total,
                        hashedDbCount = hashed
                    )
                }
            }
        }
    }

    fun updatePermissionStatus(granted: Boolean, context: Context) {
        _uiState.update { it.copy(permissionGranted = granted) }
        initDbObserver(context)
    }

    fun startFullScan(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, scanStatus = ScanStatus.Scanning(0, "媒体库")) }
            ensureMediaStoreSource(context)
            repository.scanMediaStoreImages(context, ImageSourceEntity.MEDIASTORE_SOURCE_ID).collect { list ->
                val folders = extractFolders(list, repository.getSavedFolders(context))
                _uiState.update { state ->
                    state.copy(
                        images = list,
                        folders = folders,
                        isScanning = false,
                        scanStatus = ScanStatus.Success(list.size, folders.size)
                    )
                }
            }
            val sourceDao = AppContainer.database(context).imageSourceDao()
            sourceDao.getById(ImageSourceEntity.MEDIASTORE_SOURCE_ID)?.let { source ->
                sourceDao.update(source.copy(
                    imageCount = sourceDao.countLinksForSource(source.id),
                    lifecycleStatus = ImageSourceEntity.COMPLETED,
                    lastScannedAt = System.currentTimeMillis(), errorMessage = null
                ))
            }
            startHashComputation(context)
        }
    }

    fun startHashComputation(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isHashing = true, hashProgress = "计算图片 Hash 指纹中...") }
            repository.computeMissingHashes(context) { current, total ->
                _uiState.update { it.copy(hashProgress = "已处理 $current / $total 张图片 Hash") }
            }
            _uiState.update { it.copy(isHashing = false, hashProgress = "Hash 计算完成") }
        }
    }

    fun addFolderUri(context: Context, treeUri: Uri) {
        viewModelScope.launch {
            val doc = DocumentFile.fromTreeUri(context, treeUri)
            val name = doc?.name ?: "SAF目录"
            repository.saveAuthorizedFolder(context, treeUri, name)

            _uiState.update { it.copy(isScanning = true, scanStatus = ScanStatus.Scanning(0, name)) }

            repository.scanFolderUri(context, treeUri).collect { scannedList ->
                val updatedImages = (_uiState.value.images + scannedList).distinctBy { it.id }
                val folders = extractFolders(updatedImages, repository.getSavedFolders(context))
                _uiState.update { state ->
                    state.copy(
                        images = updatedImages,
                        folders = folders,
                        isScanning = false,
                        scanStatus = ScanStatus.Success(updatedImages.size, folders.size)
                    )
                }
            }
            startHashComputation(context)
        }
    }

    fun addPickedUris(context: Context, uris: List<Uri>) {
        viewModelScope.launch {
            val db = AppContainer.database(context)
            val now = System.currentTimeMillis()
            val sessionId = UUID.randomUUID().toString()
            val source = db.withTransaction {
                val sourceDao = db.imageSourceDao()
                val current = sourceDao.getById(ImageSourceEntity.PICKER_SOURCE_ID)
                val ensured = if (current == null) ImageSourceEntity(
                    id = ImageSourceEntity.PICKER_SOURCE_ID, displayName = "图片选择器选取",
                    uriString = ImageSourceEntity.PICKER_URI, treeUriString = ImageSourceEntity.PICKER_URI,
                    isAuthorized = true, lifecycleStatus = ImageSourceEntity.SCANNING, addedAt = now
                ).also { sourceDao.insert(it) } else current.copy(lifecycleStatus = ImageSourceEntity.SCANNING)
                if (current != null) sourceDao.update(ensured)
                db.scanSessionDao().upsert(ScanSessionEntity(
                    id = sessionId, status = ScanSessionEntity.RUNNING, includeMediaStore = false,
                    folderUris = "", includeSubfolders = false, startedAt = now, updatedAt = now,
                    selectedSourceIds = ensured.id, statusText = "正在登记图片选择器选取"
                ))
                sourceDao.upsertScanStat(SourceScanStatEntity(
                    scanSessionId = sessionId, sourceId = ensured.id, status = "RUNNING", startedAt = now
                ))
                ensured
            }
            val entities = uris.distinctBy { it.toString() }.mapIndexedNotNull { index, uri ->
                runCatching {
                    runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    val existing = db.imageDao().getImageByUri(uri.toString())
                    if (existing != null) existing else {
                        var name = "Picked_Image_${now}_$index"
                        var size = 0L
                        var modified = 0L
                        var mime = runCatching { context.contentResolver.getType(uri) }.getOrNull() ?: "image/*"
                        // 部分 Photo Picker provider 只开放有限列；元数据读取失败不能丢弃用户选中的 URI。
                        runCatching {
                            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                                if (cursor.moveToFirst()) {
                                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                                    if (nameIndex >= 0) name = cursor.getString(nameIndex) ?: name
                                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                                    val dateIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATE_MODIFIED)
                                    if (dateIndex >= 0 && !cursor.isNull(dateIndex)) modified = cursor.getLong(dateIndex)
                                    val mimeIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.MIME_TYPE)
                                    if (mimeIndex >= 0 && !cursor.isNull(mimeIndex)) mime = cursor.getString(mimeIndex) ?: mime
                                }
                            }
                        }
                        val picked = ImageEntity(
                            id = uri.toString(), uriString = uri.toString(), name = name,
                            path = uri.path ?: "", size = size, dateModified = modified, mimeType = mime,
                            folderName = "图片选择器", folderPath = "PhotoPicker"
                        )
                        ImageIdentityResolver.resolveAndPersist(context, db, picked)
                    }
                }.getOrNull()
            }
            val persisted = runCatching {
                db.withTransaction {
                    val sourceDao = db.imageSourceDao()
                    val currentSource = sourceDao.getById(source.id)
                        ?: throw IllegalStateException("图片选择器来源已从项目移出")
                    val uniqueEntities = entities.distinctBy { it.id }
                    if (uniqueEntities.isNotEmpty()) db.imageDao().insertImages(uniqueEntities)
                    val links = uniqueEntities.map { ImageSourceLinkEntity(it.id, currentSource.id, now, now) }
                    val inserted = if (links.isNotEmpty()) sourceDao.addImageSourceLinks(links) else emptyList()
                    sourceDao.update(currentSource.copy(
                        imageCount = sourceDao.countLinksForSource(currentSource.id),
                        lifecycleStatus = ImageSourceEntity.COMPLETED, lastScannedAt = now
                    ))
                    val added = inserted.count { it != -1L }
                    sourceDao.upsertScanStat(SourceScanStatEntity(
                        scanSessionId = sessionId, sourceId = currentSource.id, status = "COMPLETED",
                        discoveredCount = uniqueEntities.size, addedCount = added,
                        existingCount = (uniqueEntities.size - added).coerceAtLeast(0),
                        startedAt = now, finishedAt = System.currentTimeMillis()
                    ))
                    db.scanSessionDao().getById(sessionId)?.let { session ->
                        db.scanSessionDao().upsert(session.copy(
                            status = ScanSessionEntity.SUCCESS, updatedAt = System.currentTimeMillis(),
                            finishedAt = System.currentTimeMillis(), discoveredCount = uniqueEntities.size,
                            sourcesDone = 1, sourcesTotal = 1, statusText = "图片选择器选取已登记"
                        ))
                    }
                }
            }.isSuccess
            if (!persisted) {
                db.withTransaction {
                    db.imageSourceDao().cancelActiveStatsForSession(sessionId, System.currentTimeMillis(), "图片选择器登记失败")
                    db.imageSourceDao().markSessionSourcesPartial(sessionId, "图片选择器登记失败")
                    db.scanSessionDao().getById(sessionId)?.let { session ->
                        db.scanSessionDao().upsert(session.copy(
                            status = ScanSessionEntity.FAILED, updatedAt = System.currentTimeMillis(),
                            finishedAt = System.currentTimeMillis(), statusText = "图片选择器登记失败"
                        ))
                    }
                }
                _uiState.update { it.copy(isScanning = false, scanStatus = ScanStatus.Error("图片来源已变化，未能保存本次选择；请重新选择图片")) }
                return@launch
            }
            if (entities.isNotEmpty()) {
                val newItems = entities.map { entity ->
                    ImageItem(
                        id = entity.id, uri = Uri.parse(entity.uriString), name = entity.name,
                        path = entity.path, size = entity.size, dateModified = entity.dateModified,
                        mimeType = entity.mimeType, folderName = entity.folderName, folderPath = entity.folderPath
                    )
                }
                val merged = (_uiState.value.images + newItems).distinctBy { it.id }
                val selected = _uiState.value.selectedImageIds + newItems.map { it.id }
                val folders = extractFolders(merged, repository.getSavedFolders(context))
                _uiState.update { it.copy(images = merged, selectedImageIds = selected, folders = folders, scanStatus = ScanStatus.Success(merged.size, folders.size)) }
            }
        }
    }

    private suspend fun ensureMediaStoreSource(context: Context) {
        val dao = AppContainer.database(context).imageSourceDao()
        if (dao.getById(ImageSourceEntity.MEDIASTORE_SOURCE_ID) == null) {
            dao.insert(ImageSourceEntity(
                id = ImageSourceEntity.MEDIASTORE_SOURCE_ID, displayName = "系统媒体库",
                uriString = ImageSourceEntity.MEDIASTORE_URI, treeUriString = ImageSourceEntity.MEDIASTORE_URI,
                isAuthorized = true, lifecycleStatus = ImageSourceEntity.UNSCANNED
            ))
        }
    }

    fun toggleSelection(imageId: String) {
        _uiState.update { state ->
            val set = state.selectedImageIds.toMutableSet()
            if (set.contains(imageId)) {
                set.remove(imageId)
            } else {
                set.add(imageId)
            }
            state.copy(selectedImageIds = set)
        }
    }

    fun toggleFilterDuplicatesOnly() {
        _uiState.update { it.copy(filterDuplicatesOnly = !it.filterDuplicatesOnly) }
    }

    fun selectAll() {
        _uiState.update { state ->
            val allIds = state.filteredImages.map { it.id }.toSet()
            state.copy(selectedImageIds = allIds)
        }
    }

    /** 反选：只在当前筛选结果内反转；筛选范围之外已选中的图片保持不变。 */
    fun invertSelection() {
        _uiState.update { state ->
            val visible = state.filteredImages.map { it.id }.toSet()
            val outside = state.selectedImageIds - visible
            val flipped = visible - state.selectedImageIds
            state.copy(selectedImageIds = outside + flipped)
        }
    }

    fun setSortOption(option: ImageSortOption) {
        _uiState.update { it.copy(sortOption = option) }
    }

    fun setStatusFilter(filter: ImageStatusFilter) {
        _uiState.update { it.copy(statusFilter = filter) }
    }

    /** 手动重新检查所有图片文件是否仍可读取。 */
    fun recheckAvailability(context: Context) {
        if (ScanManager.isRunning()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isHashing = true, hashProgress = "正在检查文件可用性…") }
            val n = ScanManager.checkAvailability(context.applicationContext) { cur, total ->
                _uiState.update { it.copy(hashProgress = "检查文件可用性：$cur / $total") }
            }
            _uiState.update { it.copy(isHashing = false, hashProgress = "检查完成：$n 张文件不可用") }
        }
    }

    /** 把当前选择交给识别确认页（不可用的图片会在确认页被排除）。 */
    fun commitSelectionForRecognition() {
        SelectionHolder.set(_uiState.value.selectedImageIds)
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedImageIds = emptySet()) }
    }

    fun setFilterFolder(folderPath: String?) {
        _uiState.update { it.copy(selectedFolder = folderPath) }
    }

    /** Folder-level selection: adds all visible images from this folder without clearing selections elsewhere. */
    fun selectFolder(folderPath: String) {
        _uiState.update { state ->
            val ids = state.images.filter { it.stableFolderKey() == folderPath }.map { it.id }.toSet()
            state.copy(selectedImageIds = state.selectedImageIds + ids)
        }
    }

    private fun loadSavedFolders(context: Context) {
        val saved = repository.getSavedFolders(context)
        _uiState.update { it.copy(folders = saved) }
    }

    private fun extractFolders(images: List<ImageItem>, savedFolders: List<FolderItem>): List<FolderItem> {
        // Folder identity is the full path/URI-like path, never the display name alone.
        val autoFolders = images.groupBy { it.stableFolderKey() }.map { (stablePath, items) ->
            val displayName = stablePath.trimEnd('/').substringAfterLast('/').ifBlank { items.first().folderName }
            FolderItem(
                id = stablePath,
                name = displayName,
                path = stablePath,
                imageCount = items.size,
                isAuthorized = true
            )
        }
        return (autoFolders + savedFolders).distinctBy { it.path.ifBlank { it.id } }.sortedBy { it.name.lowercase() }
    }
}
