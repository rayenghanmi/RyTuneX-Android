package com.rayenghanmi.rytunex

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.rayenghanmi.rytunex.engine.AdaptiveDeviceInspector
import com.rayenghanmi.rytunex.engine.AppUpdateManager
import com.rayenghanmi.rytunex.engine.RyTuneXDeepEngine
import com.rayenghanmi.rytunex.engine.SettingsManager
import com.rayenghanmi.rytunex.engine.ThemeMode
import com.rayenghanmi.rytunex.engine.UadDatabaseManager
import com.rayenghanmi.rytunex.theme.RyTuneXTheme
import com.rayenghanmi.rytunex.ui.RyTuneXApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private var shizukuGranted by mutableStateOf(false)

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        val granted = (grantResult == PackageManager.PERMISSION_GRANTED)
        shizukuGranted = granted
        AdaptiveDeviceInspector.updateShizukuPrivilege(granted)
        if (granted) {
            onShizukuPrivilegeGranted()
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        checkShizukuState()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        shizukuGranted = false
        AdaptiveDeviceInspector.updateShizukuPrivilege(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        SettingsManager.init(this)
        super.onCreate(savedInstanceState)

        Shizuku.addRequestPermissionResultListener(permissionListener)
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        checkShizukuState()
        // Unprivileged warm-up lives in RyTuneXApp; grants re-probe via the listeners

        if (SettingsManager.autoCheckUpdates) {
            lifecycleScope.launch(Dispatchers.IO) {
                AppUpdateManager.checkLatestUpdate(applicationContext)
            }
        }

        setContent {
            RyTuneXTheme(
                accentColor = SettingsManager.accentColor,
                themeMode = SettingsManager.themeMode.toAppThemeMode()
            ) {
                RyTuneXApp(
                    isShizukuReady = shizukuGranted,
                    onRequestShizuku = { requestShizuku() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkShizukuState()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
    }

    private fun onShizukuPrivilegeGranted() {
        lifecycleScope.launch(Dispatchers.IO) {
            RyTuneXDeepEngine.readCurrentSystemState(forceRefresh = true)
        }
        lifecycleScope.launch(Dispatchers.IO) {
            UadDatabaseManager.scanInstalledApps(applicationContext, forceRefresh = true)
        }
        lifecycleScope.launch(Dispatchers.IO) {
            AdaptiveDeviceInspector.queryHardwareDisplayModes(forceRefresh = true)
        }
    }

    private fun checkShizukuState() {
        val wasGranted = shizukuGranted
        try {
            if (Shizuku.pingBinder()) {
                shizukuGranted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } else {
                shizukuGranted = false
            }
        } catch (_: Exception) {
            shizukuGranted = false
        }
        AdaptiveDeviceInspector.updateShizukuPrivilege(shizukuGranted)
        if (shizukuGranted && !wasGranted) {
            onShizukuPrivilegeGranted()
        }
    }

    private fun requestShizuku() {
        try {
            if (Shizuku.pingBinder()) {
                if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    Shizuku.requestPermission(1001)
                } else {
                    shizukuGranted = true
                    AdaptiveDeviceInspector.updateShizukuPrivilege(true)
                    // Re-probe if privilege was already granted (state desync)
                    onShizukuPrivilegeGranted()
                }
            } else {
                openShizukuApp()
            }
        } catch (_: Exception) {
            openShizukuApp()
        }
    }

    private fun openShizukuApp() {
        try {
            val intent = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            if (intent != null) {
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } else {
                val webIntent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://shizuku.rikka.app/")
                ).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(webIntent)
            }
        } catch (_: Exception) {}
    }
}