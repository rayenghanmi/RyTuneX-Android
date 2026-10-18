package com.rayenghanmi.rytunex.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rayenghanmi.rytunex.engine.ShellEngine
import com.rayenghanmi.rytunex.theme.SemanticGreen
import com.rayenghanmi.rytunex.ui.components.GoogleSectionHeader
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TerminalScreen(
    consoleLog: String,
    onClearLogs: () -> Unit,
    onAppendLog: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var commandInput by remember { mutableStateOf("") }
    var isExecuting by remember { mutableStateOf(false) }
    val consoleScrollState = rememberScrollState()

    fun runShellCommand(cmd: String) {
        if (cmd.isBlank()) return
        scope.launch {
            isExecuting = true
            onAppendLog("\n$ $cmd")
            val res = ShellEngine.exec(cmd)
            isExecuting = false
            if (!res.isSuccess || res.exitCode != 0) {
                val errMsg = if (res.output.startsWith("[ERR]")) {
                    res.output
                } else {
                    "[ERR (Code ${res.exitCode})]: ${res.output.ifBlank { "Command returned non-zero exit status" }}"
                }
                onAppendLog(errMsg)
            } else {
                onAppendLog(res.output.ifBlank { "Command executed (Code 0)" })
            }
        }
    }

    LaunchedEffect(consoleLog) {
        consoleScrollState.animateScrollTo(consoleScrollState.maxValue)
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val greenColor = SemanticGreen

    val formattedLogs = remember(consoleLog, primaryColor, errorColor, secondaryColor, greenColor) {
        buildAnnotatedString {
            val lines = consoleLog.lines()
            lines.forEachIndexed { index, line ->
                val trimmed = line.trim()
                val styleColor = when {
                    trimmed.startsWith("$ ") || trimmed.startsWith("$") -> primaryColor
                    trimmed.startsWith("[ERR") ||
                            trimmed.contains("[ERR]") ||
                            trimmed.contains("Error:", ignoreCase = true) ||
                            trimmed.contains("Exception:", ignoreCase = true) ||
                            trimmed.contains("timed out", ignoreCase = true) ||
                            trimmed.contains("failed", ignoreCase = true) -> errorColor
                    trimmed.startsWith("===") -> secondaryColor
                    trimmed.startsWith("[") && trimmed.contains("]") -> tertiaryColor
                    else -> greenColor
                }
                withStyle(SpanStyle(color = styleColor)) {
                    append(line)
                }
                if (index < lines.size - 1) {
                    append("\n")
                }
            }
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
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Natural In-Flow Top Header (Visible at rest)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = "Privileged Shell",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Interactive Shizuku Console (UID 2000)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("RyTuneX Logs", consoleLog))
                                Toast.makeText(context, "Terminal output copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(
                                Icons.Rounded.ContentCopy,
                                contentDescription = "Copy Console Output",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(onClick = onClearLogs) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = "Clear Console",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

            // Preset Diagnostic Queries Header & Single-line Horizontal Scrollable Row
            GoogleSectionHeader(title = "PRESET DIAGNOSTIC QUERIES")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val presets = listOf(
                    "Battery" to "dumpsys battery",
                    "Display" to "dumpsys display | grep -iE 'DisplayDeviceInfo|mActiveMode|cur=|refreshRate' | head -n 8",
                    "Memory" to "dumpsys meminfo",
                    "User Apps" to "pm list packages -3",
                    "Doze State" to "dumpsys deviceidle",
                    "CPU Info" to "getprop ro.soc.model; getprop ro.board.platform; cat /proc/cpuinfo | grep -iE 'Hardware|Processor|CPU' | head -n 6"
                )

                presets.forEach { (label, cmd) ->
                    SuggestionChip(
                        onClick = { runShellCommand(cmd) },
                        label = { Text(label, fontWeight = FontWeight.SemiBold, softWrap = false, maxLines = 1) },
                        icon = { Icon(Icons.Rounded.Terminal, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Terminal Console Window Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp)
                            .verticalScroll(consoleScrollState)
                    ) {
                        Text(
                            text = formattedLogs,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // Command Input Bar (Pill Shape)
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp).size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    TextField(
                        value = commandInput,
                        onValueChange = { commandInput = it },
                        placeholder = {
                            Text(
                                "Enter shell command (e.g. getprop)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    FilledIconButton(
                        onClick = {
                            val cmd = commandInput.trim()
                            commandInput = ""
                            runShellCommand(cmd)
                        },
                        enabled = !isExecuting && commandInput.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(40.dp)
                    ) {
                        if (isExecuting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Rounded.ArrowUpward, contentDescription = "Execute Command", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.navigationBarsPadding().height(130.dp))
        }
    }
}
}
