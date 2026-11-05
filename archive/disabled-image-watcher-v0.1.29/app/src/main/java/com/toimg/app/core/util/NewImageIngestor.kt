package com.toimg.app.core.util

import com.toimg.app.core.di.AppContainer

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.toimg.app.data.local.entity.ImageEntity
import com.toimg.app.data.repository.LocalImageRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 把「监听期间新拍的照片 / 新截图」从系统媒体库增量写入本地索引（状态 SCANNED），
 * 它们会自动出现在工作区的「未识别」队列里。
 * 只建立本地索引，不读取内容、不上传；是否自动识别由用户在设置里单独开启。
 */
object NewImageIngestor {
    private const val PREFS = "toimg_watch"
    private const val KEY_ENABLED = "watch_enabled"
    private const val KEY_AUTO = "auto_recognize"
    private const val KEY_LAST_ADDED = "last_added_sec"

    private val mutex = Mutex()

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 监听已移除，默认关闭。 */
    fun isWatchEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    fun setWatchEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** 默认关闭：云端模式下自动识别意味着自动上传图片并产生费用，必须由用户主动开启。 */
    fun isAutoRecognizeEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO, false)

    fun setAutoRecognizeEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_AUTO, enabled).apply()
    }

    private fun hasMediaPermission(context: Context): Boolean {
        fun granted(p: String) =
            ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                granted(Manifest.permission.READ_MEDIA_IMAGES) ||
                    granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                granted(Manifest.permission.READ_MEDIA_IMAGES)
            else -> granted(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    /** 返回本次新入库的图片（可能为空）。幂等：重复调用不会产生重复记录。 */
    suspend fun ingest(context: Context): List<ImageEntity> = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        if (!isWatchEnabled(app) || !hasMediaPermission(app)) return@withContext emptyList()
        // 全量扫描进行中时让路，不推进游标，下次再补
        if (ScanManager.isRunning()) return@withContext emptyList()

        mutex.withLock {
            val prefs = prefs(app)
            val last = prefs.getLong(KEY_LAST_ADDED, -1L)
            if (last < 0L) {
                // 首次启用：以「现在」为起点，只关心之后新增的图片，不会把整个相册灌进队列
                prefs.edit().putLong(KEY_LAST_ADDED, System.currentTimeMillis() / 1000L).apply()
                return@withLock emptyList<ImageEntity>()
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Images.Media.RELATIVE_PATH
                else MediaStore.Images.Media.DATA
            )

            val candidates = mutableListOf<ImageEntity>()
            var maxAdded = last
            try {
                app.contentResolver.query(
                    collection,
                    projection,
                    "${MediaStore.Images.Media.DATE_ADDED} >= ?",
                    arrayOf(last.toString()),
                    "${MediaStore.Images.Media.DATE_ADDED} ASC"
                )?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val sizeCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                    val modCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                    val addedCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                    val mimeCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                    val bucketCol = c.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                    val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        c.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                    } else {
                        c.getColumnIndex(MediaStore.Images.Media.DATA)
                    }
                    while (c.moveToNext()) {
                        val id = c.getLong(idCol)
                        val added = c.getLong(addedCol)
                        if (added > maxAdded) maxAdded = added
                        val size = c.getLong(sizeCol)
                        val name = c.getString(nameCol) ?: "IMG_$id.jpg"
                        if (size <= 0L || name.startsWith(".")) continue
                        val path = if (pathCol >= 0) c.getString(pathCol) ?: "" else ""
                        val contentUri: Uri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                        candidates.add(
                            ImageEntity(
                                id = id.toString(),
                                uriString = contentUri.toString(),
                                name = name,
                                path = path,
                                size = size,
                                dateModified = c.getLong(modCol),
                                mimeType = c.getString(mimeCol) ?: "image/*",
                                folderName = if (bucketCol >= 0) c.getString(bucketCol) ?: "图库" else "图库",
                                folderPath = path
                            )
                        )
                    }
                }
            } catch (e: SecurityException) {
                return@withLock emptyList<ImageEntity>()
            } catch (e: Exception) {
                e.printStackTrace()
                return@withLock emptyList<ImageEntity>()
            }

            if (candidates.isEmpty()) {
                if (maxAdded > last) prefs.edit().putLong(KEY_LAST_ADDED, maxAdded).apply()
                return@withLock emptyList<ImageEntity>()
            }

            val db = AppContainer.database(app)
            val existing = candidates.chunked(500)
                .flatMap { chunk -> db.imageDao().getImagesByIds(chunk.map { it.id }) }
                .map { it.id }
                .toSet()
            // App 自己「移动/撤回」产生的新 MediaStore 记录不算新图片（同名同大小且已有 MOVED/ROLLED_BACK 记录）
            val fresh = candidates.filter { it.id !in existing }
                .filter { db.imageDao().countMovedLike(it.name, it.size) == 0 }

            val persisted = if (fresh.isNotEmpty()) {
                // Keep the background image listener on the same identity-ingest path as SAF,
                // MediaStore scans and Photo Picker; never bypass the cross-entry mutex/upsert.
                fresh.map { ImageIdentityResolver.resolveAndPersist(app, db, it) }
            } else emptyList()
            if (persisted.isNotEmpty()) {
                try {
                    LocalImageRepositoryImpl().computeMissingHashes(app) { _, _ -> }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            prefs.edit().putLong(KEY_LAST_ADDED, maxAdded).apply()
            persisted.distinctBy { it.id }
        }
    }
}
