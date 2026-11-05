package com.toimg.app.feature.confirm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.toimg.app.data.repository.AiConfigRepository

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.RectangleShape

@Composable
fun RecognizeConfirmScreen(
    onStarted: () -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: RecognizeConfirmViewModel = viewModel()
) {
    val context = LocalContext.current
    val s by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.load(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("确认 AI 识别任务", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

        if (!s.loading && s.total == 0) {
            Text("没有待识别的图片。请先回到选择页勾选图片。", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onBack, shape = RectangleShape) { Text("返回选择图片") }
            return@Column
        }

        // 缩略图概览
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RectangleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("已选择 ${s.total} 张图片", fontWeight = FontWeight.SemiBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(s.items.take(24), key = { it.image.id }) { item ->
                        Box {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(item.image.uri).build(),
                                contentDescription = item.image.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RectangleShape)
                            )
                        }
                    }
                }
                if (s.total > 24) {
                    Text("仅显示前 24 张缩略图", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
        }

        // 首次识别 / 已识别 / 不可用
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RectangleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("需要首次识别：${s.firstTimeCount} 张")
                Text("已有成功结果：${s.alreadyRecognizedCount} 张（默认不重复提交，避免产生额外 API 费用）")
                if (s.unavailableCount > 0) {
                    Text(
                        "文件不可用：${s.unavailableCount} 张（已自动排除）",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switch(
                        checked = s.includeRecognized,
                        onCheckedChange = { viewModel.setIncludeRecognized(it) },
                        modifier = Modifier.testTag("include_recognized_switch")
                    )
                    Text("同时重新识别已识别的图片", fontSize = 13.sp)
                }
            }
        }

        // 批次与模型
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RectangleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("模型：${s.aiConfig.modelName}", fontWeight = FontWeight.SemiBold)
                Text(
                    "网络状态：" + if (s.networkAvailable) "已连接" else "未连接",
                    fontSize = 12.sp,
                    color = if (s.networkAvailable) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("批次大小：", fontSize = 13.sp)
                    AiConfigRepository.BATCH_SIZE_OPTIONS.forEach { size ->
                        FilterChip(
                            selected = s.batchSize == size,
                            onClick = { viewModel.setBatchSize(size) },
                            shape = RectangleShape,
                            label = { Text("$size") },
                            modifier = Modifier.testTag("batch_size_chip_$size")
                        )
                    }
                }
                Text(
                    "本次将识别 ${s.toProcess.size} 张，预计 ${s.totalBatches} 批；每批一次请求携带整批图片，严格串行执行，前一批保存完才开始下一批。",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // 隐私与费用
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RectangleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("隐私与费用提醒", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
                Text(
                    "点击「开始识别」后，所选图片会被等比压缩并发送到你配置的第三方 AI 服务。" +
                        "该服务如何处理、保留这些图片以及如何计费，由服务商自己决定；费用无法由本 App 预估。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        if (!s.isKeyConfigured) {
            Text("尚未配置 AI API Key，需要先完成配置并测试连接。", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            OutlinedButton(onClick = onOpenSettings, shape = RectangleShape) { Text("前往 AI 设置") }
        }

        Button(
            onClick = { if (viewModel.start(context)) onStarted() },
            enabled = s.isKeyConfigured && s.toProcess.isNotEmpty() && !s.loading,
            shape = RectangleShape,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("confirm_start_recognition_button")
        ) { Text("开始识别（${s.toProcess.size} 张）") }

        OutlinedButton(onClick = onBack, shape = RectangleShape, modifier = Modifier.fillMaxWidth()) { Text("返回修改选择") }
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
