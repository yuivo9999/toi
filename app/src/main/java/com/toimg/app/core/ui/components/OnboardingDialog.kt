package com.toimg.app.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun OnboardingDialog(
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()

    AlertDialog(
        shape = androidx.compose.ui.graphics.RectangleShape,
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Text(
                        text = "欢迎使用 ToImg 图片整理",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "本工具遵循【图片由您完全掌控、AI 辅助理解、确认后才安全移动】的核心原则。使用前请了解以下概念：",
                    style = MaterialTheme.typography.bodyMedium
                )

                // Concept Comparison Card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = androidx.compose.ui.graphics.RectangleShape
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "核心概念与位置影响",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelLarge
                        )

                        ConceptRow(
                            icon = Icons.Default.PhotoLibrary,
                            action = "扫描 / 建立索引",
                            meaning = "读取元数据与计算哈希",
                            changesLocation = false
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ConceptRow(
                            icon = Icons.Default.AutoAwesome,
                            action = "AI 视觉识别",
                            meaning = "提取标签与摘要描述",
                            changesLocation = false
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ConceptRow(
                            icon = Icons.Default.PhotoLibrary,
                            action = "智能归组 / 分类",
                            meaning = "建立标签逻辑检索视图",
                            changesLocation = false
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ConceptRow(
                            icon = Icons.Default.DriveFileMove,
                            action = "真实移动文件",
                            meaning = "移入您指定的目标目录",
                            changesLocation = true
                        )
                    }
                }

                // Privacy Commitment Card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = androidx.compose.ui.graphics.RectangleShape
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text(
                                text = "隐私与零数据丢失承诺",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = "1. 本地优先：扫描与索引均在手机本地执行，原始图片绝不上传私有云端。\n" +
                                    "2. 用户授权 AI：仅在您主动启动识别时，才将所选图片压缩发送给您配置的 AI 服务商。\n" +
                                    "3. 绝对安全移动：移动前必须由您确认预览。采用【复制 ➔ SHA-256 完整性核验 ➔ 删除原文件】机制，严禁静默覆盖同名文件，校验不通过绝不删原图。",
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_confirm_button")
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("我已知悉，开始使用")
            }
        }
    )
}

@Composable
private fun ConceptRow(
    icon: ImageVector,
    action: String,
    meaning: String,
    changesLocation: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
            Column {
                Text(text = action, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Text(text = meaning, fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
            }
        }

        Surface(
            shape = androidx.compose.ui.graphics.RectangleShape,
            color = if (changesLocation) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
        ) {
            Text(
                text = if (changesLocation) "物理改变(需确认)" else "不改变文件位置",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (changesLocation) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}
