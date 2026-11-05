package com.toimg.app.core.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toimg.app.data.local.entity.MoveHistoryEntity
import com.toimg.app.data.repository.LocalImageRepositoryImpl
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import android.widget.Toast
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

@Composable
fun MoveHistoryDialog(
    context: Context,
    onDismiss: () -> Unit
) {
    val repository = remember { LocalImageRepositoryImpl() }
    val scope = rememberCoroutineScope()
    var historyList by remember { mutableStateOf<List<MoveHistoryEntity>>(emptyList()) }

    LaunchedEffect(Unit) {
        repository.observeMoveHistory(context).collect { list ->
            historyList = list
        }
    }

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
                        Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "移动历史与【后悔药】撤回",
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
            ) {
                if (historyList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("暂无文件移动记录", color = MaterialTheme.colorScheme.outline)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(historyList) { item ->
                            HistoryItemCard(
                                item = item,
                                onRollback = {
                                    scope.launch {
                                        val res = repository.rollbackMove(context, item.id)
                                        res.onSuccess { msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }.onFailure { err ->
                                            Toast.makeText(context, "撤回失败: ${err.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun HistoryItemCard(
    item: MoveHistoryEntity,
    onRollback: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RectangleShape),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RectangleShape
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Surface(
                    shape = RectangleShape,
                    color = when (item.status) {
                        "SUCCESS" -> MaterialTheme.colorScheme.primaryContainer
                        "PARTIALLY_COMPLETED" -> MaterialTheme.colorScheme.tertiaryContainer
                        "FAILED" -> MaterialTheme.colorScheme.errorContainer
                        "ROLLED_BACK" -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = when (item.status) {
                            "SUCCESS" -> "移动成功"
                            "PARTIALLY_COMPLETED" -> "部分完成"
                            "FAILED" -> "移动失败"
                            "SKIPPED" -> "已跳过"
                            "ROLLED_BACK" -> "已撤回"
                            else -> item.status
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when (item.status) {
                            "SUCCESS" -> MaterialTheme.colorScheme.onPrimaryContainer
                            "PARTIALLY_COMPLETED" -> MaterialTheme.colorScheme.onTertiaryContainer
                            "FAILED" -> MaterialTheme.colorScheme.onErrorContainer
                            "ROLLED_BACK" -> MaterialTheme.colorScheme.onSecondaryContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = "目标: ${item.targetPath}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!item.sha256.isNullOrBlank()) {
                Text(
                    text = "SHA-256: ${item.sha256.take(12)}... (完整性匹配)",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(item.timestamp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "方式: ${item.executionMethod} | 时间: $dateStr",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.outline
                )

                // Option A: Undo / Rollback Button
                if (item.status == "SUCCESS" || item.status == "PARTIALLY_COMPLETED") {
                    OutlinedButton(
                        onClick = onRollback,
                        modifier = Modifier.height(28.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("撤回移动", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

