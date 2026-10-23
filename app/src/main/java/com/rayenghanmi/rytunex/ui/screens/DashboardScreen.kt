package com.rayenghanmi.rytunex.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import com.rayenghanmi.rytunex.model.ProfileDeviceSpec
import com.rayenghanmi.rytunex.model.ProfileType
import com.rayenghanmi.rytunex.theme.SemanticGreen
import com.rayenghanmi.rytunex.theme.SemanticRed
import com.rayenghanmi.rytunex.ui.components.Button
import com.rayenghanmi.rytunex.ui.components.FilledTonalButton
import com.rayenghanmi.rytunex.ui.components.TextButton
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
    var showBatteryHealthSheet by remember { mutableStateOf(false) }
    var showWakelockSpotterSheet by remember { mutableStateOf(false) }

    var deviceInfo by remember { mutableStateOf(DeviceInfo()) }

    // Live panel modes and Vulkan support drive the profile descriptions
    val profileSpec by remember(deviceInfo.display.supportedRates) {
        derivedStateOf {
            val rates = deviceInfo.display.supportedRates
            ProfileDeviceSpec(
                maxHz = rates.maxOrNull()?.toInt() ?: 60,
                lowHz = rates.filter { it <= 60f }.maxOrNull()?.toInt()
                    ?: (rates.maxOrNull()?.toInt() ?: 60),
                multiMode = rates.size > 1,
                vulkanSupported = AdaptiveOptimizer.isVulkanSupported(context)
            )
        }
    }

    fun applyProfileInternal(profile: ProfileType) {
        executingProfile = profile
        scope.launch {
            // Load real panel modes before building profile commands
            if (deviceInfo.display.supportedRates.size <= 1) {
                deviceInfo = AdaptiveDeviceInspector.inspectFullDevice(context, isShizukuReady)
            }
            val res = AdaptiveOptimizer.applyProfile(context, profile, deviceInfo)
            executingProfile = null
            isLastActionSuccess = res.isSuccess
            // Only claim the profile as active when the shell work actually succeeded;
            // a failed run (e.g. Shizuku not authorized) must leave the previous card active
            if (res.isSuccess) {
                activeProfile = profile
            }

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
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .clickable { showUpdateDialog = true },
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
                            shape = BloomShape(lobes = 12, depth = 0.14f),
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
                            text = "RyTuneX applies Android system optimizations without root privileges using Shizuku wireless debugging.",
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
                    animatedNumber = deviceInfo.soc.cpuLoadPercent,
                    animatedSuffix = "%",
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
                    animatedNumber = deviceInfo.memory.usedPercent,
                    animatedSuffix = "% Used",
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
                val currentHealthPct = deviceInfo.battery.healthPercent
                val healthText = if (currentHealthPct != null) {
                    "${currentHealthPct.toInt()}% Health"
                } else {
                    deviceInfo.battery.health
                }
                val cycleText = if (deviceInfo.battery.cycleCount != null) {
                    " • ${deviceInfo.battery.cycleCount} cyc"
                } else {
                    ""
                }
                HardwareMetricTile(
                    title = "Battery & Health",
                    primaryValue = "${deviceInfo.battery.level}%",
                    subtitle = "$tempFormatted • $healthText$cycleText",
                    progress = deviceInfo.battery.level / 100f,
                    icon = Icons.Rounded.BatteryChargingFull,
                    animatedNumber = deviceInfo.battery.level.toFloat(),
                    animatedSuffix = "%",
                    accentColor = if (deviceInfo.battery.tempCelsius > 42) MaterialTheme.colorScheme.error else SemanticGreen,
                    onClick = { showBatteryHealthSheet = true },
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
                    animatedNumber = deviceInfo.display.currentRefreshRate,
                    animatedSuffix = " Hz",
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }

            // Wakelock & Drain Spotter Quick Entry
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { showWakelockSpotterSheet = true }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.SearchOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Wakelock & Drain Spotter",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Audit rogue CPU locks, wakeup alarms & background drain",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Adaptive profiles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GoogleSectionHeader(title = "HARDWARE-AWARE PROFILES")
                if (isExecuting) {
                    WavyLoadingIndicator(
                        indicatorSize = 20.dp,
                        stroke = 2.5.dp,
                        amplitude = 1.25.dp
                    )
                }
            }

            ProfileType.entries.forEach { profile ->
                val isSelected = activeProfile == profile
                // Spring-morph the card between resting and selected colors (M3E)
                val cardColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                    animationSpec = spring(
                        dampingRatio = 0.85f,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "profileCardColor"
                )
                val titleColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                    label = "profileTitleColor"
                )
                val bodyColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                    label = "profileBodyColor"
                )
                val palette = MaterialTheme.colorScheme
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = cardColor),
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = profile.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = titleColor,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                // Pop-in badge beside the profile title
                                AnimatedVisibility(
                                    visible = isSelected,
                                    enter = scaleIn(
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        ),
                                        initialScale = 0.5f
                                    ) + fadeIn(animationSpec = tween(120)),
                                    exit = fadeOut(animationSpec = tween(120)) + scaleOut(targetScale = 0.6f)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = palette.primary
                                    ) {
                                        Text(
                                            text = "ACTIVE",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = palette.onPrimary,
                                            softWrap = false,
                                            maxLines = 1,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = profile.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = bodyColor
                            )
                        }

                        Text(
                            text = profile.getDescription(profileSpec),
                            style = MaterialTheme.typography.bodySmall,
                            color = bodyColor,
                            lineHeight = 16.sp
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val btnContainer by animateColorAsState(
                                targetValue = if (isSelected) palette.primaryContainer else palette.surfaceContainerHighest,
                                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                                label = "deployBtnContainer"
                            )
                            val btnContent by animateColorAsState(
                                targetValue = if (isSelected) palette.onPrimaryContainer else palette.primary,
                                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
                                label = "deployBtnContent"
                            )
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
                                    containerColor = btnContainer,
                                    contentColor = btnContent
                                ),
                                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                            ) {
                                if (executingProfile == profile) {
                                    WavyLoadingIndicator(
                                        indicatorSize = 16.dp,
                                        stroke = 2.dp,
                                        amplitude = 1.dp,
                                        color = btnContent
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
                                WavyLoadingIndicator(indicatorSize = 14.dp, stroke = 2.dp, amplitude = 1.dp)
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
                                WavyLoadingIndicator(indicatorSize = 14.dp, stroke = 2.dp, amplitude = 1.dp)
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
                                WavyLoadingIndicator(indicatorSize = 14.dp, stroke = 2.dp, amplitude = 1.dp)
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
                            text = "Version ${com.rayenghanmi.rytunex.BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                text = { AboutRyTuneXContent() },
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

        if (showBatteryHealthSheet) {
            BatteryHealthSheet(
                battery = deviceInfo.battery,
                isShizukuReady = isShizukuReady,
                onDismiss = { showBatteryHealthSheet = false },
                onRefresh = {
                    scope.launch {
                        deviceInfo = AdaptiveDeviceInspector.inspectFullDevice(context, isShizukuReady)
                    }
                },
                onOpenWakelockSpotter = {
                    showBatteryHealthSheet = false
                    showWakelockSpotterSheet = true
                }
            )
        }

        if (showWakelockSpotterSheet) {
            WakelockSpotterSheet(
                isShizukuReady = isShizukuReady,
                onDismiss = { showWakelockSpotterSheet = false }
            )
        }
    }
}
}
