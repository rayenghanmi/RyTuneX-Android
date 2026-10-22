package com.rayenghanmi.rytunex.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rayenghanmi.rytunex.engine.AppIconManager
import com.rayenghanmi.rytunex.engine.DebloatAction
import com.rayenghanmi.rytunex.engine.SettingsManager
import com.rayenghanmi.rytunex.engine.UadDatabaseManager
import com.rayenghanmi.rytunex.model.DebloatApp
import com.rayenghanmi.rytunex.model.UadRecommendation
import com.rayenghanmi.rytunex.theme.SemanticGreen
import com.rayenghanmi.rytunex.theme.SemanticPurple
import com.rayenghanmi.rytunex.theme.SemanticRed
import com.rayenghanmi.rytunex.ui.components.Button
import com.rayenghanmi.rytunex.ui.components.FilledTonalButton
import com.rayenghanmi.rytunex.ui.components.OutlinedButton
import com.rayenghanmi.rytunex.ui.components.TextButton
import com.rayenghanmi.rytunex.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Extracts valid Android package names from arbitrary text (plain text, JSON, scripts, etc.)
fun extractPackageNames(text: String): List<String> {
    val regex = Regex("""[a-zA-Z][a-zA-Z0-9_]*(?:\.[a-zA-Z0-9_]+)+""")
    return regex.findAll(text)
        .map { it.value.trim() }
        .distinct()
        .toList()
}

@Composable
fun DebloatAppIcon(
    packageName: String,
    appName: String,
    badgeColor: Color,
    modifier: Modifier = Modifier.size(42.dp)
) {
    val context = LocalContext.current
    var iconBitmap by remember(packageName) {
        mutableStateOf(AppIconManager.getCached(packageName))
    }

    LaunchedEffect(packageName) {
        if (iconBitmap == null) {
            val loaded = AppIconManager.loadIcon(context, packageName)
            if (loaded != null) {
                iconBitmap = loaded
            }
        }
    }

    val currentIcon = iconBitmap
    if (currentIcon != null) {
        Image(
            bitmap = currentIcon,
            contentDescription = null,
            modifier = modifier.clip(RoundedCornerShape(12.dp))
        )
    } else {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = modifier
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = appName.firstOrNull()?.uppercase() ?: "A",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DebloatScreen(
    onLogUpdate: (String) -> Unit,
    isScreenVisible: Boolean = true,
    isShizukuReady: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val apps = UadDatabaseManager.cachedApps ?: emptyList()
    val isScanning = UadDatabaseManager.isScanning
    var selectedPackageNames by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isUpdatingUad by remember { mutableStateOf(false) }
    var isExecutingBatch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf("recommended") }

    var inspectingApp by remember { mutableStateOf<DebloatApp?>(null) }
    var pendingBatchConfirmAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingBatchConfirmTitle by remember { mutableStateOf("") }
    var pendingBatchConfirmMessage by remember { mutableStateOf("") }
    var pendingUninstallApp by remember { mutableStateOf<DebloatApp?>(null) }

    var showExportDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var exportTarget by remember { mutableStateOf("debloated") } // "debloated" or "selected"
    var importInputText by remember { mutableStateOf("") }

    val debloatedApps = remember(apps) { apps.filter { !it.isEnabled || !it.isInstalled } }
    val debloatedPackageNames = remember(debloatedApps) { debloatedApps.map { it.packageName }.sorted() }

    fun copyTextToClipboard(text: String, message: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("RyTuneX Debloat Preset", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Copy failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareTextToApps(text: String, title: String) {
        try {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, text)
                type = "text/plain"
            }
            val shareIntent = Intent.createChooser(sendIntent, title).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(shareIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun getFromClipboard(): String {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = clipboard?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).text?.toString() ?: ""
            } else {
                ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    val saveDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            val packagesToExport = if (exportTarget == "selected" && selectedPackageNames.isNotEmpty()) {
                selectedPackageNames.toList().sorted()
            } else {
                debloatedPackageNames
            }
            val content = "# RyTuneX Debloat Preset (${packagesToExport.size} packages)\n" + packagesToExport.joinToString("\n")
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(content.toByteArray(Charsets.UTF_8))
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Saved debloat preset file", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val content = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    } ?: ""
                    withContext(Dispatchers.Main) {
                        if (content.isNotBlank()) {
                            importInputText = content
                            Toast.makeText(context, "Loaded debloat preset file", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Selected preset file is empty", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to read file: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun runBatchWithConfirmation(actionName: String, count: Int, isDestructive: Boolean = false, action: () -> Unit) {
        if (isDestructive || SettingsManager.confirmTweaks) {
            pendingBatchConfirmTitle = "Confirm Batch $actionName"
            pendingBatchConfirmMessage = "Are you sure you want to $actionName $count selected packages? ${if (isDestructive) "Uninstalled packages can only be reinstalled via package manager if preserved in user 0." else ""}"
            pendingBatchConfirmAction = action
        } else {
            action()
        }
    }

    fun refreshApps(force: Boolean = true) {
        scope.launch {
            val scanned = UadDatabaseManager.scanInstalledApps(context, forceRefresh = force)
            AppIconManager.preloadIcons(context, scanned.map { it.packageName })
        }
    }

    fun executeBatchFreeze(packagesToTarget: Collection<String> = selectedPackageNames) {
        val list = packagesToTarget.toList()
        if (list.isEmpty()) return
        val count = list.size
        runBatchWithConfirmation("Freeze", count) {
            scope.launch {
                isExecutingBatch = true
                val res = UadDatabaseManager.batchFreeze(list)
                isExecutingBatch = false
                val isSuccess = res.isOverallSuccess || res.successCount > 0
                if (isSuccess) {
                    Toast.makeText(context, res.getSummary("Freeze"), Toast.LENGTH_SHORT).show()
                } else {
                    val err = res.commandResult.stderr.ifBlank { res.commandResult.stdout }.ifBlank { "Exit code ${res.commandResult.exitCode}" }
                    Toast.makeText(context, "Batch freeze failed: $err", Toast.LENGTH_LONG).show()
                }
                onLogUpdate("[BATCH FREEZE]: ${res.getSummary("Freeze")}\n${res.commandResult.output}")
                selectedPackageNames = emptySet()
                refreshApps()
            }
        }
    }

    fun executeBatchUninstall(packagesToTarget: Collection<String> = selectedPackageNames) {
        val list = packagesToTarget.toList()
        if (list.isEmpty()) return
        val count = list.size
        runBatchWithConfirmation("Uninstall", count, isDestructive = true) {
            scope.launch {
                isExecutingBatch = true
                val res = UadDatabaseManager.batchUninstall(list)
                isExecutingBatch = false
                val isSuccess = res.isOverallSuccess || res.successCount > 0
                if (isSuccess) {
                    Toast.makeText(context, res.getSummary("Uninstall"), Toast.LENGTH_SHORT).show()
                } else {
                    val err = res.commandResult.stderr.ifBlank { res.commandResult.stdout }.ifBlank { "Exit code ${res.commandResult.exitCode}" }
                    Toast.makeText(context, "Batch uninstall failed: $err", Toast.LENGTH_LONG).show()
                }
                onLogUpdate("[BATCH UNINSTALL]: ${res.getSummary("Uninstall")}\n${res.commandResult.output}")
                selectedPackageNames = emptySet()
                refreshApps()
            }
        }
    }

    fun executeBatchRestore(packagesToTarget: Collection<String> = selectedPackageNames) {
        val list = packagesToTarget.toList()
        if (list.isEmpty()) return
        val count = list.size
        runBatchWithConfirmation("Restore", count) {
            scope.launch {
                isExecutingBatch = true
                val res = UadDatabaseManager.batchRestore(list)
                isExecutingBatch = false
                val isSuccess = res.isOverallSuccess || res.successCount > 0
                if (isSuccess) {
                    Toast.makeText(context, res.getSummary("Restore"), Toast.LENGTH_SHORT).show()
                } else {
                    val err = res.commandResult.stderr.ifBlank { res.commandResult.stdout }.ifBlank { "Exit code ${res.commandResult.exitCode}" }
                    Toast.makeText(context, "Batch restore failed: $err", Toast.LENGTH_LONG).show()
                }
                onLogUpdate("[BATCH RESTORE]: ${res.getSummary("Restore")}\n${res.commandResult.output}")
                selectedPackageNames = emptySet()
                refreshApps()
            }
        }
    }

    // Rescan when Shizuku grant/connect state changes
    LaunchedEffect(isShizukuReady) {
        if (isShizukuReady) {
            refreshApps(force = true)
        }
    }

    // Rescan on first visibility if the previous scan was unprivileged
    LaunchedEffect(isScreenVisible) {
        if (isScreenVisible && (UadDatabaseManager.cachedApps == null || !UadDatabaseManager.hasPrivilegedScan)) {
            refreshApps(force = false)
        }
    }

    // Preload icons eagerly so scrolling feels instant
    LaunchedEffect(Unit) {
        if ((UadDatabaseManager.cachedApps == null || UadDatabaseManager.cachedApps?.isEmpty() == true) && !UadDatabaseManager.isScanning) {
            refreshApps(force = false)
        }
    }

    LaunchedEffect(apps) {
        if (apps.isNotEmpty()) {
            AppIconManager.preloadIcons(context, apps.map { it.packageName })
        }
    }

    val filteredApps = remember(apps, searchQuery, activeFilter) {
        apps.filter { app ->
            val matchesQuery = searchQuery.isBlank() ||
                    app.appName.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (activeFilter) {
                "recommended" -> app.recommendation == UadRecommendation.RECOMMENDED && app.isEnabled && app.isSystem && app.isInstalled
                "user" -> !app.isSystem && app.isInstalled && app.isEnabled
                "advanced" -> app.recommendation == UadRecommendation.ADVANCED && app.isEnabled && app.isInstalled
                "expert" -> app.recommendation == UadRecommendation.EXPERT && app.isEnabled && app.isInstalled
                "unsafe" -> app.recommendation == UadRecommendation.UNSAFE && app.isEnabled && app.isInstalled
                "frozen" -> !app.isEnabled || !app.isInstalled
                "all" -> app.isEnabled && app.isInstalled
                else -> app.isEnabled && app.isInstalled
            }

            matchesQuery && matchesFilter
        }
    }

    val recommendedCount = remember(apps) { apps.count { it.recommendation == UadRecommendation.RECOMMENDED && it.isEnabled && it.isSystem && it.isInstalled } }
    val userCount = remember(apps) { apps.count { !it.isSystem && it.isInstalled && it.isEnabled } }
    val advancedCount = remember(apps) { apps.count { it.recommendation == UadRecommendation.ADVANCED && it.isEnabled && it.isInstalled } }
    val expertCount = remember(apps) { apps.count { it.recommendation == UadRecommendation.EXPERT && it.isEnabled && it.isInstalled } }
    val unsafeCount = remember(apps) { apps.count { it.recommendation == UadRecommendation.UNSAFE && it.isEnabled && it.isInstalled } }
    val frozenCount = remember(apps) { apps.count { !it.isEnabled || !it.isInstalled } }
    val allActiveCount = remember(apps) { apps.count { it.isEnabled && it.isInstalled } }

    val hasDebloatedSelected = remember(selectedPackageNames, apps) {
        selectedPackageNames.any { pkg ->
            val app = apps.firstOrNull { it.packageName == pkg }
            app != null && (!app.isEnabled || !app.isInstalled)
        }
    }

    val listState = rememberLazyListState()

    val onToggleSelect = remember {
        { pkgName: String, checked: Boolean ->
            selectedPackageNames = if (checked) {
                selectedPackageNames + pkgName
            } else {
                selectedPackageNames - pkgName
            }
        }
    }

    val onClickInspect = remember {
        { targetApp: DebloatApp ->
            inspectingApp = targetApp
        }
    }

    val isScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 40
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = if (selectedPackageNames.isNotEmpty()) 200.dp else 145.dp)
            ) {
                // Header (scrolls away with content)
                item(key = "header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = "Debloat",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isScanning) "Scanning packages..." else "${apps.size} packages analyzed • Advanced Package Control",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                importInputText = ""
                                showImportDialog = true
                            }
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = "Import Preset")
                        }

                        IconButton(
                            onClick = {
                                val debloatedCount = apps.count { !it.isEnabled || !it.isInstalled }
                                exportTarget = if (debloatedCount > 0) "debloated" else "selected"
                                showExportDialog = true
                            }
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = "Share / Save Debloated")
                        }

                        IconButton(
                            onClick = {
                                scope.launch {
                                    isUpdatingUad = true
                                    val res = UadDatabaseManager.updateFromGitHub(context)
                                    isUpdatingUad = false
                                    if (res.isSuccess) {
                                        Toast.makeText(context, "UAD updated: ${res.getOrNull()} entries", Toast.LENGTH_SHORT).show()
                                        refreshApps()
                                    } else {
                                        Toast.makeText(context, "Update failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        ) {
                            if (isUpdatingUad) {
                                WavyLoadingIndicator(indicatorSize = 20.dp, stroke = 2.dp, amplitude = 1.25.dp)
                            } else {
                                Icon(Icons.Rounded.CloudDownload, contentDescription = "Update UAD Database")
                            }
                        }

                        IconButton(onClick = { refreshApps() }) {
                            if (isScanning) {
                                WavyLoadingIndicator(indicatorSize = 20.dp, stroke = 2.dp, amplitude = 1.25.dp)
                            } else {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh Packages")
                            }
                        }
                    }
                }
            }

            // Search bar
            item(key = "search") {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    "Search app name or package...",
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
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // Filter chips
            item(key = "filters") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = activeFilter == "recommended",
                        onClick = { activeFilter = "recommended" },
                        label = { Text("Recommended ($recommendedCount)", fontWeight = FontWeight.SemiBold, softWrap = false, maxLines = 1) },
                        shape = RoundedCornerShape(morphingChipCorner(activeFilter == "recommended"))
                    )

                    FilterChip(
                        selected = activeFilter == "user",
                        onClick = { activeFilter = "user" },
                        label = { Text("User Apps ($userCount)", fontWeight = FontWeight.SemiBold, softWrap = false, maxLines = 1) },
                        shape = RoundedCornerShape(morphingChipCorner(activeFilter == "user"))
                    )

                    FilterChip(
                        selected = activeFilter == "frozen",
                        onClick = { activeFilter = "frozen" },
                        label = { Text("Debloated ($frozenCount)", fontWeight = FontWeight.SemiBold, softWrap = false, maxLines = 1) },
                        shape = RoundedCornerShape(morphingChipCorner(activeFilter == "frozen"))
                    )

                    FilterChip(
                        selected = activeFilter == "advanced",
                        onClick = { activeFilter = "advanced" },
                        label = { Text("Advanced ($advancedCount)", softWrap = false, maxLines = 1) },
                        shape = RoundedCornerShape(morphingChipCorner(activeFilter == "advanced"))
                    )

                    FilterChip(
                        selected = activeFilter == "expert",
                        onClick = { activeFilter = "expert" },
                        label = { Text("Expert ($expertCount)", softWrap = false, maxLines = 1) },
                        shape = RoundedCornerShape(morphingChipCorner(activeFilter == "expert"))
                    )

                    FilterChip(
                        selected = activeFilter == "unsafe",
                        onClick = { activeFilter = "unsafe" },
                        label = { Text("Unsafe ($unsafeCount)", softWrap = false, maxLines = 1) },
                        shape = RoundedCornerShape(morphingChipCorner(activeFilter == "unsafe"))
                    )

                    FilterChip(
                        selected = activeFilter == "all",
                        onClick = { activeFilter = "all" },
                        label = { Text("All ($allActiveCount)", softWrap = false, maxLines = 1) },
                        shape = RoundedCornerShape(morphingChipCorner(activeFilter == "all"))
                    )
                }
            }

            // Preset summary card (Debloated tab only)
            if (activeFilter == "frozen" && frozenCount > 0) {
                item(key = "debloated_preset_banner") {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Debloated Preset ($frozenCount)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "Share, save, or copy debloated apps",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilledTonalButton(
                                    onClick = {
                                        exportTarget = "debloated"
                                        showExportDialog = true
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Share", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = {
                                        val debloatedList = apps.filter { !it.isEnabled || !it.isInstalled }.map { it.packageName }
                                        val text = "# RyTuneX Debloat Preset (${debloatedList.size} packages)\n" + debloatedList.sorted().joinToString("\n")
                                        copyTextToClipboard(text, "Copied ${debloatedList.size} debloated packages")
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Copy", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Package list
            if (isScanning && apps.isEmpty()) {
                item(key = "loading_indicator") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            WavyLoadingIndicator(indicatorSize = 36.dp, stroke = 3.dp, amplitude = 2.5.dp)
                            Text(
                                text = "Analyzing device packages...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else if (filteredApps.isEmpty()) {
                item(key = "empty") {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = SemanticGreen,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = when (activeFilter) {
                                    "recommended" -> "No Bloatware Found"
                                    "frozen" -> "No Debloated Apps"
                                    "user" -> "No User Installed Apps"
                                    else -> "No Packages Found"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = when (activeFilter) {
                                    "recommended" -> "Your device is free of recommended bloatware packages! Switch to 'All' or 'User Apps' to manage other applications."
                                    "frozen" -> "You have not frozen or uninstalled any packages yet."
                                    "user" -> "No third-party user installed apps found on this device."
                                    else -> "Your device has no installed packages matching this filter."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (activeFilter == "frozen") {
                                Spacer(modifier = Modifier.height(10.dp))
                                FilledTonalButton(
                                    onClick = {
                                        importInputText = ""
                                        showImportDialog = true
                                    },
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Import Debloat Preset", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            } else {
                items(
                    items = filteredApps,
                    key = { it.packageName },
                    contentType = { "debloat_app" }
                ) { app ->
                    val isSelected = selectedPackageNames.contains(app.packageName)
                    Box(modifier = Modifier.animateItem()) {
                        DebloatAppCard(
                            app = app,
                            isSelected = isSelected,
                            onToggleSelect = onToggleSelect,
                            onClickInspect = onClickInspect
                        )
                    }
                }
            }
        }


            // Floating batch action bar
            AnimatedVisibility(
                visible = selectedPackageNames.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 96.dp, start = 16.dp, end = 16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column {
                            Text(
                                text = "${selectedPackageNames.size} Selected",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                softWrap = false,
                                maxLines = 1
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Tap to Clear",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    softWrap = false,
                                    maxLines = 1,
                                    modifier = Modifier.clickable { selectedPackageNames = emptySet() }
                                )
                                Text("•", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "Share",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    softWrap = false,
                                    maxLines = 1,
                                    modifier = Modifier.clickable {
                                        exportTarget = "selected"
                                        showExportDialog = true
                                    }
                                )
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val defaultDebloat = SettingsManager.defaultDebloatAction

                            if (!hasDebloatedSelected) {
                                if (defaultDebloat == DebloatAction.FREEZE) {
                                    Button(
                                        onClick = { executeBatchFreeze() },
                                        enabled = !isExecutingBatch,
                                        shape = RoundedCornerShape(14.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Text("Freeze (Default)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, softWrap = false, maxLines = 1)
                                    }

                                    FilledTonalButton(
                                        onClick = { executeBatchUninstall() },
                                        enabled = !isExecutingBatch,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                                        ),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Text("Uninstall", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, softWrap = false, maxLines = 1)
                                    }
                                } else {
                                    Button(
                                        onClick = { executeBatchUninstall() },
                                        enabled = !isExecutingBatch,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error,
                                            contentColor = MaterialTheme.colorScheme.onError
                                        ),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Text("Uninstall (Default)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, softWrap = false, maxLines = 1)
                                    }

                                    FilledTonalButton(
                                        onClick = { executeBatchFreeze() },
                                        enabled = !isExecutingBatch,
                                        shape = RoundedCornerShape(14.dp),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Text("Freeze", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, softWrap = false, maxLines = 1)
                                    }
                                }
                            } else {
                                Button(
                                    onClick = { executeBatchFreeze() },
                                    enabled = !isExecutingBatch,
                                    shape = RoundedCornerShape(14.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text("Freeze", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, softWrap = false, maxLines = 1)
                                }
                            }

                            FilledTonalButton(
                                onClick = { executeBatchRestore() },
                                enabled = !isExecutingBatch,
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text("Restore", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, softWrap = false, maxLines = 1)
                            }
                        }
                    }
                }
            }

        // Floating header pill (visible when scrolled)
        FloatingTopPillHeader(
            visible = isScrolled,
            title = "Debloat",
            onClick = {
                scope.launch { listState.animateScrollToItem(0) }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            importInputText = ""
                            showImportDialog = true
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Rounded.Download, contentDescription = "Import Preset", modifier = Modifier.size(20.dp))
                    }

                    IconButton(
                        onClick = {
                            val debloatedCount = apps.count { !it.isEnabled || !it.isInstalled }
                            exportTarget = if (debloatedCount > 0) "debloated" else "selected"
                            showExportDialog = true
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = "Share Debloated", modifier = Modifier.size(20.dp))
                    }

                    IconButton(
                        onClick = {
                            scope.launch {
                                isUpdatingUad = true
                                val res = UadDatabaseManager.updateFromGitHub(context)
                                isUpdatingUad = false
                                if (res.isSuccess) {
                                    Toast.makeText(context, "UAD updated: ${res.getOrNull()} entries", Toast.LENGTH_SHORT).show()
                                    refreshApps()
                                } else {
                                    Toast.makeText(context, "Update failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        if (isUpdatingUad) {
                            WavyLoadingIndicator(indicatorSize = 18.dp, stroke = 2.dp, amplitude = 1.dp)
                        } else {
                            Icon(Icons.Rounded.CloudDownload, contentDescription = "Update UAD Database", modifier = Modifier.size(20.dp))
                        }
                    }

                    IconButton(
                        onClick = { refreshApps() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        if (isScanning) {
                            WavyLoadingIndicator(indicatorSize = 18.dp, stroke = 2.dp, amplitude = 1.dp)
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh Packages", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        )
    }

    // Inspection dialog
    inspectingApp?.let { app ->
        ExpressiveAlertDialog(
            onDismissRequest = { inspectingApp = null },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DebloatAppIcon(
                        packageName = app.packageName,
                        appName = app.appName,
                        badgeColor = app.recommendation.badgeColor,
                        modifier = Modifier.size(44.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = app.appName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = app.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = app.recommendation.badgeColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Safety: ${app.recommendation.displayName}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = app.recommendation.badgeColor,
                                softWrap = false,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (!app.isSystem) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = if (!app.isSystem) "Type: User App" else "Type: System OEM",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (!app.isSystem) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                                softWrap = false,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = "Category: ${app.listCategory}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                softWrap = false,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Text(
                        text = app.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (app.uadEntry?.dependencies?.isNotEmpty() == true) {
                        Text(
                            text = "Dependencies: ${app.uadEntry.dependencies.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { inspectingApp = null }) {
                    Text("Close", softWrap = false, maxLines = 1)
                }
            },
            dismissButton = {
                val defaultAction = SettingsManager.defaultDebloatAction
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (app.isEnabled && app.isInstalled) {
                        if (defaultAction == DebloatAction.UNINSTALL) {
                            FilledTonalButton(
                                onClick = {
                                    val target = app
                                    inspectingApp = null
                                    pendingUninstallApp = target
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) {
                                Text("Uninstall (Default)", softWrap = false, maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = {
                                    val targetPkg = app.packageName
                                    val appName = app.appName
                                    inspectingApp = null
                                    scope.launch {
                                        val res = UadDatabaseManager.freezeApp(targetPkg)
                                        val isSuccess = res.isSuccess && !res.stdout.contains("Failure", ignoreCase = true)
                                        if (isSuccess) {
                                            Toast.makeText(context, "Froze $appName", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val err = res.stderr.ifBlank { res.stdout }.ifBlank { "Exit code ${res.exitCode}" }
                                            Toast.makeText(context, "Failed to freeze $appName: $err", Toast.LENGTH_LONG).show()
                                        }
                                        onLogUpdate("[FREEZE]: ${res.output}")
                                        refreshApps()
                                    }
                                },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Freeze", softWrap = false, maxLines = 1)
                            }
                        } else {
                            FilledTonalButton(
                                onClick = {
                                    val targetPkg = app.packageName
                                    val appName = app.appName
                                    inspectingApp = null
                                    scope.launch {
                                        val res = UadDatabaseManager.freezeApp(targetPkg)
                                        val isSuccess = res.isSuccess && !res.stdout.contains("Failure", ignoreCase = true)
                                        if (isSuccess) {
                                            Toast.makeText(context, "Froze $appName", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val err = res.stderr.ifBlank { res.stdout }.ifBlank { "Exit code ${res.exitCode}" }
                                            Toast.makeText(context, "Failed to freeze $appName: $err", Toast.LENGTH_LONG).show()
                                        }
                                        onLogUpdate("[FREEZE]: ${res.output}")
                                        refreshApps()
                                    }
                                },
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Freeze (Default)", softWrap = false, maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = {
                                    val target = app
                                    inspectingApp = null
                                    pendingUninstallApp = target
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Uninstall", softWrap = false, maxLines = 1)
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                val targetPkg = app.packageName
                                val appName = app.appName
                                inspectingApp = null
                                scope.launch {
                                    val res = UadDatabaseManager.restoreApp(targetPkg)
                                    val isSuccess = res.isSuccess && !res.stdout.contains("Failure", ignoreCase = true)
                                    if (isSuccess) {
                                        Toast.makeText(context, "Restored $appName", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val err = res.stderr.ifBlank { res.stdout }.ifBlank { "Exit code ${res.exitCode}" }
                                        Toast.makeText(context, "Failed to restore $appName: $err", Toast.LENGTH_LONG).show()
                                    }
                                    onLogUpdate("[RESTORE]: ${res.output}")
                                    refreshApps()
                                }
                            },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Restore", softWrap = false, maxLines = 1)
                        }
                    }
                }
            }
        )
    }

    // Single app uninstall confirmation dialog
    pendingUninstallApp?.let { app ->
        ExpressiveAlertDialog(
            onDismissRequest = { pendingUninstallApp = null },
            icon = { Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Confirm Uninstallation", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Are you sure you want to uninstall ${app.appName} (${app.packageName}) for User 0?\n\nThis package can be restored at any time from the Debloated tab."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val targetPkg = app.packageName
                        val appName = app.appName
                        pendingUninstallApp = null
                        scope.launch {
                            val res = UadDatabaseManager.uninstallApp(targetPkg)
                            val isSuccess = res.isSuccess && !res.stdout.contains("Failure", ignoreCase = true)
                            if (isSuccess) {
                                Toast.makeText(context, "Successfully uninstalled $appName", Toast.LENGTH_SHORT).show()
                            } else {
                                val err = res.stderr.ifBlank { res.stdout }.ifBlank { "Exit code ${res.exitCode}" }
                                Toast.makeText(context, "Failed to uninstall $appName: $err", Toast.LENGTH_LONG).show()
                            }
                            onLogUpdate("[UNINSTALL]: ${res.output}")
                            refreshApps()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Uninstall", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingUninstallApp = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Batch action confirmation dialog
    pendingBatchConfirmAction?.let { action ->
        ExpressiveAlertDialog(
            onDismissRequest = { pendingBatchConfirmAction = null },
            icon = { Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(pendingBatchConfirmTitle, fontWeight = FontWeight.Bold) },
            text = { Text(pendingBatchConfirmMessage) },
            confirmButton = {
                Button(
                    onClick = {
                        val act = action
                        pendingBatchConfirmAction = null
                        act()
                    }
                ) {
                    Text("Confirm & Execute", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingBatchConfirmAction = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Export / share preset dialog
    if (showExportDialog) {
        val debloatedPackages = remember(apps) {
            apps.filter { !it.isEnabled || !it.isInstalled }.map { it.packageName }.sorted()
        }
        val selectedPackages = remember(selectedPackageNames) {
            selectedPackageNames.toList().sorted()
        }

        val activeList = if (exportTarget == "selected" && selectedPackages.isNotEmpty()) {
            selectedPackages
        } else {
            debloatedPackages
        }

        val exportText = remember(activeList) {
            "# RyTuneX Debloat Preset (${activeList.size} packages)\n" + activeList.joinToString("\n")
        }

        ExpressiveAlertDialog(
            onDismissRequest = { showExportDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Share Debloat Preset",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (debloatedPackages.isEmpty() && selectedPackages.isEmpty()) {
                        Text(
                            text = "No debloated (frozen or uninstalled) packages found, and no packages are currently selected.\n\nFreeze or uninstall apps, or select packages in the list to export and share them.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        if (debloatedPackages.isNotEmpty() && selectedPackages.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = exportTarget == "debloated",
                                    onClick = { exportTarget = "debloated" },
                                    label = { Text("Debloated (${debloatedPackages.size})") },
                                    shape = RoundedCornerShape(10.dp)
                                )
                                FilterChip(
                                    selected = exportTarget == "selected",
                                    onClick = { exportTarget = "selected" },
                                    label = { Text("Selected (${selectedPackages.size})") },
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }
                        }

                        Text(
                            text = "${activeList.size} package names ready to copy, share, or save:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp)
                        ) {
                            Box(modifier = Modifier.padding(10.dp).verticalScroll(rememberScrollState())) {
                                Text(
                                    text = activeList.joinToString("\n"),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Copy and save actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    copyTextToClipboard(exportText, "Copied ${activeList.size} package names")
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy Text", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }

                            FilledTonalButton(
                                onClick = {
                                    saveDocumentLauncher.launch("rytunex_debloat_preset.txt")
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save File", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (activeList.isNotEmpty()) {
                    Button(
                        onClick = {
                            shareTextToApps(exportText, "Share RyTuneX Debloat Preset")
                        },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share...", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Import preset dialog
    if (showImportDialog) {
        val detectedPackages = remember(importInputText) {
            extractPackageNames(importInputText)
        }
        val packageAnalysis = remember(detectedPackages, apps) {
            val appMap = apps.associateBy { it.packageName }
            val presentToDebloat = mutableListOf<DebloatApp>()
            val alreadyDebloated = mutableListOf<DebloatApp>()
            val notOnDevice = mutableListOf<String>()

            for (pkg in detectedPackages) {
                val app = appMap[pkg]
                if (app == null) {
                    notOnDevice.add(pkg)
                } else if (!app.isEnabled || !app.isInstalled) {
                    alreadyDebloated.add(app)
                } else {
                    presentToDebloat.add(app)
                }
            }
            Triple(presentToDebloat, alreadyDebloated, notOnDevice)
        }
        val presentToDebloat = packageAnalysis.first
        val alreadyDebloated = packageAnalysis.second
        val notOnDevice = packageAnalysis.third

        ExpressiveAlertDialog(
            onDismissRequest = { showImportDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Import Debloat Preset",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Paste package names or open a preset file shared from another phone. Packages not present on this device are automatically ignored so presets can be shared universally.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = importInputText,
                        onValueChange = { importInputText = it },
                        placeholder = {
                            Text(
                                "Paste package names here...\ne.g.\ncom.facebook.katana\ncom.google.android.youtube",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        },
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = {
                                val clip = getFromClipboard()
                                if (clip.isNotBlank()) {
                                    importInputText = clip
                                    Toast.makeText(context, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Rounded.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Paste", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }

                        FilledTonalButton(
                            onClick = {
                                openDocumentLauncher.launch(arrayOf("text/plain", "*/*"))
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Rounded.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open File", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }

                        if (importInputText.isNotEmpty()) {
                            Spacer(modifier = Modifier.weight(1f))
                            TextButton(
                                onClick = { importInputText = "" },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Clear")
                            }
                        }
                    }

                    if (importInputText.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (detectedPackages.isEmpty()) {
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (detectedPackages.isEmpty()) {
                                    Text(
                                        text = "No valid Android package names detected in pasted text.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.CheckCircle,
                                            contentDescription = null,
                                            tint = SemanticGreen,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Detected ${detectedPackages.size} packages in preset",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Text(
                                        text = "• ${presentToDebloat.size} installed & active on this device" +
                                                (if (alreadyDebloated.isNotEmpty()) "\n• ${alreadyDebloated.size} already debloated on this device" else "") +
                                                (if (notOnDevice.isNotEmpty()) "\n• ${notOnDevice.size} not present on this phone (will be ignored)" else ""),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (notOnDevice.isNotEmpty()) {
                                        Text(
                                            text = "Packages from other devices that are not installed here are safely ignored without aborting.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (presentToDebloat.isNotEmpty()) {
                        val defaultDebloat = SettingsManager.defaultDebloatAction
                        val debloatActionLabel = if (defaultDebloat == DebloatAction.FREEZE) "Freeze" else "Uninstall"

                        FilledTonalButton(
                            onClick = {
                                val toSelect = presentToDebloat.map { it.packageName }.toSet()
                                selectedPackageNames = selectedPackageNames + toSelect
                                activeFilter = "all"
                                showImportDialog = false
                                Toast.makeText(
                                    context,
                                    "Selected ${presentToDebloat.size} apps from preset" + (if (notOnDevice.isNotEmpty()) " (${notOnDevice.size} not found ignored)" else ""),
                                    Toast.LENGTH_SHORT
                                ).show()
                                scope.launch { listState.animateScrollToItem(0) }
                            },
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text = "Select (${presentToDebloat.size})",
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                val targetPkgs = presentToDebloat.map { it.packageName }
                                showImportDialog = false
                                if (defaultDebloat == DebloatAction.FREEZE) {
                                    executeBatchFreeze(targetPkgs)
                                } else {
                                    executeBatchUninstall(targetPkgs)
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = if (defaultDebloat == DebloatAction.UNINSTALL) {
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            } else {
                                ButtonDefaults.buttonColors()
                            }
                        ) {
                            Text(
                                text = "$debloatActionLabel (${presentToDebloat.size})",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Button(
                            onClick = {},
                            enabled = false,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(
                                text = if (detectedPackages.isEmpty()) "No Apps Detected" else if (alreadyDebloated.isNotEmpty()) "All Already Debloated" else "No Matching Apps",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
}


@Composable
private fun DebloatAppCard(
    app: DebloatApp,
    isSelected: Boolean,
    onToggleSelect: (String, Boolean) -> Unit,
    onClickInspect: (DebloatApp) -> Unit
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        border = if (isSelected) BorderStroke(1.5.dp, borderColor) else null,
        tonalElevation = if (isSelected) 3.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClickInspect(app) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { checked -> onToggleSelect(app.packageName, checked) },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary
                )
            )

            Spacer(modifier = Modifier.width(4.dp))

            DebloatAppIcon(
                packageName = app.packageName,
                appName = app.appName,
                badgeColor = app.recommendation.badgeColor,
                modifier = Modifier.size(44.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(app.recommendation.badgeColor.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = app.recommendation.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = app.recommendation.badgeColor,
                            softWrap = false,
                            maxLines = 1
                        )
                    }

                    if (!app.isSystem) {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "USER APP",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                softWrap = false,
                                maxLines = 1
                            )
                        }
                    }

                    if (!app.isEnabled || !app.isInstalled) {
                        Box(
                            modifier = Modifier
                                .background(SemanticPurple.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (!app.isInstalled) "UNINSTALLED" else "FROZEN",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = SemanticPurple,
                                softWrap = false,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = "Details",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp)
            )
        }
    }
}

