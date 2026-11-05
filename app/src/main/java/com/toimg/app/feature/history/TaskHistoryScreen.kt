package com.toimg.app.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.ExperimentalMaterial3Api
import com.toimg.app.data.local.entity.MoveHistoryEntity
import com.toimg.app.data.local.entity.TaskRecordEntity
import com.toimg.app.data.local.entity.ScanSessionEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun fmt(ts: Long): String = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date(ts))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskHistoryScreen(
    viewModel: TaskHistoryViewModel = viewModel()
) {
    val context = LocalContext.current
    val s by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.init(context) }

    Column(Modifier.fillMaxSize()) {
        if (s.interruptedCount > 0) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RectangleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("发现被中断的识别任务", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
                    Text(
                        "有 ${s.interruptedCount} 张图片上次识别到一半被中断。点击恢复后才会重新发送给 AI 服务。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Button(
                        onClick = { viewModel.resumeInterrupted(context) },
                        modifier = Modifier.testTag("resume_interrupted_button"),
                        shape = RectangleShape
                    ) {
                        Text("恢复识别")
                    }
                }
            }
        }

        PrimaryTabRow(selectedTabIndex = HistoryTab.entries.indexOf(s.tab)) {
            HistoryTab.entries.forEach { tab ->
                Tab(
                    selected = s.tab == tab,
                    onClick = { viewModel.setTab(tab) },
                    text = { Text(tab.label, fontSize = 13.sp) }
                )
            }
        }

        when (s.tab) {
            HistoryTab.SCAN -> ScanHistoryList(s.scanSessions, s.scanRecords)
            HistoryTab.RECOGNITION -> TaskRecordList(s.recognitionRecords, "还没有 AI 识别记录")
            HistoryTab.MOVE -> MoveRecordList(s.moveRecords, onRollback = { viewModel.rollbackMove(context, it) })
        }
    }

    s.message?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissMessage() },
            shape = RectangleShape,
            title = { Text("提示") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { viewModel.dismissMessage() }, shape = RectangleShape) { Text("确定") } }
        )
    }
}


@Composable
private fun ScanHistoryList(sessions: List<ScanSessionEntity>, legacyRecords: List<TaskRecordEntity>) {
    if (sessions.isEmpty() && legacyRecords.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("还没有扫描记录", color = MaterialTheme.colorScheme.outline)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(sessions, key = { "session-${it.id}" }) { session ->
            val names = session.selectedSourceNames.split('\n').filter { it.isNotBlank() }
            val ids = session.selectedSourceIds.split('\n').filter { it.isNotBlank() }
            val sourceLabel = when {
                names.isNotEmpty() -> names.joinToString("、")
                ids.isNotEmpty() -> ids.joinToString("、") { it.substringAfterLast('/').take(36) }
                else -> "旧版会话未保存来源快照"
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("扫描会话 · ${session.id.take(8)}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(statusLabel(session.status), color = statusColor(session.status), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text("来源：$sourceLabel", fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text("发现 ${session.discoveredCount} · 跳过 ${session.skippedCount} · 不可用 ${session.unavailableCount} · 来源完成 ${session.sourcesDone}/${session.sourcesTotal}", fontSize = 12.sp)
                    if (session.statusText.isNotBlank()) Text(session.statusText, fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                    session.errorMessage?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.error) }
                    Text("${fmt(session.startedAt)} → ${session.finishedAt?.let(::fmt) ?: "进行中 / 未完成"}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
        if (sessions.isEmpty() && legacyRecords.isNotEmpty()) {
            item { Text("兼容旧版扫描摘要", style = MaterialTheme.typography.titleSmall) }
            items(legacyRecords, key = { "legacy-${it.id}" }) { record ->
                Card(Modifier.fillMaxWidth(), shape = RectangleShape) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${record.title} · ${statusLabel(record.status)}", fontWeight = FontWeight.SemiBold)
                        Text("发现 ${record.totalCount} · 失败 ${record.failedCount} · 跳过 ${record.skippedCount}", fontSize = 12.sp)
                        Text(record.detail, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskRecordList(records: List<TaskRecordEntity>, emptyText: String) {
    if (records.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emptyText, color = MaterialTheme.colorScheme.outline)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(records, key = { it.id }) { r ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(r.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(statusLabel(r.status), color = statusColor(r.status), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        "总数 ${r.totalCount} · 成功 ${r.successCount} · 失败/不可用 ${r.failedCount} · 跳过/未完成 ${r.skippedCount}",
                        fontSize = 12.sp
                    )
                    if (r.detail.isNotBlank()) {
                        Text(r.detail, fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                    Text("${fmt(r.startedAt)} → ${fmt(r.finishedAt)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
private fun MoveRecordList(
    records: List<MoveHistoryEntity>,
    onRollback: (MoveHistoryEntity) -> Unit
) {
    if (records.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("还没有移动记录", color = MaterialTheme.colorScheme.outline)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(records, key = { it.id }) { r ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            r.fileName,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(statusLabel(r.status), color = statusColor(r.status), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text("来源：${r.sourcePath}", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("目标：${r.targetPath}", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    r.errorMessage?.takeIf { it.isNotBlank() }?.let {
                        Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(fmt(r.timestamp), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                        if (r.status != "ROLLED_BACK") {
                            TextButton(
                                onClick = { onRollback(r) },
                                shape = RectangleShape,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("撤销移动", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun statusLabel(status: String): String = when (status) {
    "SUCCESS" -> "成功"
    "PARTIAL" -> "部分失败"
    "PARTIALLY_COMPLETED" -> "部分完成"
    "CANCELLED" -> "已取消"
    "FAILED" -> "失败"
    "SKIPPED" -> "已跳过"
    "ROLLED_BACK" -> "已回滚"
    "QUEUED" -> "排队中"
    "RUNNING" -> "进行中"
    "PAUSED" -> "已暂停"
    else -> status
}

@Composable
private fun statusColor(status: String): Color = when (status) {
    "SUCCESS" -> MaterialTheme.colorScheme.primary
    "FAILED" -> MaterialTheme.colorScheme.error
    "RUNNING", "QUEUED", "PAUSED" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.secondary
}
