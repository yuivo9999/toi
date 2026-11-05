package com.toimg.app.feature.result

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.toimg.app.domain.model.ConflictStrategy
import com.toimg.app.domain.model.MoveItemResult
import com.toimg.app.domain.model.MoveItemStatus
import com.toimg.app.domain.model.MovePreviewItem

@Composable
fun MoveResultScreen(
    onBackHome: () -> Unit,
    viewModel: MoveResultViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    val safFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            val doc = DocumentFile.fromTreeUri(context, treeUri)
            val name = doc?.name ?: "SAF目标文件夹"
            viewModel.setTargetDirectory(context, treeUri, name)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.init(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        when (uiState.stage) {
            MoveScreenStage.PREVIEW -> {
                PreviewSection(
                    uiState = uiState,
                    onSelectFolder = { safFolderLauncher.launch(null) },
                    onSetFolder = { uri, name -> viewModel.setTargetDirectory(context, uri, name) },
                    onSetConflict = { strategy -> viewModel.setConflictStrategy(context, strategy) },
                    onConfirmExecute = { viewModel.startMoveExecution(context) },
                    onBackHome = onBackHome,
                    context = context
                )
            }

            MoveScreenStage.EXECUTING -> {
                ExecutingSection(
                    uiState = uiState,
                    onCancel = { viewModel.cancelExecution() },
                    context = context
                )
            }

            MoveScreenStage.REPORT -> {
                ReportSection(
                    uiState = uiState,
                    onFilterStatus = { viewModel.setFilterStatus(it) },
                    onBackHome = onBackHome,
                    context = context
                )
            }
        }
    }

    uiState.errorMessage?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            title = { Text("授权或写入异常") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissError() }) {
                    Text("确定")
                }
            }
        )
    }
}

@Composable
private fun PreviewSection(
    uiState: MoveResultUiState,
    onSelectFolder: () -> Unit,
    onSetFolder: (android.net.Uri, String) -> Unit,
    onSetConflict: (ConflictStrategy) -> Unit,
    onConfirmExecute: () -> Unit,
    onBackHome: () -> Unit,
    context: Context
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(Icons.Default.DriveFileMove, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Column {
                Text(text = "移动确认与预览清单", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(text = "用户旅程第 11 步：核对目标路径与冲突策略，确认后再移动", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
        }

        // 1. Target Directory Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RectangleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Text(text = "目标文件夹: ${uiState.targetDirName}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = onSelectFolder,
                        shape = RectangleShape,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("指定/新建 SAF 目录", fontSize = 12.sp)
                    }
                }

                if (uiState.savedFolders.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(uiState.savedFolders) { folder ->
                            if (folder.uri != null) {
                                FilterChip(
                                    selected = uiState.targetDirUri == folder.uri,
                                    onClick = { onSetFolder(folder.uri, folder.name) },
                                    shape = RectangleShape,
                                    label = { Text(folder.name, fontSize = 12.sp) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Conflict Strategy Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RectangleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "同名冲突处理策略 (严禁静默覆盖)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ConflictStrategy.entries.forEach { strategy ->
                        FilterChip(
                            selected = uiState.conflictStrategy == strategy,
                            onClick = { onSetConflict(strategy) },
                            shape = RectangleShape,
                            label = { Text(strategy.displayName, fontSize = 12.sp) }
                        )
                    }
                }
                Text(text = uiState.conflictStrategy.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
            }
        }

        // 3. Safety Notice
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RectangleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
        ) {
            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                Text(
                    text = "⚠️ 零数据丢失机制：优先采用原生移动；回退方案将先复制、校验 SHA-256 完全一致后才删除源文件。若源文件无法删除将标记为部分完成，绝不误删原图。",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        // 4. Move Plan Summary & Items List
        uiState.movePlan?.let { plan ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "预定移动 ${plan.totalCount} 张", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(shape = RectangleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Text("可移动 ${plan.validCount}", fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    if (plan.conflictCount > 0) {
                        Surface(shape = RectangleShape, color = MaterialTheme.colorScheme.errorContainer) {
                            Text("同名碰撞 ${plan.conflictCount}", fontSize = 11.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (uiState.isGeneratingPlan) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.movePlan?.items.isNullOrEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无待移动预览的图片，请先指定目标文件夹。", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyPreviewList(items = uiState.movePlan!!.items, context = context)
            }
        }

        // Action Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(onClick = onBackHome, modifier = Modifier.weight(1f)) {
                Text("返回首页")
            }

            Button(
                onClick = onConfirmExecute,
                enabled = uiState.movePlan?.canExecute == true,
                modifier = Modifier
                    .weight(1.5f)
                    .testTag("confirm_execute_move_button")
            ) {
                Text("确认真实移动 (${uiState.movePlan?.validCount ?: 0}张) ➔")
            }
        }
    }
}

@Composable
private fun ExecutingSection(
    uiState: MoveResultUiState,
    onCancel: () -> Unit,
    context: Context
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                Text(text = "真实文件移动执行中...", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick = onCancel,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("取消剩余", fontSize = 12.sp)
            }
        }

        val total = uiState.movePlan?.totalCount ?: 1
        val progressFraction = uiState.currentIndex.toFloat() / total
        LinearProgressIndicator(progress = { progressFraction }, modifier = Modifier.fillMaxWidth())

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "正在处理第 ${uiState.currentIndex} / $total 项", fontWeight = FontWeight.SemiBold)
                Text(text = "文件名: ${uiState.currentItemName}", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("成功: ${uiState.successCount}", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    if (uiState.partialCount > 0) {
                        Text("部分完成: ${uiState.partialCount}", color = MaterialTheme.colorScheme.tertiary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (uiState.failedCount > 0) {
                        Text("失败: ${uiState.failedCount}", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (uiState.skippedCount > 0) {
                        Text("跳过: ${uiState.skippedCount}", color = MaterialTheme.colorScheme.outline, fontSize = 12.sp)
                    }
                }
            }
        }

        // Live Log of executed items
        Text(text = "实时操作日志", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(uiState.executedResults.reversed()) { res ->
                ResultItemRow(item = res, context = context)
            }
        }
    }
}

@Composable
private fun ReportSection(
    uiState: MoveResultUiState,
    onFilterStatus: (MoveItemStatus?) -> Unit,
    onBackHome: () -> Unit,
    context: Context
) {
    val report = uiState.finalReport

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Column {
                Text(text = "移动任务结果报告", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(text = "用户旅程第 13 步：核验移动结果，更新本地记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
        }

        // Summary Metric Cards
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RectangleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "目标文件夹: ${report?.targetDirName}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MetricBox(title = "成功", count = report?.successCount ?: 0, color = MaterialTheme.colorScheme.primary)
                    MetricBox(title = "部分完成", count = report?.partialCount ?: 0, color = MaterialTheme.colorScheme.tertiary)
                    MetricBox(title = "跳过", count = report?.skippedCount ?: 0, color = MaterialTheme.colorScheme.outline)
                    MetricBox(title = "失败", count = report?.failedCount ?: 0, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // Filter Chips Row
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                FilterChip(
                    selected = uiState.selectedFilterStatus == null,
                    onClick = { onFilterStatus(null) },
                    label = { Text("全部 (${report?.totalCount ?: 0})", fontSize = 12.sp) }
                )
            }
            item {
                FilterChip(
                    selected = uiState.selectedFilterStatus == MoveItemStatus.SUCCESS,
                    onClick = { onFilterStatus(MoveItemStatus.SUCCESS) },
                    label = { Text("成功 (${report?.successCount ?: 0})", fontSize = 12.sp) }
                )
            }
            if ((report?.partialCount ?: 0) > 0) {
                item {
                    FilterChip(
                        selected = uiState.selectedFilterStatus == MoveItemStatus.PARTIALLY_COMPLETED,
                        onClick = { onFilterStatus(MoveItemStatus.PARTIALLY_COMPLETED) },
                        label = { Text("部分完成 (${report?.partialCount})", fontSize = 12.sp) }
                    )
                }
            }
            if ((report?.failedCount ?: 0) > 0) {
                item {
                    FilterChip(
                        selected = uiState.selectedFilterStatus == MoveItemStatus.FAILED,
                        onClick = { onFilterStatus(MoveItemStatus.FAILED) },
                        label = { Text("失败 (${report?.failedCount})", fontSize = 12.sp) }
                    )
                }
            }
            if ((report?.skippedCount ?: 0) > 0) {
                item {
                    FilterChip(
                        selected = uiState.selectedFilterStatus == MoveItemStatus.SKIPPED,
                        onClick = { onFilterStatus(MoveItemStatus.SKIPPED) },
                        label = { Text("已跳过 (${report?.skippedCount})", fontSize = 12.sp) }
                    )
                }
            }
        }

        // Results List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            items(uiState.filteredReportItems) { item ->
                ResultItemRow(item = item, context = context)
            }
        }

        // Bottom Action Button
        Button(
            onClick = onBackHome,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .testTag("report_back_home_button")
        ) {
            Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("完成整理，返回首页")
        }
    }
}

@Composable
private fun MetricBox(title: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "$count", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = color)
        Text(text = title, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun ResultItemRow(item: MoveItemResult, context: Context) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RectangleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(
            containerColor = when (item.status) {
                MoveItemStatus.SUCCESS -> MaterialTheme.colorScheme.surfaceVariant
                MoveItemStatus.PARTIALLY_COMPLETED -> MaterialTheme.colorScheme.tertiaryContainer
                MoveItemStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
                MoveItemStatus.SKIPPED -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(item.targetUri ?: item.sourceUri)
                    .build(),
                contentDescription = item.originalName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(50.dp)
                    .clip(RectangleShape)
            )

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = item.finalName, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Surface(
                        shape = RectangleShape,
                        color = when (item.status) {
                            MoveItemStatus.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
                            MoveItemStatus.PARTIALLY_COMPLETED -> MaterialTheme.colorScheme.tertiaryContainer
                            MoveItemStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {
                        Text(
                            text = item.status.displayName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                if (item.status == MoveItemStatus.SUCCESS && !item.targetHash.isNullOrBlank()) {
                    Text(text = "✔ SHA-256 核验一致 (${item.targetHash.take(8)}...) | 方式: ${item.executionMethod}", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                } else if (item.errorMessage != null) {
                    Text(text = item.errorMessage, fontSize = 11.sp, color = MaterialTheme.colorScheme.error, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun LazyPreviewList(items: List<MovePreviewItem>, context: Context) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
        items(items, key = { it.imageItem.id }) { previewItem ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RectangleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(previewItem.imageItem.uri).crossfade(true).build(),
                        contentDescription = previewItem.imageItem.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RectangleShape)
                    )

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = previewItem.imageItem.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = previewItem.sourcePathDisplay, fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(12.dp), tint = MaterialTheme.colorScheme.primary)
                            Text(text = "【${previewItem.targetDirName}】${previewItem.proposedTargetName}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        }
                        if (previewItem.hasConflict) {
                            Text(text = "⚡ 存在同名碰撞 ➔ 重命名为: ${previewItem.proposedTargetName}", fontSize = 11.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
