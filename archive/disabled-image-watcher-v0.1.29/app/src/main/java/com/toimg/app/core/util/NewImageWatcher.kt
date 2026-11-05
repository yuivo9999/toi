package com.toimg.app.core.util

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.toimg.app.core.work.NewImageWatchWorker
import com.toimg.app.data.local.entity.ImageEntity
import com.toimg.app.data.repository.AiConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * 监听系统相册的新增图片，自动加入待识别队列：
 * - App 进程存活时：ContentObserver（防抖 3 秒）即时响应；
 * - 进程已被回收时：WorkManager 内容 URI 触发器（[NewImageWatchWorker]）在新图出现后唤醒进程。
 * 两条路径共用 [NewImageIngestor]（互斥、幂等），不会重复入库。
 */
object NewImageWatcher {
    private const val WORK_NAME = "toimg_new_image_watch"
    private const val DEBOUNCE_MS = 3_000L

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var observer: ContentObserver? = null
    private var debounce: Runnable? = null

    /** 按当前设置注销监听，清理后台任务（监听器已停用）。 */
    @Synchronized
    fun apply(context: Context) {
        val app = context.applicationContext
        unregisterObserver(app)
        try {
            WorkManager.getInstance(app).cancelUniqueWork(WORK_NAME)
        } catch (e: Throwable) {
            // ignore
        }
    }

    private fun registerObserver(app: Context) {
        // 监听器已停用，不注册 ContentObserver
    }

    private fun unregisterObserver(app: Context) {
        observer?.let {
            try {
                app.contentResolver.unregisterContentObserver(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        observer = null
        debounce?.let { handler.removeCallbacks(it) }
        debounce = null
    }

    private fun scheduleIngest(app: Context) {
        // 监听器已停用
    }

    /** 监听器已停用，不做任何 Worker 调度。 */
    fun enqueueWorker(app: Context, keepExisting: Boolean) {
        try {
            WorkManager.getInstance(app).cancelUniqueWork(WORK_NAME)
        } catch (e: Throwable) {
            // ignore
        }
    }

    suspend fun ingestAndDispatch(app: Context): Int {
        val fresh = NewImageIngestor.ingest(app)
        if (fresh.isNotEmpty()) dispatch(app, fresh)
        return fresh.size
    }

    private fun dispatch(app: Context, fresh: List<ImageEntity>) {
        val cfg = AiConfigRepository(app).getAiConfig()
        val engineReady = cfg.isOfflineMode || cfg.isHybridMode || cfg.apiKey.isNotBlank()
        val auto = NewImageIngestor.isAutoRecognizeEnabled(app) && engineReady && !BackgroundBatchManager.isRunning()
        if (auto) {
            BackgroundBatchManager.startBatchRecognition(app, fresh)
            NotificationHelper.notifyNewImages(app, fresh.size, autoStarted = true)
        } else {
            NotificationHelper.notifyNewImages(app, fresh.size, autoStarted = false)
        }
    }
}
