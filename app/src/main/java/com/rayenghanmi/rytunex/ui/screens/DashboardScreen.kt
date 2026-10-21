package com.rayenghanmi.rytunex.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rayenghanmi.rytunex.R
import com.rayenghanmi.rytunex.engine.AdaptiveDeviceInspector
import com.rayenghanmi.rytunex.engine.AdaptiveOptimizer
import com.rayenghanmi.rytunex.engine.AppUpdateManager
import com.rayenghanmi.rytunex.engine.RyTuneXDeepEngine
import com.rayenghanmi.rytunex.engine.SettingsManager
import com.rayenghanmi.rytunex.model.DeviceInfo
import com.rayenghanmi.rytunex.model.ProfileType
import com.rayenghanmi.rytunex.theme.SemanticGreen
import com.rayenghanmi.rytunex.theme.SemanticRed
import com.rayenghanmi.rytunex.ui.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    isShizukuReady: Boolean,
    onRequestAuth: () -> Unit,
    onNavigateToDebloat: () -> Unit,
    onNavigateToOptimizations: () -> Unit,
    onLogUpdate: (String) -> Unit,
    isScreenVisible: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isExecuting by remember { mutableStateOf(false) }
    var executingProfile by remember { mutableStateOf<ProfileType?>(null) }
    var isCleaningStorage by remember { mutableStateOf(false) }
    var isTrimmingStorage by remember { mutableStateOf(false) }
    var isTrimmingRam by remember { mutableStateOf(false) }
    var isLastActionSuccess by remember { mutableStateOf(true) }
    var activeProfile by remember { mutableStateOf<ProfileType?>(null) }
    var pendingProfileToConfirm by remember { mutableStateOf<ProfileType?>(null) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showUpdateBanner by remember { mutableStateOf(true) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    var deviceInfo by remember { mutableStateOf(DeviceInfo()) }

    fun applyProfileInternal(profile: ProfileType) {
        executingProfile = profile
        scope.launch {
            val res = AdaptiveOptimizer.applyProfile(context, profile, deviceInfo.display)
            executingProfile = null
            activeProfile = profile
            isLastActionSuccess = res.isSuccess

            val feedbackMsg = if (res.isSuccess) {
                when (profile) {
                    ProfileType.EXTREME_GAMING -> "✓ Extreme Gaming applied! Max Hz locked, Vulkan & GPU Driver enabled."
                    ProfileType.BATTERY_SAVER -> "✓ Adaptive Endurance applied! 60Hz locked, power saving & doze enabled."
                    ProfileType.RYTUNEX_BALANCED -> "✓ RyTuneX Fluid applied! Dynamic refresh rate & 0.5x animations enabled."
                    ProfileType.STOCK_RESET -> "✓ Reset to Stock complete! All system toggles restored to OEM defaults."
                }
            } else {
                "✗ Failed to apply ${profile.title}: ${res.stderr.ifBlank { res.stdout.ifBlank { "Shell execution error" } }}"
            }
            Toast.makeText(context, feedbackMsg, Toast.LENGTH_SHORT).show()
            onLogUpdate("[PROFILE APPLIED]: ${profile.title}\n${res.output}")
        }
    }

    val scrollState = rememberScrollState()
    val isScrolled by remember { derivedStateOf { scrollState.value > 60 } }

    LaunchedEffect(isScreenVisible, isShizukuReady, SettingsManager.telemetryEnabled, SettingsManager.telemetryIntervalMs) {
        if (!isScreenVisible) return@LaunchedEffect
        if (!SettingsManager.telemetryEnabled) {
            deviceInfo = AdaptiveDeviceInspector.inspectFullDevice(context, isShizukuReady)
            return@LaunchedEffect
        }
        while (isActive && isScreenVisible) {
            deviceInfo = AdaptiveDeviceInspector.inspectFullDevice(context, isShizukuReady)
            delay(SettingsManager.telemetryIntervalMs)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header (scrolls away with content)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showAboutDialog = true },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.app_logo),
                            contentDescription = "RyTuneX Logo",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            RyTuneXBrandText(
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Advanced Tuning Engine",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    PulsingStatusBadge(
                        isActive = isShizukuReady,
                        activeText = "UID 2000",
                        inactiveText = "NO PRIVILEGE"
                    )
                }
            // Update banner
            val updateInfo = AppUpdateManager.latestReleaseInfo
            if (updateInfo?.isUpdateAvailable == true && showUpdateBanner) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showUpdateDialog = true },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.SystemUpdate,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (updateInfo.isPreRelease) "RyTuneX Pre-Release: v${updateInfo.versionName}" else "RyTuneX Update: v${updateInfo.versionName}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tap to review changelog & install",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        IconButton(onClick = { showUpdateBanner = false }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Dismiss",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            // Shizuku status card
            if (!isShizukuReady) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Privileged Access Required",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = buildRyTuneXAnnotatedString("RyTuneX applies Android system optimizations without root privileges using Shizuku wireless debugging."),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = onRequestAuth,
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Icon(Icons.Rounded.VerifiedUser, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Grant Shizuku UID", fontWeight = FontWeight.SemiBold, softWrap = false, maxLines = 1)
                        }
                    }
                }
            }

            // Device specs
            GoogleGroupedCard {
                GoogleSettingsRow(
                    title = "${deviceInfo.manufacturer} ${deviceInfo.model}".ifBlank { "Android Device" },
                    subtitle = "Android ${deviceInfo.androidVersion.removePrefix("Android ").trim()} (API ${deviceInfo.apiLevel}) • Security: ${deviceInfo.securityPatch}",
                    icon = Icons.Rounded.Smartphone,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    bottomContent = {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isShizukuReady) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = if (isShizukuReady) "ADB UID 2000" else "No Privilege",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isShizukuReady) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                softWrap = false,
                                maxLines = 1
                            )
                        }
                    }
                )
            }

            // Real-Time Telemetry Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GoogleSectionHeader(title = "LIVE HARDWARE TELEMETRY")
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (SettingsManager.telemetryEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    val intervalText = if (SettingsManager.telemetryEnabled) {
                        "${String.format(java.util.Locale.US, "%.1f", SettingsManager.telemetryIntervalMs / 1000f)}s TICK"
                    } else {
                        "PAUSED"
                    }
                    Text(
                        text = intervalText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (SettingsManager.telemetryEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Telemetry 2x2 grid
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HardwareMetricTile(
                    title = "Processor Load",
                    primaryValue = deviceInfo.soc.cpuLoadFormatted,
                    subtitle = "${deviceInfo.soc.socModel} • ${deviceInfo.soc.freqFormatted}",
                    progress = deviceInfo.soc.cpuLoadPercent / 100f,
                    icon = Icons.Rounded.Memory,
                    accentColor = if (deviceInfo.soc.cpuLoadPercent > 75) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                HardwareMetricTile(
                    title = "Active Memory",
                    primaryValue = "${deviceInfo.memory.usedPercent.toInt()}% Used",
                    subtitle = "${deviceInfo.memory.usedGbFormatted} / ${deviceInfo.memory.totalGbFormatted}",
                    progress = deviceInfo.memory.usedPercent / 100f,
                    icon = Icons.Rounded.Storage,
                    accentColor = if (deviceInfo.memory.usedPercent > 85) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val tempFormatted = if (SettingsManager.isFahrenheit) {
                    "${String.format("%.1f", (deviceInfo.battery.tempCelsius * 9f / 5f) + 32f)}°F"
                } else {
                    "${String.format("%.1f", deviceInfo.battery.tempCelsius)}°C"
                }
                HardwareMetricTile(
                    title = "Battery & Health",
                    primaryValue = "${deviceInfo.battery.level}%",
                    subtitle = "$tempFormatted • ${deviceInfo.battery.health}",
                    progress = deviceInfo.battery.level / 100f,
                    icon = Icons.Rounded.BatteryChargingFull,
                    accentColor = if (deviceInfo.battery.tempCelsius > 42) MaterialTheme.colorScheme.error else SemanticGreen,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                val maxRefresh = deviceInfo.display.supportedRates.maxOrNull()?.takeIf { it > 0f } ?: 120f
                val displayProgress = (deviceInfo.display.currentRefreshRate / maxRefresh).coerceIn(0f, 1f)
                HardwareMetricTile(
                    title = "Display Panel",
                    primaryValue = "${deviceInfo.display.currentRefreshRate.toInt()} Hz",
                    subtitle = "${deviceInfo.display.width}x${deviceInfo.display.height} px",
                    progress = displayProgress,
                    icon = Icons.Rounded.Speed,
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }

            // Adaptive profiles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GoogleSectionHeader(title = "HARDWARE-AWARE PROFILES")
                if (isExecuting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            ProfileType.entries.forEach { profile ->
                val isSelected = activeProfile == profile
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = if (profile.title.contains("RyTuneX")) buildRyTuneXAnnotatedString(profile.title) else AnnotatedString(profile.title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = profile.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = profile.getDescription(deviceInfo.display.supportedRates.maxOrNull()?.toInt() ?: 60),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isSelected) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Text(
                                        text = "ACTIVE",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        softWrap = false,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    if (SettingsManager.confirmTweaks) {
                                        pendingProfileToConfirm = profile
                                    } else {
                                        applyProfileInternal(profile)
                                    }
                                },
                                enabled = executingProfile == null,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                                ),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                            ) {
                                if (executingProfile == profile) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = if (isSelected) "Re-apply Profile" else "Deploy Profile",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    softWrap = false,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // Quick maintenance actions
            GoogleSectionHeader(title = "QUICK ACTIONS")

            GoogleGroupedCard {
                GoogleSettingsRow(
                    title = "Debloat",
                    subtitle = "Surgically remove or manage pre-installed packages, safely",
                    icon = Icons.Rounded.CleaningServices,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    trailingContent = {
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = onNavigateToDebloat
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                GoogleSettingsRow(
                    title = "Optimize",
                    subtitle = "Hardware-aware tuning for display, latency, GPU, network & power",
                    icon = Icons.Rounded.Tune,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    trailingContent = {
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = onNavigateToOptimizations
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                GoogleSettingsRow(
                    title = "Deep Storage & Cache Cleaner",
                    subtitle = "Reclaim application caches and clean temporary storage",
                    icon = Icons.Rounded.DeleteSweep,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    bottomContent = {
                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    isCleaningStorage = true
                                    val res = RyTuneXDeepEngine.deepStorageAndCacheClean()
                                    isCleaningStorage = false
                                    isLastActionSuccess = res.isSuccess
                                    val msg = if (res.isSuccess) {
                                        "Deep Storage Clean: App caches reclaimed and temp storage purged"
                                    } else {
                                        "Cache clean error: ${res.stderr}"
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    onLogUpdate("[DEEP STORAGE CLEAN]: ${res.output}")
                                }
                            },
                            enabled = !isCleaningStorage,
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                        ) {
                            if (isCleaningStorage) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text("Clean Storage", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, softWrap = false, maxLines = 1)
                        }
                    }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                GoogleSettingsRow(
                    title = "Trim Runtime RAM",
                    subtitle = "Purge background cached processes and free runtime RAM",
                    icon = Icons.Rounded.Memory,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    bottomContent = {
                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    isTrimmingRam = true
                                    val res = AdaptiveOptimizer.trimMemoryCaches()
                                    isTrimmingRam = false
                                    isLastActionSuccess = res.isSuccess
                                    val msg = if (res.isSuccess) {
                                        res.stdout.ifBlank { "Runtime RAM Trimmed: Background cached processes purged" }
                                    } else {
                                        "RAM trim error: ${res.stderr.ifBlank { res.stdout }}"
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    onLogUpdate("[RAM TRIM]: ${res.output}")
                                }
                            },
                            enabled = !isTrimmingRam,
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                        ) {
                            if (isTrimmingRam) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text("Trim RAM", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, softWrap = false, maxLines = 1)
                        }
                    }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                GoogleSettingsRow(
                    title = "Hardware Storage TRIM (fstrim)",
                    subtitle = "Issue kernel discard to UFS & eMMC flash blocks to restore I/O write speed",
                    icon = Icons.Rounded.Storage,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    bottomContent = {
                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    isTrimmingStorage = true
                                    val res = RyTuneXDeepEngine.runStorageTrim()
                                    isTrimmingStorage = false
                                    isLastActionSuccess = res.isSuccess
                                    val msg = if (res.isSuccess) {
                                        res.stdout.ifBlank { "Storage TRIM completed: Flash dirty blocks discarded" }
                                    } else {
                                        "Storage TRIM error: ${res.stderr.ifBlank { res.stdout }}"
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    onLogUpdate("[FLASH STORAGE TRIM]: ${res.output}")
                                }
                            },
                            enabled = !isTrimmingStorage,
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                        ) {
                            if (isTrimmingStorage) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text("TRIM Flash", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, softWrap = false, maxLines = 1)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.navigationBarsPadding().height(130.dp))
        }

        // Floating header pill (visible when scrolled)
        FloatingTopPillHeader(
            visible = isScrolled,
            leadingContent = {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = null,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
            },
            titleContent = {
                RyTuneXBrandText(
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            onClick = {
                scope.launch { scrollState.animateScrollTo(0) }
            },
            trailingContent = {
                PulsingStatusBadge(
                    isActive = isShizukuReady,
                    activeText = "UID 2000",
                    inactiveText = "NO PRIVILEGE"
                )
            }
        )


        // About dialog
        if (showAboutDialog) {
            ExpressiveAlertDialog(
                onDismissRequest = { showAboutDialog = false },
                icon = {
                    Image(
                        painter = painterResource(id = R.drawable.app_logo),
                        contentDescription = "RyTuneX Logo",
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(18.dp))
                    )
                },
                title = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RyTuneXBrandText(
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Version ${com.rayenghanmi.rytunex.BuildConfig.VERSION_NAME} • Advanced Tuning Engine",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = buildRyTuneXAnnotatedString("RyTuneX is an advanced, open-source tuning engine for power users and gamers. Maximize performance, eliminate latency, and manage deeply embedded system policies natively. Engineered for absolute transparency and real-world gains, RyTuneX delivers granular, hardware-level control with zero persistent background daemons and zero battery drain."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "ARCHITECTURE & ENGINE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text("• Privileged Service: Shizuku (ADB UID 2000)", style = MaterialTheme.typography.bodySmall)
                                Text("• Debloater: Universal Android Debloater (UAD Next-Gen)", style = MaterialTheme.typography.bodySmall)
                                Text("• Target: Android 8.0 – 16+ (API 26–36)", style = MaterialTheme.typography.bodySmall)
                                Text("• PC Edition: RyTuneX for Windows 10 & 11", style = MaterialTheme.typography.bodySmall)
                                Text("• Website: rayenghanmi.github.io/rytunex", style = MaterialTheme.typography.bodySmall)
                                Text("• Status: ${if (isShizukuReady) "Authorized (UID 2000)" else "No Privileges"}", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Text(
                            text = "Created by Rayen Ghanmi • Open-Source Tuning Engine (AGPL-3.0)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    FilledTonalButton(
                        onClick = { showAboutDialog = false },
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        Text("Close", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // Profile confirmation dialog
        pendingProfileToConfirm?.let { profile ->
            ExpressiveAlertDialog(
                onDismissRequest = { pendingProfileToConfirm = null },
                icon = { Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("Deploy ${profile.title}?", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "Are you sure you want to deploy the '${profile.title}' optimization profile? This will reconfigure your display refresh rate, touch response, and background resource limits."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val target = profile
                            pendingProfileToConfirm = null
                            applyProfileInternal(target)
                        }
                    ) {
                        Text("Confirm & Apply", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingProfileToConfirm = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Update dialog
        if (showUpdateDialog && AppUpdateManager.latestReleaseInfo != null) {
            AppUpdateDialog(
                releaseInfo = AppUpdateManager.latestReleaseInfo!!,
                onDismissRequest = { showUpdateDialog = false },
                onLogUpdate = onLogUpdate
            )
        }
    }
}
}
