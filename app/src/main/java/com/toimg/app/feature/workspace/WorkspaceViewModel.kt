package com.toimg.app.feature.workspace

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.toimg.app.core.util.BackgroundBatchManager
import com.toimg.app.core.util.SmartProposalEngine
import com.toimg.app.core.util.SelectionHolder
import com.toimg.app.data.repository.AiConfigRepository
import com.toimg.app.data.repository.LocalImageRepositoryImpl
import com.toimg.app.domain.model.AiConfig
import com.toimg.app.domain.model.BatchProgressState
import com.toimg.app.domain.model.ImageItem
import com.toimg.app.domain.model.RecognitionResult
import com.toimg.app.domain.repository.ImageRepository
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class WorkspaceTab {
    UNRECOGNIZED,
    RECOGNIZED,
    ALL
}

data class WorkspaceUiState(
    val images: List<ImageItem> = emptyList(),
    val recognitions: Map<String, RecognitionResult> = emptyMap(),
    val selectedImageIds: Set<String> = emptySet(),
    val currentTab: WorkspaceTab = WorkspaceTab.UNRECOGNIZED,
    val batchSize: Int = AiConfigRepository.DEFAULT_BATCH_SIZE,
    val progress: BatchProgressState = BatchProgressState(),
    val aiConfig: AiConfig = AiConfig(),
    val selectedDetailItem: Pair<ImageItem, RecognitionResult?>? = null,
    val errorMessage: String? = null,
    val proposalCount: Int = 0
) {
    val unrecognizedImages: List<ImageItem>
        get() = images.filter { !recognitions.containsKey(it.id) || recognitions[it.id]?.isSuccess != true }

    val recognizedImages: List<ImageItem>
        get() = images.filter { recognitions[it.id]?.isSuccess == true }

    val failedImages: List<ImageItem>
        get() = images.filter { recognitions[it.id]?.isSuccess == false }

    val currentTabImages: List<ImageItem>
        get() = when (currentTab) {
            WorkspaceTab.UNRECOGNIZED -> unrecognizedImages
            WorkspaceTab.RECOGNIZED -> recognizedImages
            WorkspaceTab.ALL -> images
        }

    val selectedCount: Int
        get() = selectedImageIds.size

    val isKeyConfigured: Boolean
        get() = aiConfig.apiKey.isNotBlank() || aiConfig.isOfflineMode || aiConfig.isHybridMode
}


class WorkspaceViewModel(
    private val repository: ImageRepository = LocalImageRepositoryImpl()
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkspaceUiState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    private var progressObserved = false

    fun getPagedImages(context: Context): Flow<PagingData<ImageItem>> {
        return repository.getPagedImages(context).cachedIn(viewModelScope)
    }

    fun init(context: Context) {
        loadAiConfig(context)
        refreshProposalCount(context)

        // 进度完全来自全局唯一的识别引擎，工作区不再自带第二套批次逻辑
        if (!progressObserved) {
            progressObserved = true
            viewModelScope.launch {
                BackgroundBatchManager.progressState.collect { p ->
                    _uiState.update { it.copy(progress = p) }
                    if (p.isFinished && !p.isRunning) refreshProposalCount(context)
                }
            }
        }

        // Observe images from Room DB
        viewModelScope.launch {
            combine(
                repository.observeImagesFromDb(context),
                repository.observeRecognitions(context)
            ) { images, recognitions ->
                val map = recognitions.associateBy { it.imageId }
                Pair(images, map)
            }.collect { (images, recMap) ->
                _uiState.update { state ->
                    state.copy(
                        images = images,
                        recognitions = recMap
                    )
                }
            }
        }
    }

    private fun refreshProposalCount(context: Context) {
        val app = context.applicationContext
        viewModelScope.launch {
            val n = try {
                SmartProposalEngine.count(app)
            } catch (e: Exception) {
                0
            }
            _uiState.update { it.copy(proposalCount = n) }
        }
    }

    fun loadAiConfig(context: Context) {
        val repo = AiConfigRepository(context)
        _uiState.update { it.copy(aiConfig = repo.getAiConfig(), batchSize = repo.getBatchSize()) }
    }

    fun setTab(tab: WorkspaceTab) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun setBatchSize(context: Context, size: Int) {
        AiConfigRepository(context).saveBatchSize(size)
        _uiState.update { it.copy(batchSize = size) }
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

    fun selectAllInCurrentTab() {
        _uiState.update { state ->
            val ids = state.currentTabImages.map { it.id }.toSet()
            state.copy(selectedImageIds = ids)
        }
    }

    fun invertSelectionInCurrentTab() {
        _uiState.update { state ->
            val visible = state.currentTabImages.map { it.id }.toSet()
            state.copy(selectedImageIds = (state.selectedImageIds - visible) + (visible - state.selectedImageIds))
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedImageIds = emptySet()) }
    }

    fun openDetail(item: ImageItem) {
        val result = _uiState.value.recognitions[item.id]
        _uiState.update { it.copy(selectedDetailItem = Pair(item, result)) }
    }

    fun closeDetail() {
        _uiState.update { it.copy(selectedDetailItem = null) }
    }

    /**
     * 点击"开始识别"：不直接上传，而是把待识别图片交给「识别确认页」，
     * 用户在确认页核对数量、批次、隐私与费用提示后才真正开始。
     * 返回 false 表示没有可识别的图片（已通过 errorMessage 提示）。
     */
    fun prepareConfirm(targetImages: List<ImageItem>? = null, includeRecognized: Boolean = false): Boolean {
        val state = _uiState.value
        if (state.aiConfig.apiKey.isBlank() && state.aiConfig.engineMode == AiConfig.MODE_CLOUD_ONLY) {
            _uiState.update { it.copy(errorMessage = "当前设为【仅云端】模式但尚未配置 API Key，请先进入【设置】填入 Key 或切换为本地离线模式。") }
            return false
        }

        val candidates = targetImages ?: if (state.selectedImageIds.isNotEmpty()) {
            state.images.filter { state.selectedImageIds.contains(it.id) }
        } else {
            state.unrecognizedImages
        }
        if (candidates.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "当前没有需要识别的图片。") }
            return false
        }
        SelectionHolder.set(candidates.map { it.id }, includeRecognized)
        return true
    }

    fun pauseRecognition() = BackgroundBatchManager.pause()

    fun resumeRecognition() = BackgroundBatchManager.resume()

    fun cancelRecognition() = BackgroundBatchManager.stopBatch()

    fun dismissProgress() = BackgroundBatchManager.dismissFinished()

    /** 重试失败项同样先进入确认页。 */
    fun prepareRetryFailed(): Boolean {
        val failed = _uiState.value.failedImages
        if (failed.isEmpty()) return false
        return prepareConfirm(failed)
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun updateKeywords(context: Context, imageId: String, newKeywords: List<String>) {
        viewModelScope.launch {
            repository.updateKeywords(context, imageId, newKeywords)
            val currentDetail = _uiState.value.selectedDetailItem
            if (currentDetail != null && currentDetail.first.id == imageId) {
                val updatedRes = currentDetail.second?.copy(keywords = newKeywords)
                _uiState.update { it.copy(selectedDetailItem = Pair(currentDetail.first, updatedRes)) }
            }
        }
    }
}

