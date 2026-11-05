package com.toimg.app.feature.categories

import com.toimg.app.core.ui.theme.MediaLabelBg
import com.toimg.app.core.ui.theme.MediaLabelSub
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.RectangleShape
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import com.toimg.app.domain.model.DateRangeFilter
import com.toimg.app.domain.model.SizeRangeFilter
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.toimg.app.domain.model.CategoryItem
import com.toimg.app.domain.model.ImageItem
import com.toimg.app.domain.model.RecognitionResult

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onGoToWorkspaceOrResult: () -> Unit,
    viewModel: CategoriesViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var showMergeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.init(context)
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Search Input Bar with Filter Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(context, it) },
                placeholder = { Text("FTS 全文检索标签、描述、OCR 或文件名...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateSearchQuery(context, "") }) {
                            Icon(Icons.Default.Clear, contentDescription = "清空搜索")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("categories_search_input")
            )

            IconButton(
                onClick = { viewModel.toggleFilterPanel() },
                modifier = Modifier
                    .background(
                        color = if (uiState.filterCriteria.hasActiveFilters || uiState.isFilterPanelExpanded)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant,
                        shape = androidx.compose.ui.graphics.RectangleShape
                    )
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), androidx.compose.ui.graphics.RectangleShape)
                    .testTag("toggle_filter_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "多维高级筛选",
                    tint = if (uiState.filterCriteria.hasActiveFilters || uiState.isFilterPanelExpanded)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Multi-dimensional Filter Expandable Panel
        if (uiState.isFilterPanelExpanded) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), androidx.compose.ui.graphics.RectangleShape)
                    .testTag("multi_filter_panel"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = androidx.compose.ui.graphics.RectangleShape
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("多维条件筛选", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        }

                        if (uiState.filterCriteria.hasActiveFilters) {
                            TextButton(
                                onClick = { viewModel.resetFilters(context) },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("重置筛选", fontSize = 12.sp)
                            }
                        }
                    }

                    // 1. 时间区间筛选
                    Text("拍摄/修改时间：", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        DateRangeFilter.entries.forEach { range ->
                            FilterChip(
                                selected = uiState.filterCriteria.dateRange == range,
                                onClick = { viewModel.updateDateRange(context, range) },
                                label = { Text(range.label, fontSize = 11.sp) }
                            )
                        }
                    }

                    // 2. 文件大小筛选
                    Text("文件体积尺寸：", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        SizeRangeFilter.entries.forEach { range ->
                            FilterChip(
                                selected = uiState.filterCriteria.sizeRange == range,
                                onClick = { viewModel.updateSizeRange(context, range) },
                                label = { Text(range.label, fontSize = 11.sp) }
                            )
                        }
                    }

                    // 3. 来源相册目录筛选
                    if (uiState.availableFolders.isNotEmpty()) {
                        Text("来源相册文件夹：", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            uiState.availableFolders.take(8).forEach { folder ->
                                val isSelected = uiState.filterCriteria.folderName == folder
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.updateFolderFilter(context, folder) },
                                    label = { Text(folder, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    // 4. 状态与敏感保护
                    Text("属性与隐私过滤：", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        FilterChip(
                            selected = uiState.filterCriteria.onlySensitive,
                            onClick = { viewModel.updateSensitiveFilter(context, !uiState.filterCriteria.onlySensitive) },
                            label = { Text("仅私密证件/发票", fontSize = 11.sp) }
                        )

                        FilterChip(
                            selected = uiState.filterCriteria.status == "RECOGNIZED",
                            onClick = { viewModel.updateStatusFilter(context, "RECOGNIZED") },
                            label = { Text("已完成 AI 识别", fontSize = 11.sp) }
                        )

                        FilterChip(
                            selected = uiState.filterCriteria.status == "SCANNED",
                            onClick = { viewModel.updateStatusFilter(context, "SCANNED") },
                            label = { Text("待识别", fontSize = 11.sp) }
                        )

                        FilterChip(
                            selected = uiState.filterCriteria.status == "MOVED",
                            onClick = { viewModel.updateStatusFilter(context, "MOVED") },
                            label = { Text("已归档移入相册", fontSize = 11.sp) }
                        )
                    }
                }
            }
        }

        // Active Search Results
        if (uiState.isSearchOrFilterActive) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "检索结果 (${uiState.searchResults.size} 张)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                if (uiState.searchDurationMs > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = androidx.compose.ui.graphics.RectangleShape
                    ) {
                        Text(
                            text = "FTS4 索引检索: ${uiState.searchDurationMs}ms",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (uiState.searchResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("未找到符合多维检索条件的图片", color = MaterialTheme.colorScheme.outline)
                        Text("已在 Room FTS 全文索引（标签、描述、OCR）、文件夹与日期中检索", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        TextButton(onClick = { viewModel.resetFilters(context) }) {
                            Text("清空所有筛选条件")
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(uiState.searchResults, key = { it.id }) { item ->
                        val rec = uiState.recognitions[item.id]
                        SearchResultCard(item = item, recognition = rec, context = context)
                    }
                }
            }
        } else {
            // Logic View Notice
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), androidx.compose.ui.graphics.RectangleShape),
                color = MaterialTheme.colorScheme.surface,
                shape = androidx.compose.ui.graphics.RectangleShape
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "逻辑分类说明：分类由 AI 关键词自动聚合。同一个图片可归属多个标签，原文件物理位置保持不变。",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        if (!uiState.isSearchOrFilterActive && uiState.selectedCategory != null) {

            // Category Detail View
            val category = uiState.selectedCategory!!
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.selectCategory(null) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回分类")
                    }
                    Text(
                        text = "${category.displayName} (${category.imageCount}张)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showMergeDialog = true }) {
                        Text("🔀 合并", fontSize = 12.sp)
                    }
                    TextButton(onClick = { viewModel.selectAllInDetail() }) {
                        Text("全选", fontSize = 12.sp)
                    }
                    if (uiState.selectedImageIds.isNotEmpty()) {
                        TextButton(onClick = { viewModel.clearSelection() }) {
                            Text("清空", fontSize = 12.sp)
                        }
                    }
                }

            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(bottom = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(uiState.categoryDetailImages, key = { it.id }) { item ->
                    val isSelected = uiState.selectedImageIds.contains(item.id)
                    val rec = uiState.recognitions[item.id]
                    CategoryDetailTile(
                        item = item,
                        recognition = rec,
                        isSelected = isSelected,
                        onToggleSelect = { viewModel.toggleSelection(item.id) },
                        context = context
                    )
                }
            }

            Button(
                onClick = onGoToWorkspaceOrResult,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .testTag("organize_category_button")
            ) {
                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (uiState.selectedImageIds.isNotEmpty()) {
                        "整理分类选中的 ${uiState.selectedImageIds.size} 张图片 ➔"
                    } else {
                        "前往工作区整理此分类 ➔"
                    }
                )
            }
        } else if (!uiState.isSearchOrFilterActive) {
            // Merge Suggestions Card
            if (uiState.mergeSuggestions.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    shape = RectangleShape,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
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
                                text = "智能标签合并建议 (${uiState.mergeSuggestions.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        uiState.mergeSuggestions.forEach { suggestion ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "#${suggestion.sourceTag} ➔ #${suggestion.targetTag}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = suggestion.reason,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                Button(
                                    onClick = {
                                        viewModel.mergeCategories(context, suggestion.sourceTag, suggestion.targetTag)
                                        Toast.makeText(
                                            context,
                                            "已将 #${suggestion.sourceTag} 合并至 #${suggestion.targetTag}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("一键合并", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Main Category Cards Grid
            Text(
                text = "AI 聚合分类 (${uiState.categories.size} 个标签)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (uiState.categories.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                        Spacer(Modifier.height(8.dp))
                        Text("暂无分类结果，请先进行图片识别。", color = MaterialTheme.colorScheme.outline)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(uiState.categories, key = { it.tag }) { category ->
                        CategoryCardItem(
                            category = category,
                            onClick = { viewModel.selectCategory(category) },
                            context = context
                        )
                    }
                }
            }
        }
    }

    if (showMergeDialog && uiState.selectedCategory != null) {
        val currentCategory = uiState.selectedCategory!!
        val otherCategories = uiState.categories.filter { it.tag != currentCategory.tag }
        MergeCategoryDialog(
            currentTag = currentCategory.tag,
            otherCategories = otherCategories,
            onDismiss = { showMergeDialog = false },
            onConfirmMerge = { targetTag ->
                viewModel.mergeCategories(context, currentCategory.tag, targetTag)
                showMergeDialog = false
                Toast.makeText(context, "已成功将 #${currentCategory.tag} 合并至 #$targetTag", Toast.LENGTH_SHORT).show()
            }
        )
    }
}


@Composable
fun CategoryCardItem(
    category: CategoryItem,
    onClick: () -> Unit,
    context: Context
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.ui.graphics.RectangleShape)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), androidx.compose.ui.graphics.RectangleShape)
            .clickable { onClick() }
            .testTag("category_card_${category.tag}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = androidx.compose.ui.graphics.RectangleShape
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.3f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (category.coverUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(category.coverUri)
                            .build(),
                        contentDescription = category.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Category, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                    }
                }

                // Count Badge
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                    color = MediaLabelBg,
                    shape = androidx.compose.ui.graphics.RectangleShape
                ) {
                    Text(
                        text = "${category.imageCount} 张",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = category.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryDetailTile(
    item: ImageItem,
    recognition: RecognitionResult?,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    context: Context
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.ui.graphics.RectangleShape)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), androidx.compose.ui.graphics.RectangleShape)
            .clickable { onToggleSelect() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = androidx.compose.ui.graphics.RectangleShape
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

                IconButton(
                    onClick = onToggleSelect,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    if (isSelected) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "已选择", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Outlined.Circle, contentDescription = "未选择", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }

                // Source Folder Badge Bottom Left
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp),
                    color = MediaLabelBg,
                    shape = RectangleShape
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = MediaLabelSub, modifier = Modifier.size(10.dp))
                        Spacer(Modifier.width(2.dp))
                        Text(item.folderName, color = Color.White, fontSize = 10.sp, maxLines = 1)
                    }
                }
            }

            Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = item.name, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (recognition?.description != null) {
                    Text(text = recognition.description, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

@Composable
fun SearchResultCard(
    item: ImageItem,
    recognition: RecognitionResult?,
    context: Context
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RectangleShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.3f)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.uri)
                        .build(),
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Status / Sensitive badges top right
                if (recognition?.isSensitive == true) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RectangleShape
                    ) {
                        Text(
                            text = "私密凭证",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                } else if (recognition?.modelName == "MLKit-OnDevice") {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RectangleShape
                    ) {
                        Text(
                            text = "端侧离线",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                // Folder badge bottom left
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp),
                    color = MediaLabelBg,
                    shape = RectangleShape
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = MediaLabelSub, modifier = Modifier.size(10.dp))
                        Spacer(Modifier.width(2.dp))
                        Text(item.folderName, color = Color.White, fontSize = 9.sp, maxLines = 1)
                    }
                }
            }

            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.name, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("大小: ${item.formattedSize}", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                if (recognition?.keywordsSummary?.isNotBlank() == true) {
                    Text("#${recognition.keywordsSummary}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MergeCategoryDialog(
    currentTag: String,
    otherCategories: List<CategoryItem>,
    onDismiss: () -> Unit,
    onConfirmMerge: (String) -> Unit
) {
    var customTargetTag by remember { mutableStateOf("") }
    var selectedExistingTag by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "合并分类标签", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "将当前分类【#$currentTag】下的所有图片合并到另一个分类：",
                    style = MaterialTheme.typography.bodyMedium
                )

                if (otherCategories.isNotEmpty()) {
                    Text(
                        text = "选择已有分类：",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        otherCategories.take(12).forEach { cat ->
                            val isChosen = selectedExistingTag == cat.tag
                            FilterChip(
                                selected = isChosen,
                                onClick = {
                                    selectedExistingTag = if (isChosen) null else cat.tag
                                    if (selectedExistingTag != null) customTargetTag = ""
                                },
                                label = { Text("#${cat.tag} (${cat.imageCount})", fontSize = 12.sp) }
                            )
                        }
                    }
                }

                Text(
                    text = "或输入新目标分类名：",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                OutlinedTextField(
                    value = customTargetTag,
                    onValueChange = {
                        customTargetTag = it
                        if (it.isNotBlank()) selectedExistingTag = null
                    },
                    placeholder = { Text("例如：工作票据", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            val finalTarget = selectedExistingTag ?: customTargetTag.trim()
            Button(
                onClick = {
                    if (finalTarget.isNotBlank()) {
                        onConfirmMerge(finalTarget)
                    }
                },
                enabled = finalTarget.isNotBlank() && finalTarget != currentTag
            ) {
                Text("确认合并")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

