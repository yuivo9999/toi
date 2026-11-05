package com.toimg.app.feature.proposal

import com.toimg.app.core.di.AppContainer

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.toimg.app.core.util.FileMoveUtil
import com.toimg.app.core.util.PendingMoveProposal
import com.toimg.app.core.util.SmartProposalEngine
import com.toimg.app.core.util.SmartProposalGroup
import com.toimg.app.data.local.entity.ImageEntity
import com.toimg.app.data.local.entity.SmartRuleEntity
import com.toimg.app.data.repository.LocalImageRepositoryImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProposalGroupUi(
    val rule: SmartRuleEntity,
    val images: List<ImageEntity>,
    val excludedIds: Set<String> = emptySet(),
    val targetUri: Uri? = null,
    val targetName: String = "",
    val targetWritable: Boolean = false
) {
    val selectedImages: List<ImageEntity>
        get() = images.filter { it.id !in excludedIds }
}

data class SmartProposalUiState(
    val isLoading: Boolean = true,
    val groups: List<ProposalGroupUi> = emptyList(),
    val message: String? = null
)

class SmartProposalViewModel : ViewModel() {

    private val repository = LocalImageRepositoryImpl()
    private val _uiState = MutableStateFlow(SmartProposalUiState())
    val uiState: StateFlow<SmartProposalUiState> = _uiState.asStateFlow()

    fun load(context: Context) {
        val app = context.applicationContext
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val groups = try {
                SmartProposalEngine.build(app)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
            val ui = groups.map { resolveTarget(app, it) }
            _uiState.update { it.copy(isLoading = false, groups = ui) }
        }
    }

    private suspend fun resolveTarget(app: Context, g: SmartProposalGroup): ProposalGroupUi {
        val raw = g.rule.targetTreeUri
        if (raw.isBlank()) return ProposalGroupUi(rule = g.rule, images = g.images)
        val uri = Uri.parse(raw)
        val hasPermission = app.contentResolver.persistedUriPermissions
            .any { it.uri == uri && it.isWritePermission }
        val writable = hasPermission && FileMoveUtil.checkDirectoryWritable(app, uri)
        return ProposalGroupUi(
            rule = g.rule,
            images = g.images,
            targetUri = uri,
            targetName = g.rule.targetFolderName,
            targetWritable = writable
        )
    }

    fun toggleExclude(ruleId: Long, imageId: String) {
        _uiState.update { st ->
            st.copy(groups = st.groups.map { g ->
                if (g.rule.id != ruleId) g
                else g.copy(
                    excludedIds = if (imageId in g.excludedIds) g.excludedIds - imageId else g.excludedIds + imageId
                )
            })
        }
    }

    fun setTarget(context: Context, ruleId: Long, uri: Uri, name: String) {
        val app = context.applicationContext
        viewModelScope.launch {
            repository.saveAuthorizedFolder(app, uri, name)
            val writable = FileMoveUtil.checkDirectoryWritable(app, uri)
            AppContainer.database(app).smartRuleDao().updateTarget(ruleId, uri.toString(), name)
            _uiState.update { st ->
                st.copy(
                    groups = st.groups.map { g ->
                        if (g.rule.id == ruleId) g.copy(targetUri = uri, targetName = name, targetWritable = writable) else g
                    },
                    message = if (writable) null else "所选目标文件夹无写入权限，请重新授权或选择其他文件夹。"
                )
            }
        }
    }

    /** 把该组交给「移动预览」；返回 true 表示可以跳转。真正的移动仍需在预览页再次确认。 */
    fun prepareMove(ruleId: Long): Boolean {
        val group = _uiState.value.groups.firstOrNull { it.rule.id == ruleId } ?: return false
        val target = group.targetUri
        val selected = group.selectedImages
        if (target == null || !group.targetWritable) {
            _uiState.update { it.copy(message = "请先为这组选择一个可写入的目标文件夹。") }
            return false
        }
        if (selected.isEmpty()) {
            _uiState.update { it.copy(message = "这组图片都被排除了，没有可移动的内容。") }
            return false
        }
        PendingMoveProposal.set(
            PendingMoveProposal.Proposal(
                imageIds = selected.map { it.id },
                targetUri = target,
                targetName = group.targetName
            )
        )
        return true
    }

    fun dismissMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
