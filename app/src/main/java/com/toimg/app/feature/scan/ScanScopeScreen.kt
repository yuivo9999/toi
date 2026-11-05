package com.toimg.app.feature.scan

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.toimg.app.core.work.ScanTaskScheduler
import com.toimg.app.core.util.ScanMode

import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.RectangleShape

@Composable
fun ScanScopeScreen(
    onStartScan: () -> Unit,
    viewModel: ScanScopeViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var showRationale by remember { mutableStateOf(false) }
    var showSubfolderDialog by remember { mutableStateOf(false) }
    var includeSubfoldersChoice by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val requiredPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    fun hasPermission() = ContextCompat.checkSelfPermission(context, requiredPermission) == PackageManager.PERMISSION_GRANTED

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onPermissionResult(granted)
    }

    val safLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
        if (treeUri != null) viewModel.addFolder(context, treeUri)
    }

    LaunchedEffect(Unit) { viewModel.load(context, hasPermission()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("选择扫描范围", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(
            "扫描只会在本机建立图片索引：不复制、不修改原图，也不会上传到任何服务。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 方式 A：系统媒体库
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RectangleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("方式 A：扫描可访问的图片", fontWeight = FontWeight.SemiBold)
                Text(
                    "读取系统媒体库中 App 有权访问的图片，适合快速覆盖相机、截图、下载等常见位置。",
                    style = MaterialTheme.typography.bodySmall
                )
                if (uiState.permissionGranted) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = uiState.includeMediaStore,
                            onCheckedChange = { viewModel.toggleMediaStore() },
                            modifier = Modifier.testTag("scope_mediastore_checkbox")
                        )
                        Text("本次扫描包含系统媒体库", fontSize = 14.sp)
                    }
                } else {
                    Text(
                        if (uiState.permissionDenied) "已拒绝图片读取权限。你仍可使用下方「选择文件夹」继续，不受影响。"
                        else "尚未授予图片读取权限。",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (uiState.permissionDenied) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                    )
                    OutlinedButton(
                        onClick = { showRationale = true },
                        shape = RectangleShape,
                        modifier = Modifier.testTag("scan_mediastore_button")
                    ) { Text("授予读取权限") }
                }
                Text(
                    "说明：如果系统只授予「部分照片」访问，扫描结果只包含你授权的那一部分。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        // 方式 B：SAF 文件夹
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RectangleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("方式 B：选择文件夹（推荐，最稳定）", fontWeight = FontWeight.SemiBold)
                Text(
                    "通过系统文件夹选择器授权目录，可重复添加多个，例如 Pictures、DCIM、Download。开始扫描前可选择只扫当前目录，或同时扫描所有下级目录。系统默认跳过 .thumbnails、thumbnails、cache、temp 等缓存目录；如果你主动把其中某个目录本身选为扫描根目录，则允许本次扫描该目录。",
                    style = MaterialTheme.typography.bodySmall
                )

                if (uiState.folders.isEmpty()) {
                    Text("还没有已授权的目录。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                } else {
                    Text("来源的重命名、重新授权和移出项目，请统一在首页的「图片来源管理」中操作。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }

                uiState.folders.forEach { folder ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = folder.id in uiState.selectedFolderIds && folder.isAuthorized,
                            onCheckedChange = { viewModel.toggleFolder(folder.id) },
                            enabled = folder.isAuthorized
                        )
                        Column(Modifier.weight(1f)) {
                            Text(folder.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(
                                if (folder.isAuthorized) "授权有效" else "授权已失效，需要重新授权",
                                fontSize = 11.sp,
                                color = if (folder.isAuthorized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                        if (!folder.isAuthorized) {
                            Text("请返回首页，在「图片来源管理」中重新授权。", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        safLauncher.launch(null)
                    },
                    shape = RectangleShape,
                    modifier = Modifier.testTag("authorize_saf_folder_button")
                ) { Text("选择文件夹") }
            }
        }

        // 扫描策略：快速增量为默认；核对模式额外核验旧记录。
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RectangleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("扫描方式", fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = uiState.scanMode == ScanMode.FAST_INCREMENTAL,
                        onClick = { viewModel.setScanMode(ScanMode.FAST_INCREMENTAL) },
                        modifier = Modifier.testTag("scan_mode_fast_radio")
                    )
                    Column(Modifier.weight(1f)) {
                        Text("快速增量扫描（默认）", fontWeight = FontWeight.Medium)
                        Text("旧图片已入库就直接略过，只为新图片建立索引；不核对旧图片是否被修改或移走。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = uiState.scanMode == ScanMode.VERIFY_EXISTING,
                        onClick = { viewModel.setScanMode(ScanMode.VERIFY_EXISTING) },
                        modifier = Modifier.testTag("scan_mode_verify_radio")
                    )
                    Column(Modifier.weight(1f)) {
                        Text("增量扫描 + 核对旧图片", fontWeight = FontWeight.Medium)
                        Text("新图片正常入库；同时检查旧图片的文件大小、修改时间和可用性。发现元数据变化时重新计算指纹，并将旧识别结果标记为待重新识别。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("提示：核对模式通过文件元数据筛查变化；不会对每一张未变化图片都重新计算 SHA-256。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
        }

        // 范围摘要
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RectangleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("扫描范围摘要", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                val mediaText = if (uiState.includeMediaStore && uiState.permissionGranted) "系统媒体库" else null
                val folderText = uiState.selectedFolders.map { it.name }
                val parts = listOfNotNull(mediaText) + folderText
                Text(
                    if (parts.isEmpty()) "尚未选择任何扫描范围" else "将扫描：" + parts.joinToString("、"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "可能无法访问：其他 App 的私有目录、Android/data、系统保护区域，以及未授权的目录。扫描结果只代表授权范围内发现的图片，不代表整个手机。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Button(
            onClick = {
                if (ScanTaskScheduler.hasActiveTask(context)) {
                    // 已有任务时直接回到任务页，不重新弹范围选项、不创建第二次扫描。
                    onStartScan()
                } else if (uiState.selectedFolders.isNotEmpty()) {
                    // SAF 目录扫描前明确询问是否进入下级目录；系统媒体库扫描不受此选项影响。
                    includeSubfoldersChoice = false // 默认最小扫描范围，避免未经确认遍历更深目录。
                    showSubfolderDialog = true
                } else if (viewModel.startScan(context, includeSubfolders = true)) {
                    onStartScan()
                }
            },
            enabled = uiState.canStart || ScanTaskScheduler.hasActiveTask(context),
            shape = RectangleShape,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("start_scan_button")
        ) { Text("开始扫描") }

        Spacer(Modifier.height(8.dp))
    }


    if (showSubfolderDialog) {
        AlertDialog(
            onDismissRequest = { showSubfolderDialog = false },
            shape = RectangleShape,
            title = { Text("是否扫描下级文件夹？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "你选择了 ${uiState.selectedFolders.size} 个文件夹。请选择扫描深度：",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !includeSubfoldersChoice,
                            onClick = { includeSubfoldersChoice = false },
                            modifier = Modifier.testTag("scan_current_folder_only_radio")
                        )
                        Column(Modifier.weight(1f)) {
                            Text("仅扫描当前文件夹", fontWeight = FontWeight.Medium)
                            Text("只读取所选目录本层的图片，不进入里面的文件夹。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = includeSubfoldersChoice,
                            onClick = { includeSubfoldersChoice = true },
                            modifier = Modifier.testTag("scan_include_subfolders_radio")
                        )
                        Column(Modifier.weight(1f)) {
                            Text("同时扫描所有下级文件夹", fontWeight = FontWeight.Medium)
                            Text("递归读取所选目录及其下级目录中的图片。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(
                        "此选项仅影响你通过「选择文件夹」授权的目录；系统媒体库扫描范围保持不变。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSubfolderDialog = false
                        if (viewModel.startScan(context, includeSubfoldersChoice)) onStartScan()
                    },
                    shape = RectangleShape,
                    modifier = Modifier.testTag("confirm_folder_scan_depth_button")
                ) { Text("开始扫描") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSubfolderDialog = false },
                    shape = RectangleShape
                ) { Text("取消") }
            }
        )
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            shape = RectangleShape,
            title = { Text("为什么需要读取图片权限？") },
            text = {
                Text(
                    "权限只用于在本机发现图片并建立索引，不会上传任何图片。\n\n" +
                        "注意：Android 不允许 App 读取所有目录，其他 App 私有目录、Android/data 等位置无法扫描。" +
                        "如果你不想授权，可以改用「选择文件夹」。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRationale = false
                        permissionLauncher.launch(requiredPermission)
                    },
                    shape = RectangleShape
                ) { Text("继续授权") }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }, shape = RectangleShape) { Text("暂不授权") }
            }
        )
    }
}
