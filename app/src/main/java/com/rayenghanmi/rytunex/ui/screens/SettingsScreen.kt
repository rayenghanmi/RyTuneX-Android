package com.rayenghanmi.rytunex.ui.screens

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rayenghanmi.rytunex.R
import com.rayenghanmi.rytunex.engine.AppUpdateManager
import com.rayenghanmi.rytunex.engine.DebloatAction
import com.rayenghanmi.rytunex.engine.RyTuneXDeepEngine
import com.rayenghanmi.rytunex.engine.SettingsManager
import com.rayenghanmi.rytunex.engine.UadDatabaseManager
import com.rayenghanmi.rytunex.theme.SemanticGreen
import com.rayenghanmi.rytunex.theme.SemanticRed
import com.rayenghanmi.rytunex.ui.components.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    isShizukuReady: Boolean,
    onRequestAuth: () -> Unit,
    onClearLogs: () -> Unit,
    onLogUpdate: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val isScrolled by remember { derivedStateOf { scrollState.value > 60 } }

    var isUpdatingUad by remember { mutableStateOf(false) }
    var isResettingTweaks by remember { mutableStateOf(false) }
    var isCheckingUpdates by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

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
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Preferences, Material Theme & Engine Controls",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                PulsingStatusBadge(
                    isActive = isShizukuReady,
                    activeText = "UID 2000",
                    inactiveText = "NO PRIVILEGE"
                )
            }

            // Appearance & theming
            GoogleSectionHeader(title = "Appearance & Theming")
            ThemeModeSelector(
                themeMode = SettingsManager.themeMode.toAppThemeMode(),
                onThemeModeChanged = { SettingsManager.setAppThemeMode(context, it) }
            )

            val isDynamicColorSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            MaterialSettingsSwitchCard(
                icon = Icons.Rounded.ColorLens,
                title = "Material Design Dynamic Colors",
                description = if (isDynamicColorSupported) {
                    "Match system palette dynamically with expressive tonal elevation"
                } else {
                    "Dynamic colors require Android 12+ (API 31+)"
                },
                checked = SettingsManager.useDynamicColor && isDynamicColorSupported,
                position = SettingsCardPosition.Single,
                enabled = isDynamicColorSupported,
                onCheckedChange = { enabled ->
                    SettingsManager.setDynamicColor(context, enabled)
                }
            )

            // Telemetry & monitoring
            GoogleSectionHeader(title = "Hardware Telemetry")
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MaterialSettingsSwitchCard(
                    icon = Icons.Rounded.Speed,
                    title = "Real-Time Telemetry Monitor",
                    description = "Calculates CPU usage, RAM utilization, thermals, and display refresh rates",
                    checked = SettingsManager.telemetryEnabled,
                    position = SettingsCardPosition.Top,
                    onCheckedChange = { SettingsManager.setTelemetryEnabled(context, it) }
                )

                MaterialSettingsCard(
                    icon = Icons.Rounded.Timer,
                    title = "Telemetry Polling Interval",
                    description = "Frequency of hardware metrics refresh (${SettingsManager.telemetryIntervalMs}ms)",
                    position = SettingsCardPosition.Middle,
                    bottomContent = {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(800L to "0.8s", 1200L to "1.2s", 2000L to "2.0s", 3000L to "3.0s").forEach { (interval, label) ->
                                val isSelected = SettingsManager.telemetryIntervalMs == interval
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { SettingsManager.setTelemetryInterval(context, interval) },
                                    label = { Text(label, maxLines = 1, softWrap = false) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    }
                )

                MaterialSettingsCard(
                    icon = Icons.Rounded.Thermostat,
                    title = "Temperature Unit",
                    description = if (SettingsManager.isFahrenheit) "Displaying in Fahrenheit (°F)" else "Displaying in Celsius (°C)",
                    position = SettingsCardPosition.Bottom,
                    bottomContent = {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = !SettingsManager.isFahrenheit,
                                onClick = { SettingsManager.setFahrenheit(context, false) },
                                label = { Text("Celsius (°C)", maxLines = 1, softWrap = false) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                            FilterChip(
                                selected = SettingsManager.isFahrenheit,
                                onClick = { SettingsManager.setFahrenheit(context, true) },
                                label = { Text("Fahrenheit (°F)", maxLines = 1, softWrap = false) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                )
            }

            // Debloat & package management
            GoogleSectionHeader(title = "Debloat & Package Management")
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MaterialSettingsCard(
                    icon = Icons.Rounded.CleaningServices,
                    title = "Default Debloat Action",
                    description = "Action executed when batch processing packages",
                    position = SettingsCardPosition.Top,
                    bottomContent = {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DebloatAction.entries.forEach { action ->
                                val isSelected = SettingsManager.defaultDebloatAction == action
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { SettingsManager.setDebloatAction(context, action) },
                                    label = { Text(action.label, maxLines = 1, softWrap = false) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }
                    }
                )

                MaterialSettingsCard(
                    icon = Icons.Rounded.CloudDownload,
                    title = "UAD Next-Gen Database",
                    description = "Update 1,500+ bloatware definitions directly from GitHub",
                    position = SettingsCardPosition.Bottom,
                    trailingContent = {
                        Button(
                            onClick = {
                                scope.launch {
                                    isUpdatingUad = true
                                    val res = UadDatabaseManager.updateFromGitHub(context)
                                    isUpdatingUad = false
                                    if (res.isSuccess) {
                                        Toast.makeText(context, "UAD updated: ${res.getOrNull()} entries", Toast.LENGTH_SHORT).show()
                                        onLogUpdate("[UAD] Database updated live with ${res.getOrNull()} entries")
                                    } else {
                                        Toast.makeText(context, "Update failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = !isUpdatingUad,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            if (isUpdatingUad) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Update", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                )
            }

            // Tweaks & safety
            GoogleSectionHeader(title = "Optimize & Safety")
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MaterialSettingsSwitchCard(
                    icon = Icons.Rounded.Security,
                    title = "Confirm Before Applying Optimizations",
                    description = "Ask for confirmation before deploying aggressive optimization profiles",
                    checked = SettingsManager.confirmTweaks,
                    position = SettingsCardPosition.Top,
                    onCheckedChange = { SettingsManager.setConfirmTweaks(context, it) }
                )

                MaterialSettingsCard(
                    icon = Icons.Rounded.Restore,
                    title = "Roll Back All Changes",
                    description = "Transparent rollback: reverts every RyTuneX change back to OEM defaults",
                    position = SettingsCardPosition.Bottom,
                    trailingContent = {
                        OutlinedButton(
                            onClick = { showResetDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("Reset", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            // Terminal & Shizuku service
            GoogleSectionHeader(title = "Terminal & Shizuku Service")
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MaterialSettingsCard(
                    icon = if (isShizukuReady) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                    title = "Privileged Service",
                    description = if (isShizukuReady) "Shizuku ADB UID 2000 connection is active and healthy" else "Shizuku connection is inactive or permission not granted",
                    position = SettingsCardPosition.Top,
                    trailingContent = {
                        if (!isShizukuReady) {
                            Button(
                                onClick = onRequestAuth,
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("Grant", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                )

                MaterialSettingsCard(
                    icon = Icons.Rounded.DeleteSweep,
                    title = "Clear Terminal Buffer",
                    description = "Clears all execution logs and resets the terminal stream",
                    position = SettingsCardPosition.Bottom,
                    trailingContent = {
                        TextButton(
                            onClick = {
                                onClearLogs()
                                Toast.makeText(context, "Terminal buffer cleared", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("Clear", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            // Updates & distribution
            GoogleSectionHeader(title = "Updates & Distribution")
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                MaterialSettingsCard(
                    icon = Icons.Rounded.SystemUpdate,
                    title = "Check for Updates",
                    description = "Installed: v${AppUpdateManager.getCurrentVersion(context)} • GitHub (RyTuneX-Android)",
                    position = SettingsCardPosition.Top,
                    trailingContent = {
                        Button(
                            onClick = {
                                scope.launch {
                                    isCheckingUpdates = true
                                    val res = AppUpdateManager.checkLatestUpdate(context)
                                    isCheckingUpdates = false
                                    if (res.isSuccess) {
                                        val info = res.getOrThrow()
                                        if (info.isUpdateAvailable) {
                                            showUpdateDialog = true
                                        } else {
                                            Toast.makeText(context, "RyTuneX is up to date (v${info.versionName})", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "Update check: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            enabled = !isCheckingUpdates,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            if (isCheckingUpdates) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Check", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                )

                MaterialSettingsSwitchCard(
                    icon = Icons.Rounded.Science,
                    title = "Include Pre-Release Builds",
                    description = "Check for experimental betas, release candidates, and preview releases",
                    checked = SettingsManager.includePreReleases,
                    position = SettingsCardPosition.Middle,
                    onCheckedChange = { SettingsManager.setIncludePreReleases(context, it) }
                )

                MaterialSettingsSwitchCard(
                    icon = Icons.Rounded.Sync,
                    title = "Auto-Check on App Launch",
                    description = "Query GitHub for new releases automatically when RyTuneX starts",
                    checked = SettingsManager.autoCheckUpdates,
                    position = SettingsCardPosition.Bottom,
                    onCheckedChange = { SettingsManager.setAutoCheckUpdates(context, it) }
                )
            }

            // About RyTuneX
            GoogleSectionHeader(title = "About")
            MaterialSettingsCard(
                title = "RyTuneX for Android",
                description = "Version ${com.rayenghanmi.rytunex.BuildConfig.VERSION_NAME} • Advanced Open-Source Tuning Engine for Power Users & Gamers",
                icon = Icons.Rounded.Info,
                position = SettingsCardPosition.GroupedSingle,
                onClick = { showAboutDialog = true },
                trailingContent = {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = "About Details",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )

            Spacer(modifier = Modifier.navigationBarsPadding().height(130.dp))
        }

        // Floating header pill (visible when scrolled)
        FloatingTopPillHeader(
            visible = isScrolled,
            title = "Settings",
            onClick = {
                scope.launch { scrollState.animateScrollTo(0) }
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
                                Text("• Design System: Material Design 3 (Dynamic Color)", style = MaterialTheme.typography.bodySmall)
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

        // Reset dialog
        if (showResetDialog) {
            ExpressiveAlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text("Roll Back All Changes?") },
                text = { Text("This will revert display refresh rate overrides, animation scale (1.0x), touch response, window blurs, Vulkan HWUI renderer, Force 2D HWUI, predictive back gestures, private DNS, and telemetry suppression back to original system defaults.") },
                confirmButton = {
                    Button(
                        onClick = {
                            showResetDialog = false
                            scope.launch {
                                isResettingTweaks = true
                                val res = RyTuneXDeepEngine.restoreSystemDefaults(context)
                                isResettingTweaks = false
                                Toast.makeText(context, "System defaults fully restored", Toast.LENGTH_SHORT).show()
                                onLogUpdate("[RESET ALL]: System defaults fully restored\n${res.stdout}")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Roll Back All", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) {
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
