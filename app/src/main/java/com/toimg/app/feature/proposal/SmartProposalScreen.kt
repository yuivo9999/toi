package com.toimg.app.feature.proposal

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage

@Composable
fun SmartProposalScreen(
    onStartMove: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: SmartProposalViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    var pickingRuleId by remember { mutableStateOf<Long?>(null) }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        val ruleId = pickingRuleId
        if (uri != null && ruleId != null) {
            val name = DocumentFile.fromTreeUri(context, uri)?.name ?: "目标文件夹"
            viewModel.setTarget(context, ruleId, uri, name)
        }
        pickingRuleId = null
    }

    LaunchedEffect(Unit) { viewModel.load(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "识别结果已按你的归档规则预先分好组。这里只是提案：选好目标文件夹后会进入「移动预览」，由你最后确认，不会直接移动任何文件。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        when {
            state.isLoading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            state.groups.isEmpty() -> Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "暂时没有可归类的图片。需要：图片已识别、尚未移动，并且关键词命中了已启用的归档规则。",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(onClick = onOpenSettings) { Text("去设置归档规则") }
            }

            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.groups, key = { it.rule.id }) { group ->
                    ProposalGroupCard(
                        group = group,
                        onToggleImage = { viewModel.toggleExclude(group.rule.id, it) },
                        onPickFolder = {
                            pickingRuleId = group.rule.id
                            folderLauncher.launch(null)
                        },
                        onMove = { if (viewModel.prepareMove(group.rule.id)) onStartMove() }
                    )
                }
            }
        }
    }

    state.message?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissMessage() },
            shape = RectangleShape,
            confirmButton = { TextButton(onClick = { viewModel.dismissMessage() }, shape = RectangleShape) { Text("确定") } },
            text = { Text(msg) }
        )
    }
}

@Composable
private fun ProposalGroupCard(
    group: ProposalGroupUi,
    onToggleImage: (String) -> Unit,
    onPickFolder: () -> Unit,
    onMove: () -> Unit
) {
    val selected = group.selectedImages
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RectangleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = group.rule.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${selected.size}/${group.images.size} 张",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Text(
                text = "触发词：${group.rule.keywordTrigger}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(group.images, key = { it.id }) { img ->
                    val excluded = img.id in group.excludedIds
                    AsyncImage(
                        model = img.uriString,
                        contentDescription = img.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RectangleShape)
                            .alpha(if (excluded) 0.3f else 1f)
                            .clickable { onToggleImage(img.id) }
                    )
                }
            }
            Text(
                text = "点按缩略图可把它从本次提案中排除 / 恢复",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                val targetText = when {
                    group.targetUri == null -> "目标文件夹未指定（规则建议：${group.rule.targetFolderName}）"
                    !group.targetWritable -> "目标：${group.targetName}（无写入权限，请重新选择）"
                    else -> "目标：${group.targetName}"
                }
                Text(text = targetText, fontSize = 12.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = onPickFolder, shape = RectangleShape) {
                    Text(if (group.targetUri == null) "选择" else "更换")
                }
            }
            Button(
                onClick = onMove,
                enabled = selected.isNotEmpty() && group.targetUri != null && group.targetWritable,
                shape = RectangleShape,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("proposal_move_${group.rule.id}")
            ) {
                Text("预览并移动这 ${selected.size} 张")
            }
        }
    }
}
