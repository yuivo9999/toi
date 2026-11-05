package com.toimg.app.feature.scan

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.paging.PagingData
import com.toimg.app.data.local.entity.MoveHistoryEntity
import com.toimg.app.data.local.entity.SmartRuleEntity
import com.toimg.app.domain.model.FolderItem
import com.toimg.app.domain.model.ImageItem
import com.toimg.app.domain.model.MoveItemResult
import com.toimg.app.domain.model.RecognitionResult
import com.toimg.app.domain.model.SearchFilterCriteria
import com.toimg.app.domain.repository.ImageRepository
import com.toimg.app.core.util.ScanMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 只实现 ScanScopeViewModel 用到的方法；其余一律报错，防止测试悄悄依赖到别处。 */
private class FakeImageRepository(var folders: List<FolderItem>) : ImageRepository {
    override fun getSavedFolders(context: Context): List<FolderItem> = folders

    override fun scanMediaStoreImages(context: Context): Flow<List<ImageItem>> = emptyFlow()
    override fun scanFolderUri(context: Context, treeUri: Uri, includeSubfolders: Boolean, sourceId: String?, scanSessionId: String?): Flow<List<ImageItem>> = emptyFlow()
    override fun observeImagesFromDb(context: Context): Flow<List<ImageItem>> = emptyFlow()
    override fun getPagedImages(context: Context): Flow<PagingData<ImageItem>> = emptyFlow()
    override fun observeDatabaseStats(context: Context): Flow<Pair<Int, Int>> = emptyFlow()
    override suspend fun computeMissingHashes(context: Context, onProgress: (Int, Int) -> Unit) = Unit
    override fun saveAuthorizedFolder(context: Context, uri: Uri, folderName: String) = Unit
    override fun removeAuthorizedFolder(context: Context, uriString: String) = Unit
    override fun observeRecognitions(context: Context): Flow<List<RecognitionResult>> = emptyFlow()
    override fun observeSuccessfulRecognitions(context: Context): Flow<List<RecognitionResult>> = emptyFlow()
    override suspend fun saveRecognitions(context: Context, results: List<RecognitionResult>) = Unit
    override suspend fun getRecognition(context: Context, imageId: String): RecognitionResult? = null
    override suspend fun recordMoveSuccess(
        context: Context, imageId: String, newUriString: String, newName: String, newFolderName: String
    ) = Unit
    override suspend fun recordMoveHistory(
        context: Context, results: List<MoveItemResult>, targetDirName: String
    ) = Unit
    override fun observeMoveHistory(context: Context): Flow<List<MoveHistoryEntity>> = emptyFlow()
    override suspend fun rollbackMove(context: Context, historyId: Long): Result<String> =
        Result.failure(UnsupportedOperationException())
    override suspend fun updateKeywords(context: Context, imageId: String, newKeywords: List<String>) = Unit
    override suspend fun mergeCategory(context: Context, oldCategory: String, targetCategory: String) = Unit
    override fun observeSmartRules(context: Context): Flow<List<SmartRuleEntity>> = emptyFlow()
    override suspend fun saveSmartRule(context: Context, rule: SmartRuleEntity): Long = 0L
    override suspend fun deleteSmartRule(context: Context, id: Long) = Unit
    override suspend fun updateRuleStatus(context: Context, id: Long, isEnabled: Boolean) = Unit
    override suspend fun seedPresetSmartRulesIfEmpty(context: Context) = Unit
    override fun searchImagesWithFilters(
        context: Context, criteria: SearchFilterCriteria
    ): Flow<List<ImageItem>> = emptyFlow()
    override suspend fun rebuildSearchIndex(context: Context) = Unit
}

class ScanScopeViewModelTest {

    // unitTests.isReturnDefaultValues = true，ContextWrapper(null) 在 JVM 上可安全构造
    private val context: Context = ContextWrapper(null)

    private fun folder(id: String, authorized: Boolean) =
        FolderItem(id = id, name = id, isAuthorized = authorized)

    private fun vm(vararg folders: FolderItem) = ScanScopeViewModel(FakeImageRepository(folders.toList()))

    @Test
    fun firstLoad_selectsAuthorizedFoldersOnly() {
        val vm = vm(folder("a", true), folder("b", false), folder("c", true))
        vm.load(context, permissionGranted = false)
        assertEquals(setOf("a", "c"), vm.uiState.value.selectedFolderIds)
        assertFalse(vm.uiState.value.includeMediaStore)
    }

    @Test
    fun firstLoad_withPermission_enablesMediaStore() {
        val vm = vm()
        vm.load(context, permissionGranted = true)
        assertTrue(vm.uiState.value.includeMediaStore)
        assertTrue(vm.uiState.value.canStart)
    }

    @Test
    fun reload_dropsSelectionsForFoldersThatNoLongerExist() {
        val repo = FakeImageRepository(listOf(folder("a", true), folder("b", true)))
        val vm = ScanScopeViewModel(repo)
        vm.load(context, false)
        repo.folders = listOf(folder("a", true))
        vm.load(context, false)
        assertEquals(setOf("a"), vm.uiState.value.selectedFolderIds)
    }

    @Test
    fun reload_keepsUserDeselection() {
        val vm = vm(folder("a", true), folder("b", true))
        vm.load(context, false)
        vm.toggleFolder("a")
        vm.load(context, false)
        assertEquals(setOf("b"), vm.uiState.value.selectedFolderIds)
    }

    @Test
    fun toggleFolder_addsThenRemoves() {
        val vm = vm()
        vm.toggleFolder("x")
        assertEquals(setOf("x"), vm.uiState.value.selectedFolderIds)
        vm.toggleFolder("x")
        assertTrue(vm.uiState.value.selectedFolderIds.isEmpty())
    }

    @Test
    fun permissionDenied_disablesMediaStoreAndFlagsDenied() {
        val vm = vm()
        vm.onPermissionResult(true)
        vm.onPermissionResult(false)
        val s = vm.uiState.value
        assertFalse(s.includeMediaStore)
        assertTrue(s.permissionDenied)
        assertFalse(s.canStart)
    }

    @Test
    fun toggleMediaStore_needsPermissionToStart() {
        val vm = vm()
        vm.toggleMediaStore() // 开了开关但没有权限
        assertTrue(vm.uiState.value.includeMediaStore)
        assertFalse(vm.uiState.value.canStart)
    }

    @Test
    fun scanMode_defaultsToFastIncremental() {
        val vm = vm()
        assertEquals(ScanMode.FAST_INCREMENTAL, vm.uiState.value.scanMode)
    }

    @Test
    fun scanMode_canSwitchToVerifyExisting() {
        val vm = vm()
        vm.setScanMode(ScanMode.VERIFY_EXISTING)
        assertEquals(ScanMode.VERIFY_EXISTING, vm.uiState.value.scanMode)
    }

    @Test
    fun startScan_refusesWhenNothingSelected() {
        val vm = vm(folder("a", false))
        vm.load(context, permissionGranted = false)
        assertFalse(vm.startScan(context))
    }
}
