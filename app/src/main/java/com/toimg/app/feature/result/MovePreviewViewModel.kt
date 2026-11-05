package com.toimg.app.feature.result

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.toimg.app.core.util.FileMoveUtil
import com.toimg.app.data.repository.LocalImageRepositoryImpl
import com.toimg.app.domain.model.ConflictStrategy
import com.toimg.app.domain.model.FolderItem
import com.toimg.app.domain.model.ImageItem
import com.toimg.app.domain.model.MovePlan
import com.toimg.app.domain.repository.ImageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MovePreviewUiState(
    val imagesToMove: List<ImageItem> = emptyList(),
    val savedFolders: List<FolderItem> = emptyList(),
    val targetDirUri: Uri? = null,
    val targetDirName: String = "",
    val isWritable: Boolean = false,
    val conflictStrategy: ConflictStrategy = ConflictStrategy.RENAME_NEW,
    val movePlan: MovePlan? = null,
    val isGeneratingPlan: Boolean = false,
    val errorMessage: String? = null
)

class MovePreviewViewModel(
    private val repository: ImageRepository = LocalImageRepositoryImpl()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MovePreviewUiState())
    val uiState: StateFlow<MovePreviewUiState> = _uiState.asStateFlow()

    fun init(context: Context, specificImageIds: List<String>? = null) {
        viewModelScope.launch {
            val allImages = repository.observeImagesFromDb(context).first()
            val targetList = if (!specificImageIds.isNullOrEmpty()) {
                allImages.filter { specificImageIds.contains(it.id) }
            } else {
                allImages
            }

            val savedFolders = repository.getSavedFolders(context)
            val defaultFolder = savedFolders.firstOrNull()

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

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
