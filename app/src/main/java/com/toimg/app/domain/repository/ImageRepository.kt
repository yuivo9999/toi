package com.toimg.app.domain.repository

import android.content.Context
import android.net.Uri
import androidx.paging.PagingData
import com.toimg.app.domain.model.FolderItem
import com.toimg.app.domain.model.ImageItem
import com.toimg.app.domain.model.RecognitionResult
import kotlinx.coroutines.flow.Flow

interface ImageRepository {
    fun scanMediaStoreImages(context: Context, sourceId: String? = null, scanSessionId: String? = null): Flow<List<ImageItem>>
    fun scanFolderUri(context: Context, treeUri: Uri, includeSubfolders: Boolean = true, sourceId: String? = null, scanSessionId: String? = null): Flow<List<ImageItem>>
    fun observeImagesFromDb(context: Context): Flow<List<ImageItem>>
    fun getPagedImages(context: Context): Flow<PagingData<ImageItem>>
    fun observeDatabaseStats(context: Context): Flow<Pair<Int, Int>>
    suspend fun computeMissingHashes(context: Context, onProgress: (Int, Int) -> Unit)
    fun getSavedFolders(context: Context): List<FolderItem>
    fun saveAuthorizedFolder(context: Context, uri: Uri, folderName: String)

    fun observeRecognitions(context: Context): Flow<List<RecognitionResult>>
    fun observeSuccessfulRecognitions(context: Context): Flow<List<RecognitionResult>>
    suspend fun saveRecognitions(context: Context, results: List<RecognitionResult>)
    suspend fun getRecognition(context: Context, imageId: String): RecognitionResult?

    suspend fun recordMoveSuccess(
        context: Context,
        imageId: String,
        newUriString: String,
        newName: String,
        newFolderName: String
    )
    suspend fun recordMoveHistory(
        context: Context,
        results: List<com.toimg.app.domain.model.MoveItemResult>,
        targetDirName: String
    )
    fun observeMoveHistory(context: Context): Flow<List<com.toimg.app.data.local.entity.MoveHistoryEntity>>
    suspend fun rollbackMove(context: Context, historyId: Long): Result<String>

    suspend fun updateKeywords(context: Context, imageId: String, newKeywords: List<String>)
    suspend fun mergeCategory(context: Context, oldCategory: String, targetCategory: String)

    fun observeSmartRules(context: Context): Flow<List<com.toimg.app.data.local.entity.SmartRuleEntity>>
    suspend fun saveSmartRule(context: Context, rule: com.toimg.app.data.local.entity.SmartRuleEntity): Long
    suspend fun deleteSmartRule(context: Context, id: Long)
    suspend fun updateRuleStatus(context: Context, id: Long, isEnabled: Boolean)
    suspend fun seedPresetSmartRulesIfEmpty(context: Context)

    fun searchImagesWithFilters(
        context: Context,
        criteria: com.toimg.app.domain.model.SearchFilterCriteria
    ): Flow<List<ImageItem>>
    suspend fun rebuildSearchIndex(context: Context)
}


