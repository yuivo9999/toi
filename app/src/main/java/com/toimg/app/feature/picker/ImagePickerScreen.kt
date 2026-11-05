package com.toimg.app.feature.picker

import com.toimg.app.core.ui.theme.MediaLabelBg
import com.toimg.app.core.ui.theme.MediaLabelSub
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.toimg.app.domain.model.ImageItem
import com.toimg.app.data.repository.LegacyIndexCleanupRepository
import kotlinx.coroutines.launch

@Composable
fun ImagePickerScreen(
    onContinue: () -> Unit,
    onOpenScanScope: () -> Unit = {},
    onOpenWorkspace: () -> Unit = {},
    viewModel: ImagePickerViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var cleanupPreview by remember { mutableStateOf<LegacyIndexCleanupRepository.Preview?>(null) }
    var cleanupMessage by remember { mutableStateOf<String?>(null) }
    var cleanupBusy by remember { mutableStateOf(false) }

    val requiredPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addPickedUris(context, uris)
        }
    }

    LaunchedEffect(Unit) {
        val isGranted = ContextCompat.checkSelfPermission(
            context,
            requiredPermission
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.updatePermissionStatus(isGranted, context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Status & Room DB Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "Room本地索引",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "P2 本地 SQLite 数据库索引",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "已索引 ${uiState.totalDbCount} 张 | Hash完成 ${uiState.hashedDbCount} 张",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    if (uiState.isScanning || uiState.isHashing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }

                if (uiState.isHashing) {
                    Text(
                        text = uiState.hashProgress,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("photo_picker_button"),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.AddCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("系统照片选择", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onOpenScanScope,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("scan_mediastore_button"),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("扫描范围/重新扫描", fontSize = 12.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.recheckAvailability(context) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("recheck_availability_button"),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("检查文件可用性", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = { viewModel.startHashComputation(context) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("compute_hash_button"),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("重算 SHA-256", fontSize = 12.sp)
                    }
                }
                OutlinedButton(
                    onClick = {
                        cleanupBusy = true
                        scope.launch {
                            cleanupPreview = LegacyIndexCleanupRepository(context).preview()
                            cleanupBusy = false
                        }
                    },
                    enabled = !cleanupBusy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (cleanupBusy) "正在检查历史索引…" else "数据库维护：检查历史缩略图索引") }
            }
        }

        // Folder-first navigation: users see folder groups before individual image tiles.
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (uiState.selectedFolder == null) "图片文件夹 (${uiState.folders.size})" else "文件夹 / ${uiState.folders.firstOrNull { it.path == uiState.selectedFolder }?.name ?: "图片"}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (uiState.selectedFolder != null) {
                TextButton(onClick = { viewModel.setFilterFolder(null) }) { Text("返回文件夹") }
            } else {
                TextButton(onClick = {
                    if (uiState.filterDuplicatesOnly) viewModel.toggleFilterDuplicatesOnly()
                }) { Text("全部 ${uiState.images.size} 张") }
            }
        }

        // Status filter chips + sort menu
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(ImageStatusFilter.entries.toList()) { filter ->
                    FilterChip(
                        selected = uiState.statusFilter == filter,
                        onClick = { viewModel.setStatusFilter(filter) },
                        label = { Text("${filter.label} (${uiState.countOf(filter)})", fontSize = 12.sp) },
                        modifier = Modifier.testTag("status_filter_${filter.name}")
                    )
                }
            }
            var sortMenuOpen by remember { mutableStateOf(false) }
            Box {
                TextButton(
                    onClick = { sortMenuOpen = true },
                    modifier = Modifier.testTag("sort_menu_button")
                ) { Text("排序：${uiState.sortOption.label}", fontSize = 12.sp) }
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                    ImageSortOption.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                viewModel.setSortOption(option)
                                sortMenuOpen = false
                            }
                        )
                    }
                }
            }
        }

        // Selection Controller Bar
        if (uiState.images.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RectangleShape),
                shape = RectangleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "已选择 ${uiState.selectedCount} / ${uiState.filteredImages.size} 张",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (uiState.selectedFolder != null) Text(
                            text = "全选当前文件夹",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { viewModel.selectAll() }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (uiState.selectedFolder != null) Text(
                            text = "反选",
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable { viewModel.invertSelection() }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                                .testTag("invert_selection_button"),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "清空",
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier
                                .clickable { viewModel.clearSelection() }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // Main Content Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when {
                uiState.selectedFolder == null && uiState.folders.isNotEmpty() -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.folders, key = { it.path.ifBlank { it.id } }) { folder ->
                            val folderImages = uiState.images.filter { (if (it.folderPath.isBlank() || it.folderPath == it.path) it.path.substringBeforeLast("/", it.folderName) else it.folderPath) == folder.path }
                            val selectedInFolder = folderImages.count { it.id in uiState.selectedImageIds }
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { viewModel.setFilterFolder(folder.path) },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context).data(folderImages.firstOrNull()?.uri).crossfade(true).build(),
                                        contentDescription = folder.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth().height(112.dp)
                                    )
                                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(folder.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${folder.imageCount} 张 · 已选 $selectedInFolder 张", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            TextButton(onClick = { viewModel.selectFolder(folder.path) }) { Text("选择整个文件夹") }
                                            TextButton(onClick = { viewModel.setFilterFolder(folder.path) }) { Text("进入") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                uiState.filteredImages.isEmpty() && !uiState.isScanning -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = when {
                                uiState.statusFilter != ImageStatusFilter.ALL -> "没有符合「${uiState.statusFilter.label}」的图片"
                                uiState.filterDuplicatesOnly -> "未发现重复图片"
                                else -> "还没有图片记录"
                            },
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "点击【扫描范围/重新扫描】选择要扫描的位置，扫描只在本机建立索引，不会上传图片",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = uiState.filteredImages,
                            key = { it.id }
                        ) { item ->
                            val isSelected = uiState.selectedImageIds.contains(item.id)
                            val isDuplicate = uiState.duplicateImageIds.contains(item.id)
                            ImageGridTile(
                                item = item,
                                isSelected = isSelected,
                                isDuplicate = isDuplicate,
                                isUnavailable = uiState.isUnavailable(item),
                                onToggleSelect = { viewModel.toggleSelection(item.id) },
                                context = context
                            )
                        }
                    }
                }
            }
        }

        // Bottom Action Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onOpenWorkspace,
                modifier = Modifier.testTag("open_workspace_button")
            ) { Text("工作区") }
            Button(
                onClick = {
                    viewModel.commitSelectionForRecognition()
                    onContinue()
                },
                enabled = uiState.selectedCount > 0,
                modifier = Modifier
                    .weight(1f)
                    .testTag("confirm_selection_button")
            ) {
                Text(
                    text = if (uiState.selectedCount > 0) "识别所选 ${uiState.selectedCount} 张（先确认）" else "请选择需要整理的图片"
                )
            }
        }
    }

    cleanupPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { cleanupPreview = null },
            title = { Text("历史索引清理预览") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("发现 ${preview.imageCount} 张明确位于缩略图/缓存目录的历史索引。")
                    Text("关联识别记录：${preview.recognitionCount} 条")
                    Text("涉及目录：${preview.folders.take(8).joinToString("、")}")
                    Text("确认后只删除 ToImg 数据库中的图片索引、识别记录和全文搜索索引；不会删除或移动手机原始图片，也不会修改移动历史。")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    cleanupBusy = true
                    scope.launch {
                        val removed = LegacyIndexCleanupRepository(context).clean(preview)
                        cleanupPreview = null
                        cleanupBusy = false
                        cleanupMessage = "已清理 $removed 条历史索引；手机原始图片未更改。"
                    }
                }, enabled = !cleanupBusy && preview.imageCount > 0) { Text("确认清理索引") }
            },
            dismissButton = { TextButton(onClick = { cleanupPreview = null }) { Text("取消") } }
        )
    }
    cleanupMessage?.let { message ->
        AlertDialog(onDismissRequest = { cleanupMessage = null }, title = { Text("清理完成") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { cleanupMessage = null }) { Text("知道了") } })
    }
}

@Composable
fun ImageGridTile(
    item: ImageItem,
    isSelected: Boolean,
    isDuplicate: Boolean,
    onToggleSelect: () -> Unit,
    context: Context,
    isUnavailable: Boolean = false
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(androidx.compose.ui.graphics.RectangleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onToggleSelect() }
            .testTag("image_tile_${item.id}")
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(item.uri)
                .build(),
            contentDescription = item.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(2.dp, MaterialTheme.colorScheme.primary, androidx.compose.ui.graphics.RectangleShape)
            )
        }

        if (isUnavailable) {
            Surface(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.error,
                shape = androidx.compose.ui.graphics.RectangleShape
            ) {
                Text(
                    text = "文件不可用",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        // Duplicate Tag Top Left
        if (isDuplicate) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = androidx.compose.ui.graphics.RectangleShape
            ) {
                Text(
                    text = "重复",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }

        // Checkbox Badge Top Right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "已选择",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Circle,
                    contentDescription = "未选择",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Bottom Info Badge
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(),
            color = MediaLabelBg
        ) {
            Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                Text(
                    text = item.formattedSize,
                    color = Color.White,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!item.sha256.isNullOrBlank()) {
                    Text(
                        text = "Hash: ${item.sha256.take(8)}...",
                        color = MediaLabelSub,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
