package com.toimg.app.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.toimg.app.feature.categories.CategoriesScreen
import com.toimg.app.feature.confirm.RecognizeConfirmScreen
import com.toimg.app.feature.duplicates.DuplicatesScreen
import com.toimg.app.feature.history.TaskHistoryScreen
import com.toimg.app.feature.proposal.SmartProposalScreen
import com.toimg.app.feature.home.HomeScreen
import com.toimg.app.feature.scan.ScanProgressScreen
import com.toimg.app.feature.scan.ScanScopeScreen
import com.toimg.app.feature.picker.ImagePickerScreen
import com.toimg.app.feature.result.MoveResultScreen
import com.toimg.app.feature.settings.SettingsScreen
import com.toimg.app.feature.sources.SourceManagementScreen
import com.toimg.app.feature.workspace.ImageWorkspaceScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToImgNavHost(
    initialRoute: String? = null,
    onInitialRouteConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()
    LaunchedEffect(initialRoute) {
        if (initialRoute != null) {
            navController.navigate(initialRoute) { launchSingleTop = true }
            onInitialRouteConsumed()
        }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: AppRoute.HOME

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.primary,
                        navigationIconContentColor = MaterialTheme.colorScheme.primary,
                    ),
                    title = {
                        Text(
                            AppRoute.titleFor(currentRoute),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        )
                    },
                    navigationIcon = {
                        if (currentRoute != AppRoute.HOME) {
                            TextButton(
                                onClick = { navController.navigateUp() },
                                shape = RectangleShape
                            ) {
                                Text("返回", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    },
                    actions = {
                        if (currentRoute != AppRoute.SETTINGS) {
                            TextButton(
                                onClick = { navController.navigate(AppRoute.SETTINGS) },
                                shape = RectangleShape
                            ) {
                                Text("设置", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    },
                )
                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppRoute.HOME,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            composable(AppRoute.HOME) {
                HomeScreen(
                    onSelectImages = { navController.navigate(AppRoute.PICKER) },
                    onOpenWorkspace = { navController.navigate(AppRoute.WORKSPACE) },
                    onOpenCategories = { navController.navigate(AppRoute.CATEGORIES) },
                    onOpenScanScope = { navController.navigate(AppRoute.SCAN_SCOPE) },
                    onOpenTaskHistory = { navController.navigate(AppRoute.TASK_HISTORY) },
                    onOpenDuplicates = { navController.navigate(AppRoute.DUPLICATES) },
                    onOpenProposals = { navController.navigate(AppRoute.SMART_PROPOSAL) },
                    onOpenScanProgress = { navController.navigate(AppRoute.SCAN_PROGRESS) },
                    onOpenSources = { navController.navigate(AppRoute.SOURCES) }
                )
            }
            composable(AppRoute.PICKER) {
                ImagePickerScreen(
                    // 选定图片后先进入识别确认页，确认数量/批次/隐私提示后才会开始识别
                    onContinue = { navController.navigate(AppRoute.RECOGNIZE_CONFIRM) },
                    onOpenScanScope = { navController.navigate(AppRoute.SCAN_SCOPE) },
                    onOpenWorkspace = { navController.navigate(AppRoute.WORKSPACE) }
                )
            }
            composable(AppRoute.SCAN_SCOPE) {
                ScanScopeScreen(
                    onStartScan = { navController.navigate(AppRoute.SCAN_PROGRESS) }
                )
            }
            composable(AppRoute.SOURCES) { SourceManagementScreen() }
            composable(AppRoute.SCAN_PROGRESS) {
                ScanProgressScreen(
                    onViewImages = {
                        navController.navigate(AppRoute.PICKER) {
                            popUpTo(AppRoute.HOME)
                            launchSingleTop = true
                        }
                    },
                    onBackToScope = { navController.navigate(AppRoute.SCAN_SCOPE) { popUpTo(AppRoute.HOME) } },
                    onBackHome = {
                        navController.navigate(AppRoute.HOME) {
                            popUpTo(AppRoute.HOME) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(AppRoute.RECOGNIZE_CONFIRM) {
                RecognizeConfirmScreen(
                    onStarted = {
                        navController.navigate(AppRoute.WORKSPACE) {
                            popUpTo(AppRoute.RECOGNIZE_CONFIRM) { inclusive = true }
                        }
                    },
                    onBack = { navController.navigateUp() },
                    onOpenSettings = { navController.navigate(AppRoute.SETTINGS) }
                )
            }
            composable(AppRoute.WORKSPACE) {
                ImageWorkspaceScreen(
                    onPreviewComplete = { navController.navigate(AppRoute.RESULT) },
                    onNavigateToSettings = { navController.navigate(AppRoute.SETTINGS) },
                    onOpenConfirm = { navController.navigate(AppRoute.RECOGNIZE_CONFIRM) },
                    onOpenProposals = { navController.navigate(AppRoute.SMART_PROPOSAL) }
                )
            }
            composable(AppRoute.TASK_HISTORY) {
                TaskHistoryScreen()
            }
            composable(AppRoute.CATEGORIES) {
                CategoriesScreen(
                    onGoToWorkspaceOrResult = { navController.navigate(AppRoute.WORKSPACE) }
                )
            }
            composable(AppRoute.RESULT) {
                MoveResultScreen(
                    onBackHome = {
                        navController.navigate(AppRoute.HOME) {
                            popUpTo(AppRoute.HOME) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(AppRoute.SMART_PROPOSAL) {
                SmartProposalScreen(
                    onStartMove = { navController.navigate(AppRoute.RESULT) },
                    onOpenSettings = { navController.navigate(AppRoute.SETTINGS) }
                )
            }
            composable(AppRoute.SETTINGS) {
                SettingsScreen()
            }
            composable(AppRoute.DUPLICATES) {
                DuplicatesScreen(
                    onBack = { navController.navigateUp() }
                )
            }
        }
    }
}
