package com.toimg.app.core.util

import com.toimg.app.data.local.ToImgDatabase
import androidx.room.withTransaction

/** F3/F5 只读完整性诊断；不擅自删除用户识别结果或来源历史。 */
data class ImageIndexIntegrityReport(
    val orphanRecognitions: Int,
    val orphanFtsRows: Int,
    val imagesMissingFtsRows: Int,
    val orphanSourceLinks: Int,
    val recognizedImagesMissingResult: Int,
    val successResultsWithStaleImageStatus: Int
) {
    val isHealthy: Boolean
        get() = orphanRecognitions == 0 && orphanFtsRows == 0 && imagesMissingFtsRows == 0 && orphanSourceLinks == 0 &&
            recognizedImagesMissingResult == 0 && successResultsWithStaleImageStatus == 0
}

class ImageIndexIntegrityAuditor(private val database: ToImgDatabase) {
    suspend fun inspect(): ImageIndexIntegrityReport = ImageIndexIntegrityReport(
        orphanRecognitions = database.recognitionDao().countOrphanRecognitions(),
        orphanFtsRows = database.imageFtsDao().countOrphanFtsRows(),
        imagesMissingFtsRows = database.imageFtsDao().countImagesMissingFtsRows(),
        orphanSourceLinks = database.imageSourceDao().countOrphanSourceLinks(),
        recognizedImagesMissingResult = database.recognitionDao().countRecognizedImagesMissingSuccess(),
        successResultsWithStaleImageStatus = database.recognitionDao().countSuccessResultsWithStaleImageStatus()
    )

    /** 仅重建可派生的 FTS 搜索表；不更改图片、识别结果和来源关联。 */
    suspend fun rebuildSearchIndex() {
        database.imageFtsDao().rebuildAllFts()
    }

    /** Repairs only the derived image status flags; recognition result content is never deleted. */
    suspend fun repairRecognitionStatusFlags(): Pair<Int, Int> = database.withTransaction {
        val restored = database.recognitionDao().restoreRecognizedImageStatuses()
        val downgraded = database.recognitionDao().downgradeImagesMissingRecognition()
        restored to downgraded
    }
}
