package com.toimg.app.feature.result

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.toimg.app.core.util.FileMoveUtil
import com.toimg.app.core.util.PendingMoveProposal
import com.toimg.app.data.repository.LocalImageRepositoryImpl
import com.toimg.app.domain.model.ConflictStrategy
import com.toimg.app.domain.model.FolderItem
import com.toimg.app.domain.model.ImageItem
import com.toimg.app.domain.model.MoveItemResult
import com.toimg.app.domain.model.MoveItemStatus
import com.toimg.app.domain.model.MovePlan
import com.toimg.app.domain.model.MoveSessionReport
import com.toimg.app.domain.repository.ImageRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MoveScreenStage {
    PREVIEW,
    EXECUTING,
    REPORT
}

data class MoveResultUiState(
    val stage: MoveScreenStage = MoveScreenStage.PREVIEW,
    val imagesToMove: List<ImageItem> = emptyList(),
    val savedFolders: List<FolderItem> = emptyList(),
    val targetDirUri: Uri? = null,
    val targetDirName: String = "",
    val isWritable: Boolean = false,
    val conflictStrategy: ConflictStrategy = ConflictStrategy.RENAME_NEW,
    val movePlan: MovePlan? = null,
    val isGeneratingPlan: Boolean = false,

    // Execution states
    val currentIndex: Int = 0,
    val currentItemName: String = "",
    val executedResults: List<MoveItemResult> = emptyList(),
    val isRunning: Boolean = false,

    // Final report
    val finalReport: MoveSessionReport? = null,
    val selectedFilterStatus: MoveItemStatus? = null,
    val errorMessage: String? = null
) {
    val successCount: Int
        get() = executedResults.count { it.status == MoveItemStatus.SUCCESS }

    val partialCount: Int
        get() = executedResults.count { it.status == MoveItemStatus.PARTIALLY_COMPLETED }

    val failedCount: Int
        get() = executedResults.count { it.status == MoveItemStatus.FAILED }

    val skippedCount: Int
        get() = executedResults.count { it.status == MoveItemStatus.SKIPPED }

    val filteredReportItems: List<MoveItemResult>
        get() = if (selectedFilterStatus == null) {
            finalReport?.items ?: emptyList()
        } else {
            finalReport?.items?.filter { it.status == selectedFilterStatus } ?: emptyList()
        }
}

class MoveResultViewModel(
    private val repository: ImageRepository = LocalImageRepositoryImpl()
) : ViewModel() {

    private var executionJob: Job? = null
    private val _uiState = MutableStateFlow(MoveResultUiState())
    val uiState: StateFlow<MoveResultUiState> = _uiState.asStateFlow()

    private var initialized = false

    fun init(context: Context, specificImageIds: List<String>? = null) {
        // 屏幕旋转会重复触发 init；一次性的「智能提案」只能消费一次，不能因此退回到「全部图片」
        if (initialized) return
        initialized = true
        val proposal = PendingMoveProposal.consume()
        viewModelScope.launch {
            val allImages = repository.observeImagesFromDb(context).first()
            val idSet = (specificImageIds ?: proposal?.imageIds)?.toSet()
            val targetList = if (!idSet.isNullOrEmpty()) {
                allImages.filter { idSet.contains(it.id) }
            } else {
                allImages
            }

            val savedFolders = repository.getSavedFolders(context)
            val defaultFolder: FolderItem? = if (proposal != null) {
                savedFolders.firstOrNull { it.uri == proposal.targetUri }
                    ?: FolderItem(
                        id = proposal.targetUri.toString(),
                        name = proposal.targetName,
                        uri = proposal.targetUri,
                        isAuthorized = true
                    )
            } else {
                savedFolders.firstOrNull()
            }

            _uiState.update {
                it.copy(
                    imagesToMove = targetList,
                    savedFolders = savedFolders,
                    targetDirUri = defaultFolder?.uri,
                    targetDirName = defaultFolder?.name ?: "未指定目标文件夹"
                )
            }

            if (defaultFolder?.uri != null) {
                recalculatePlan(context)
            }
        }
    }

    fun setTargetDirectory(context: Context, treeUri: Uri, dirName: String) {
        viewModelScope.launch {
            repository.saveAuthorizedFolder(context, treeUri, dirName)
            val isWritable = FileMoveUtil.checkDirectoryWritable(context, treeUri)

            if (!isWritable) {
                _uiState.update {
                    it.copy(
                        targetDirUri = treeUri,
                        targetDirName = dirName,
                        isWritable = false,
                        errorMessage = "所选目标文件夹无写入权限，请重新授权或选择其他文件夹。"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        targetDirUri = treeUri,
                        targetDirName = dirName,
                        isWritable = true,
                        errorMessage = null
                    )
                }
                recalculatePlan(context)
            }
        }
    }

    fun setConflictStrategy(context: Context, strategy: ConflictStrategy) {
        _uiState.update { it.copy(conflictStrategy = strategy) }
        recalculatePlan(context)
    }

    fun recalculatePlan(context: Context) {
        val targetUri = _uiState.value.targetDirUri ?: return
        val dirName = _uiState.value.targetDirName
        val images = _uiState.value.imagesToMove
        val strategy = _uiState.value.conflictStrategy

        if (images.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingPlan = true) }
            val plan = FileMoveUtil.generateMovePlan(context, images, targetUri, dirName, strategy)
            _uiState.update {
                it.copy(
                    movePlan = plan,
                    isGeneratingPlan = false,
                    isWritable = true
                )
            }
        }
    }

    fun startMoveExecution(context: Context) {
        val plan = _uiState.value.movePlan ?: return
        if (!plan.canExecute) return

        executionJob?.cancel()
        executionJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            _uiState.update {
                it.copy(
                    stage = MoveScreenStage.EXECUTING,
                    isRunning = true,
                    currentIndex = 0,
                    executedResults = emptyList(),
                    errorMessage = null
                )
            }

            val results = mutableListOf<MoveItemResult>()

            for (i in plan.items.indices) {
                val item = plan.items[i]
                _uiState.update {
                    it.copy(
                        currentIndex = i + 1,
                        currentItemName = item.imageItem.name
                    )
                }

                // Execute safe move
                val itemResult = FileMoveUtil.executeSafeMove(context, item, plan.conflictStrategy)
                results.add(itemResult)

                // If fully success, update image location in Room database
                if (itemResult.status == MoveItemStatus.SUCCESS && itemResult.targetUri != null) {
                    repository.recordMoveSuccess(
                        context = context,
                        imageId = item.imageItem.id,
                        newUriString = itemResult.targetUri.toString(),
                        newName = itemResult.finalName,
                        newFolderName = plan.targetDirName
                    )
                }

                _uiState.update { it.copy(executedResults = results.toList()) }
            }

            // Record to move history in Room DB
            repository.recordMoveHistory(context, results, plan.targetDirName)

            val report = MoveSessionReport(
                targetDirName = plan.targetDirName,
                totalCount = results.size,
                successCount = results.count { it.status == MoveItemStatus.SUCCESS },
                partialCount = results.count { it.status == MoveItemStatus.PARTIALLY_COMPLETED },
                skippedCount = results.count { it.status == MoveItemStatus.SKIPPED },
                failedCount = results.count { it.status == MoveItemStatus.FAILED },
                items = results,
                startTime = startTime,
                endTime = System.currentTimeMillis()
            )

            _uiState.update {
                it.copy(
                    stage = MoveScreenStage.REPORT,
                    isRunning = false,
                    finalReport = report
                )
            }
        }
    }

    fun cancelExecution() {
        executionJob?.cancel()
        _uiState.update { state ->
            val report = MoveSessionReport(
                targetDirName = state.targetDirName,
                totalCount = state.executedResults.size,
                successCount = state.successCount,
                partialCount = state.partialCount,
                skippedCount = state.skippedCount,
                failedCount = state.failedCount,
                items = state.executedResults,
                startTime = System.currentTimeMillis()
            )
            state.copy(
                stage = MoveScreenStage.REPORT,
                isRunning = false,
                finalReport = report
            )
        }
    }

    fun setFilterStatus(status: MoveItemStatus?) {
        _uiState.update { it.copy(selectedFilterStatus = status) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
