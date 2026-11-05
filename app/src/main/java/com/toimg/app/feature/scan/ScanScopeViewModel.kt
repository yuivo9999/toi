package com.toimg.app.feature.scan

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import com.toimg.app.core.work.ScanTaskScheduler
import com.toimg.app.core.util.ScanMode
import com.toimg.app.data.repository.LocalImageRepositoryImpl
import com.toimg.app.domain.model.FolderItem
import com.toimg.app.domain.repository.ImageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ScanScopeUiState(
    val folders: List<FolderItem> = emptyList(),
    val selectedFolderIds: Set<String> = emptySet(),
    val includeMediaStore: Boolean = false,
    val permissionGranted: Boolean = false,
    val permissionDenied: Boolean = false,
    val scanMode: ScanMode = ScanMode.FAST_INCREMENTAL
) {
    val selectedFolders: List<FolderItem>
        get() = folders.filter { it.id in selectedFolderIds && it.isAuthorized && it.uri != null }

    val canStart: Boolean
        get() = (includeMediaStore && permissionGranted) || selectedFolders.isNotEmpty()
}

class ScanScopeViewModel(
    private val repository: ImageRepository = LocalImageRepositoryImpl()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanScopeUiState())
    val uiState: StateFlow<ScanScopeUiState> = _uiState.asStateFlow()

    private var initialized = false

    fun load(context: Context, permissionGranted: Boolean) {
        val folders = repository.getSavedFolders(context)
        _uiState.update { state ->
            // 每次新进入扫描范围页面，都从“未选择”开始。
            // 已登记来源只代表项目记住了该目录，不代表用户本次仍然要扫描它。
            // 否则用户上次选中 DCIM，下一次只想扫描 Download 时，会意外把 DCIM 一并带入任务。
            val selected = if (!initialized) emptySet() else {
                state.selectedFolderIds.intersect(folders.map { it.id }.toSet())
            }
            state.copy(
                folders = folders,
                selectedFolderIds = selected,
                permissionGranted = permissionGranted,
                includeMediaStore = if (!initialized) permissionGranted else state.includeMediaStore && permissionGranted
            )
        }
        initialized = true
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.update {
            it.copy(
                permissionGranted = granted,
                permissionDenied = !granted,
                includeMediaStore = granted
            )
        }
    }

    fun setScanMode(mode: ScanMode) {
        _uiState.update { it.copy(scanMode = mode) }
    }

    fun toggleMediaStore() {
        _uiState.update { it.copy(includeMediaStore = !it.includeMediaStore) }
    }

    fun toggleFolder(id: String) {
        _uiState.update {
            val set = it.selectedFolderIds.toMutableSet()
            if (!set.add(id)) set.remove(id)
            it.copy(selectedFolderIds = set)
        }
    }

    fun addFolder(context: Context, treeUri: Uri) {
        val name = try {
            DocumentFile.fromTreeUri(context, treeUri)?.name ?: "SAF 目录"
        } catch (e: Exception) {
            "SAF 目录"
        }
        repository.saveAuthorizedFolder(context, treeUri, name)
        val folders = repository.getSavedFolders(context)
        _uiState.update { it.copy(folders = folders, selectedFolderIds = it.selectedFolderIds + treeUri.toString()) }
    }

    /** 返回是否真的启动了扫描。 */
    fun startScan(context: Context, includeSubfolders: Boolean = true): Boolean {
        val s = _uiState.value
        if (!s.canStart) return false
        // 若已有活动任务，Scheduler 会直接复用唯一任务，不会再次扫描或覆盖其范围。
        return ScanTaskScheduler.enqueue(
            context = context,
            includeMediaStore = s.includeMediaStore && s.permissionGranted,
            folderUris = s.selectedFolders.mapNotNull { it.uri },
            includeSubfolders = includeSubfolders,
            scanMode = s.scanMode
        )
    }
}
