package com.rayenghanmi.rytunex.ui.components

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rayenghanmi.rytunex.engine.RyTuneXAppOpsEngine
import com.rayenghanmi.rytunex.model.AppOpCategory
import com.rayenghanmi.rytunex.model.AppOpItem
import com.rayenghanmi.rytunex.model.AppOpMode
import com.rayenghanmi.rytunex.model.DebloatApp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppOpsSheet(
    app: DebloatApp,
    onDismiss: () -> Unit,
    onLogUpdate: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var opsList by remember(app.packageName) { mutableStateOf<List<AppOpItem>>(emptyList()) }
    var isLoading by remember(app.packageName) { mutableStateOf(true) }
    var selectedCategory by remember { mutableStateOf<AppOpCategory?>(null) }
    var isOperating by remember { mutableStateOf(false) }

    fun refreshOps() {
        scope.launch {
            isLoading = true
            try {
                opsList = RyTuneXAppOpsEngine.getAppOpsForPackage(app.packageName)
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(app.packageName) {
        refreshOps()
    }

    val filteredOps = remember(opsList, selectedCategory) {
        if (selectedCategory == null) opsList else opsList.filter { it.category == selectedCategory }
    }

    val blockedCount = remember(opsList) {
        opsList.count { it.mode.isBlocked }
    }

    val isBackgroundRestricted = remember(opsList) {
        val bgOp = opsList.firstOrNull { it.opCode == "RUN_IN_BACKGROUND" }
        bgOp?.mode == AppOpMode.IGNORE
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
        ) {
            // Header Info Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(50.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = app.appName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Blocked Counter Badge
                Surface(
                    shape = CircleShape,
                    color = if (blockedCount > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = if (blockedCount > 0) "$blockedCount Blocked" else "Unrestricted",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (blockedCount > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        softWrap = false,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            // Quick Batch Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            isOperating = true
                            val restrict = !isBackgroundRestricted
                            val res = RyTuneXAppOpsEngine.restrictBackgroundDrain(app.packageName, restrict)
                            if (res.isSuccess) {
                                val msg = if (restrict) "Restricted background drain for ${app.appName}" else "Restored background execution for ${app.appName}"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                onLogUpdate("[APPOPS]: $msg")
                            } else {
                                Toast.makeText(context, "Failed: ${res.output}", Toast.LENGTH_LONG).show()
                            }
                            refreshOps()
                            isOperating = false
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isBackgroundRestricted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier.weight(1f),
                    enabled = !isOperating
                ) {
                    Icon(
                        imageVector = if (isBackgroundRestricted) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatterySaver,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isBackgroundRestricted) "Allow Background" else "Cut Background Drain",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isOperating = true
                            val res = RyTuneXAppOpsEngine.resetAppOps(app.packageName)
                            if (res.isSuccess) {
                                Toast.makeText(context, "Reset ${app.appName} AppOps to defaults", Toast.LENGTH_SHORT).show()
                                onLogUpdate("[APPOPS]: Reset all ops for ${app.packageName}")
                            } else {
                                Toast.makeText(context, "Reset failed: ${res.output}", Toast.LENGTH_LONG).show()
                            }
                            refreshOps()
                            isOperating = false
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    enabled = !isOperating
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Reset",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // Category Filter Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { selectedCategory = null },
                    label = { Text("All (${opsList.size})") },
                    shape = CircleShape
                )
                AppOpCategory.entries.forEach { category ->
                    val count = opsList.count { it.category == category }
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = if (selectedCategory == category) null else category },
                        label = { Text("${category.title} ($count)") },
                        shape = CircleShape
                    )
                }
            }

            // Ops List or Loading
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        Text(
                            text = "Reading AppOps privileges via Shizuku...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(filteredOps, key = { it.opCode }) { op ->
                        AppOpRowCard(
                            op = op,
                            onToggle = { isChecked ->
                                scope.launch {
                                    val newMode = if (isChecked) AppOpMode.ALLOW else AppOpMode.IGNORE
                                    val res = RyTuneXAppOpsEngine.setAppOp(app.packageName, op.opCode, newMode)
                                    if (res.isSuccess) {
                                        opsList = opsList.map {
                                            if (it.opCode == op.opCode) it.copy(mode = newMode) else it
                                        }
                                        onLogUpdate("[APPOPS]: ${app.packageName} ${op.opCode} -> ${newMode.raw}")
                                    } else {
                                        Toast.makeText(context, "Failed: ${res.output}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onResetToDefault = {
                                scope.launch {
                                    val res = RyTuneXAppOpsEngine.setAppOp(app.packageName, op.opCode, AppOpMode.DEFAULT)
                                    if (res.isSuccess) {
                                        opsList = opsList.map {
                                            if (it.opCode == op.opCode) it.copy(mode = AppOpMode.DEFAULT) else it
                                        }
                                        onLogUpdate("[APPOPS]: ${app.packageName} ${op.opCode} -> default")
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppOpRowCard(
    op: AppOpItem,
    onToggle: (Boolean) -> Unit,
    onResetToDefault: () -> Unit
) {
    val isBlocked = op.mode.isBlocked
    val isExplicit = op.mode != AppOpMode.DEFAULT

    val icon = when (op.opCode) {
        "RUN_IN_BACKGROUND", "RUN_ANY_IN_BACKGROUND" -> Icons.Rounded.Bedtime
        "WAKE_LOCK" -> Icons.Rounded.Bolt
        "BOOT_COMPLETED" -> Icons.Rounded.PowerSettingsNew
        "READ_CLIPBOARD", "WRITE_CLIPBOARD" -> Icons.Rounded.ContentPaste
        "FINE_LOCATION", "COARSE_LOCATION" -> Icons.Rounded.LocationOn
        "RECORD_AUDIO" -> Icons.Rounded.Mic
        "CAMERA" -> Icons.Rounded.PhotoCamera
        "SYSTEM_ALERT_WINDOW" -> Icons.Rounded.Layers
        "POST_NOTIFICATION" -> Icons.Rounded.Notifications
        "VIBRATE" -> Icons.Rounded.Vibration
        "GET_USAGE_STATS" -> Icons.Rounded.QueryStats
        "PROJECT_MEDIA" -> Icons.Rounded.ScreenShare
        else -> Icons.Rounded.Security
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isBlocked) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        border = if (isBlocked) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
        } else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (isBlocked) {
                    MaterialTheme.colorScheme.errorContainer
                } else if (isExplicit) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isBlocked) {
                            MaterialTheme.colorScheme.error
                        } else if (isExplicit) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = op.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (op.isHighImpact) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.75f)
                    ) {
                        Text(
                            text = "High Impact",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            softWrap = false,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = op.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Current mode chip & reset button if modified
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when (op.mode) {
                            AppOpMode.IGNORE, AppOpMode.DENY -> MaterialTheme.colorScheme.errorContainer
                            AppOpMode.ALLOW -> MaterialTheme.colorScheme.primaryContainer
                            AppOpMode.DEFAULT -> MaterialTheme.colorScheme.surfaceContainerHighest
                        }
                    ) {
                        Text(
                            text = when (op.mode) {
                                AppOpMode.IGNORE -> "Blocked (Ignored)"
                                AppOpMode.DENY -> "Denied"
                                AppOpMode.ALLOW -> "Allowed (Explicit)"
                                AppOpMode.DEFAULT -> "System Default (Allowed)"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            fontWeight = FontWeight.Bold,
                            color = when (op.mode) {
                                AppOpMode.IGNORE, AppOpMode.DENY -> MaterialTheme.colorScheme.onErrorContainer
                                AppOpMode.ALLOW -> MaterialTheme.colorScheme.onPrimaryContainer
                                AppOpMode.DEFAULT -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            softWrap = false,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (isExplicit) {
                        Text(
                            text = "Reset to default",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            softWrap = false,
                            maxLines = 1,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onResetToDefault() }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Switch(
                checked = op.mode.isAllowedOrActive,
                onCheckedChange = { onToggle(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}
