package com.toimg.app.feature.sources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.room.withTransaction
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.toimg.app.core.di.AppContainer
import com.toimg.app.core.util.ImageIndexIntegrityAuditor
import com.toimg.app.core.util.ImageIndexIntegrityReport
import com.toimg.app.data.local.entity.ImageSourceEntity
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/** F4 来源管理：列表、实时关联数、详情、扫描历史与项目内显示名称编辑。 */
@Composable
fun SourceManagementScreen() {
    val context = LocalContext.current
    val repository = remember { AppContainer.imageSourceRepository(context) }
    val sources by repository.observeSources().collectAsState(initial = emptyList())
    val lifecycleEvents by repository.observeRecentLifecycleEvents(30).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var pendingRemoval by remember { mutableStateOf<ImageSourceEntity?>(null) }
    var selectedSource by remember { mutableStateOf<ImageSourceEntity?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var reauthTarget by remember { mutableStateOf<ImageSourceEntity?>(null) }
    var pendingReauthUri by remember { mutableStateOf<Uri?>(null) }
    var integrityReport by remember { mutableStateOf<ImageIndexIntegrityReport?>(null) }
    var checkingIntegrity by remember { mutableStateOf(false) }
    fun isSourceAccessible(source: ImageSourceEntity): Boolean {
        if (source.id == ImageSourceEntity.MEDIASTORE_SOURCE_ID || source.id == ImageSourceEntity.PICKER_SOURCE_ID) return true
        val granted = context.contentResolver.persistedUriPermissions.any {
            it.uri.toString() == source.uriString && it.isReadPermission
        }
        if (!granted) return false
        return runCatching {
            val root = DocumentFile.fromTreeUri(context, Uri.parse(source.treeUriString))
            root != null && root.exists() && root.canRead()
        }.getOrDefault(false)
    }

    val reauthLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri: Uri? ->
        if (treeUri != null && reauthTarget != null) pendingReauthUri = treeUri
        else reauthTarget = null
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val db = AppContainer.database(context)
            val now = System.currentTimeMillis()
            val cutoff = now - 6L * 60L * 60L * 1000L
            db.withTransaction {
                db.scanSessionDao().failStalePickerSessions(cutoff, now)
                db.imageSourceDao().failStalePickerStats(cutoff, now)
                db.imageSourceDao().markPickerSourceInterrupted()
            }
        }
    }

    LaunchedEffect(sources) {
        sources.forEach { summary ->
            val source = summary.source
            if (source.id != ImageSourceEntity.MEDIASTORE_SOURCE_ID && source.id != ImageSourceEntity.PICKER_SOURCE_ID) {
                val accessible = isSourceAccessible(source)
                val desiredStatus = if (accessible) {
                    if (source.lifecycleStatus == ImageSourceEntity.AUTH_EXPIRED) ImageSourceEntity.UNSCANNED else source.lifecycleStatus
                } else ImageSourceEntity.AUTH_EXPIRED
                if (source.isAuthorized != accessible || source.lifecycleStatus != desiredStatus) {
                    repository.updateSource(source.copy(isAuthorized = accessible, lifecycleStatus = desiredStatus,
                        errorMessage = if (accessible) null else "SAF 授权失效或目录已移动，请重新授权"))
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("图片来源管理", style = MaterialTheme.typography.headlineSmall)
        Text("已登记的来源会持续保留在这里；是否参与本次扫描由扫描目录页面单独决定。", style = MaterialTheme.typography.bodyMedium)
        if (lifecycleEvents.isNotEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("来源变更审计（最近 ${lifecycleEvents.size} 条）", style = MaterialTheme.typography.titleSmall)
                    lifecycleEvents.take(5).forEach { event ->
                        Text(
                            "${event.sourceName} · ${if (event.status == "COMPLETED") "已移出" else event.status} · 关联 ${event.linkedImageCount} · 清理索引 ${event.deletedImageCount} · 保留共享 ${event.retainedSharedImageCount} · 设备原图删除 ${event.deviceFilesDeleted}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(event.createdAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !checkingIntegrity, onClick = {
                scope.launch {
                    checkingIntegrity = true
                    integrityReport = runCatching {
                        ImageIndexIntegrityAuditor(AppContainer.database(context)).inspect()
                    }.getOrNull()
                    checkingIntegrity = false
                }
            }) { Text(if (checkingIntegrity) "检查中…" else "检查索引完整性") }
            if (integrityReport != null && (integrityReport!!.orphanFtsRows > 0 || integrityReport!!.imagesMissingFtsRows > 0)) {
                TextButton(onClick = {
                    scope.launch {
                        runCatching { ImageIndexIntegrityAuditor(AppContainer.database(context)).rebuildSearchIndex() }
                        integrityReport = runCatching { ImageIndexIntegrityAuditor(AppContainer.database(context)).inspect() }.getOrNull()
                    }
                }) { Text("重建搜索索引") }
            }
            if (integrityReport != null && (integrityReport!!.recognizedImagesMissingResult > 0 || integrityReport!!.successResultsWithStaleImageStatus > 0)) {
                TextButton(onClick = {
                    scope.launch {
                        ImageIndexIntegrityAuditor(AppContainer.database(context)).repairRecognitionStatusFlags()
                        integrityReport = runCatching { ImageIndexIntegrityAuditor(AppContainer.database(context)).inspect() }.getOrNull()
                    }
                }) { Text("修复识别状态") }
            }
        }
        integrityReport?.let { report ->
            Text("索引检查：${if (report.isHealthy) "正常" else "发现异常"} · 孤立识别 ${report.orphanRecognitions} · 孤立 FTS ${report.orphanFtsRows} · 缺失 FTS ${report.imagesMissingFtsRows} · 孤立来源关联 ${report.orphanSourceLinks} · 已识别但缺结果 ${report.recognizedImagesMissingResult} · 识别成功但状态未同步 ${report.successResultsWithStaleImageStatus}", style = MaterialTheme.typography.bodySmall)
        }
        if (sources.isEmpty()) {
            Text("暂时没有已登记的来源。请先进入“扫描目录”添加文件夹，或通过图片选择器选取图片。")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sources, key = { it.source.id }) { summary ->
                    val source = summary.source
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(source.displayName, style = MaterialTheme.typography.titleMedium)
                            val accessible = isSourceAccessible(source)
                            Text("状态：${source.lifecycleStatus} · ${if (accessible) "已授权" else "授权失效"}")
                            Text("来源关联 ${summary.linkedImageCount} 张 · 当前仍在此位置 ${summary.currentLocationCount?.let { "$it 张" } ?: "不适用"}")
                            Text("最近扫描：${source.lastScannedAt?.let { java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it)) } ?: "尚未扫描"}")
                            source.errorMessage?.let { Text("最近错误：$it", color = MaterialTheme.colorScheme.error) }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { selectedSource = source }) { Text("详情 / 编辑") }
                                TextButton(onClick = { pendingRemoval = source }) { Text("从项目移出") }
                            }
                        }
                    }
                }
            }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
    selectedSource?.let { source ->
        val stats by repository.observeScanStats(source.id).collectAsState(initial = emptyList())
        val persistedGrant = isSourceAccessible(source)
        var editedName by remember(source.id) { mutableStateOf(source.displayName) }
        AlertDialog(
            onDismissRequest = { selectedSource = null },
            title = { Text("来源详情") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = editedName, onValueChange = { editedName = it }, label = { Text("项目显示名称") }, singleLine = true)
                    Text("类型：${when (source.id) { ImageSourceEntity.MEDIASTORE_SOURCE_ID -> "系统媒体库"; ImageSourceEntity.PICKER_SOURCE_ID -> "图片选择器"; else -> "SAF 文件夹" }}")
                    Text("授权：${if (persistedGrant) "有效" else "失效 / 需要重新授权"}")
                    Text("状态：${source.lifecycleStatus}")
                    val sourceSummary = sources.firstOrNull { it.source.id == source.id }
                    Text("来源关联图片：${sourceSummary?.linkedImageCount ?: source.imageCount}（包含由此来源发现、后来移动的图片）")
                    val currentLocationLabel = when {
                        source.id == ImageSourceEntity.PICKER_SOURCE_ID -> "不适用（图片选择器来源）"
                        sourceSummary?.currentLocationCount != null -> "${sourceSummary.currentLocationCount} 张"
                        else -> "暂不可计算"
                    }
                    Text("当前仍位于此来源位置：$currentLocationLabel")
                    Text("最近扫描：${source.lastScannedAt?.let { java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it)) } ?: "尚未扫描"}")
                    Text("来源标识：${source.uriString}", style = MaterialTheme.typography.bodySmall)
                    Text("扫描历史（最近 ${stats.size} 次）", style = MaterialTheme.typography.titleSmall)
                    if (stats.isEmpty()) Text("暂无扫描记录")
                    stats.take(8).forEach { stat ->
                        Text("${stat.status} · 发现 ${stat.discoveredCount} · 新增关联 ${stat.addedCount} · 更新 ${stat.updatedCount} · 已存在 ${stat.existingCount} · 跳过 ${stat.skippedCount} · 不可用 ${stat.unavailableCount}" +
                            (stat.errorMessage?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (source.id != ImageSourceEntity.MEDIASTORE_SOURCE_ID && source.id != ImageSourceEntity.PICKER_SOURCE_ID && !persistedGrant) {
                        TextButton(onClick = { reauthTarget = source; reauthLauncher.launch(Uri.parse(source.treeUriString)) }) { Text("重新授权") }
                    }
                    TextButton(onClick = { selectedSource = null }) { Text("关闭") }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch {
                        repository.updateSource(source.copy(displayName = editedName.trim().ifBlank { source.displayName }))
                        selectedSource = null
                    }
                }) { Text("保存名称") }
            }
        )
    }
    val targetForReauth = reauthTarget
    val replacementUri = pendingReauthUri
    if (targetForReauth != null && replacementUri != null) {
        AlertDialog(
            onDismissRequest = { pendingReauthUri = null; reauthTarget = null },
            title = { Text("确认替换文件夹授权？") },
            text = {
                Text("你选择的新目录将作为「${targetForReauth.displayName}」的访问位置。原有图片索引和识别结果会保留并继续归属于这个来源。请确认新选择的确实是原来的文件夹；如果选错，来源归属可能不准确。")
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val ok = repository.reauthorizeSource(targetForReauth.id, replacementUri.toString(), targetForReauth.displayName)
                        message = if (ok) "${targetForReauth.displayName} 已重新授权；原有索引和识别结果已保留。" else "重新授权失败。若该来源正在扫描，请等待任务结束后重试。"
                        if (ok) selectedSource = null
                        pendingReauthUri = null
                        reauthTarget = null
                    }
                }) { Text("确认替换") }
            },
            dismissButton = { TextButton(onClick = { pendingReauthUri = null; reauthTarget = null }) { Text("取消") } }
        )
    }

    pendingRemoval?.let { source ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("从项目移出此来源？") },
            text = { Text("将删除项目内该来源独占的图片索引和识别结果；不会删除手机原图。仍被其他来源引用的图片会保留。此操作不可自动恢复。") },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val result = repository.removeSourceFromProject(source.id)
                        message = if (result.blockedByActiveScan) "该来源仍有扫描任务，暂不能移出。请先取消或等待扫描结束。" else "已移出：清理 ${result.removedImageCount} 张独占索引，保留 ${result.retainedSharedImageCount} 张共享索引。"
                        pendingRemoval = null
                    }
                }) { Text("确认移出") }
            },
            dismissButton = { TextButton(onClick = { pendingRemoval = null }) { Text("取消") } }
        )
    }
}
