package com.toimg.app.feature.duplicates

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
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
import com.toimg.app.core.ui.theme.MediaLabelBg
import com.toimg.app.core.ui.theme.MediaLabelSub
import com.toimg.app.core.util.DuplicateGroup
import com.toimg.app.core.util.DuplicateType
import com.toimg.app.domain.model.ImageItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesScreen(
    onBack: () -> Unit = {},
    viewModel: DuplicatesViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadDuplicates(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Stats Overview Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RectangleShape),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RectangleShape
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CleaningServices,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "重复与相似图片清理",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    IconButton(
                        onClick = { viewModel.loadDuplicates(context) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "重新扫描",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Text(
                    text = "基于 SHA-256 算法精准识别完全相同图片，同时利用 dHash 64位感知哈希聚类连拍与视觉相似图片，助您迅速释放相册空间。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "发现 ${uiState.groups.size} 个组",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "预计可释放空间: ${uiState.formatBytes(uiState.totalPotentialSavings)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Tabs
        PrimaryTabRow(
            selectedTabIndex = uiState.currentTab.ordinal,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = uiState.currentTab == DuplicateFilterTab.ALL,
                onClick = { viewModel.setTab(DuplicateFilterTab.ALL) },
                text = { Text("全部 (${uiState.groups.size})", fontSize = 13.sp) }
            )
            Tab(
                selected = uiState.currentTab == DuplicateFilterTab.EXACT_SHA256,
                onClick = { viewModel.setTab(DuplicateFilterTab.EXACT_SHA256) },
                text = {
                    Text(
                        "完全重复 (${uiState.groups.count { it.type == DuplicateType.EXACT_SHA256 }})",
                        fontSize = 13.sp
                    )
                }
            )
            Tab(
                selected = uiState.currentTab == DuplicateFilterTab.VISUALLY_SIMILAR,
                onClick = { viewModel.setTab(DuplicateFilterTab.VISUALLY_SIMILAR) },
                text = {
                    Text(
                        "视觉相似 (${uiState.groups.count { it.type == DuplicateType.VISUALLY_SIMILAR }})",
                        fontSize = 13.sp
                    )
                }
            )
        }

        // Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "已选 ${uiState.selectedDuplicateIds.size} 张 (${uiState.formatBytes(uiState.selectedSavingsBytes)})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { viewModel.selectAllRecommendedDuplicates() },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("智能保留最优", fontSize = 12.sp)
                }
                if (uiState.selectedDuplicateIds.isNotEmpty()) {
                    TextButton(
                        onClick = { viewModel.clearSelection() },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("清空", fontSize = 12.sp)
                    }
                }
            }
        }

        // Content List
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (uiState.isLoading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(uiState.progressMessage, style = MaterialTheme.typography.bodyMedium)
                }
            } else if (uiState.filteredGroups.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("太棒了！当前分类下未发现重复或相似照片。", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("相册非常整洁，无多余冗余副本。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyLazyGroupList(
                    groups = uiState.filteredGroups,
                    selectedDuplicateIds = uiState.selectedDuplicateIds,
                    onToggleSelect = { viewModel.toggleDuplicateSelection(it) },
                    formatBytes = { uiState.formatBytes(it) },
                    context = context
                )
            }
        }

        // Bottom Action Bar
        if (uiState.selectedDuplicateIds.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        Toast.makeText(
                            context,
                            "已标记选中的 ${uiState.selectedDuplicateIds.size} 张副本图片",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("clean_duplicates_button")
                ) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("处理选中的 ${uiState.selectedDuplicateIds.size} 张重复图片 (${uiState.formatBytes(uiState.selectedSavingsBytes)})")
                }
            }
        }
    }
}

@Composable
fun LazyLazyGroupList(
    groups: List<DuplicateGroup>,
    selectedDuplicateIds: Set<String>,
    onToggleSelect: (String) -> Unit,
    formatBytes: (Long) -> String,
    context: Context
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(groups, key = { it.id }) { group ->
            DuplicateGroupCard(
                group = group,
                selectedDuplicateIds = selectedDuplicateIds,
                onToggleSelect = onToggleSelect,
                formatBytes = formatBytes,
                context = context
            )
        }
    }
}

@Composable
fun DuplicateGroupCard(
    group: DuplicateGroup,
    selectedDuplicateIds: Set<String>,
    onToggleSelect: (String) -> Unit,
    formatBytes: (Long) -> String,
    context: Context
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), androidx.compose.ui.graphics.RectangleShape),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = androidx.compose.ui.graphics.RectangleShape
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Group Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = androidx.compose.ui.graphics.RectangleShape,
                        color = if (group.type == DuplicateType.EXACT_SHA256)
                            MaterialTheme.colorScheme.errorContainer
                        else
                            MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            text = if (group.type == DuplicateType.EXACT_SHA256) "完全重复" else "视觉相似",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (group.type == DuplicateType.EXACT_SHA256)
                                MaterialTheme.colorScheme.onErrorContainer
                            else
                                MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = group.similarityDescription,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "释放 ${formatBytes(group.potentialSavingsBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(10.dp))

            // Horizontal Scroll of Images in this Group
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Primary Image (Keep Recommendation)
                ImageThumbCard(
                    item = group.primaryImage,
                    isPrimary = true,
                    isSelected = false,
                    onToggleSelect = {},
                    context = context
                )

                // Duplicate Images (Can be cleaned)
                group.duplicateImages.forEach { dupItem ->
                    val isChecked = selectedDuplicateIds.contains(dupItem.id)
                    ImageThumbCard(
                        item = dupItem,
                        isPrimary = false,
                        isSelected = isChecked,
                        onToggleSelect = { onToggleSelect(dupItem.id) },
                        context = context
                    )
                }
            }
        }
    }
}

@Composable
fun ImageThumbCard(
    item: ImageItem,
    isPrimary: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    context: Context
) {
    Card(
        modifier = Modifier
            .width(130.dp)
            .clip(androidx.compose.ui.graphics.RectangleShape)
            .border(
                width = if (isPrimary) 2.dp else if (isSelected) 2.dp else 1.dp,
                color = if (isPrimary) MaterialTheme.colorScheme.primary else if (isSelected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
                shape = androidx.compose.ui.graphics.RectangleShape
            )
            .clickable(enabled = !isPrimary) { onToggleSelect() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = androidx.compose.ui.graphics.RectangleShape
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.1f)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.uri)
                        .build(),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                if (isPrimary) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(4.dp),
                        shape = androidx.compose.ui.graphics.RectangleShape,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = "⭐ 保留最优",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = onToggleSelect,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                    ) {
                        if (isSelected) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "已标记删除",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(22.dp)
                            )
                        } else {
                            Icon(
                                Icons.Outlined.Circle,
                                contentDescription = "未标记",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // Folder Name bottom
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(3.dp),
                    shape = androidx.compose.ui.graphics.RectangleShape,
                    color = MediaLabelBg
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = MediaLabelSub, modifier = Modifier.size(10.dp))
                        Spacer(Modifier.width(2.dp))
                        Text(item.folderName, color = Color.White, fontSize = 9.sp, maxLines = 1)
                    }
                }
            }

            Column(modifier = Modifier.padding(6.dp)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.formattedSize,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}
