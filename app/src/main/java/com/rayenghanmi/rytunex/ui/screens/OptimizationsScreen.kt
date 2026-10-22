package com.rayenghanmi.rytunex.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rayenghanmi.rytunex.engine.AdaptiveDeviceInspector
import com.rayenghanmi.rytunex.engine.AdaptiveOptimizer
import com.rayenghanmi.rytunex.engine.DnsProvider
import com.rayenghanmi.rytunex.engine.RyTuneXDeepEngine
import com.rayenghanmi.rytunex.engine.SettingsManager
import com.rayenghanmi.rytunex.engine.SystemTuningState
import com.rayenghanmi.rytunex.theme.SemanticGreen
import com.rayenghanmi.rytunex.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OptimizationsScreen(
    onLogUpdate: (String) -> Unit,
    isScreenVisible: Boolean = true,
    isShizukuReady: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun showStatusToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    var liveRefreshRate by remember { mutableFloatStateOf(AdaptiveDeviceInspector.readCurrentRefreshRate(context)) }
    val displayInfo = remember { AdaptiveDeviceInspector.getDisplayInfo(context) }
    var supportedRates by remember { mutableStateOf(displayInfo.supportedRates) }
    var selectedHz by remember { mutableFloatStateOf(liveRefreshRate) }

    val cachedState = RyTuneXDeepEngine.cachedSystemState
    var isLoadingState by remember { mutableStateOf(cachedState == null) }
    var animScale by remember { mutableFloatStateOf(cachedState?.animationScale ?: 1.0f) }
    var artMode by remember { mutableStateOf("speed-profile") }

    var touchBoostEnabled by remember { mutableStateOf(cachedState?.isTouchBoostEnabled ?: false) }
    var disableBlursEnabled by remember { mutableStateOf(cachedState?.isBlursDisabled ?: false) }
    var forceGameDriverEnabled by remember { mutableStateOf(cachedState?.isGameDriverForced ?: false) }
    var forceHwUiEnabled by remember { mutableStateOf(cachedState?.isForceHwUiEnabled ?: false) }
    var predictiveBackEnabled by remember { mutableStateOf(cachedState?.isPredictiveBackEnabled ?: false) }
    var vulkanRendererEnabled by remember { mutableStateOf(cachedState?.isVulkanRendererEnabled ?: false) }
    var networkPowerSaveEnabled by remember { mutableStateOf(cachedState?.isRadioSaverEnabled ?: false) }
    var selectedDns by remember { mutableStateOf(cachedState?.dnsProvider ?: DnsProvider.STOCK_OFF) }
    var customDnsHost by remember { mutableStateOf(cachedState?.customDnsHost) }
    var aggressiveDozeEnabled by remember { mutableStateOf(cachedState?.isAggressiveDozeEnabled ?: false) }
    var logsSuppressed by remember { mutableStateOf(cachedState?.isLoggingSuppressed ?: false) }
    var phantomBypassed by remember { mutableStateOf(cachedState?.isPhantomBypassed ?: false) }

    // Additional efficiency & latency tunables
    var hwOverlaysDisabled by remember { mutableStateOf(cachedState?.isHwOverlaysDisabled ?: false) }
    var appFreezerEnabled by remember { mutableStateOf(cachedState?.isAppFreezerEnabled ?: true) }
    var netstatsDisabled by remember { mutableStateOf(cachedState?.isNetstatsDisabled ?: false) }
    var adaptiveBatteryEnabled by remember { mutableStateOf(cachedState?.isAdaptiveBatteryEnabled ?: true) }
    var scanThrottleEnabled by remember { mutableStateOf(cachedState?.isScanThrottleEnabled ?: true) }
    var appStandbyEnabled by remember { mutableStateOf(cachedState?.isAppStandbyEnabled ?: true) }
    var stayAwakeCharging by remember { mutableStateOf(cachedState?.isStayAwakeCharging ?: false) }
    var stickyBatterySaver by remember { mutableStateOf(cachedState?.isLowPowerSticky ?: false) }
    var lowPowerTrigger by remember { mutableStateOf(cachedState?.lowPowerTriggerLevel) }

    // Render resolution override
    var nativeResolution by remember { mutableStateOf(cachedState?.nativeResolution) }
    var overrideResolution by remember { mutableStateOf(cachedState?.overrideResolution) }
    var selectedResolutionScale by remember { mutableFloatStateOf(1f) }

    var isLockingHz by remember { mutableStateOf(false) }
    var isResettingHz by remember { mutableStateOf(false) }
    var isApplyingAnim by remember { mutableStateOf(false) }
    var isApplyingDns by remember { mutableStateOf(false) }
    var isApplyingPhantom by remember { mutableStateOf(false) }
    var isApplyingArt by remember { mutableStateOf(false) }
    var isTrimmingStorage by remember { mutableStateOf(false) }
    var isCompactingMemory by remember { mutableStateOf(false) }
    var isFlushingLogs by remember { mutableStateOf(false) }
    var isApplyingResolution by remember { mutableStateOf(false) }
    var isLastActionSuccess by remember { mutableStateOf(true) }

    var pendingConfirmAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingConfirmTitle by remember { mutableStateOf("") }
    var pendingConfirmMessage by remember { mutableStateOf("") }

    fun executeWithConfirmation(title: String, message: String, action: () -> Unit) {
        if (SettingsManager.confirmTweaks) {
            pendingConfirmTitle = title
            pendingConfirmMessage = message
            pendingConfirmAction = action
        } else {
            action()
        }
    }

    fun inferResolutionScale(state: SystemTuningState?): Float {
        val override = state?.overrideResolution ?: return 1f
        val native = state.nativeResolution
        return if (native != null && native.first > 0) {
            (override.first.toFloat() / native.first.toFloat()).coerceIn(0.5f, 1f)
        } else {
            0f
        }
    }

    fun reloadState(force: Boolean = true) {
        scope.launch(Dispatchers.IO) {
            if (force || RyTuneXDeepEngine.cachedSystemState == null) {
                withContext(Dispatchers.Main) { isLoadingState = true }
            }
            val live = AdaptiveDeviceInspector.readCurrentRefreshRate(context)
            var dInfo = AdaptiveDeviceInspector.getDisplayInfo(context, forceRefresh = force)
            try {
                val hwModes = AdaptiveDeviceInspector.queryHardwareDisplayModes(forceRefresh = force)
                if (hwModes.isNotEmpty()) {
                    val merged = (dInfo.supportedRates + hwModes + 60f)
                        .filter { it in AdaptiveDeviceInspector.STANDARD_DISCRETE_REFRESH_RATES }
                        .distinct()
                        .sorted()
                    dInfo = dInfo.copy(supportedRates = merged)
                }
            } catch (_: Exception) {}
            val rates = dInfo.supportedRates
            val state = RyTuneXDeepEngine.readCurrentSystemState(forceRefresh = force)
            withContext(Dispatchers.Main) {
                liveRefreshRate = live
                supportedRates = rates
                selectedDns = state.dnsProvider
                customDnsHost = state.customDnsHost
                animScale = state.animationScale
                touchBoostEnabled = state.isTouchBoostEnabled
                disableBlursEnabled = state.isBlursDisabled
                forceGameDriverEnabled = state.isGameDriverForced
                forceHwUiEnabled = state.isForceHwUiEnabled
                predictiveBackEnabled = state.isPredictiveBackEnabled
                vulkanRendererEnabled = state.isVulkanRendererEnabled
                networkPowerSaveEnabled = state.isRadioSaverEnabled
                aggressiveDozeEnabled = state.isAggressiveDozeEnabled
                logsSuppressed = state.isLoggingSuppressed
                phantomBypassed = state.isPhantomBypassed
                hwOverlaysDisabled = state.isHwOverlaysDisabled
                appFreezerEnabled = state.isAppFreezerEnabled
                netstatsDisabled = state.isNetstatsDisabled
                adaptiveBatteryEnabled = state.isAdaptiveBatteryEnabled
                scanThrottleEnabled = state.isScanThrottleEnabled
                appStandbyEnabled = state.isAppStandbyEnabled
                stayAwakeCharging = state.isStayAwakeCharging
                stickyBatterySaver = state.isLowPowerSticky
                lowPowerTrigger = state.lowPowerTriggerLevel
                nativeResolution = state.nativeResolution
                overrideResolution = state.overrideResolution
                selectedResolutionScale = inferResolutionScale(state)
                if (state.peakRefreshRate != null && state.peakRefreshRate > 0 && rates.contains(state.peakRefreshRate)) {
                    selectedHz = state.peakRefreshRate
                } else if (!rates.contains(selectedHz)) {
                    selectedHz = rates.firstOrNull { it >= 60f } ?: rates.firstOrNull() ?: 60f
                }
                isLoadingState = false
            }
        }
    }

    LaunchedEffect(isShizukuReady) {
        if (isShizukuReady) {
            reloadState(force = true)
        }
    }

    LaunchedEffect(RyTuneXDeepEngine.cachedSystemState) {
        val state = RyTuneXDeepEngine.cachedSystemState ?: return@LaunchedEffect
        selectedDns = state.dnsProvider
        customDnsHost = state.customDnsHost
        animScale = state.animationScale
        touchBoostEnabled = state.isTouchBoostEnabled
        disableBlursEnabled = state.isBlursDisabled
        forceGameDriverEnabled = state.isGameDriverForced
        forceHwUiEnabled = state.isForceHwUiEnabled
        predictiveBackEnabled = state.isPredictiveBackEnabled
        vulkanRendererEnabled = state.isVulkanRendererEnabled
        networkPowerSaveEnabled = state.isRadioSaverEnabled
        aggressiveDozeEnabled = state.isAggressiveDozeEnabled
        logsSuppressed = state.isLoggingSuppressed
        phantomBypassed = state.isPhantomBypassed
        hwOverlaysDisabled = state.isHwOverlaysDisabled
        appFreezerEnabled = state.isAppFreezerEnabled
        netstatsDisabled = state.isNetstatsDisabled
        adaptiveBatteryEnabled = state.isAdaptiveBatteryEnabled
        scanThrottleEnabled = state.isScanThrottleEnabled
        appStandbyEnabled = state.isAppStandbyEnabled
        stayAwakeCharging = state.isStayAwakeCharging
        stickyBatterySaver = state.isLowPowerSticky
        lowPowerTrigger = state.lowPowerTriggerLevel
        nativeResolution = state.nativeResolution
        overrideResolution = state.overrideResolution
        selectedResolutionScale = inferResolutionScale(state)
        if (state.peakRefreshRate != null && state.peakRefreshRate > 0) {
            selectedHz = state.peakRefreshRate
        }
        isLoadingState = false
    }

    LaunchedEffect(isScreenVisible) {
        if (!isScreenVisible) return@LaunchedEffect
        reloadState(force = true)
        while (isActive && isScreenVisible) {
            delay(2500L)
            val live = withContext(Dispatchers.IO) {
                AdaptiveDeviceInspector.readCurrentRefreshRate(context, liveRefreshRate)
            }
            if (live != liveRefreshRate) {
                liveRefreshRate = live
            }
        }
    }

    val scrollState = rememberScrollState()
    val isScrolled by remember { derivedStateOf { scrollState.value > 60 } }

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
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = "Optimize",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Hardware-Aware Tuning & Latency Control",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                IconButton(onClick = { reloadState() }) {
                    if (isLoadingState) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = "Refresh Live Status",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }


            // Display & refresh rate
            GoogleSectionHeader(title = "DISPLAY & ANIMATIONS")

            GoogleGroupedCard {
                GoogleSettingsRow(
                    title = "Display Refresh Rate",
                    subtitle = "Panel modes: ${supportedRates.joinToString { "${it.toInt()}Hz" }}",
                    icon = Icons.Rounded.Speed,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    bottomContent = {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "${liveRefreshRate.toInt()} Hz Active",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                softWrap = false,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Hardware panel frequencies:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    supportedRates.forEach { rate ->
                        val isCurrent = selectedHz.toInt() == rate.toInt()
                        FilterChip(
                            selected = isCurrent,
                            onClick = { selectedHz = rate },
                            label = { Text("${rate.toInt()} Hz", fontWeight = FontWeight.SemiBold, softWrap = false, maxLines = 1) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            executeWithConfirmation(
                                title = "Lock Refresh Rate",
                                message = "Lock the display refresh rate to ${selectedHz.toInt()} Hz across system and vendor display controllers?"
                            ) {
                                scope.launch {
                                    isLockingHz = true
                                    val res = AdaptiveOptimizer.setDisplayRefreshRate(selectedHz, selectedHz)
                                    isLockingHz = false
                                    isLastActionSuccess = res.isSuccess
                                    val msg = if (res.isSuccess) {
                                        "Refresh rate locked to ${selectedHz.toInt()} Hz across system & vendor controllers"
                                    } else {
                                        "Failed to lock refresh rate: ${res.stderr}"
                                    }
                                    showStatusToast(msg)
                                    delay(350L)
                                    liveRefreshRate = AdaptiveDeviceInspector.readCurrentRefreshRate(context)
                                    val dInfo = AdaptiveDeviceInspector.getDisplayInfo(context, forceRefresh = true)
                                    supportedRates = dInfo.supportedRates
                                    onLogUpdate("[REFRESH RATE LOCKED]: ${selectedHz.toInt()}Hz\n${res.output}")
                                }
                            }
                        },
                        enabled = !isLockingHz && !isResettingHz,
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        if (isLockingHz) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("Lock to ${selectedHz.toInt()} Hz", fontWeight = FontWeight.SemiBold, softWrap = false, maxLines = 1)
                    }

                    OutlinedButton(
                        onClick = {
                            executeWithConfirmation(
                                title = "Reset Refresh Rate",
                                message = "Reset display refresh rate back to OEM dynamic scaling?"
                            ) {
                                scope.launch {
                                    isResettingHz = true
                                    val res = AdaptiveOptimizer.resetDisplayRefreshRate()
                                    isResettingHz = false
                                    isLastActionSuccess = res.isSuccess
                                    val msg = if (res.isSuccess) {
                                        "Refresh rate reset to OEM default dynamic scaling"
                                    } else {
                                        "Failed to reset refresh rate: ${res.stderr}"
                                    }
                                    showStatusToast(msg)
                                    delay(350L)
                                    liveRefreshRate = AdaptiveDeviceInspector.readCurrentRefreshRate(context)
                                    val dInfo = AdaptiveDeviceInspector.getDisplayInfo(context, forceRefresh = true)
                                    supportedRates = dInfo.supportedRates
                                    onLogUpdate("[REFRESH RATE RESET]: OEM Default Dynamic Scaling\n${res.output}")
                                }
                            }
                        },
                        enabled = !isLockingHz && !isResettingHz,
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        if (isResettingHz) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("Reset Stock Dynamic", fontWeight = FontWeight.SemiBold, softWrap = false, maxLines = 1)
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                GoogleSettingsRow(
                    title = "Window Animation Duration Scale",
                    subtitle = "Current factor: ${String.format(Locale.US, "%.2f", animScale)}x",
                    icon = Icons.Rounded.Animation,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "0.0x (Instant)" to 0.0f,
                        "0.25x (Fast)" to 0.25f,
                        "0.50x (Fluid)" to 0.50f,
                        "1.0x (Stock)" to 1.0f
                    ).forEach { (label, value) ->
                        val isSelected = Math.abs(animScale - value) < 0.05f
                        FilterChip(
                            selected = isSelected,
                            onClick = { animScale = value },
                            label = { Text(label, fontWeight = FontWeight.SemiBold, softWrap = false, maxLines = 1) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                Slider(
                    value = animScale,
                    onValueChange = { animScale = it },
                    valueRange = 0.0f..1.5f,
                    steps = 29,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                FilledTonalButton(
                    onClick = {
                        executeWithConfirmation(
                            title = "Apply Animation Speed",
                            message = "Apply ${String.format(Locale.US, "%.2f", animScale)}x system animation duration scale?"
                        ) {
                            scope.launch {
                                isApplyingAnim = true
                                val res = AdaptiveOptimizer.setAnimationScale(animScale)
                                isApplyingAnim = false
                                isLastActionSuccess = res.isSuccess
                                val msg = if (res.isSuccess) {
                                    "Animation scale set to ${String.format(Locale.US, "%.2f", animScale)}x"
                                } else {
                                    "Failed to apply animation scale: ${res.stderr}"
                                }
                                showStatusToast(msg)
                                onLogUpdate("[ANIMATION SPEED]: ${String.format(Locale.US, "%.2f", animScale)}x applied\n${res.output}")
                            }
                        }
                    },
                    enabled = !isApplyingAnim,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    if (isApplyingAnim) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        "Apply ${String.format(Locale.US, "%.2f", animScale)}x Animation Speed",
                        fontWeight = FontWeight.SemiBold,
                        softWrap = false,
                        maxLines = 1
                    )
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                GoogleSettingsRow(
                    title = "Predictive Back Navigation Gesture",
                    subtitle = "Unlocks Android 13–15 fluid cross-activity predictive swipe transitions",
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    trailingContent = {
                        Switch(
                            checked = predictiveBackEnabled,
                            onCheckedChange = { checked ->
                                predictiveBackEnabled = checked
                                scope.launch {
                                    val res = RyTuneXDeepEngine.setPredictiveBackAnimation(checked)
                                    isLastActionSuccess = res.isSuccess
                                    val msg = if (checked) {
                                        "Predictive back animations enabled (enable_predictive_back_animation=1)"
                                    } else {
                                        "Predictive back animations disabled"
                                    }
                                    showStatusToast(msg)
                                    onLogUpdate("[PREDICTIVE BACK GESTURES]: Enabled=$checked\n${res.output}")
                                }
                            }
                        )
                    }
                )
            }

            // Render resolution scaling
            GoogleSectionHeader(title = "RENDER RESOLUTION & SCALING")

            GoogleGroupedCard {
                GoogleSettingsRow(
                    title = "Output Resolution Override",
                    subtitle = "Native: ${nativeResolution?.first ?: "?"}x${nativeResolution?.second ?: "?"} • Active: ${
                        (overrideResolution ?: nativeResolution)?.let { "${it.first}x${it.second}" } ?: "?"
                    }. Scales the GPU render surface to reduce fill-rate work and improve frame pacing",
                    icon = Icons.Rounded.AspectRatio,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "Native (100%)" to 1f,
                        "90%" to 0.9f,
                        "85%" to 0.85f,
                        "75%" to 0.75f
                    ).forEach { (label, value) ->
                        val isSelected = Math.abs(selectedResolutionScale - value) < 0.02f
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedResolutionScale = value },
                            label = { Text(label, fontWeight = FontWeight.SemiBold, softWrap = false, maxLines = 1) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val scale = selectedResolutionScale
                            executeWithConfirmation(
                                title = if (scale >= 0.99f) "Reset Render Resolution" else "Scale Render Resolution",
                                message = if (scale >= 0.99f) {
                                    "Clear the resolution override and return the panel to its native size?"
                                } else {
                                    "Override the render resolution to ${
                                        nativeResolution?.let {
                                            val w = ((it.first * scale) / 2f).toInt() * 2
                                            val h = ((it.second * scale) / 2f).toInt() * 2
                                            "${w}x${h}"
                                        } ?: "a scaled size"
                                    }? Apps keep the same aspect ratio and you can restore native at any time."
                                }
                            ) {
                                scope.launch {
                                    isApplyingResolution = true
                                    val res = RyTuneXDeepEngine.applyRenderResolution(scale)
                                    isApplyingResolution = false
                                    isLastActionSuccess = res.isSuccess
                                    val pctLabel = String.format(Locale.US, "%.0f", scale * 100)
                                    val msg = if (res.isSuccess) {
                                        if (scale >= 0.99f) "Render resolution restored to native" else "Render resolution scaled to $pctLabel%"
                                    } else {
                                        "Resolution change failed: ${res.stderr}"
                                    }
                                    showStatusToast(msg)
                                    onLogUpdate("[RENDER RESOLUTION]: $pctLabel% • ${res.output}")
                                }
                            }
                        },
                        enabled = !isApplyingResolution && nativeResolution != null,
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        if (isApplyingResolution) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            if (selectedResolutionScale >= 0.99f) "Reset to Native Resolution"
                            else "Apply ${String.format(Locale.US, "%.0f", selectedResolutionScale * 100)}% Scale",
                            fontWeight = FontWeight.SemiBold,
                            softWrap = false,
                            maxLines = 1
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            executeWithConfirmation(
                                title = "Reset Render Resolution",
                                message = "Clear the resolution override and return the panel to its native size?"
                            ) {
                                scope.launch {
                                    isApplyingResolution = true
                                    val res = RyTuneXDeepEngine.applyRenderResolution(1f)
                                    isApplyingResolution = false
                                    isLastActionSuccess = res.isSuccess
                                    val msg = if (res.isSuccess) "Render resolution restored to native" else "Resolution reset failed: ${res.stderr}"
                                    showStatusToast(msg)
                                    onLogUpdate("[RENDER RESOLUTION]: Native • ${res.output}")
                                }
                            }
                        },
                        enabled = !isApplyingResolution && overrideResolution != null,
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text("Restore Native", fontWeight = FontWeight.SemiBold, softWrap = false, maxLines = 1)
                    }
                }
            }

            // Touch & GPU acceleration
            GoogleSectionHeader(title = "INPUT LATENCY & GPU ACCELERATION")

            GoogleGroupedCard {
                GoogleSettingsRow(
                    title = "Touchscreen Latency Boost",
                    subtitle = "Sets pointer speed +7, eliminates touch delay thresholds",
                    icon = Icons.Rounded.TouchApp,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    trailingContent = {
                        Switch(
                            checked = touchBoostEnabled,
                            onCheckedChange = { checked ->
                                touchBoostEnabled = checked
                                scope.launch {
                                    val res = RyTuneXDeepEngine.optimizeTouchResponse(checked)
                                    isLastActionSuccess = res.isSuccess
                                    val msg = if (checked) {
                                        "Touch response boost enabled (pointer_speed=7, tap_duration=0)"
                                    } else {
                                        "Touch response boost restored to system default"
                                    }
                                    showStatusToast(msg)
                                    onLogUpdate("[TOUCH RESPONSE BOOST]: Enabled=$checked\n${res.output}")
                                }
                            }
                        )
                    }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                GoogleSettingsRow(
                    title = "Disable GPU Window Blurs",
                    subtitle = "Eliminates shader compositing overhead, boosting frame rates",
                    icon = Icons.Rounded.BlurOff,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    trailingContent = {
                        Switch(
                            checked = disableBlursEnabled,
                            onCheckedChange = { checked ->
                                disableBlursEnabled = checked
                                scope.launch {
                                    val res = RyTuneXDeepEngine.setGpuBlursAndRendering(checked, forceGameDriverEnabled)
                                    isLastActionSuccess = res.isSuccess
                                    val msg = if (checked) {
                                        "GPU window blurs disabled (compositing overhead eliminated)"
                                    } else {
                                        "GPU window blurs enabled"
                                    }
                                    showStatusToast(msg)
                                    onLogUpdate("[GPU WINDOW BLURS]: Disable=$checked\n${res.output}")
                                }
                            }
                        )
                    }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                GoogleSettingsRow(
                    title = "Force Hardware Game Driver",
                    subtitle = "Directs all app graphics rendering through vendor GPU driver",
                    icon = Icons.Rounded.SportsEsports,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    trailingContent = {
                        Switch(
                            checked = forceGameDriverEnabled,
                            onCheckedChange = { checked ->
                                forceGameDriverEnabled = checked
                                scope.launch {
                                    val res = RyTuneXDeepEngine.setGpuBlursAndRendering(disableBlursEnabled, checked)
                                    isLastActionSuccess = res.isSuccess
                                    val msg = if (checked) {
                                        "Hardware Game Driver forced for all applications"
                                    } else {
                                        "Game driver reset to system default"
                                    }
                                    showStatusToast(msg)
                                    onLogUpdate("[GAME DRIVER FOR ALL]: Enabled=$checked\n${res.output}")
                                }
                            }
                        )
                    }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                GoogleSettingsRow(
                    title = "Hardware 2D GPU Rendering",
                    subtitle = "Offloads all 2D screen drawing to the GPU (force_hw_ui), reducing CPU rendering bottlenecks on older & budget chips",
                    icon = Icons.Rounded.Speed,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    trailingContent = {
                        Switch(
                            checked = forceHwUiEnabled,
                            onCheckedChange = { checked ->
                                forceHwUiEnabled = checked
                                scope.launch {
                                    val res = RyTuneXDeepEngine.setForceHwUi(checked)
                                    isLastActionSuccess = res.isSuccess
                                    val msg = if (checked) {
                                        "Hardware 2D GPU Rendering enabled (force_hw_ui=1)"
                                    } else {
                                        "Hardware 2D GPU Rendering disabled (force_hw_ui=0)"
                                    }
                                    showStatusToast(msg)
                                    onLogUpdate("[FORCE 2D HW UI]: Enabled=$checked\n${res.output}")
                                }
                            }
                        )
                    }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                GoogleSettingsRow(
                    title = "Vulkan HWUI Graphics Backend",
                    subtitle = "Routes app draw-calls through Vulkan (skiavk) instead of OpenGL ES (skiagl), reducing CPU driver overhead on Android 10+. Applies to apps launched after the switch",
                    icon = Icons.Rounded.DeveloperBoard,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    trailingContent = {
                        Switch(
                            checked = vulkanRendererEnabled,
                            onCheckedChange = { checked ->
                                vulkanRendererEnabled = checked
                                scope.launch {
                                    val res = RyTuneXDeepEngine.setVulkanRendering(checked)
                                    isLastActionSuccess = res.isSuccess
                                    val msg = if (checked) {
                                        "Vulkan HWUI backend enabled (skiavk)"
                                    } else {
                                        "OpenGL ES HWUI backend restored (skiagl)"
                                    }
                                    showStatusToast(msg)
                                    onLogUpdate("[VULKAN HWUI BACKEND]: Enabled=$checked\n${res.output}")
                                }
                            }
                        )
                    }
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                GoogleSettingsRow(
                    title = "Force GPU Compositing (Disable HW Overlays)",
                    subtitle = "Runs all layer composition on the GPU. Stabilizes frame pacing and blur rendering, may increase power draw",
                    icon = Icons.Rounded.Layers,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    trailingContent = {
                        Switch(
                            checked = hwOverlaysDisabled,
                            onCheckedChange = { checked ->
                                hwOverlaysDisabled = checked
                                scope.launch {
                                    val res = RyTuneXDeepEngine.setHwOverlaysDisabled(checked)
                                    isLastActionSuccess = res.isSuccess
                                    val msg = if (res.isSuccess) {
                                        if (checked) "Hardware overlays disabled (GPU compositing forced)"
                                        else "Hardware overlays restored to system default"
                                    } else {
                                        "Failed to change HW overlays: ${res.stderr}"
                                    }
                                    showStatusToast(msg)
                                    onLogUpdate("[HW OVERLAYS]: Disabled=$checked\n${res.output}")
                                }
                            }
                        )
                    }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                GoogleSettingsRow(
                    title = "Cached Apps Freezer",
                    subtitle = "Freezes the CPU of cached background apps instead of only limiting them. Applies to apps newly entering the cache",
                    icon = Icons.Rounded.AcUnit,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    trailingContent = {
                        Switch(
                            checked = appFreezerEnabled,
                            onCheckedChange = { checked ->
                                appFreezerEnabled = checked
                                scope.launch {
                                    val res = RyTuneXDeepEngine.setCachedAppFreezer(checked)
                                    isLastActionSuccess = res.isSuccess
                                    val msg = if (res.isSuccess) {
                                        if (checked) "App Freezer enabled for cached apps"
                                        else "App Freezer disabled"
                                    } else {
                                        "Failed to change App Freezer: ${res.stderr}"
                                    }
                                    showStatusToast(msg)
                                    onLogUpdate("[CACHED APPS FREEZER]: Enabled=$checked\n${res.output}")
                                }
                            }
                        )
                    }
                )
            }

            // Network & Private DNS
            GoogleSectionHeader(title = "NETWORK & LATENCY CONTROL")

            GoogleGroupedCard {
                GoogleSettingsRow(
                    title = "Private Encrypted DNS",
                    subtitle = if (customDnsHost != null && selectedDns == DnsProvider.CLOUDFLARE && !customDnsHost.orEmpty().contains("cloudflare")) {
                        "Active Custom: $customDnsHost"
                    } else {
                        "Current: ${selectedDns.title} • ${selectedDns.description}"
                    },
                    icon = Icons.Rounded.Dns,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    trailingContent = {
                        if (isApplyingDns) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DnsProvider.entries.forEach { provider ->
                        val isSelected = selectedDns == provider
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedDns = provider
                                customDnsHost = provider.hostname
                                scope.launch {
                                    isApplyingDns = true
                                    val res = RyTuneXDeepEngine.applyPrivateDns(provider)
                                    isApplyingDns = false
                                    isLastActionSuccess = res.isSuccess
                                    val msg = if (res.isSuccess) {
                                        "Private DNS set to ${provider.title}"
                                    } else {
                                        "Failed to apply DNS: ${res.stderr}"
                                    }
                                    showStatusToast(msg)
                                    onLogUpdate("[PRIVATE DNS]: Applied ${provider.title}\n${res.output}")
                                }
                            },
                            label = { Text(provider.title, fontWeight = FontWeight.SemiBold, softWrap = false, maxLines = 1) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = 10.dp)
                )

                GoogleSettingsRow(
                    title = "Wi-Fi & Bluetooth Radio Saver",
                    subtitle = "Disables continuous background location scans and radio keepalive",
                    icon = Icons.Rounded.WifiFind,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    trailingContent = {
                        Switch(
                            checked = networkPowerSaveEnabled,
                            onCheckedChange = { checked ->
                                networkPowerSaveEnabled = checked
                                scope.launch {
                                    val res = RyTuneXDeepEngine.optimizeNetworkRadio(checked)
                                    isLastActionSuccess = res.isSuccess
                                    val msg = if (checked) {
                                        "Radio saver enabled (Wi-Fi/BLE background scans disabled)"
                                    } else {
                                        "Radio saver disabled (standard background scans enabled)"
                                    }
                                    showStatusToast(msg)
                                    onLogUpdate("[NETWORK RADIO POWER SAVE]: Enabled=$checked\n${res.output}")
                                }
                            }
                        )
                    }
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                GoogleSettingsRow(
                    title = "Wi-Fi Scan Throttle Bypass",
                    subtitle = "Removes the scan backoff between background Wi-Fi scans. Faster GPS locks, indoor positioning and roaming reconnection",
                    icon = Icons.Rounded.WifiFind,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    trailingContent = {
                        Switch(
                            checked = scanThrottleEnabled,
                            onCheckedChange = { checked ->
                                scanThrottleEnabled = checked
                                scope.launch {
                                    val res = RyTuneXDeepEngine.setWifiScanThrottle(checked)
                                    isLastActionSuccess = res.isSuccess
                                    val msg = if (res.isSuccess) {
                                        if (checked) "Wi-Fi scan throttling restored (stock behavior)"
                                        else "Wi-Fi scan throttling bypassed (unrestricted background scans)"
                                    } else {
                                        "Failed to change scan throttling: ${res.stderr}"
                                    }
                                    showStatusToast(msg)
                                    onLogUpdate("[WIFI SCAN THROTTLE]: Enabled=$checked\n${res.output}")
                                }
                            }
                        )
                    }
                )
            }

            // System & power management
            GoogleSectionHeader(title = "SYSTEM & POWER MANAGEMENT")

            GoogleGroupedCard {
                GoogleSettingsRow(
                    title = "Phantom Process Killer Bypass",
                    subtitle = if (phantomBypassed) {
                        "Active: max_phantom_processes=2,147,483,647 (No background killing)"
                    } else {
                        "Default: Android kills background sub-processes exceeding 32 instances"
                    },
                    icon = Icons.Rounded.Security,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    bottomContent = {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (phantomBypassed) SemanticGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainerHighest
                            ) {
                                Text(
                                    text = if (phantomBypassed) "Bypassed (2.1B)" else "Stock (32)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (phantomBypassed) SemanticGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                    softWrap = false,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }

                            FilledTonalButton(
                                onClick = {
                                    executeWithConfirmation(
                                        title = "Bypass Phantom Process Killer",
                                        message = "Raise the phantom process limit to 2,147,483,647 to prevent Android from aggressively terminating background child processes?"
                                    ) {
                                        scope.launch {
                                            isApplyingPhantom = true
                                            val res = RyTuneXDeepEngine.fixPhantomProcessKiller()
                                            isApplyingPhantom = false
                                            val success = res.isSuccess || res.stdout.contains("2147483647")
                                            isLastActionSuccess = success
                                            phantomBypassed = success
                                            val msg = if (success) {
                                                "Phantom Process Killer bypassed (2,147,483,647 max processes verified)"
                                            } else {
                                                "Failed to bypass Phantom Process Killer: ${res.stderr}"
                                            }
                                            showStatusToast(msg)
                                            onLogUpdate("[PHANTOM PROCESS KILLER]: ${if (success) "Bypass Applied" else "Failed"}\n${res.output}")
                                        }
                                    }
                                },
                                enabled = !isApplyingPhantom,
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                if (isApplyingPhantom) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = if (phantomBypassed) "Re-apply Bypass" else "Apply Bypass",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    softWrap = false,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                GoogleSettingsRow(
                    title = "Block Telemetry & Error Logging",
                    subtitle = "Reduces continuous write transactions to dropbox & netstats",
                    icon = Icons.Rounded.BugReport,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    trailingContent = {
                        Switch(
                            checked = logsSuppressed,
                            onCheckedChange = { checked ->
                                logsSuppressed = checked
                                scope.launch {
                                    val res = RyTuneXDeepEngine.suppressTelemetryAndLogging(context, checked)
                                    isLastActionSuccess = res.isSuccess
                                    val msg = if (checked) {
                                        "Telemetry & error logging suppressed (I/O disk overhead reduced)"
                                    } else {
                                        "Telemetry logging restored to system default"
                                    }
                                    showStatusToast(msg)
                                    onLogUpdate("[TELEMETRY LOGGERS]: Suppress=$checked\n${res.output}")
                                }
                            }
                        )
                    }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                GoogleSettingsRow(
                    title = "Aggressive Doze & Deep Sleep",
                    subtitle = "Enforces rapid transition into deep sleep when screen is turned off",
                    icon = Icons.Rounded.Bedtime,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    trailingContent = {
                        Switch(
                            checked = aggressiveDozeEnabled,
                            onCheckedChange = { checked ->
                                aggressiveDozeEnabled = checked
                                scope.launch {
                                    val res = RyTuneXDeepEngine.setAggressiveDoze(checked)
                                    isLastActionSuccess = res.isSuccess
                                    val msg = if (checked) {
                                        "Aggressive Doze enabled (instant deep sleep on screen-off)"
                                    } else {
                                        "Aggressive Doze disabled"
                                    }
                                    showStatusToast(msg)
                                    onLogUpdate("[AGGRESSIVE DOZE]: Enabled=$checked\n${res.output}")
                                }
                            }
                        )
                    }
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                GoogleSettingsRow(
                    title = "App Standby & Doze Buckets",
                    subtitle = "Restricts idle apps based on usage buckets, covering CPU, network and job scheduling",
                    icon = Icons.Rounded.Snooze,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    trailingContent = {
                        Switch(
                            checked = appStandbyEnabled,
                            onCheckedChange = { checked ->
                                appStandbyEnabled = checked
                                scope.launch {
                                    val res = RyTuneXDeepEngine.setAppStandby(checked)
                                    isLastActionSuccess = res.isSuccess
                                    val msg = if (res.isSuccess) {
                                        if (checked) "App Standby buckets enabled"
                                        else "App Standby buckets disabled (unrestricted background apps)"
                                    } else {
                                        "Failed to change App Standby: ${res.stderr}"
                                    }
                                    showStatusToast(msg)
                                    onLogUpdate("[APP STANDBY BUCKETS]: Enabled=$checked\n${res.output}")
                                }
                            }
                        )
                    }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                GoogleSettingsRow(
                    title = "Adaptive On-Device Battery",
                    subtitle = "System-managed Doze anticipation using the device power model. Disable to keep background connectivity unrestricted",
                    icon = Icons.Rounded.BatterySaver,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    trailingContent = {
                        Switch(
                            checked = adaptiveBatteryEnabled,
                            onCheckedChange = { checked ->
                                adaptiveBatteryEnabled = checked
                                scope.launch {
                                    val res = RyTuneXDeepEngine.setAdaptiveBattery(checked)
                                    isLastActionSuccess = res.isSuccess
                                    val msg = if (res.isSuccess) {
                                        if (checked) "Adaptive battery management enabled"
                                        else "Adaptive battery management disabled"
                                    } else {
                                        "Failed to change adaptive battery: ${res.stderr}"
                                    }
                                    showStatusToast(msg)
                                    onLogUpdate("[ADAPTIVE BATTERY]: Enabled=$checked\n${res.output}")
                                }
                            }
                        )
                    }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                GoogleSettingsRow(
                    title = "Disable Network Stats Collection",
                    subtitle = "Stops periodic netstats accounting and its flash writes, lowering background disk I/O. Per-app traffic history stops updating",
                    icon = Icons.Rounded.DataUsage,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    trailingContent = {
                        Switch(
                            checked = netstatsDisabled,
                            onCheckedChange = { checked ->
                                netstatsDisabled = checked
                                scope.launch {
                                    val res = RyTuneXDeepEngine.setNetworkStatsCollection(!checked)
                                    isLastActionSuccess = res.isSuccess
                                    val msg = if (res.isSuccess) {
                                        if (checked) "Network stats collection disabled"
                                        else "Network stats collection restored"
                                    } else {
                                        "Failed to change network stats: ${res.stderr}"
                                    }
                                    showStatusToast(msg)
                                    onLogUpdate("[NETWORK STATS COLLECTION]: Disabled=$checked\n${res.output}")
                                }
                            }
                        )
                    }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                GoogleSettingsRow(
                    title = "Stay Awake While Charging",
                    subtitle = "Keeps the screen on while charging on AC, USB or wireless",
                    icon = Icons.Rounded.ScreenLockPortrait,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    trailingContent = {
                        Switch(
                            checked = stayAwakeCharging,
                            onCheckedChange = { checked ->
                                stayAwakeCharging = checked
                                scope.launch {
                                    val res = RyTuneXDeepEngine.setStayAwakeCharging(checked)
                                    isLastActionSuccess = res.isSuccess
                                    val msg = if (res.isSuccess) {
                                        if (checked) "Stay awake while charging enabled"
                                        else "Stay awake while charging disabled"
                                    } else {
                                        "Failed to change stay awake: ${res.stderr}"
                                    }
                                    showStatusToast(msg)
                                    onLogUpdate("[STAY AWAKE CHARGING]: Enabled=$checked\n${res.output}")
                                }
                            }
                        )
                    }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                GoogleSettingsRow(
                    title = "Sticky Battery Saver",
                    subtitle = "Keeps Battery Saver on after a full recharge until disabled manually",
                    icon = Icons.Rounded.BatteryChargingFull,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    trailingContent = {
                        Switch(
                            checked = stickyBatterySaver,
                            onCheckedChange = { checked ->
                                stickyBatterySaver = checked
                                scope.launch {
                                    val res = RyTuneXDeepEngine.setStickyBatterySaver(checked)
                                    isLastActionSuccess = res.isSuccess
                                    val msg = if (res.isSuccess) {
                                        if (checked) "Sticky Battery Saver enabled (persists after recharge)"
                                        else "Sticky Battery Saver disabled"
                                    } else {
                                        "Failed to change sticky battery saver: ${res.stderr}"
                                    }
                                    showStatusToast(msg)
                                    onLogUpdate("[STICKY BATTERY SAVER]: Enabled=$checked\n${res.output}")
                                }
                            }
                        )
                    }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                GoogleSettingsRow(
                    title = "Automatic Battery Saver Trigger",
                    subtitle = "Enters Battery Saver when the charge level drops to the selected threshold",
                    icon = Icons.Rounded.BatteryAlert,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    bottomContent = {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf<Int?>(null, 15, 30, 50).forEach { level ->
                                FilterChip(
                                    selected = lowPowerTrigger == level,
                                    onClick = {
                                        lowPowerTrigger = level
                                        scope.launch {
                                            val res = RyTuneXDeepEngine.setLowPowerTriggerLevel(level)
                                            isLastActionSuccess = res.isSuccess
                                            val msg = if (res.isSuccess) {
                                                if (level == null) "Automatic battery saver disabled"
                                                else "Battery Saver triggers automatically at $level%"
                                            } else {
                                                "Failed to set battery saver trigger: ${res.stderr}"
                                            }
                                            showStatusToast(msg)
                                            onLogUpdate("[AUTO BATTERY SAVER]: ${level?.toString() ?: "Off"}\n${res.output}")
                                        }
                                    },
                                    label = {
                                        Text(
                                            level?.let { "$it%" } ?: "Off",
                                            fontWeight = FontWeight.SemiBold,
                                            softWrap = false,
                                            maxLines = 1
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                GoogleSettingsRow(
                    title = "Flush Log Buffers",
                    subtitle = "Clears logcat buffers and the kernel dmesg ring buffer",
                    icon = Icons.Rounded.Terminal,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    bottomContent = {
                        FilledTonalButton(
                            onClick = {
                                executeWithConfirmation(
                                    title = "Flush Log Buffers",
                                    message = "Clear every logcat buffer and the kernel ring buffer? Historical logs will be lost."
                                ) {
                                    scope.launch {
                                        isFlushingLogs = true
                                        val res = RyTuneXDeepEngine.flushLogBuffers()
                                        isFlushingLogs = false
                                        isLastActionSuccess = res.isSuccess
                                        val msg = if (res.isSuccess) {
                                            "Logcat and kernel ring buffers flushed"
                                        } else {
                                            "Log flush failed: ${res.stderr.ifBlank { res.stdout }}"
                                        }
                                        showStatusToast(msg)
                                        onLogUpdate("[LOG BUFFER FLUSH]: ${res.output}")
                                    }
                                }
                            },
                            enabled = !isFlushingLogs,
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                        ) {
                            if (isFlushingLogs) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                if (isFlushingLogs) "Flushing Buffers..." else "Flush Buffers",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                softWrap = false,
                                maxLines = 1
                            )
                        }
                    }
                )
            }

            // Storage & memory
            GoogleSectionHeader(title = "STORAGE & MEMORY OPTIMIZATION")

            GoogleGroupedCard {
                GoogleSettingsRow(
                    title = "UFS & eMMC Flash Storage TRIM",
                    subtitle = "Executes kernel fstrim on all NAND flash partitions to restore write performance and clear dirty blocks",
                    icon = Icons.Rounded.Storage,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    bottomContent = {
                        FilledTonalButton(
                            onClick = {
                                executeWithConfirmation(
                                    title = "Hardware Storage TRIM",
                                    message = "Run fstrim on internal storage blocks to restore write latency and garbage collect trimmed NAND sectors?"
                                ) {
                                    scope.launch {
                                        isTrimmingStorage = true
                                        val res = RyTuneXDeepEngine.runStorageTrim()
                                        isTrimmingStorage = false
                                        isLastActionSuccess = res.isSuccess
                                        val msg = if (res.isSuccess) {
                                            res.stdout.ifBlank { "Storage TRIM completed successfully across flash partitions" }
                                        } else {
                                            "Storage TRIM failed: ${res.stderr.ifBlank { res.stdout }}"
                                        }
                                        showStatusToast(msg)
                                        onLogUpdate("[HARDWARE FLASH TRIM]: ${res.output}")
                                    }
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
                            Text(
                                if (isTrimmingStorage) "Trimming Flash Blocks..." else "Run Hardware TRIM (fstrim)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                softWrap = false,
                                maxLines = 1
                            )
                        }
                    }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                GoogleSettingsRow(
                    title = "Kernel ZRAM Memory Compaction",
                    subtitle = "Forces full kernel heap compaction and compresses background anonymous pages into ZRAM to free physical RAM",
                    icon = Icons.Rounded.Compress,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    bottomContent = {
                        FilledTonalButton(
                            onClick = {
                                executeWithConfirmation(
                                    title = "Kernel ZRAM Memory Compaction",
                                    message = "Force kernel memory compaction and compress inactive anonymous memory pages into ZRAM swap?"
                                ) {
                                    scope.launch {
                                        isCompactingMemory = true
                                        val res = RyTuneXDeepEngine.compactMemory()
                                        isCompactingMemory = false
                                        isLastActionSuccess = res.isSuccess
                                        val msg = if (res.isSuccess) {
                                            res.stdout.ifBlank { "Kernel memory compaction & ZRAM compression completed" }
                                        } else {
                                            "Memory compaction failed: ${res.stderr.ifBlank { res.stdout }}"
                                        }
                                        showStatusToast(msg)
                                        onLogUpdate("[ZRAM MEMORY COMPACTION]: ${res.output}")
                                    }
                                }
                            },
                            enabled = !isCompactingMemory,
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
                        ) {
                            if (isCompactingMemory) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                if (isCompactingMemory) "Compacting Memory..." else "Compact Kernel ZRAM",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                softWrap = false,
                                maxLines = 1
                            )
                        }
                    }
                )
            }

            // ART runtime compilation
            GoogleSectionHeader(title = "ART RUNTIME COMPILATION")

            GoogleGroupedCard {
                GoogleSettingsRow(
                    title = "AOT Bytecode Pre-Compilation",
                    subtitle = "Pre-compiles DEX into native machine code to eliminate JIT stutter",
                    icon = Icons.Rounded.Bolt,
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconColor = MaterialTheme.colorScheme.onPrimaryContainer
                )

                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "speed-profile" to "Speed Profile (PGO)",
                        "speed" to "Full AOT Speed",
                        "quicken" to "Quicken (Fastest)",
                        "bg-dexopt" to "Background DexOpt"
                    ).forEach { (mode, title) ->
                        val isSel = artMode == mode
                        FilterChip(
                            selected = isSel,
                            onClick = { artMode = mode },
                            label = { Text(title, fontWeight = FontWeight.SemiBold, softWrap = false, maxLines = 1) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        executeWithConfirmation(
                            title = "ART Runtime Compilation",
                            message = "Run full ahead-of-time (AOT) bytecode compilation for installed user packages with target mode '$artMode'? This process may take 15-60 seconds."
                        ) {
                            scope.launch {
                                isApplyingArt = true
                                val res = AdaptiveOptimizer.runArtOptimization(artMode)
                                isApplyingArt = false
                                isLastActionSuccess = res.isSuccess
                                val msg = if (res.isSuccess) {
                                    "ART Compilation ($artMode) completed successfully"
                                } else {
                                    "ART compilation failed: ${res.stderr}"
                                }
                                showStatusToast(msg)
                                onLogUpdate("[ART COMPILER]: Mode $artMode completed\n${res.output}")
                            }
                        }
                    },
                    enabled = !isApplyingArt,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    if (isApplyingArt) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        if (isApplyingArt) "Compiling Packages..." else "Pre-compile Packages ($artMode)",
                        fontWeight = FontWeight.SemiBold,
                        softWrap = false,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.navigationBarsPadding().height(130.dp))
        }

        // Floating header pill (visible when scrolled)
        FloatingTopPillHeader(
            visible = isScrolled,
            title = "Optimize",
            onClick = {
                scope.launch { scrollState.animateScrollTo(0) }
            },
            trailingContent = {
                IconButton(
                    onClick = { reloadState() },
                    modifier = Modifier.size(36.dp)
                ) {
                    if (isLoadingState) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = "Refresh Live Status",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        )

        // Tweak confirmation dialog
        pendingConfirmAction?.let { action ->
            ExpressiveAlertDialog(
                onDismissRequest = { pendingConfirmAction = null },
                icon = { Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text(pendingConfirmTitle, fontWeight = FontWeight.Bold) },
                text = { Text(pendingConfirmMessage) },
                confirmButton = {
                    Button(
                        onClick = {
                            val act = action
                            pendingConfirmAction = null
                            act()
                        }
                    ) {
                        Text("Confirm & Apply", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingConfirmAction = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
}
