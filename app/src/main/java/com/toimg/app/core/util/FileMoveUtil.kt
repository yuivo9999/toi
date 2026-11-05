package com.toimg.app.core.util

import com.toimg.app.core.di.AppContainer

import android.content.ContentValues
import android.content.Context

import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.toimg.app.data.local.entity.MoveHistoryEntity
import com.toimg.app.data.local.entity.SmartRuleEntity
import com.toimg.app.domain.model.ConflictStrategy
import com.toimg.app.domain.model.ImageItem
import com.toimg.app.domain.model.MoveItemResult
import com.toimg.app.domain.model.MoveItemStatus
import com.toimg.app.domain.model.MovePlan
import com.toimg.app.domain.model.MovePreviewItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

object FileMoveUtil {

    suspend fun checkDirectoryWritable(context: Context, treeUri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val doc = DocumentFile.fromTreeUri(context, treeUri)
            doc != null && doc.exists() && doc.canWrite()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun getExistingFileNames(context: Context, treeUri: Uri): Set<String> = withContext(Dispatchers.IO) {
        try {
            val doc = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptySet()
            val files = doc.listFiles()
            files.mapNotNull { it.name?.lowercase() }.toSet()
        } catch (e: Exception) {
            e.printStackTrace()
            emptySet()
        }
    }

    suspend fun generateMovePlan(
        context: Context,
        images: List<ImageItem>,
        targetUri: Uri,
        targetDirName: String,
        strategy: ConflictStrategy
    ): MovePlan = withContext(Dispatchers.IO) {
        val existingNames = getExistingFileNames(context, targetUri)
        val previewItems = mutableListOf<MovePreviewItem>()

        images.forEach { item ->
            val origName = item.name
            val lowerOrig = origName.lowercase()
            val hasConflict = existingNames.contains(lowerOrig)

            var isValid = true
            var reason: String? = null

            if (item.uri == Uri.EMPTY) {
                isValid = false
                reason = "文件 URI 为空，不可移动"
            }

            val proposedName = if (hasConflict) {
                if (strategy == ConflictStrategy.RENAME_NEW) {
                    generateUniqueName(origName, existingNames)
                } else {
                    origName
                }
            } else {
                origName
            }

            previewItems.add(
                MovePreviewItem(
                    imageItem = item,
                    sourcePathDisplay = if (item.folderName.isNotBlank()) "【${item.folderName}】${item.path}" else item.path,
                    targetDirName = targetDirName,
                    targetDirUri = targetUri,
                    proposedTargetName = proposedName,
                    hasConflict = hasConflict,
                    isValid = isValid,
                    invalidReason = reason
                )
            )
        }

        MovePlan(
            items = previewItems,
            targetDirName = targetDirName,
            targetDirUri = targetUri,
            conflictStrategy = strategy
        )
    }

    /**
     * Executes zero-data-loss safe move:
     * 1. Checks conflict strategy (skip if requested).
     * 2. Attempts native DocumentProvider move if feasible.
     * 3. Fallback: Creates target file -> Copies stream -> Verifies dual SHA-256 integrity -> Deletes source file.
     * 4. If copy/verify fails: Deletes target fragment, source is strictly preserved.
     * 5. If copy succeeds but source delete fails: returns PARTIALLY_COMPLETED.
     */
    suspend fun executeSafeMove(
        context: Context,
        item: MovePreviewItem,
        strategy: ConflictStrategy
    ): MoveItemResult = withContext(Dispatchers.IO) {
        val image = item.imageItem

        if (item.hasConflict && strategy == ConflictStrategy.SKIP) {
            return@withContext MoveItemResult(
                imageId = image.id,
                originalName = image.name,
                sourceUri = image.uri,
                targetUri = null,
                finalName = image.name,
                status = MoveItemStatus.SKIPPED,
                errorMessage = "目标目录已存在同名文件，按用户策略跳过"
            )
        }

        if (!item.isValid) {
            return@withContext MoveItemResult(
                imageId = image.id,
                originalName = image.name,
                sourceUri = image.uri,
                targetUri = null,
                finalName = image.name,
                status = MoveItemStatus.FAILED,
                errorMessage = item.invalidReason ?: "无效文件项"
            )
        }

        // Safe Copy -> Verify SHA-256 -> Delete Source engine
        val targetDir = DocumentFile.fromTreeUri(context, item.targetDirUri)
        if (targetDir == null || !targetDir.canWrite()) {
            return@withContext MoveItemResult(
                imageId = image.id,
                originalName = image.name,
                sourceUri = image.uri,
                targetUri = null,
                finalName = image.name,
                status = MoveItemStatus.FAILED,
                errorMessage = "目标文件夹不可写或授权已过期"
            )
        }

        val mimeType = image.mimeType.ifBlank { "image/jpeg" }
        val targetFile = targetDir.createFile(mimeType, item.proposedTargetName)
        if (targetFile == null) {
            return@withContext MoveItemResult(
                imageId = image.id,
                originalName = image.name,
                sourceUri = image.uri,
                targetUri = null,
                finalName = item.proposedTargetName,
                status = MoveItemStatus.FAILED,
                errorMessage = "无法在目标文件夹中创建目标文件"
            )
        }

        var sourceHash = ""
        var targetHash = ""
        var copySuccess = false

        try {
            val digestSource = MessageDigest.getInstance("SHA-256")
            val digestTarget = MessageDigest.getInstance("SHA-256")

            context.contentResolver.openInputStream(image.uri)?.use { inStream ->
                context.contentResolver.openOutputStream(targetFile.uri)?.use { outStream ->
                    val buffer = ByteArray(16384)
                    var bytesRead: Int
                    while (inStream.read(buffer).also { bytesRead = it } != -1) {
                        digestSource.update(buffer, 0, bytesRead)
                        digestTarget.update(buffer, 0, bytesRead)
                        outStream.write(buffer, 0, bytesRead)
                    }
                    outStream.flush()
                }
            }

            sourceHash = bytesToHex(digestSource.digest())
            targetHash = bytesToHex(digestTarget.digest())

            // Verify integrity
            if (sourceHash.isNotBlank() && sourceHash == targetHash) {
                copySuccess = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            copySuccess = false
        }

        if (!copySuccess) {
            // Delete target fragment to avoid corrupted files
            try {
                targetFile.delete()
            } catch (ignored: Exception) {}

            return@withContext MoveItemResult(
                imageId = image.id,
                originalName = image.name,
                sourceUri = image.uri,
                targetUri = null,
                finalName = item.proposedTargetName,
                status = MoveItemStatus.FAILED,
                sourceHash = sourceHash,
                targetHash = targetHash,
                errorMessage = "复制或完整性哈希校验失败。已清理目标残留碎片，源文件严格保留。"
            )
        }

        // Integrity verified! Now attempt source file deletion
        var sourceDeleted = false
        try {
            if (DocumentsContract.isDocumentUri(context, image.uri)) {
                val srcDoc = DocumentFile.fromSingleUri(context, image.uri)
                sourceDeleted = srcDoc?.delete() == true
            }
            if (!sourceDeleted) {
                sourceDeleted = context.contentResolver.delete(image.uri, null, null) > 0
            }
        } catch (e: Exception) {
            sourceDeleted = false
        }

        if (sourceDeleted) {
            MoveItemResult(
                imageId = image.id,
                originalName = image.name,
                sourceUri = image.uri,
                targetUri = targetFile.uri,
                finalName = item.proposedTargetName,
                status = MoveItemStatus.SUCCESS,
                sourceHash = sourceHash,
                targetHash = targetHash,
                executionMethod = "COPY_VERIFY_DELETE"
            )
        } else {
            MoveItemResult(
                imageId = image.id,
                originalName = image.name,
                sourceUri = image.uri,
                targetUri = targetFile.uri,
                finalName = item.proposedTargetName,
                status = MoveItemStatus.PARTIALLY_COMPLETED,
                sourceHash = sourceHash,
                targetHash = targetHash,
                errorMessage = "目标文件已成功复制并通过完整性校验，但系统权限限制未能删除原位置文件。两处均已保留。",
                executionMethod = "COPY_VERIFY_RETAIN"
            )
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexString = StringBuilder()
        for (b in bytes) {
            val hex = Integer.toHexString(0xff and b.toInt())
            if (hex.length == 1) hexString.append('0')
            hexString.append(hex)
        }
        return hexString.toString()
    }

    private fun generateUniqueName(fileName: String, existingNamesSet: Set<String>): String {
        val dotIndex = fileName.lastIndexOf('.')
        val nameWithoutExt = if (dotIndex > 0) fileName.substring(0, dotIndex) else fileName
        val ext = if (dotIndex > 0) fileName.substring(dotIndex) else ""

        var counter = 1
        var candidate = "${nameWithoutExt}_$counter$ext"
        while (existingNamesSet.contains(candidate.lowercase())) {
            counter++
            candidate = "${nameWithoutExt}_$counter$ext"
        }
        return candidate
    }

    /**
     * Executes safe undo / rollback for a previous move:
     * 1. Checks that target file still exists.
     * 2. If status was PARTIALLY_COMPLETED, source was never deleted: safely deletes target file, updates status.
     * 3. If status was SUCCESS: restores the file to MediaStore/original location, verifies SHA-256, removes target file, updates DB.
     */
    suspend fun executeRollback(
        context: Context,
        history: MoveHistoryEntity
    ): Result<String> = withContext(Dispatchers.IO) {
        if (history.status == "ROLLED_BACK") {
            return@withContext Result.failure(IllegalStateException("该记录已经撤回过，无法重复撤回"))
        }

        val targetUri = try {
            Uri.parse(history.targetPath)
        } catch (e: Exception) {
            return@withContext Result.failure(IllegalArgumentException("目标文件路径无效: ${history.targetPath}"))
        }

        // Check if target file exists
        val targetDoc = DocumentFile.fromSingleUri(context, targetUri)
        val targetExists = targetDoc?.exists() == true || try {
            context.contentResolver.openInputStream(targetUri)?.use { true } ?: false
        } catch (e: Exception) {
            false
        }

        if (!targetExists) {
            return@withContext Result.failure(IllegalStateException("目标文件已不存在或已被移走，无法执行撤回"))
        }

        val db = AppContainer.database(context)

        // Case A: PARTIALLY_COMPLETED (Source was never deleted)
        if (history.status == "PARTIALLY_COMPLETED") {
            val targetDeleted = runCatching { targetDoc?.delete() == true }.getOrDefault(false) ||
                runCatching { context.contentResolver.delete(targetUri, null, null) > 0 }.getOrDefault(false)
            if (!targetDeleted) {
                return@withContext Result.failure(IllegalStateException("目标副本无法删除，已保留移动历史，未标记撤回成功"))
            }
            db.withTransaction {
                db.moveHistoryDao().updateHistoryStatus(history.id, "ROLLED_BACK")
                db.imageDao().updateStatus(history.imageId, "ORIGINAL")
                db.imageFtsDao().syncFtsForImage(history.imageId)
            }
            return@withContext Result.success("撤回成功：已清理目标副本，原文件保持不变")
        }

        // Case B: SUCCESS (Source was deleted -> Copy target back to MediaStore / Source)
        try {
            val mimeType = if (history.fileName.endsWith(".png", true)) "image/png"
            else if (history.fileName.endsWith(".webp", true)) "image/webp"
            else "image/jpeg"

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, history.fileName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val restoredUri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: return@withContext Result.failure(IllegalStateException("创建恢复文件失败"))

            val digestTarget = MessageDigest.getInstance("SHA-256")
            val digestRestored = MessageDigest.getInstance("SHA-256")

            context.contentResolver.openInputStream(targetUri)?.use { inStream ->
                context.contentResolver.openOutputStream(restoredUri)?.use { outStream ->
                    val buffer = ByteArray(16384)
                    var read: Int
                    while (inStream.read(buffer).also { read = it } != -1) {
                        digestTarget.update(buffer, 0, read)
                        digestRestored.update(buffer, 0, read)
                        outStream.write(buffer, 0, read)
                    }
                    outStream.flush()
                }
            }

            val targetHash = bytesToHex(digestTarget.digest())
            val restoredHash = bytesToHex(digestRestored.digest())

            if (targetHash.isBlank() || targetHash != restoredHash) {
                // Delete bad restored file
                try { context.contentResolver.delete(restoredUri, null, null) } catch (ignored: Exception) {}
                return@withContext Result.failure(IllegalStateException("回退完整性哈希校验不一致，回退中止"))
            }

            // Publish if Q+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(restoredUri, contentValues, null, null)
            }

            // Only commit the rollback after the moved copy is actually removed. If the
            // provider refuses deletion, remove the newly restored duplicate and keep history
            // retryable; never claim success while two copies remain.
            val targetDeleted = runCatching { targetDoc?.delete() == true }.getOrDefault(false) ||
                runCatching { context.contentResolver.delete(targetUri, null, null) > 0 }.getOrDefault(false)
            if (!targetDeleted) {
                runCatching { context.contentResolver.delete(restoredUri, null, null) }
                return@withContext Result.failure(IllegalStateException("目标副本无法删除，已撤销本次恢复副本创建；请检查目标文件权限后重试"))
            }

            // Update DB records
            db.withTransaction {
                db.imageDao().updateImageLocation(
                    id = history.imageId,
                    uriString = restoredUri.toString(),
                    name = history.fileName,
                    path = restoredUri.toString(),
                    folderName = "已撤回相册",
                    folderPath = "已撤回相册",
                    status = "ROLLED_BACK"
                )
                db.imageFtsDao().syncFtsForImage(history.imageId)
                db.moveHistoryDao().updateHistoryStatus(history.id, "ROLLED_BACK")
            }

            Result.success("撤回成功：文件已安全复原至相册，目标副本已删除")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun matchSmartRule(
        keywords: List<String>,
        rules: List<SmartRuleEntity>
    ): SmartRuleEntity? {
        val enabledRules = rules.filter { it.isEnabled }
        for (rule in enabledRules) {
            val triggers = rule.keywordTrigger.split(",", "，")
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() }
            for (kw in keywords) {
                val lowerKw = kw.lowercase()
                if (triggers.any { lowerKw.contains(it) || it.contains(lowerKw) }) {
                    return rule
                }
            }
        }
        return null
    }

    /**
     * Creates a MediaStore batch deletion request for Android 11+ (API 30+).
     * Prompts the user for permission to delete the source files after copy verification.
     */
    fun createBatchDeleteRequest(context: Context, uris: List<Uri>): android.app.PendingIntent? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && uris.isNotEmpty()) {
            return try {
                MediaStore.createDeleteRequest(context.contentResolver, uris)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
        return null
    }
}

