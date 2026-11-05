package com.toimg.app.feature.home

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toimg.app.core.di.AppContainer
import com.toimg.app.core.ui.components.MoveHistoryDialog
import com.toimg.app.core.ui.components.OnboardingDialog
import com.toimg.app.core.util.BackgroundBatchManager
import com.toimg.app.core.util.SmartProposalEngine
import com.toimg.app.core.work.ScanTaskScheduler
import com.toimg.app.data.local.entity.ScanSessionEntity
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 重构版 HomeScreen：
 * 专为 1998x918 高比例纵向屏幕深度优化。
 * 遵循极简包豪斯/瑞士风格：直角零圆角、纯色无模糊、主次分明、精炼文字排版、杜绝功能堆砌。
 */
@Composable
fun HomeScreen(
    onSelectImages: () -> Unit,
    onOpenWorkspace: () -> Unit,
    onOpenCategories: (() -> Unit)? = null,
    onOpenScanScope: (() -> Unit)? = null,
    onOpenTaskHistory: (() -> Unit)? = null,
    onOpenDuplicates: (() -> Unit)? = null,
    onOpenProposals: (() -> Unit)? = null,
    onOpenScanProgress: (() -> Unit)? = null,
    onOpenSources: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val prefs = remember { context.getSharedPreferences("toimg_app_prefs", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    var showOnboarding by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showResumeDialog by remember { mutableStateOf(false) }
    var interruptedCount by remember { mutableStateOf(0) }
    var proposalCount by remember { mutableStateOf(0) }
    var scanTaskActive by remember { mutableStateOf(ScanTaskScheduler.hasActiveTask(context)) }
    var activeScanSession by remember { mutableStateOf<ScanSessionEntity?>(null) }

    // 状态统计看板数据
    var totalCount by remember { mutableStateOf(0) }
    var recognizedCount by remember { mutableStateOf(0) }
    var pendingCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        try {
            val db = AppContainer.database(context)
            totalCount = db.imageDao().getImageCount().firstOrNull() ?: 0
            recognizedCount = db.imageDao().countImagesByStatus("RECOGNIZED").firstOrNull() ?: 0
            pendingCount = db.imageDao().countImagesByStatus("SCANNED").firstOrNull() ?: 0
        } catch (e: Throwable) {
            // 安全捕获
        }
    }

    LaunchedEffect(Unit) {
        ScanTaskScheduler.reconcile(context)
        try {
            AppContainer.database(context).scanSessionDao().observeActive().collect { session ->
                activeScanSession = session
                scanTaskActive = session != null || ScanTaskScheduler.hasActiveTask(context)
            }
        } catch (_: Throwable) {
            activeScanSession = null
            scanTaskActive = ScanTaskScheduler.hasActiveTask(context)
        }
    }

    LaunchedEffect(Unit) {
        var refreshCounter = 0
        while (true) {
            val active = ScanTaskScheduler.hasActiveTask(context)
            if (active != scanTaskActive) scanTaskActive = active
            if (++refreshCounter % 6 == 0) ScanTaskScheduler.reconcile(context)
            delay(900)
        }
    }

    LaunchedEffect(Unit) {
        proposalCount = try {
            SmartProposalEngine.count(context)
        } catch (e: Throwable) {
            0
        }
    }

    LaunchedEffect(Unit) {
        try {
            val hasSeen = prefs.getBoolean("has_seen_onboarding", false)
            if (!hasSeen) {
                showOnboarding = true
            }
            val count = BackgroundBatchManager.checkAndResumeInterrupted(context)
            if (count > 0) {
                interruptedCount = count
                showResumeDialog = true
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // --- 1. 顶部标识与副标（排版规整精炼） ---
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SYS // TOIMG 0.1",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.5.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        onClick = { showHistory = true },
                        shape = RectangleShape,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(28.dp)
                            .testTag("home_history_button")
                    ) {
                        Text("移动历史", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(
                        onClick = { showOnboarding = true },
                        shape = RectangleShape,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(28.dp)
                            .testTag("home_onboarding_button")
                    ) {
                        Text("指引", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Text(
                text = "智能图像整理系统",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Text(
                text = "本地索引 · 批次识别 · 逻辑归类 · 安全去重",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

        // --- 2. 实时数据统计看板（直角三等分几何网格，主次分明） ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RectangleShape)
                .background(MaterialTheme.colorScheme.surface),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MetricCell(
                modifier = Modifier.weight(1f),
                label = "已收录图片",
                value = "$totalCount",
                accent = MaterialTheme.colorScheme.primary
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(68.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            MetricCell(
                modifier = Modifier.weight(1f),
                label = "AI 已解析",
                value = "$recognizedCount",
                accent = MaterialTheme.colorScheme.tertiary
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(68.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            MetricCell(
                modifier = Modifier.weight(1f),
                label = "待处理队列",
                value = "$pendingCount",
                accent = if (pendingCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
            )
        }

        // --- 扫描任务快捷入口：离开进度页后仍可从首页找回 ---
        if (scanTaskActive && onOpenScanProgress != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenScanProgress() }
                    .border(1.5.dp, MaterialTheme.colorScheme.tertiary, RectangleShape)
                    .testTag("home_active_scan_card"),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RectangleShape
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            if (activeScanSession?.status == ScanSessionEntity.PAUSED) "扫描任务 · 已暂停" else "扫描任务 · 正在进行",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            if (activeScanSession != null) "${activeScanSession?.phase} · 已发现 ${activeScanSession?.discoveredCount ?: 0} 张图片"
                            else "任务正在启动，可随时返回查看扫描进度",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Text("查看进度 →", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
        }

        // --- 3. 智能分类提案微提醒条（有命中时才出现，主次分明） ---
        if (proposalCount > 0 && onOpenProposals != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenProposals() }
                    .border(1.dp, MaterialTheme.colorScheme.primary, RectangleShape)
                    .testTag("open_proposals_banner"),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RectangleShape
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "命中归档规则：$proposalCount 张图片等待确认移动",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Text(
                        text = "查看 ➔",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // --- 4. 核心主操作区：01 AI 识别工作区（主要焦点） ---
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, MaterialTheme.colorScheme.primary, RectangleShape),
            color = MaterialTheme.colorScheme.surface,
            shape = RectangleShape
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "01 // CORE WORKSPACE",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RectangleShape
                    ) {
                        Text(
                            text = "主要业务",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "AI 批量识别工作区",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "启动严格串行识别流水线，支持端侧 ML Kit 零流量离线解析与云端大模型混合调度。提取 3-10 个中文核心关键词与语义短语。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }

                Button(
                    onClick = onOpenWorkspace,
                    shape = RectangleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .testTag("open_workspace_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "进入识别工作区",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // --- 5. 次要功能导航模块（排版规整直角卡片，主次清晰） ---
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "SECONDARY MODULES // 辅助工具",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 模块 02：挑选与扫描
            ModuleRowCard(
                code = "02",
                title = "挑选与检索图片",
                subtitle = "零权限 Photo Picker 或授权目录扫描",
                actionLabel = "挑选",
                onAction = onSelectImages,
                secondaryLabel = if (onOpenScanScope != null) "扫描目录" else null,
                onSecondaryAction = onOpenScanScope,
                testTag = "start_picker_button"
            )

            // 模块 02A：已登记来源管理（与本次扫描选择分离）
            if (onOpenSources != null) {
                ModuleRowCard(
                    code = "02A",
                    title = "图片来源管理",
                    subtitle = "查看已登记文件夹、状态与项目内索引",
                    actionLabel = "管理来源",
                    onAction = onOpenSources,
                    testTag = "open_image_sources_button"
                )
            }

            // 模块 03：智能分类检索
            if (onOpenCategories != null) {
                ModuleRowCard(
                    code = "03",
                    title = "分类标签与语义搜索",
                    subtitle = "AI 关键词多维聚类，全文倒排索引检索",
                    actionLabel = "查看分类",
                    onAction = onOpenCategories,
                    testTag = "open_categories_button"
                )
            }

            // 模块 04：重复相片清理
            if (onOpenDuplicates != null) {
                ModuleRowCard(
                    code = "04",
                    title = "重复与相似照片清理",
                    subtitle = "SHA-256 精确去重与 dHash 感知哈希排查",
                    actionLabel = "打开清理",
                    onAction = onOpenDuplicates,
                    testTag = "open_duplicates_button"
                )
            }
        }

        // --- 6. 任务历史中心快捷入口 ---
        if (onOpenTaskHistory != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenTaskHistory() }
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RectangleShape),
                color = MaterialTheme.colorScheme.surface,
                shape = RectangleShape
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "任务历史中心（扫描 / 识别 / 移动记录）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "➔",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }

    if (showOnboarding) {
        OnboardingDialog(
            onDismiss = {
                prefs.edit().putBoolean("has_seen_onboarding", true).apply()
                showOnboarding = false
            }
        )
    }

    if (showHistory) {
        MoveHistoryDialog(
            context = context,
            onDismiss = { showHistory = false }
        )
    }

    if (showResumeDialog) {
        androidx.compose.material3.AlertDialog(
            shape = RectangleShape,
            onDismissRequest = { showResumeDialog = false },
            title = {
                Text(text = "未完成的识别任务", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            },
            text = {
                Text(
                    text = "检测到上次会话有 $interruptedCount 张图片处于后台处理队列。是否继续恢复识别？",
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                Button(
                    shape = RectangleShape,
                    onClick = {
                        showResumeDialog = false
                        scope.launch {
                            val interrupted = AppContainer.database(context).imageDao().getImagesByStatus("PROCESSING")
                            if (interrupted.isNotEmpty()) {
                                BackgroundBatchManager.startBatchRecognition(context, interrupted)
                            }
                        }
                    }
                ) {
                    Text("继续识别", style = MaterialTheme.typography.labelMedium)
                }
            },
            dismissButton = {
                TextButton(
                    shape = RectangleShape,
                    onClick = { showResumeDialog = false }
                ) {
                    Text("忽略", style = MaterialTheme.typography.labelMedium)
                }
            }
        )
    }
}

/** 简洁直角的统计指标单元格 */
@Composable
private fun MetricCell(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    accent: Color
) {
    Column(
        modifier = modifier
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = accent,
            fontFamily = FontFamily.Monospace
        )
    }
}

/** 次要功能模块横条卡片 */
@Composable
private fun ModuleRowCard(
    code: String,
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
    secondaryLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    testTag: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RectangleShape),
        color = MaterialTheme.colorScheme.surface,
        shape = RectangleShape
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = code,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (secondaryLabel != null && onSecondaryAction != null) {
                    OutlinedButton(
                        onClick = onSecondaryAction,
                        shape = RectangleShape,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(secondaryLabel, fontSize = 12.sp)
                    }
                }
                OutlinedButton(
                    onClick = onAction,
                    shape = RectangleShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(34.dp)
                        .testTag(testTag)
                ) {
                    Text(actionLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
