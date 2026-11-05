package com.toimg.app.core.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.toimg.app.core.util.BackgroundBatchManager
import com.toimg.app.core.util.NotificationHelper
import com.toimg.app.domain.model.BatchProgressState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 批量识别的前台服务：让识别在切后台 / 锁屏时继续运行，并在通知栏显示进度（可暂停、继续、取消）。
 * 识别逻辑仍由 [BackgroundBatchManager] 执行，本服务只负责「保活 + 通知 + 生命周期」。
 *
 * 进程被系统杀掉后，START_STICKY 会让系统重启本服务（intent 为 null），
 * 此时若上次任务是用户主动发起且未结束，则自动从「处理中」的图片继续。
 */
class RecognitionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null
    private var sawRunning = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        NotificationHelper.ensureChannels(this)
        enterForeground(
            NotificationHelper.buildProgressNotification(this, BackgroundBatchManager.progressState.value)
        )

        when (intent?.action) {
            ACTION_PAUSE -> BackgroundBatchManager.pause()
            ACTION_RESUME -> BackgroundBatchManager.resume()
            ACTION_CANCEL -> BackgroundBatchManager.stopBatch()
        }

        sawRunning = BackgroundBatchManager.isRunning()
        if (observeJob == null) {
            observeJob = scope.launch {
                BackgroundBatchManager.progressState.collect { handleState(it) }
            }
        }

        if (intent == null) {
            // 系统在进程被杀后重启了服务：尝试续跑上次未完成的任务
            scope.launch {
                val resumed = try {
                    BackgroundBatchManager.resumeAfterProcessDeath(applicationContext)
                } catch (e: Exception) {
                    false
                }
                if (!resumed) shutDown()
            }
        }
        return START_STICKY
    }

    private fun handleState(p: BatchProgressState) {
        if (p.isRunning) {
            sawRunning = true
            NotificationHelper.notifyProgress(this, p)
        } else if (sawRunning) {
            shutDown()
        }
    }

    private fun enterForeground(n: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NotificationHelper.ID_PROGRESS,
                    n,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NotificationHelper.ID_PROGRESS, n)
            }
        } catch (e: Exception) {
            // 系统不允许从后台启动前台服务时，识别仍会在进程内继续，只是失去保活
            e.printStackTrace()
        }
    }

    private fun shutDown() {
        observeJob?.cancel()
        observeJob = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Android 15+：dataSync 前台服务累计时长到达系统上限时回调，必须尽快停止。 */
    override fun onTimeout(startId: Int, fgsType: Int) {
        BackgroundBatchManager.interruptKeepingProgress()
        shutDown()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_PAUSE = "com.toimg.app.action.PAUSE"
        const val ACTION_RESUME = "com.toimg.app.action.RESUME"
        const val ACTION_CANCEL = "com.toimg.app.action.CANCEL"

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(
                    context.applicationContext,
                    Intent(context.applicationContext, RecognitionService::class.java)
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
