package com.toimg.app.feature.duplicates

import com.toimg.app.core.di.AppContainer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.toimg.app.core.util.DuplicateGroup
import com.toimg.app.core.util.DuplicateType
import com.toimg.app.core.util.FileMoveUtil
import com.toimg.app.core.util.PerceptualHashUtil
import com.toimg.app.data.repository.LocalImageRepositoryImpl
import com.toimg.app.domain.model.ImageItem
import com.toimg.app.domain.repository.ImageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DuplicateFilterTab {
    ALL,
    EXACT_SHA256,
    VISUALLY_SIMILAR
}

data class DuplicatesUiState(
    val isLoading: Boolean = false,
    val progressMessage: String = "",
    val groups: List<DuplicateGroup> = emptyList(),
    val currentTab: DuplicateFilterTab = DuplicateFilterTab.ALL,
    val selectedDuplicateIds: Set<String> = emptySet(),
    val isProcessingAction: Boolean = false,
    val actionResultMessage: String? = null
) {
    val filteredGroups: List<DuplicateGroup>
        get() = when (currentTab) {
            DuplicateFilterTab.ALL -> groups
            DuplicateFilterTab.EXACT_SHA256 -> groups.filter { it.type == DuplicateType.EXACT_SHA256 }
            DuplicateFilterTab.VISUALLY_SIMILAR -> groups.filter { it.type == DuplicateType.VISUALLY_SIMILAR }
        }

    val totalPotentialSavings: Long
        get() = groups.sumOf { it.potentialSavingsBytes }

    val selectedSavingsBytes: Long
        get() {
            val allDuplicates = groups.flatMap { it.duplicateImages }
            return allDuplicates.filter { selectedDuplicateIds.contains(it.id) }.sumOf { it.size }
        }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }
}

class DuplicatesViewModel(
    private val repository: ImageRepository = LocalImageRepositoryImpl()
) : ViewModel() {

    private val _uiState = MutableStateFlow(DuplicatesUiState())
    val uiState: StateFlow<DuplicatesUiState> = _uiState.asStateFlow()

    private val dHashCache = mutableMapOf<String, String>()

    fun loadDuplicates(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, progressMessage = "正在检索相册并比对图片指纹...") }

            val images: List<ImageItem> = withContext(Dispatchers.IO) {
                val db = AppContainer.database(context)
                db.imageDao().getAllImagesOnce().map { entity ->
                    ImageItem(
                        id = entity.id,
                        uri = android.net.Uri.parse(entity.uriString),
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
            }

            if (images.isEmpty()) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        groups = emptyList(),
                        progressMessage = "暂无图片数据"
                    )
                }
                return@launch
            }

            // 针对尚未计算 dHash 的图片计算感知哈希 (按需限制最大数量保证流畅)
            val dHashes = withContext(Dispatchers.IO) {
                val map = HashMap(dHashCache)
                val needHash = images.filter { !map.containsKey(it.id) }.take(300)
                needHash.forEachIndexed { index, img ->
                    val hash = PerceptualHashUtil.calculateDHashFromUri(context, img.uri)
                    if (hash != null) {
                        map[img.id] = hash
                        dHashCache[img.id] = hash
                    }
                }
                map
            }

            val groups = withContext(Dispatchers.Default) {
                PerceptualHashUtil.groupDuplicatesAndSimilar(images, dHashes)
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    groups = groups,
                    progressMessage = ""
                )
            }
        }
    }

    fun setTab(tab: DuplicateFilterTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun toggleDuplicateSelection(imageId: String) {
        _uiState.update { state ->
            val set = state.selectedDuplicateIds.toMutableSet()
            if (set.contains(imageId)) {
                set.remove(imageId)
            } else {
                set.add(imageId)
            }
            state.copy(selectedDuplicateIds = set)
        }
    }

    /**
     * 智能优选推荐：勾选所有组中的副本地图，保留每组的主图片 (Primary)
     */
    fun selectAllRecommendedDuplicates() {
        _uiState.update { state ->
            val allRecommended = state.filteredGroups.flatMap { group ->
                group.duplicateImages.map { it.id }
            }.toSet()
            state.copy(selectedDuplicateIds = allRecommended)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedDuplicateIds = emptySet()) }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(actionResultMessage = null) }
    }
}
