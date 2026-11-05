package com.toimg.app.feature.settings

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyOff
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.toimg.app.core.util.TagNormalizeUtil
import com.toimg.app.data.repository.AiConfigRepository
import com.toimg.app.domain.model.AiConfig
import com.toimg.app.domain.model.NvidiaModelProfile

/**
 * 重构版 SettingsScreen：
 * 针对 1998x918 竖屏视野，通过顶部分组 Tab 将设置拆分为 4 个清晰独立的主题，
 * 坚决避免在单个页面堆满所有功能；直角纯色、精致字阶、紧凑控件。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("01 视觉引擎", "02 归档规则", "03 词库归一", "04 数据存储")

    LaunchedEffect(Unit) {
        try {
            viewModel.loadConfig(context)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    LaunchedEffect(uiState.saveMessage) {
        uiState.saveMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- 顶部直角平切 Tab 导航条 ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RectangleShape)
        ) {
            tabTitles.forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedTab = index }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                        Box(
                            modifier = Modifier
                                .width(28.dp)
                                .height(2.dp)
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        )
                    }
                }
            }
        }

        // --- Tab 对应的内容面板 ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (selectedTab) {
                0 -> AiEngineSettingsTab(viewModel, uiState, context)
                1 -> SmartRulesSettingsTab(viewModel, uiState, context)
                2 -> SynonymsSettingsTab(viewModel, uiState, context)
                3 -> DataStorageSettingsTab(viewModel, uiState, context)
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// TAB 0: AI 视觉引擎与配置
// -------------------------------------------------------------------------------------------------
@Composable
private fun AiEngineSettingsTab(
    viewModel: SettingsViewModel,
    uiState: SettingsUiState,
    context: Context
) {
    SectionHeader("AI 视觉引擎与模型配置", "设置端侧 ML Kit 或云端大模型连接凭据")

    // 1. 运行模式选择
    SettingCard(title = "识别模式") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CompactFilterChip(
                label = "智能混合",
                selected = uiState.engineMode == AiConfig.MODE_HYBRID,
                onClick = { viewModel.updateEngineMode(AiConfig.MODE_HYBRID) },
                modifier = Modifier.weight(1f).testTag("mode_hybrid_chip")
            )
            CompactFilterChip(
                label = "纯端侧离线",
                selected = uiState.engineMode == AiConfig.MODE_OFFLINE_ONLY,
                onClick = { viewModel.updateEngineMode(AiConfig.MODE_OFFLINE_ONLY) },
                modifier = Modifier.weight(1f).testTag("mode_offline_chip")
            )
            CompactFilterChip(
                label = "仅云端",
                selected = uiState.engineMode == AiConfig.MODE_CLOUD_ONLY,
                onClick = { viewModel.updateEngineMode(AiConfig.MODE_CLOUD_ONLY) },
                modifier = Modifier.weight(1f).testTag("mode_cloud_chip")
            )
        }

        Text(
            text = when (uiState.engineMode) {
                AiConfig.MODE_HYBRID -> "智能混合（默认推荐）：大模型深度解析，无网或额度受限时自动无缝由本地 ML Kit 接管。"
                AiConfig.MODE_OFFLINE_ONLY -> "纯本地端侧离线：完全在手机本地运行 Google ML Kit，零网络流量消耗，数据绝对保密。"
                else -> "仅云端：仅向配置的 AI 服务发起分析请求。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
    }

    // 2. 服务商选择
    if (uiState.engineMode != AiConfig.MODE_OFFLINE_ONLY) {
        SettingCard(title = "AI 服务商接入") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CompactFilterChip(
                    label = "Google Gemini 官方",
                    selected = uiState.providerType == AiConfig.PROVIDER_GEMINI,
                    onClick = { viewModel.updateProviderType(AiConfig.PROVIDER_GEMINI) },
                    modifier = Modifier.weight(1f).testTag("provider_gemini_chip")
                )
                CompactFilterChip(
                    label = "OpenAI 兼容代理",
                    selected = uiState.providerType == AiConfig.PROVIDER_OPENAI,
                    onClick = { viewModel.updateProviderType(AiConfig.PROVIDER_OPENAI) },
                    modifier = Modifier.weight(1f).testTag("provider_openai_chip")
                )
                CompactFilterChip(
                    label = "NVIDIA Build（主力）",
                    selected = uiState.providerType == AiConfig.PROVIDER_NVIDIA,
                    onClick = { viewModel.updateProviderType(AiConfig.PROVIDER_NVIDIA) },
                    modifier = Modifier.weight(1f).testTag("provider_nvidia_chip")
                )
            }

            // API Key 输入
            OutlinedTextField(
                value = uiState.apiKey,
                onValueChange = { viewModel.updateApiKey(it) },
                label = { Text("API Key", fontSize = 12.sp) },
                singleLine = true,
                visualTransformation = if (uiState.isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { viewModel.toggleKeyVisibility() }) {
                        Icon(
                            imageVector = if (uiState.isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                },
                shape = RectangleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("api_key_input")
            )

            // OpenAI / 代理自定义地址与模型
            if (uiState.providerType == AiConfig.PROVIDER_OPENAI || uiState.providerType == AiConfig.PROVIDER_NVIDIA) {
                OutlinedTextField(
                    value = uiState.baseUrl,
                    onValueChange = { viewModel.updateBaseUrl(it) },
                    label = { Text(if (uiState.providerType == AiConfig.PROVIDER_NVIDIA) "NVIDIA API Base URL" else "Base URL", fontSize = 12.sp) },
                    singleLine = true,
                    shape = RectangleShape,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (uiState.providerType != AiConfig.PROVIDER_NVIDIA) {
                OutlinedTextField(
                    value = uiState.modelName,
                    onValueChange = { viewModel.updateModelName(it) },
                    label = { Text("模型标识 (Model)", fontSize = 12.sp) },
                    singleLine = true,
                    shape = RectangleShape,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                NvidiaModelLibrary(viewModel, uiState, context)
                NvidiaBatchPolicyCard(context)
            }
        }
    }

    if (uiState.engineMode != AiConfig.MODE_OFFLINE_ONLY) {
        SettingCard(title = "备用模型（主力失败时使用）") {
            Text("备用模型独立保存。主力模型请求失败后，系统将尝试备用模型；两者均失败时，智能混合模式再由本地 ML Kit 兜底。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactFilterChip("Gemini", uiState.backupProviderType == AiConfig.PROVIDER_GEMINI, { viewModel.updateBackupProviderType(AiConfig.PROVIDER_GEMINI) })
                CompactFilterChip("NVIDIA", uiState.backupProviderType == AiConfig.PROVIDER_NVIDIA, { viewModel.updateBackupProviderType(AiConfig.PROVIDER_NVIDIA) })
                CompactFilterChip("OpenAI 兼容", uiState.backupProviderType == AiConfig.PROVIDER_OPENAI, { viewModel.updateBackupProviderType(AiConfig.PROVIDER_OPENAI) })
            }
            OutlinedTextField(value = uiState.backupApiKey, onValueChange = viewModel::updateBackupApiKey, label = { Text("备用 API Key") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = uiState.backupBaseUrl, onValueChange = viewModel::updateBackupBaseUrl, label = { Text("备用 Base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = uiState.backupModelName, onValueChange = viewModel::updateBackupModelName, label = { Text("备用 Model ID（手动填写）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }

    // 3. 连通性测试与保存操作
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (uiState.engineMode != AiConfig.MODE_OFFLINE_ONLY) {
            OutlinedButton(
                onClick = { viewModel.testConnectivity(context) },
                shape = RectangleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                enabled = !uiState.isTesting,
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .testTag("test_connect_button")
            ) {
                if (uiState.isTesting) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("连通测试中…", fontSize = 12.sp)
                } else {
                    Text("测试网络连通", fontSize = 12.sp)
                }
            }
        }

        Button(
            onClick = { viewModel.saveSettings(context) },
            shape = RectangleShape,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .weight(1f)
                .height(38.dp)
                .testTag("save_settings_button")
        ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("保存配置", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }

    // 连通性测试结果反馈
    uiState.testResult?.let { result ->
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, if (result.isSuccess) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error, RectangleShape),
            color = if (result.isSuccess) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer,
            shape = RectangleShape
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (result.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (result.isSuccess) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = result.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (result.isSuccess) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun NvidiaBatchPolicyCard(context: Context) {
    val repo = remember(context) { AiConfigRepository(context) }
    var interval by remember { mutableIntStateOf(repo.getNvidiaRequestIntervalSeconds()) }
    var customValue by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    SettingCard(title = "NVIDIA 批量识图策略") {
        Text(
            "无上下文任务：每次请求只携带当前批次，不发送历史对话或上一批结果。每批完成并写入本地数据库后，才开始下一批倒计时。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("批次间隔", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(15, 25, 35).forEach { seconds ->
                CompactFilterChip(
                    label = "$seconds 秒",
                    selected = interval == seconds,
                    onClick = {
                        interval = seconds
                        repo.saveNvidiaRequestIntervalSeconds(seconds)
                        message = "已保存：${seconds} 秒"
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = customValue,
                onValueChange = { customValue = it.filter { ch -> ch.isDigit() }.take(3); message = null },
                label = { Text("自定义秒数（5–600）", fontSize = 11.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = {
                val seconds = customValue.toIntOrNull()
                if (seconds != null && seconds in 5..600) {
                    interval = seconds
                    repo.saveNvidiaRequestIntervalSeconds(seconds)
                    message = "已保存：${seconds} 秒"
                } else message = "请输入 5–600 之间的整数"
            }) { Text("应用") }
        }
        Text("当前间隔：${interval} 秒", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text("Max Tokens 自动按批次分配：≤5 张 2048、≤10 张 4096、更多图片 8192；同时不超过模型配置值与 NVIDIA 托管接口上限。Nemotron 默认关闭 Thinking。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("遇到 HTTP 429 时优先遵守服务器 Retry-After；未提供时至少冷却 60 秒。限流或临时故障不会立即切换备用云端模型。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        message?.let { Text(it, color = MaterialTheme.colorScheme.tertiary, fontSize = 11.sp) }
    }
}

@Composable
private fun NvidiaModelLibrary(viewModel: SettingsViewModel, uiState: SettingsUiState, context: Context) {
    var editing by remember { mutableStateOf<NvidiaModelProfile?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    val selected = uiState.nvidiaModels.firstOrNull { it.id == uiState.selectedNvidiaModelId }

    SettingCard(title = "NVIDIA Build · 视觉模型库") {
        Text("主力模型优先展示。每个模型独立保存采样参数；添加、编辑或删除模型不会影响已保存的历史识别记录。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        selected?.let { profile ->
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RectangleShape, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("主力视觉模型", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(profile.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(profile.id, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Text("temperature ${profile.temperature}  ·  top_p ${profile.topP}  ·  max_tokens ${profile.maxTokens}", fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { editing = profile; showEditor = true }) { Text("参数设置") }
                        if (profile.isBuiltIn) TextButton(onClick = { viewModel.resetNvidiaModel(context, profile.id) }) { Text("恢复官方参数") }
                        TextButton(onClick = { viewModel.deleteNvidiaModel(context, profile.id) }, enabled = uiState.nvidiaModels.size > 1) { Text("删除", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
        Text("模型列表", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        uiState.nvidiaModels.filter { it.id != uiState.selectedNvidiaModelId }.forEach { profile ->
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable { viewModel.selectNvidiaModel(context, profile.id) }) {
                        Text(profile.displayName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(profile.id, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                        Text("temp ${profile.temperature} · top_p ${profile.topP} · ${profile.maxTokens} tokens", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { viewModel.selectNvidiaModel(context, profile.id) }) { Text("设为主力") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { editing = profile; showEditor = true }) { Text("编辑参数") }
                    TextButton(onClick = { viewModel.deleteNvidiaModel(context, profile.id) }) { Text("删除模型", color = MaterialTheme.colorScheme.error) }
                }
            }
            HorizontalDivider()
        }
        OutlinedButton(onClick = { editing = null; showEditor = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("添加 NVIDIA 视觉模型")
        }
    }

    if (showEditor) {
        ModelProfileEditorDialog(
            initial = editing,
            onDismiss = { showEditor = false },
            onSave = { profile -> viewModel.saveNvidiaModel(context, profile, select = true, replaceId = editing?.id); showEditor = false }
        )
    }
}

@Composable
private fun ModelProfileEditorDialog(
    initial: NvidiaModelProfile?,
    onDismiss: () -> Unit,
    onSave: (NvidiaModelProfile) -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial?.displayName ?: "") }
    var id by remember(initial) { mutableStateOf(initial?.id ?: "") }
    var temperature by remember(initial) { mutableStateOf((initial?.temperature ?: 0.6).toString()) }
    var topP by remember(initial) { mutableStateOf((initial?.topP ?: 0.95).toString()) }
    var maxTokens by remember(initial) { mutableStateOf((initial?.maxTokens ?: 4096).toString()) }
    var seed by remember(initial) { mutableStateOf(initial?.seed?.toString() ?: "") }
    var frequency by remember(initial) { mutableStateOf(initial?.frequencyPenalty?.toString() ?: "") }
    var presence by remember(initial) { mutableStateOf(initial?.presencePenalty?.toString() ?: "") }
    var reasoning by remember(initial) { mutableStateOf(initial?.reasoningEffort ?: "") }
    var thinking by remember(initial) { mutableStateOf(initial?.enableThinking ?: false) }
    var parallel by remember(initial) { mutableStateOf(initial?.parallelToolCalls ?: false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加视觉模型" else "编辑模型参数") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("显示名称") }, singleLine = true)
                OutlinedTextField(id, { id = it }, label = { Text("Model ID") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(temperature, { temperature = it }, label = { Text("temperature") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(topP, { topP = it }, label = { Text("top_p") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
                OutlinedTextField(maxTokens, { maxTokens = it }, label = { Text("max_tokens") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(seed, { seed = it }, label = { Text("seed（留空则不发送）") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(frequency, { frequency = it }, label = { Text("frequency_penalty") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(presence, { presence = it }, label = { Text("presence_penalty") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                OutlinedTextField(reasoning, { reasoning = it }, label = { Text("reasoning_effort（low/medium/high）") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) { Switch(thinking, { thinking = it }); Spacer(Modifier.width(6.dp)); Text("enable_thinking") }
                Row(verticalAlignment = Alignment.CenterVertically) { Switch(parallel, { parallel = it }); Spacer(Modifier.width(6.dp)); Text("parallel_tool_calls") }
                Text("可选参数仅在填写或启用时发送。自定义模型请先确认 NVIDIA 官方页面支持图片输入。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val temp = temperature.toDoubleOrNull() ?: 0.6
                val p = topP.toDoubleOrNull() ?: 0.95
                val tokens = maxTokens.toIntOrNull() ?: 4096
                if (id.isNotBlank() && temp in 0.0..2.0 && p in 0.0..1.0 && tokens > 0) {
                    onSave(NvidiaModelProfile(id.trim(), name.trim().ifBlank { id.trim() }, temp, p, tokens,
                        seed = seed.toIntOrNull(), frequencyPenalty = frequency.toDoubleOrNull(), presencePenalty = presence.toDoubleOrNull(),
                        reasoningEffort = reasoning.trim().ifBlank { null }, enableThinking = if (initial?.enableThinking != null || thinking) thinking else null,
                        parallelToolCalls = if (initial?.parallelToolCalls != null || parallel) parallel else null, isBuiltIn = initial?.isBuiltIn ?: false))
                }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}


// -------------------------------------------------------------------------------------------------
// TAB 1: 智能归档规则
// -------------------------------------------------------------------------------------------------
@Composable
private fun SmartRulesSettingsTab(
    viewModel: SettingsViewModel,
    uiState: SettingsUiState,
    context: Context
) {
    var showAddDialog by remember { mutableStateOf(false) }

    SectionHeader("智能归档规则引擎", "根据识别关键词自动匹配归类目标文件夹")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "已启用规则 (${uiState.smartRules.count { it.isEnabled }}/${uiState.smartRules.size})",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        OutlinedButton(
            onClick = { showAddDialog = true },
            shape = RectangleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            modifier = Modifier.height(32.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text("新增规则", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        }
    }

    if (uiState.smartRules.isEmpty()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RectangleShape),
            color = MaterialTheme.colorScheme.surface,
            shape = RectangleShape
        ) {
            Text(
                text = "暂无规则，点击上方「新增规则」添加自动归类触发条件。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            uiState.smartRules.forEach { rule ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RectangleShape),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RectangleShape
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = rule.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RectangleShape
                                ) {
                                    Text(
                                        text = "➔ ${rule.targetFolderName}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = "触发词: ${rule.keywordTrigger}",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = rule.isEnabled,
                                onCheckedChange = { isChecked ->
                                    viewModel.toggleRule(context, rule.id, isChecked)
                                }
                            )
                            IconButton(onClick = { viewModel.deleteRule(context, rule.id) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var ruleName by remember { mutableStateOf("") }
        var trigger by remember { mutableStateOf("") }
        var folder by remember { mutableStateOf("Pictures/归类整理") }

        AlertDialog(
            shape = RectangleShape,
            onDismissRequest = { showAddDialog = false },
            title = { Text("新增智能归档规则", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = ruleName,
                        onValueChange = { ruleName = it },
                        label = { Text("规则名称 (例如：发票报销)") },
                        singleLine = true,
                        shape = RectangleShape,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = trigger,
                        onValueChange = { trigger = it },
                        label = { Text("触发关键词 (以逗号分隔)") },
                        singleLine = true,
                        shape = RectangleShape,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = folder,
                        onValueChange = { folder = it },
                        label = { Text("目标归档文件夹名") },
                        singleLine = true,
                        shape = RectangleShape,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    shape = RectangleShape,
                    onClick = {
                        if (ruleName.isNotBlank() && trigger.isNotBlank()) {
                            viewModel.addRule(context, ruleName.trim(), trigger.trim(), folder.trim())
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("添加")
                }
            },
            dismissButton = {
                TextButton(shape = RectangleShape, onClick = { showAddDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

// -------------------------------------------------------------------------------------------------
// TAB 2: 同义词库与标签归一化
// -------------------------------------------------------------------------------------------------
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SynonymsSettingsTab(
    viewModel: SettingsViewModel,
    uiState: SettingsUiState,
    context: Context
) {
    var sourceWord by remember { mutableStateOf("") }
    var targetTag by remember { mutableStateOf("") }
    var showBuiltIn by remember { mutableStateOf(false) }

    SectionHeader("标签归一与同义词库", "将 AI 提取的特异词归并到统一标准标签分类中")

    // 添加同义词表单
    SettingCard(title = "新增自定义同义词映射") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = sourceWord,
                onValueChange = { sourceWord = it },
                label = { Text("原词 (如: 咪咪)", fontSize = 11.sp) },
                singleLine = true,
                shape = RectangleShape,
                modifier = Modifier.weight(1f)
            )
            Text("➔", fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = targetTag,
                onValueChange = { targetTag = it },
                label = { Text("归一标签 (如: 猫咪)", fontSize = 11.sp) },
                singleLine = true,
                shape = RectangleShape,
                modifier = Modifier.weight(1f)
            )
        }

        Button(
            onClick = {
                if (sourceWord.isNotBlank() && targetTag.isNotBlank()) {
                    viewModel.addCustomSynonym(context, sourceWord, targetTag)
                    sourceWord = ""
                    targetTag = ""
                }
            },
            shape = RectangleShape,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
        ) {
            Text("保存同义词映射", fontSize = 12.sp)
        }
    }

    // 自定义同义词列表
    SettingCard(title = "已配置的自定义同义词 (${uiState.customSynonyms.size})") {
        if (uiState.customSynonyms.isEmpty()) {
            Text(
                text = "暂无自定义同义词，添加后可规范化 AI 关键词。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                uiState.customSynonyms.forEach { (word, tag) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RectangleShape)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$word ➔ $tag",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )
                        IconButton(
                            onClick = { viewModel.deleteCustomSynonym(context, word) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "删除", modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }
    }

    // 预设词库参考
    SettingCard(title = "内置基础词库参考") {
        TextButton(
            onClick = { showBuiltIn = !showBuiltIn },
            shape = RectangleShape,
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(if (showBuiltIn) "收起内置词库" else "展开查看 25+ 内置映射规则 ➔", fontSize = 12.sp)
        }

        if (showBuiltIn) {
            val builtIn = TagNormalizeUtil.getBuiltInSynonyms()
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                builtIn.entries.take(20).forEach { (word, target) ->
                    Surface(
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        color = MaterialTheme.colorScheme.surface,
                        shape = RectangleShape
                    ) {
                        Text(
                            text = "$word ➔ $target",
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// TAB 3: 数据存储与维护
// -------------------------------------------------------------------------------------------------
@Composable
private fun DataStorageSettingsTab(
    viewModel: SettingsViewModel,
    uiState: SettingsUiState,
    context: Context
) {
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { viewModel.exportRecognitions(context, it) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importRecognitions(context, it) }
    }

    SectionHeader("数据维护与本地存储", "管理识别记录导出备份、恢复与凭据安全")

    // 1. 批量识别批次大小
    val aiRepo = remember { AiConfigRepository(context) }
    var batchSize by remember { mutableIntStateOf(aiRepo.getBatchSize()) }

    SettingCard(title = "单批次处理图片数量") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(5, 10, 20).forEach { size ->
                CompactFilterChip(
                    label = "$size 张 / 批",
                    selected = batchSize == size,
                    onClick = {
                        batchSize = size
                        aiRepo.saveBatchSize(size)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Text(
            text = "串行识别时每批聚合分析的图片数量。推荐 5 张（低延迟且平稳）。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
    }

    // 2. 识别元数据导入与导出
    SettingCard(title = "AI 识别元数据备份") {
        Text(
            text = "可将本地所有已识别的标签、关键词和描述导出为结构化 JSON 文件备份；在新设备或重置后可一键导入恢复。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { exportLauncher.launch("toimg_backup_${System.currentTimeMillis()}.json") },
                shape = RectangleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
            ) {
                Text("导出 JSON 备份", fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json")) },
                shape = RectangleShape,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
            ) {
                Text("导入备份恢复", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    // 3. 安全擦除
    SettingCard(title = "敏感凭证清理") {
        Text(
            text = "擦除保存在本机 SharedPreferences 中的 API Key 密匙。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )

        OutlinedButton(
            onClick = { viewModel.clearApiKey(context) },
            shape = RectangleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
        ) {
            Text("擦除已保存的 API Key", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
        }
    }
}

// -------------------------------------------------------------------------------------------------
// 公用建筑风格直角微组件
// -------------------------------------------------------------------------------------------------
@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RectangleShape),
        color = MaterialTheme.colorScheme.surface,
        shape = RectangleShape
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            content()
        }
    }
}

@Composable
private fun CompactFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(34.dp)
            .clickable { onClick() }
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                RectangleShape
            ),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = RectangleShape
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
    }
}
