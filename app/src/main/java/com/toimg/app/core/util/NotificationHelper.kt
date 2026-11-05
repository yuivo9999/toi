package com.toimg.app.core.util

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.toimg.app.MainActivity
import com.toimg.app.R
import com.toimg.app.core.service.RecognitionService
import com.toimg.app.domain.model.BatchProgressState
import com.toimg.app.navigation.AppRoute

object NotificationHelper {
    const val CHANNEL_PROGRESS = "recognition_progress"
    const val CHANNEL_RESULT = "recognition_result"
    const val ID_PROGRESS = 1001
    const val ID_RESULT = 1002
    const val ID_NEW_IMAGES = 1003
    const val EXTRA_OPEN_ROUTE = "toimg_open_route"

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PROGRESS, "识别进度", NotificationManager.IMPORTANCE_LOW).apply {
                description = "批量识别图片时显示的进度通知"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_RESULT, "识别结果与新图片", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "识别完成、发现新图片、可归类提案的提醒"
            }
        )
    }

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun contentIntent(context: Context, route: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (route != null) putExtra(EXTRA_OPEN_ROUTE, route)
        }
        return PendingIntent.getActivity(
            context,
            route?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun serviceAction(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, RecognitionService::class.java).setAction(action)
        return PendingIntent.getForegroundService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun buildProgressNotification(context: Context, p: BatchProgressState): Notification {
        val done = p.completedCount + p.failedCount
        val max = p.totalCount.coerceAtLeast(1)
        val title = when {
            p.isPaused -> "识别已暂停"
            p.totalBatches > 0 -> "正在识别图片 · 第 ${p.currentBatchIndex.coerceAtLeast(1)}/${p.totalBatches} 批"
            else -> "正在准备识别…"
        }
        val text = p.statusText.ifBlank { "已完成 $done / ${p.totalCount}" }
        return NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_toimg)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setProgress(max, done.coerceAtMost(max), p.totalCount == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(contentIntent(context, AppRoute.WORKSPACE))
            .addAction(
                0,
                if (p.isPaused) "继续" else "暂停",
                serviceAction(
                    context,
                    if (p.isPaused) RecognitionService.ACTION_RESUME else RecognitionService.ACTION_PAUSE,
                    1
                )
            )
            .addAction(0, "取消", serviceAction(context, RecognitionService.ACTION_CANCEL, 2))
            .build()
    }

    fun notifyProgress(context: Context, p: BatchProgressState) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.notify(ID_PROGRESS, buildProgressNotification(context, p))
    }

    fun notifyRecognitionFinished(context: Context, p: BatchProgressState, proposalCount: Int) {
        if (!canPost(context)) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannels(context)
        val title = if (p.failedCount == 0) "识别完成" else "识别完成（部分失败）"
        val text = buildString {
            append("成功 ${p.completedCount} 张")
            if (p.failedCount > 0) append("，失败 ${p.failedCount} 张（可在工作区「仅重试失败项」）")
            if (proposalCount > 0) append("；${proposalCount} 张可按智能规则归类")
        }
        val route = if (proposalCount > 0) AppRoute.SMART_PROPOSAL else AppRoute.WORKSPACE
        val n = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_stat_toimg)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(contentIntent(context, route))
            .build()
        nm.notify(ID_RESULT, n)
    }

    fun notifyNewImages(context: Context, count: Int, autoStarted: Boolean) {
        if (count <= 0 || !canPost(context)) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        ensureChannels(context)
        val text = if (autoStarted) {
            "发现 $count 张新图片，已自动开始识别"
        } else {
            "发现 $count 张新图片，已加入「未识别」队列，点击去识别"
        }
        val n = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_stat_toimg)
            .setContentTitle("ToImg 新图片")
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(context, AppRoute.WORKSPACE))
            .build()
        nm.notify(ID_NEW_IMAGES, n)
    }
}
