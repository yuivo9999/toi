package com.toimg.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.toimg.app.core.ui.theme.ToImgTheme
import com.toimg.app.core.util.NotificationHelper
import com.toimg.app.navigation.ToImgNavHost

class MainActivity : ComponentActivity() {

    /** 来自通知点击的目标页面（一次性）。 */
    private val openRoute = mutableStateOf<String?>(null)

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 旋转重建时不重复跳转
        if (savedInstanceState == null) {
            openRoute.value = intent?.getStringExtra(NotificationHelper.EXTRA_OPEN_ROUTE)
        }
        requestNotificationPermissionOnce()

        setContent {
            ToImgTheme {
                ToImgNavHost(
                    initialRoute = openRoute.value,
                    onInitialRouteConsumed = { openRoute.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openRoute.value = intent.getStringExtra(NotificationHelper.EXTRA_OPEN_ROUTE)
    }

    /** Android 13+ 需要通知权限才能在通知栏显示识别进度；只在首次启动时询问一次。 */
    private fun requestNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return
        val prefs = getSharedPreferences("toimg_app_prefs", MODE_PRIVATE)
        if (prefs.getBoolean("asked_notification_permission", false)) return
        prefs.edit().putBoolean("asked_notification_permission", true).apply()
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
