package com.toimg.app.feature.workspace

import com.toimg.app.core.ui.theme.MediaLabelBg
import com.toimg.app.core.ui.theme.MediaLabelSub
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import android.widget.Toast
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
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.toimg.app.domain.model.ImageItem
import com.toimg.app.domain.model.RecognitionResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ImageWorkspaceScreen(
    onPreviewComplete: () -> Unit,
    onNavigateToSettings: (() -> Unit)? = null,
    onOpenConfirm: () -> Unit = {},
    onOpenProposals: () -> Unit = {},
    viewModel: WorkspaceViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val pagedItems = remember(viewModel, context) {
        viewModel.getPagedImages(context)
    }.collectAsLazyPagingItems()

    LaunchedEffect(Unit) {
        viewModel.init(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // AI Status & Configuration Header
        if (!uiState.isKeyConfigured) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RectangleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "尚未配置 AI API Key",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "必须先在设置中填写并保存您的 AI API Key 后才能执行识别任务。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    if (onNavigateToSettings != null) {
                        Button(
                            onClick = onNavigateToSettings,
                            shape = RectangleShape,
                            modifier = Modifier.testTag("goto_settings_button")
                        ) {
                            Text("去设置", fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            // AI Configuration Bar
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RectangleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "模型: ${uiState.aiConfig.modelName}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("批次大小: ", style = MaterialTheme.typography.labelSmall)
                            com.toimg.app.data.repository.AiConfigRepository.BATCH_SIZE_OPTIONS.forEach { size ->
                                FilterChip(
                                    selected = uiState.batchSize == size,
                                    enabled = !uiState.progress.isRunning,
                                    onClick = { viewModel.setBatchSize(context, size) },
                                    shape = RectangleShape,
                                    label = { Text("$size", fontSize = 12.sp) },
                                    modifier = Modifier
                                        .padding(horizontal = 2.dp)
                                        .testTag("batch_size_chip_$size")
                                )
                            }
                        }
                    }

                    Text(
                        text = "🔒 隐私告知：识别图片将被等比压缩后发送给配置的第三方 AI 服务，费用依服务商为准。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        // Active Batch Progress Bar
        if (uiState.progress.isRunning || uiState.progress.statusText.isNotBlank()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RectangleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when {
                                uiState.progress.isRunning && uiState.progress.isPaused -> "识别已暂停"
                                uiState.progress.isRunning -> "正在批量识别（第 ${uiState.progress.currentBatchIndex}/${uiState.progress.totalBatches} 批，每批 ${uiState.progress.batchSize} 张）"
                                else -> "批次任务通知"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (uiState.progress.isRunning) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        if (uiState.progress.isPaused) viewModel.resumeRecognition() else viewModel.pauseRecognition()
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.testTag("workspace_pause_button")
                                ) {
                                    Text(if (uiState.progress.isPaused) "继续" else "暂停", fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = { viewModel.cancelRecognition() },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("取消任务", fontSize = 12.sp)
                                }
                            }
                        } else if (uiState.progress.isFinished) {
                            TextButton(
                                onClick = { viewModel.dismissProgress() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) { Text("关闭", fontSize = 12.sp) }
                        }
                    }

                    if (uiState.progress.isRunning) {
                        val progressFraction = if (uiState.progress.totalCount > 0) {
                            (uiState.progress.completedCount + uiState.progress.failedCount).toFloat() / uiState.progress.totalCount
                        } else 0f
                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = uiState.progress.statusText,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "成功 ${uiState.progress.completedCount}",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (uiState.progress.failedCount > 0) {
                                Text(
                                    text = "失败 ${uiState.progress.failedCount}",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    if (uiState.progress.isFinished && !uiState.progress.isRunning) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (uiState.failedImages.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = { if (viewModel.prepareRetryFailed()) onOpenConfirm() },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.testTag("workspace_retry_failed_button")
                                ) {
                                    Text("仅重试失败项 (${uiState.failedImages.size})", fontSize = 12.sp)
                                }
                            }
                            if (uiState.proposalCount > 0) {
                                Button(
                                    onClick = onOpenProposals,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.testTag("workspace_open_proposals_button")
                                ) {
                                    Text("智能分类提案 (${uiState.proposalCount})", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Tabs Row: 未识别 / 已识别结果 / 全部
        PrimaryTabRow(
            selectedTabIndex = uiState.currentTab.ordinal,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = uiState.currentTab == WorkspaceTab.UNRECOGNIZED,
                onClick = { viewModel.setTab(WorkspaceTab.UNRECOGNIZED) },
                text = { Text("未识别 (${uiState.unrecognizedImages.size})", fontSize = 13.sp) },
                modifier = Modifier.testTag("tab_unrecognized")
            )
            Tab(
                selected = uiState.currentTab == WorkspaceTab.RECOGNIZED,
                onClick = { viewModel.setTab(WorkspaceTab.RECOGNIZED) },
                text = { Text("已识别结果 (${uiState.recognizedImages.size})", fontSize = 13.sp) },
                modifier = Modifier.testTag("tab_recognized")
            )
            Tab(
                selected = uiState.currentTab == WorkspaceTab.ALL,
                onClick = { viewModel.setTab(WorkspaceTab.ALL) },
                text = { Text("全部 (${uiState.images.size})", fontSize = 13.sp) },
                modifier = Modifier.testTag("tab_all")
            )
        }

        Spacer(Modifier.height(8.dp))

        // Multi-select & Action bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "已选 ${uiState.selectedCount} / ${uiState.currentTabImages.size} 张",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { viewModel.selectAllInCurrentTab() },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("全选本页", fontSize = 12.sp)
                }
                TextButton(
                    onClick = { viewModel.invertSelectionInCurrentTab() },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("反选", fontSize = 12.sp)
                }
                if (uiState.selectedCount > 0) {
                    TextButton(
                        onClick = { viewModel.clearSelection() },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("清空", fontSize = 12.sp)
                    }
                }
                if (uiState.failedImages.isNotEmpty() && uiState.currentTab == WorkspaceTab.UNRECOGNIZED) {
                    TextButton(
                        onClick = { if (viewModel.prepareRetryFailed()) onOpenConfirm() },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("重试失败项 (${uiState.failedImages.size})", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        // Image Grid List
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (uiState.currentTabImages.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = when (uiState.currentTab) {
                            WorkspaceTab.UNRECOGNIZED -> "没有待识别的图片，所有图片均已识别完毕！"
                            WorkspaceTab.RECOGNIZED -> "暂无已识别的图片，请先挑选并启动识别任务。"
                            WorkspaceTab.ALL -> "工作区暂无图片记录，请先在【选择图片】中扫描或挑选图片。"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (uiState.currentTab == WorkspaceTab.ALL && pagedItems.itemCount > 0) {
                // 大图库采用 Paging 3 分页增量流式渲染，避免海量图片一次性加载导致 OOM
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(bottom = 70.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        count = pagedItems.itemCount,
                        key = pagedItems.itemKey { it.id }
                    ) { index ->
                        val item = pagedItems[index]
                        if (item != null) {
                            val isSelected = uiState.selectedImageIds.contains(item.id)
                            val recognition = uiState.recognitions[item.id]

                            WorkspaceImageCard(
                                item = item,
                                recognition = recognition,
                                isSelected = isSelected,
                                onToggleSelect = { viewModel.toggleSelection(item.id) },
                                onClickDetail = { viewModel.openDetail(item) },
                                context = context
                            )
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(bottom = 70.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = uiState.currentTabImages,
                        key = { it.id }
                    ) { item ->
                        val isSelected = uiState.selectedImageIds.contains(item.id)
                        val recognition = uiState.recognitions[item.id]

                        WorkspaceImageCard(
                            item = item,
                            recognition = recognition,
                            isSelected = isSelected,
                            onToggleSelect = { viewModel.toggleSelection(item.id) },
                            onClickDetail = { viewModel.openDetail(item) },
                            context = context
                        )
                    }
                }
            }
        }

        // Bottom Action Button Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    if (viewModel.prepareConfirm()) onOpenConfirm()
                },
                enabled = !uiState.progress.isRunning && uiState.isKeyConfigured && (uiState.selectedCount > 0 || uiState.unrecognizedImages.isNotEmpty()),
                shape = RectangleShape,
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .testTag("start_batch_recognition_button")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (uiState.selectedCount > 0) "启动所选 (${uiState.selectedCount}) 识别" else "识别全部未识别 (${uiState.unrecognizedImages.size})",
                    fontSize = 13.sp
                )
            }

            OutlinedButton(
                onClick = onPreviewComplete,
                shape = RectangleShape,
                modifier = Modifier
                    .height(40.dp)
                    .testTag("goto_result_button")
            ) {
                Text("整理结果/移动", fontSize = 13.sp)
            }
        }
    }

    // Detail Inspection Dialog
    uiState.selectedDetailItem?.let { (item, recognition) ->
        ImageDetailDialog(
            item = item,
            recognition = recognition,
            onDismiss = { viewModel.closeDetail() },
            onReRecognize = {
                viewModel.closeDetail()
                if (viewModel.prepareConfirm(listOf(item), includeRecognized = true)) onOpenConfirm()
            },
            onSaveKeywords = { newKeywords ->
                viewModel.updateKeywords(context, item.id, newKeywords)
            },
            context = context
        )
    }


    // Error Message Alert
    uiState.errorMessage?.let { error ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissError() },
            title = { Text("提示") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissError() }) {
                    Text("确定")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkspaceImageCard(
    item: ImageItem,
    recognition: RecognitionResult?,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onClickDetail: () -> Unit,
    context: Context
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RectangleShape)
            .clickable { onClickDetail() }
            .testTag("workspace_card_${item.id}"),
        shape = RectangleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.2f)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.uri)
                        .build(),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Selection check button
                IconButton(
                    onClick = onToggleSelect,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "已勾选",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    } else {
                        Icon(
                            Icons.Outlined.Circle,
                            contentDescription = "未勾选",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Status chip top-left
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                    shape = RectangleShape,
                    color = when {
                        recognition?.isSensitive == true -> MaterialTheme.colorScheme.errorContainer
                        recognition?.isSuccess == true -> MaterialTheme.colorScheme.primaryContainer
                        recognition?.isSuccess == false -> MaterialTheme.colorScheme.errorContainer
                        else -> MediaLabelBg
                    }
                ) {
                    Text(
                        text = when {
                            recognition?.isSensitive == true -> "⚠️ 敏感图"
                            recognition?.isSuccess == true -> "已识别"
                            recognition?.isSuccess == false -> "识别失败"
                            else -> "待识别"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            recognition?.isSensitive == true -> MaterialTheme.colorScheme.onErrorContainer
                            recognition?.isSuccess == true -> MaterialTheme.colorScheme.onPrimaryContainer
                            recognition?.isSuccess == false -> MaterialTheme.colorScheme.onErrorContainer
                            else -> Color.White
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

            }

            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )

                if (recognition?.isSuccess == true) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        recognition.keywords.take(3).forEach { tag ->
                            Surface(
                                shape = RectangleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "#$tag",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = recognition.description,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (recognition?.isSuccess == false) {
                    Text(
                        text = recognition.errorMessage ?: "识别异常",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = "大小: ${item.formattedSize} | 目录: ${item.folderName}",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImageDetailDialog(
    item: ImageItem,
    recognition: RecognitionResult?,
    onDismiss: () -> Unit,
    onReRecognize: () -> Unit,
    onSaveKeywords: (List<String>) -> Unit,
    context: Context
) {
    val scrollState = rememberScrollState()
    val clipboardManager = LocalClipboardManager.current

    // Option D: Tag editing state
    val editableKeywords = remember(recognition) {
        mutableStateListOf<String>().apply {
            if (recognition != null) {
                addAll(recognition.keywords)
            }
        }
    }
    var newTagInput by remember { mutableStateOf("") }
    var isEditingTags by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RectangleShape,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "图片识别详情", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Large Image Preview
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.uri)
                        .build(),
                    contentDescription = item.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RectangleShape)
                        .background(Color.Black)
                )

                // Option C: Sensitive Warning Banner
                if (recognition?.isSensitive == true) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RectangleShape,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "⚠️ 敏感图提示：检测到身份证/银行卡/私密凭证信息，建议加强隐私防护！",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // File metadata
                Text(text = "文件名: ${item.name}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(text = "来源目录: ${item.folderName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                Text(text = "文件大小: ${item.formattedSize}", style = MaterialTheme.typography.bodySmall)
                if (!item.sha256.isNullOrBlank()) {
                    Text(text = "SHA-256: ${item.sha256}", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                }

                // Option C: OCR Text Card
                if (!recognition?.ocrText.isNullOrBlank()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RectangleShape,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Description,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "提取文本与票据文字 (OCR):",
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(recognition.ocrText!!))
                                        Toast.makeText(context, "已复制文字到剪贴板", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "复制文字",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = recognition.ocrText!!,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                if (recognition?.isSuccess == true) {
                    // Option D: Keywords with Inline Editing & Deletion
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "分类关键词 (${editableKeywords.size}个):",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelLarge
                        )
                        TextButton(onClick = { isEditingTags = !isEditingTags }) {
                            Text(if (isEditingTags) "收起编辑" else "✏️ 管理标签")
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        editableKeywords.forEach { kw ->
                            if (isEditingTags) {
                                InputChip(
                                    selected = false,
                                    onClick = {},
                                    label = { Text("#$kw", fontSize = 12.sp) },
                                    trailingIcon = {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "删除标签",
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable {
                                                    editableKeywords.remove(kw)
                                                    onSaveKeywords(editableKeywords.toList())
                                                }
                                        )
                                    }
                                )
                            } else {
                                Surface(
                                    shape = RectangleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer
                                ) {
                                    Text(
                                        text = "#$kw",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Add new tag input field when editing
                    if (isEditingTags) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newTagInput,
                                onValueChange = { newTagInput = it },
                                label = { Text("添加新标签", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    val trimmed = newTagInput.trim()
                                    if (trimmed.isNotBlank() && !editableKeywords.contains(trimmed)) {
                                        editableKeywords.add(trimmed)
                                        onSaveKeywords(editableKeywords.toList())
                                        newTagInput = ""
                                        Toast.makeText(context, "已添加并保存标签", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "添加", modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(text = "一句话总结描述:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
                    Text(text = recognition.description, style = MaterialTheme.typography.bodyMedium)

                    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(recognition.recognizedAt))
                    Text(text = "识别模型: ${recognition.modelName} | 时间: $dateStr", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                } else if (recognition?.isSuccess == false) {
                    Text(text = "识别失败原因:", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                    Text(text = recognition.errorMessage ?: "未知错误", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                } else {
                    Text(text = "当前图片尚未进行 AI 识别。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                }
            }
        },
        confirmButton = {
            Button(onClick = onReRecognize, shape = RectangleShape) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (recognition?.isSuccess == true) "重新识别" else "立即识别")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = RectangleShape) {
                Text("关闭")
            }
        }
    )
}

