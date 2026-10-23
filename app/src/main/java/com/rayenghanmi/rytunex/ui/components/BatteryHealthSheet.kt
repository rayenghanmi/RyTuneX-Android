package com.rayenghanmi.rytunex.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rayenghanmi.rytunex.engine.BatteryHealthManager
import com.rayenghanmi.rytunex.model.BatteryInfo
import com.rayenghanmi.rytunex.theme.SemanticGreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryHealthSheet(
    battery: BatteryInfo,
    isShizukuReady: Boolean,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onOpenWakelockSpotter: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isRefreshing by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }

    LaunchedEffect(isShizukuReady) {
        if (isShizukuReady) {
            isRefreshing = true
            BatteryHealthManager.refreshHardwareMetrics(force = true)
            onRefresh()
            isRefreshing = false
        }
    }

    val healthPct = battery.healthPercent ?: 100f
    val healthColor = when {
        healthPct >= 90f -> SemanticGreen
        healthPct >= 80f -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }

    val tempColor = when {
        battery.tempCelsius > 42f -> MaterialTheme.colorScheme.error
        battery.tempCelsius > 38f -> MaterialTheme.colorScheme.tertiary
        else -> SemanticGreen
    }

    val actualCapacity = if (battery.actualCapacityMah > 0) {
        battery.actualCapacityMah
    } else if (battery.designCapacityMah > 0 && battery.healthPercent != null) {
        (battery.designCapacityMah * (battery.healthPercent / 100f)).toInt()
    } else {
        battery.designCapacityMah
    }

    val wearMah = if (battery.designCapacityMah > actualCapacity && actualCapacity > 0) {
        battery.designCapacityMah - actualCapacity
    } else {
        0
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 6.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = healthColor.copy(alpha = 0.16f),
                    modifier = Modifier.size(50.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (battery.isCharging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatterySaver,
                            contentDescription = null,
                            tint = healthColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Battery Health & Hardware",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Real-time Fuel Gauge & Lifecycle Telemetry",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = {
                        scope.launch {
                            isRefreshing = true
                            BatteryHealthManager.refreshHardwareMetrics(force = true)
                            onRefresh()
                            isRefreshing = false
                            Toast.makeText(context, "Battery telemetry updated", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isRefreshing
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Refresh",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Hero Card: True Health Gauge
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "TRUE BATTERY HEALTH",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 0.8.sp
                            )
                            Text(
                                text = if (battery.healthPercent != null) "${String.format("%.1f", battery.healthPercent)}%" else "Hardware: ${battery.health}",
                                style = MaterialTheme.typography.headlineLarge,
                                fontWeight = FontWeight.Bold,
                                color = healthColor
                            )
                        }

                        Surface(
                            shape = CircleShape,
                            color = healthColor.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = when {
                                    battery.healthPercent == null -> battery.health
                                    battery.healthPercent >= 90f -> "Optimal Condition"
                                    battery.healthPercent >= 80f -> "Good Condition"
                                    else -> "Service Advised"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = healthColor,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }

                    // Linear Indicator
                    LinearProgressIndicator(
                        progress = { (healthPct / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = healthColor,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    )

                    // Capacity Breakdown
                    if (battery.designCapacityMah > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Estimated Remaining",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "$actualCapacity mAh",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Factory Design",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${battery.designCapacityMah} mAh",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        if (wearMah > 0) {
                            Text(
                                text = "Estimated capacity wear: -$wearMah mAh (${String.format("%.1f", (wearMah.toFloat() / battery.designCapacityMah) * 100f)}% loss)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Universal Charge Cycles Card
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val hasRealCycles = battery.cycleCount != null && battery.cycleCount > 0
                    val isHardware = battery.isCycleHardwareVerified

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isHardware) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Autorenew,
                                        contentDescription = null,
                                        tint = if (isHardware) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "CHARGE CYCLES",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    letterSpacing = 0.8.sp
                                )
                                Text(
                                    text = if (hasRealCycles) {
                                        if (isHardware) "${battery.cycleCount} Cycles" else "~${battery.cycleCount} Cycles"
                                    } else "Hardware Limited",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (battery.firstUseDate != null) {
                                    Text(
                                        text = "First Activated: ${battery.firstUseDate}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                } else {
                                    Text(
                                        text = battery.cycleSourceText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = if (isHardware) SemanticGreen.copy(alpha = 0.16f) else MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = if (isHardware) "Hardware Verified" else "Universal AOSP",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isHardware) SemanticGreen else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (hasRealCycles) {
                        LinearProgressIndicator(
                            progress = { (battery.cycleCount!!.toFloat() / 800f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape),
                            color = if (isHardware) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                        Text(
                            text = if (isHardware) {
                                "Direct hardware telemetry from ${battery.cycleSourceText}. Standard smartphone batteries typically retain ~80% capacity after 500 to 800 complete cycles."
                            } else {
                                "Universal Android Coulomb model calculated from factory design capacity (${battery.designCapacityMah} mAh) and battery energy integration. Transsion/Tecno hardware drivers omit physical cycle registers."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "AOSP universal estimation is calibrating energy counters. Plug in your device or discharge 5% to compute universal Coulomb cycles.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Live Telemetry Grid
            Text(
                text = "Live Power Telemetry",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp)
            )

            val wattageDisplay = if (battery.wattage > 0.05f) {
                "${if (battery.isCharging) "+" else "-"}${String.format("%.1f", battery.wattage)} W"
            } else if (battery.isCharging) {
                "Charging"
            } else {
                "Discharging"
            }

            val currentDisplay = if (battery.currentMa != 0) {
                "${if (battery.currentMa > 0) "+" else ""}${battery.currentMa} mA"
            } else if (battery.isCharging) {
                "Active Flow"
            } else {
                "Normal Drain"
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TelemetryMetricCard(
                    title = "Power Rate",
                    value = wattageDisplay,
                    subtitle = if (battery.isFastCharging) "Fast Charging" else if (battery.isCharging) "Standard Rate" else "Discharging",
                    icon = Icons.Rounded.Bolt,
                    accentColor = if (battery.isCharging) SemanticGreen else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )

                TelemetryMetricCard(
                    title = "Current Flow",
                    value = currentDisplay,
                    subtitle = if (battery.isCharging) "Input Current" else "Draw Current",
                    icon = Icons.Rounded.ElectricBolt,
                    accentColor = if (battery.isCharging) SemanticGreen else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TelemetryMetricCard(
                    title = "Voltage",
                    value = "${String.format("%.2f", battery.voltageMv / 1000f)} V",
                    subtitle = "${battery.voltageMv} mV",
                    icon = Icons.Rounded.Speed,
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )

                TelemetryMetricCard(
                    title = "Temperature",
                    value = "${String.format("%.1f", battery.tempCelsius)}°C",
                    subtitle = "${String.format("%.1f", (battery.tempCelsius * 9f / 5f) + 32f)}°F",
                    icon = Icons.Rounded.Thermostat,
                    accentColor = tempColor,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TelemetryMetricCard(
                    title = "Power Source",
                    value = battery.powerSource,
                    subtitle = if (battery.isCharging) "Active connection" else "Internal cell",
                    icon = Icons.Rounded.Cable,
                    accentColor = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )

                TelemetryMetricCard(
                    title = "Chemistry",
                    value = battery.technology,
                    subtitle = "Internal Battery",
                    icon = Icons.Rounded.Science,
                    accentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }

            // Hardware Registers & Diagnostics Inspector Card
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .clickable { showDiagnostics = !showDiagnostics }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Terminal,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Column {
                                Text(
                                    text = "Hardware Telemetry Nodes",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isShizukuReady) "Kernel sysfs & dumpsys registers" else "Privileged Shizuku required",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isShizukuReady) SemanticGreen.copy(alpha = 0.16f) else MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = if (isShizukuReady) "Active" else "Offline",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isShizukuReady) SemanticGreen else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }

                            Icon(
                                imageVector = if (showDiagnostics) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = showDiagnostics,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (!isShizukuReady) {
                                Text(
                                    text = "Shizuku is not running or permission is not granted. Privileged battery registers (/sys/class/power_supply/ and dumpsys) require Shizuku or root access.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    text = "Raw battery node query captured from kernel uevent, sysfs files, and Android BatteryService:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 220.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(12.dp)
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        Text(
                                            text = BatteryHealthManager.rawDiagnosticDump,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            val clip = ClipData.newPlainText("RyTuneX Battery Diagnostics", BatteryHealthManager.rawDiagnosticDump)
                                            clipboard?.setPrimaryClip(clip)
                                            Toast.makeText(context, "Hardware telemetry copied to clipboard", Toast.LENGTH_SHORT).show()
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ContentCopy,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Copy Output", style = MaterialTheme.typography.labelMedium)
                                    }

                                    Button(
                                        onClick = {
                                            scope.launch {
                                                isRefreshing = true
                                                BatteryHealthManager.refreshHardwareMetrics(force = true)
                                                onRefresh()
                                                isRefreshing = false
                                                Toast.makeText(context, "Telemetry refreshed", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Re-query Nodes", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Quick Actions & System Settings
            if (onOpenWakelockSpotter != null) {
                Button(
                    onClick = onOpenWakelockSpotter,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SearchOff,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Spot Wakelocks & Background Drain",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    try {
                        val intent = Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(context, "Could not launch system battery settings", Toast.LENGTH_SHORT).show()
                    }
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Open Android Battery Settings",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun TelemetryMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }

            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
