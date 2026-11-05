package com.toimg.app.feature.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import com.toimg.app.core.di.AppContainer
import kotlinx.coroutines.flow.flowOf
import com.toimg.app.core.work.ScanTaskScheduler
import com.toimg.app.data.local.entity.ScanSessionEntity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toimg.app.core.util.ScanManager

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.RectangleShape

@Composable
fun ScanProgressScreen(
    onViewImages: () -> Unit,
    onBackToScope: () -> Unit,
    onBackHome: () -> Unit
) {
    val p by ScanManager.progress.collectAsState()
    val context = LocalContext.current
    val sessionFlow = remember(context) { AppContainer.database(context).scanSessionDao().observeLatest() }
    val session by sessionFlow.collectAsState(initial = null)
    val sourceRepository = remember(context) { AppContainer.imageSourceRepository(context) }
    val sourceSummaries by sourceRepository.observeSources().collectAsState(initial = emptyList())
    val sourceStatsFlow = remember(session?.id) {
        session?.id?.let { sourceRepository.observeScanStatsForSession(it) } ?: flowOf(emptyList())
    }
    val sourceStats by sourceStatsFlow.collectAsState(initial = emptyList())
    val showLive = p.hasStarted
    val queuedFlag = !showLive && session == null && ScanTaskScheduler.hasActiveTask(context)
    val persistedActive = !showLive && (queuedFlag || session?.status?.let { it in ScanSessionEntity.ACTIVE } == true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!p.hasStarted && session == null && !queuedFlag) {
            Text("当前没有扫描任务", style = MaterialTheme.typography.titleMedium)
            Text("请先选择扫描范围并点击「开始扫描」。", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onBackToScope, shape = RectangleShape) { Text("选择扫描范围") }
            return@Column
        }

        val title = if (showLive) when {
            p.isCancelled -> "扫描已取消"
            p.isFinished -> "扫描完成"
            p.isPaused -> "扫描已暂停"
            else -> "正在扫描：${p.phase}"
        } else if (queuedFlag) {
            "扫描任务正在排队"
        } else when (session?.status) {
            ScanSessionEntity.QUEUED -> "扫描任务正在排队"
            ScanSessionEntity.RUNNING -> "扫描任务正在运行 / 恢复中"
            ScanSessionEntity.PAUSED -> "扫描已暂停"
            ScanSessionEntity.SUCCESS -> "扫描完成"
            ScanSessionEntity.PARTIAL -> "扫描部分完成"
            ScanSessionEntity.CANCELLED -> "扫描已取消"
            ScanSessionEntity.FAILED -> "扫描失败"
            else -> "扫描任务"
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RectangleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (p.isRunning || persistedActive) {
                    val totalSources = if (showLive) p.sourcesTotal else session?.sourcesTotal ?: 0
                    val doneSources = if (showLive) p.sourcesDone else session?.sourcesDone ?: 0
                    if (totalSources > 0) {
                        LinearProgressIndicator(
                            progress = { (doneSources.toFloat() / totalSources).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("来源进度 $doneSources/$totalSources", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
                Text(if (showLive) p.statusText else session?.statusText ?: if (queuedFlag) "任务已提交，正在等待后台调度器启动。" else "", style = MaterialTheme.typography.bodyMedium)
                val source = if (showLive) p.currentSource else session?.currentSource.orEmpty()
                val directory = if (showLive) p.currentDirectory else session?.currentDirectory.orEmpty()
                if (source.isNotBlank()) {
                    Text("当前来源：$source", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                }
                if (directory.isNotBlank() && (p.isRunning || persistedActive)) {
                    Text("当前目录：$directory", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("已发现", (if (showLive) p.discoveredCount else session?.discoveredCount ?: 0).toString(), Modifier.weight(1f))
            StatCard("已跳过", (if (showLive) p.skippedCount else session?.skippedCount ?: 0).toString(), Modifier.weight(1f))
            StatCard("不可用", (if (showLive) p.unavailableCount else session?.unavailableCount ?: 0).toString(), Modifier.weight(1f))
        }

        if (sourceStats.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("本次各来源进度", fontWeight = FontWeight.SemiBold)
                    sourceStats.forEach { stat ->
                        val sourceName = sourceSummaries.firstOrNull { it.source.id == stat.sourceId }?.source?.displayName
                            ?: stat.sourceId.takeLast(32)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("$sourceName · ${stat.status}", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "发现 ${stat.discoveredCount} · 新增关联 ${stat.addedCount} · 更新 ${stat.updatedCount} · 已存在 ${stat.existingCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (stat.skippedCount > 0 || stat.unavailableCount > 0 || stat.failedCount > 0) {
                                Text("跳过 ${stat.skippedCount} · 不可用 ${stat.unavailableCount} · 失败 ${stat.failedCount}", style = MaterialTheme.typography.bodySmall)
                            }
                            stat.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }

        if (p.skipReasons.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("跳过原因", fontWeight = FontWeight.SemiBold)
                    p.skipReasons.entries.sortedByDescending { it.value }.forEach { (reason, count) ->
                        Text("· $reason：$count", fontSize = 13.sp)
                    }
                }
            }
        }

        (if (showLive) p.errorMessage else session?.errorMessage)?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (showLive && p.warnings.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("扫描提示", fontWeight = FontWeight.SemiBold)
                    p.warnings.forEach { warning ->
                        Text(warning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (p.isRunning) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { if (p.isPaused) ScanTaskScheduler.resume(context) else ScanTaskScheduler.pause(context) },
                    shape = RectangleShape,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("scan_pause_button")
                ) { Text(if (p.isPaused) "继续" else "暂停") }
                OutlinedButton(
                    onClick = { ScanTaskScheduler.cancel(context) },
                    shape = RectangleShape,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("scan_cancel_button")
                ) { Text("取消") }
            }
            Text(
                "可以先离开本页，扫描会在后台继续；之后从首页或「任务历史」查看结果。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        } else if (persistedActive) {
            Text("扫描任务由 Android 后台任务调度器管理。即使离开页面，仍可从首页重新进入。若系统回收进程，任务会尝试重新调度并重新核对已授权来源。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (session?.status == ScanSessionEntity.PAUSED) {
                    OutlinedButton(onClick = { ScanTaskScheduler.resume(context) }, modifier = Modifier.weight(1f), shape = RectangleShape) { Text("继续扫描") }
                }
                OutlinedButton(onClick = { ScanTaskScheduler.cancel(context) }, modifier = Modifier.weight(1f), shape = RectangleShape) { Text("取消任务") }
                Button(onClick = onBackHome, modifier = Modifier.weight(1f), shape = RectangleShape) { Text("返回首页") }
            }
        } else {
            Text(
                "提示：扫描结果只代表 App 在当前授权范围内发现的图片，不代表整个手机的全部文件。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
            Button(
                onClick = onViewImages,
                shape = RectangleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("scan_view_images_button")
            ) { Text("查看图片") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBackToScope, shape = RectangleShape, modifier = Modifier.weight(1f)) { Text("调整范围重新扫描") }
                OutlinedButton(onClick = onBackHome, shape = RectangleShape, modifier = Modifier.weight(1f)) { Text("返回首页") }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RectangleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 12.sp)
        }
    }
}
