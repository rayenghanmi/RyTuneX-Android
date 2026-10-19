package com.rayenghanmi.rytunex.engine

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rayenghanmi.rytunex.model.DebloatApp
import com.rayenghanmi.rytunex.model.UadPackageEntry
import com.rayenghanmi.rytunex.model.UadRecommendation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class BatchActionResult(
    val commandResult: CommandResult,
    val totalCount: Int,
    val successCount: Int,
    val ignoredNotFoundCount: Int,
    val failedCount: Int
) {
    val isOverallSuccess: Boolean
        get() = commandResult.isSuccess && failedCount == 0 && (successCount > 0 || (totalCount > 0 && ignoredNotFoundCount == totalCount))

    fun getSummary(actionName: String): String {
        val verb = when (actionName.lowercase()) {
            "freeze" -> "froze"
            "uninstall" -> "uninstalled"
            "restore" -> "restored"
            else -> "${actionName.lowercase()}d"
        }
        return when {
            successCount > 0 && ignoredNotFoundCount > 0 ->
                "Successfully $verb $successCount packages ($ignoredNotFoundCount not found, ignored)"
            successCount > 0 && ignoredNotFoundCount == 0 && failedCount == 0 ->
                "Successfully $verb $successCount packages"
            successCount > 0 && failedCount > 0 ->
                "${verb.replaceFirstChar { it.uppercase() }} $successCount packages ($failedCount failed, $ignoredNotFoundCount ignored)"
            successCount == 0 && ignoredNotFoundCount > 0 && failedCount == 0 ->
                "All $ignoredNotFoundCount packages are not present on this device (ignored)"
            totalCount == 0 ->
                "No packages selected"
            else ->
                "Batch $actionName failed (${commandResult.stderr.ifBlank { commandResult.stdout }.take(60)})"
        }
    }
}

object UadDatabaseManager {

    private const val UAD_REMOTE_URL =
        "https://raw.githubusercontent.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/main/resources/assets/uad_lists.json"
    private const val CACHED_UAD_FILE = "uad_lists.json"

    private var uadDatabase: Map<String, UadPackageEntry> = emptyMap()
    private var isLoaded = false

    suspend fun loadDatabase(context: Context, forceDownload: Boolean = true): Int = withContext(Dispatchers.IO) {
        if (forceDownload) {
            try {
                val url = URL(UAD_REMOTE_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 4000
                connection.readTimeout = 6000
                connection.requestMethod = "GET"
                connection.connect()

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val tempFile = File(context.filesDir, "$CACHED_UAD_FILE.tmp")
                    connection.inputStream.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (tempFile.length() > 1000) {
                        val targetFile = File(context.filesDir, CACHED_UAD_FILE)
                        if (targetFile.exists()) targetFile.delete()
                        tempFile.renameTo(targetFile)
                    } else {
                        tempFile.delete()
                    }
                }
            } catch (_: Exception) {
                // Network unavailable or timed out: fall back smoothly to cached or bundled database
            }
        } else if (isLoaded && uadDatabase.isNotEmpty()) {
            return@withContext uadDatabase.size
        }

        val map = mutableMapOf<String, UadPackageEntry>()
        val cachedFile = File(context.filesDir, CACHED_UAD_FILE)

        val stream: InputStream = try {
            if (cachedFile.exists() && cachedFile.length() > 1000) {
                cachedFile.inputStream()
            } else {
                context.assets.open("uad_lists.json")
            }
        } catch (_: Exception) {
            try {
                context.assets.open("uad_lists.json")
            } catch (_: Exception) {
                return@withContext 0
            }
        }

        try {
            BufferedReader(InputStreamReader(stream, "UTF-8")).use { reader ->
                var line: String?
                var curPkg: String? = null
                var curList = "Oem"
                var curDesc = ""
                var curRemoval = "Recommended"

                while (reader.readLine().also { line = it } != null) {
                    val trimmed = line!!.trim()
                    if (trimmed.startsWith("\"") && trimmed.contains("\": {")) {
                        if (curPkg != null) {
                            map[curPkg] = UadPackageEntry(
                                packageName = curPkg,
                                list = curList,
                                description = curDesc.replace("\\n", "\n").replace("\\\"", "\""),
                                removal = UadRecommendation.fromString(curRemoval)
                            )
                        }
                        val idx = trimmed.indexOf("\":")
                        curPkg = trimmed.substring(1, idx)
                        curList = "Oem"
                        curDesc = ""
                        curRemoval = "Recommended"
                    } else if (trimmed.startsWith("\"list\":")) {
                        val q1 = trimmed.indexOf('\"', 7)
                        val q2 = trimmed.indexOf('\"', q1 + 1)
                        if (q1 != -1 && q2 != -1) curList = trimmed.substring(q1 + 1, q2)
                    } else if (trimmed.startsWith("\"removal\":")) {
                        val q1 = trimmed.indexOf('\"', 10)
                        val q2 = trimmed.indexOf('\"', q1 + 1)
                        if (q1 != -1 && q2 != -1) curRemoval = trimmed.substring(q1 + 1, q2)
                    } else if (trimmed.startsWith("\"description\":")) {
                        val q1 = trimmed.indexOf('\"', 14)
                        val q2 = trimmed.lastIndexOf('\"')
                        if (q1 != -1 && q2 > q1) curDesc = trimmed.substring(q1 + 1, q2)
                    }
                }
                if (curPkg != null) {
                    map[curPkg] = UadPackageEntry(
                        packageName = curPkg,
                        list = curList,
                        description = curDesc.replace("\\n", "\n").replace("\\\"", "\""),
                        removal = UadRecommendation.fromString(curRemoval)
                    )
                }
            }
            uadDatabase = map
            isLoaded = true
        } catch (_: Exception) {}

        return@withContext uadDatabase.size
    }

    suspend fun updateFromGitHub(context: Context): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val count = loadDatabase(context, forceDownload = true)
            if (count > 0) {
                Result.success(count)
            } else {
                Result.failure(Exception("Failed to download or parse UAD database"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private val scanMutex = Mutex()

    var cachedApps by mutableStateOf<List<DebloatApp>?>(null)
        private set

    var hasPrivilegedScan by mutableStateOf(false)
        private set

    var isScanning by mutableStateOf(false)
        private set

    suspend fun scanInstalledApps(context: Context, forceRefresh: Boolean = false): List<DebloatApp> = withContext(Dispatchers.IO) {
        val shizukuReady = try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) { false }

        scanMutex.withLock {
            if (!forceRefresh && cachedApps != null && cachedApps!!.isNotEmpty() && (!shizukuReady || hasPrivilegedScan)) {
                return@withLock cachedApps!!
            }
            isScanning = true
            var privilegedShellSucceeded = false
            try {
                // Fast local database load without blocking for network downloads
                loadDatabase(context, forceDownload = false)
                val pm = context.packageManager

                // Check disabled packages via shell
                val disabledFromShell = try {
                    val res = ShellEngine.exec("pm list packages -d", timeoutMs = 3000L)
                    if (res.isSuccess) privilegedShellSucceeded = true
                    res.stdout.lines().mapNotNull { line ->
                        line.trim().removePrefix("package:").takeIf { it.isNotBlank() }
                    }.toSet()
                } catch (_: Throwable) {
                    emptySet()
                }

                // Check uninstalled packages (user 0 debloated apps) via shell
                val uninstalledFromShell = try {
                    val res = ShellEngine.exec("pm list packages -u", timeoutMs = 3000L)
                    if (res.isSuccess) privilegedShellSucceeded = true
                    res.stdout.lines().mapNotNull { line ->
                        line.trim().removePrefix("package:").takeIf { it.isNotBlank() }
                    }.toSet()
                } catch (_: Throwable) {
                    emptySet()
                }

                // Query installed packages (include disabled components)
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    PackageManager.MATCH_DISABLED_COMPONENTS
                } else {
                    @Suppress("DEPRECATION")
                    PackageManager.GET_DISABLED_COMPONENTS
                }

                val installedPackages = try {
                    pm.getInstalledPackages(flags) ?: emptyList()
                } catch (_: Throwable) {
                    try {
                        pm.getInstalledPackages(0) ?: emptyList()
                    } catch (_: Throwable) {
                        emptyList()
                    }
                }

                val installedMap = installedPackages.associateBy { it.packageName }
                val results = ArrayList<DebloatApp>(installedMap.size + 32)

                // 1. Process currently installed packages (both enabled and frozen/disabled)
                for ((pkg, pkgInfo) in installedMap) {
                    val appInfo = pkgInfo.applicationInfo
                    val isFrozen = disabledFromShell.contains(pkg) || (appInfo != null && !appInfo.enabled)
                    val isEnabled = !isFrozen
                    val uadEntry = uadDatabase[pkg]

                    val isSystem = appInfo?.let {
                        ((it.flags and ApplicationInfo.FLAG_SYSTEM) != 0) || ((it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)
                    } ?: (uadEntry != null && !uadEntry.list.equals("User", ignoreCase = true))

                    val effectiveUadEntry = if (!isSystem || uadEntry?.list?.equals("User", ignoreCase = true) == true) {
                        uadEntry?.copy(removal = UadRecommendation.CUSTOM, list = "User")
                    } else {
                        uadEntry
                    }

                    val label = try {
                        appInfo?.nonLocalizedLabel?.toString()
                            ?: appInfo?.loadLabel(pm)?.toString()
                    } catch (_: Throwable) {
                        null
                    } ?: pkg.substringAfterLast('.')

                    results.add(
                        DebloatApp(
                            packageName = pkg,
                            appName = label,
                            isSystem = isSystem,
                            isEnabled = isEnabled,
                            isInstalled = true,
                            uadEntry = effectiveUadEntry
                        )
                    )
                }

                // 2. Process debloated packages uninstalled for user 0
                val uninstalledFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    PackageManager.MATCH_UNINSTALLED_PACKAGES
                } else {
                    @Suppress("DEPRECATION")
                    PackageManager.GET_UNINSTALLED_PACKAGES
                }

                for (pkg in uninstalledFromShell) {
                    if (installedMap.containsKey(pkg)) continue

                    val uadEntry = uadDatabase[pkg]
                    val uninstalledPkgInfo = try {
                        pm.getPackageInfo(pkg, uninstalledFlags)
                    } catch (_: Throwable) {
                        null
                    }
                    val appInfo = uninstalledPkgInfo?.applicationInfo

                    val isSystem = appInfo?.let {
                        ((it.flags and ApplicationInfo.FLAG_SYSTEM) != 0) || ((it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)
                    } ?: (uadEntry != null && !uadEntry.list.equals("User", ignoreCase = true))

                    // Only include debloated OEM/system apps or packages listed in the UAD database.
                    // This strictly excludes uninstalled 3rd-party user apps from cluttering debloat.
                    if (isSystem || uadEntry != null) {
                        val effectiveUadEntry = if (!isSystem || uadEntry?.list?.equals("User", ignoreCase = true) == true) {
                            uadEntry?.copy(removal = UadRecommendation.CUSTOM, list = "User")
                        } else {
                            uadEntry
                        }

                        val label = try {
                            appInfo?.nonLocalizedLabel?.toString()
                                ?: appInfo?.loadLabel(pm)?.toString()
                        } catch (_: Throwable) {
                            null
                        } ?: pkg.substringAfterLast('.')

                        results.add(
                            DebloatApp(
                                packageName = pkg,
                                appName = label,
                                isSystem = isSystem,
                                isEnabled = false,
                                isInstalled = false,
                                uadEntry = effectiveUadEntry
                            )
                        )
                    }
                }

                // Sort: Recommended first, then Advanced, Expert, Unsafe, Custom, then alphabetically
                val sorted = results.sortedWith(
                    compareBy<DebloatApp> { it.recommendation.priority }
                        .thenBy { it.isEnabled && it.isInstalled }
                        .thenBy { it.appName.lowercase() }
                )
                hasPrivilegedScan = privilegedShellSucceeded || shizukuReady
                cachedApps = sorted
                return@withLock sorted
            } catch (_: Throwable) {
                return@withLock cachedApps ?: emptyList()
            } finally {
                isScanning = false
            }
        }
    }

    suspend fun freezeApp(packageName: String): CommandResult = withContext(Dispatchers.IO) {
        ShellEngine.exec("pm disable-user --user 0 $packageName")
    }

    suspend fun uninstallApp(packageName: String): CommandResult = withContext(Dispatchers.IO) {
        ShellEngine.exec("pm uninstall -k --user 0 $packageName")
    }

    suspend fun restoreApp(packageName: String): CommandResult = withContext(Dispatchers.IO) {
        ShellEngine.exec("cmd package install-existing $packageName && pm enable $packageName")
    }

    suspend fun batchFreeze(packages: List<String>): BatchActionResult = withContext(Dispatchers.IO) {
        val validPackages = packages.map { it.trim() }.filter { it.matches(Regex("""[a-zA-Z][a-zA-Z0-9_]*(?:\.[a-zA-Z0-9_]+)+""")) }.distinct()
        if (validPackages.isEmpty()) {
            return@withContext BatchActionResult(CommandResult("", "No valid packages selected.", "", 0, true), 0, 0, 0, 0)
        }
        val sb = StringBuilder()
        sb.append("for p in ")
        validPackages.forEach { sb.append(it).append(" ") }
        sb.append("; do echo \"===PKG:\$p===\"; pm disable-user --user 0 \"\$p\" 2>&1; done; exit 0")
        val timeout = maxOf(30000L, validPackages.size * 600L)
        val res = ShellEngine.exec(sb.toString(), timeoutMs = timeout)
        parseBatchResult(res, validPackages, "freeze")
    }

    suspend fun batchUninstall(packages: List<String>): BatchActionResult = withContext(Dispatchers.IO) {
        val validPackages = packages.map { it.trim() }.filter { it.matches(Regex("""[a-zA-Z][a-zA-Z0-9_]*(?:\.[a-zA-Z0-9_]+)+""")) }.distinct()
        if (validPackages.isEmpty()) {
            return@withContext BatchActionResult(CommandResult("", "No valid packages selected.", "", 0, true), 0, 0, 0, 0)
        }
        val sb = StringBuilder()
        sb.append("for p in ")
        validPackages.forEach { sb.append(it).append(" ") }
        sb.append("; do echo \"===PKG:\$p===\"; pm uninstall -k --user 0 \"\$p\" 2>&1; done; exit 0")
        val timeout = maxOf(30000L, validPackages.size * 600L)
        val res = ShellEngine.exec(sb.toString(), timeoutMs = timeout)
        parseBatchResult(res, validPackages, "uninstall")
    }

    suspend fun batchRestore(packages: List<String>): BatchActionResult = withContext(Dispatchers.IO) {
        val validPackages = packages.map { it.trim() }.filter { it.matches(Regex("""[a-zA-Z][a-zA-Z0-9_]*(?:\.[a-zA-Z0-9_]+)+""")) }.distinct()
        if (validPackages.isEmpty()) {
            return@withContext BatchActionResult(CommandResult("", "No valid packages selected.", "", 0, true), 0, 0, 0, 0)
        }
        val sb = StringBuilder()
        sb.append("for p in ")
        validPackages.forEach { sb.append(it).append(" ") }
        sb.append("; do echo \"===PKG:\$p===\"; cmd package install-existing \"\$p\" 2>&1; pm enable \"\$p\" 2>&1; done; exit 0")
        val timeout = maxOf(30000L, validPackages.size * 600L)
        val res = ShellEngine.exec(sb.toString(), timeoutMs = timeout)
        parseBatchResult(res, validPackages, "restore")
    }

    private fun parseBatchResult(res: CommandResult, packages: List<String>, action: String): BatchActionResult {
        if (!res.isSuccess && res.exitCode < 0) {
            return BatchActionResult(res, packages.size, 0, 0, packages.size)
        }

        var successCount = 0
        var ignoredNotFoundCount = 0
        var failedCount = 0

        val blocks = res.stdout.split("===PKG:")
        val processedPackages = mutableSetOf<String>()

        for (block in blocks) {
            if (block.isBlank()) continue
            val lines = block.lines()
            val pkg = lines.firstOrNull()?.trim()?.substringBefore("===") ?: continue
            processedPackages.add(pkg)
            val output = lines.drop(1).joinToString("\n").trim()

            val isNotFound = output.contains("not installed", ignoreCase = true) ||
                    output.contains("doesn't exist", ignoreCase = true) ||
                    output.contains("unknown package", ignoreCase = true) ||
                    output.contains("package not found", ignoreCase = true) ||
                    output.contains("Cannot disable a package that is not installed", ignoreCase = true) ||
                    output.contains("Cannot enable a package that is not installed", ignoreCase = true) ||
                    output.contains("not found", ignoreCase = true)

            if (isNotFound) {
                ignoredNotFoundCount++
            } else {
                when (action) {
                    "freeze" -> {
                        if (output.contains("disabled", ignoreCase = true) || output.contains("Success", ignoreCase = true)) {
                            successCount++
                        } else {
                            failedCount++
                        }
                    }
                    "uninstall" -> {
                        if (output.contains("Success", ignoreCase = true)) {
                            successCount++
                        } else {
                            failedCount++
                        }
                    }
                    "restore" -> {
                        if (output.contains("installed for user", ignoreCase = true) || output.contains("enabled", ignoreCase = true) || output.contains("Success", ignoreCase = true)) {
                            successCount++
                        } else {
                            failedCount++
                        }
                    }
                    else -> {
                        if (output.contains("Success", ignoreCase = true)) successCount++ else failedCount++
                    }
                }
            }
        }

        val missingBlocks = packages.size - processedPackages.size
        if (missingBlocks > 0 && failedCount == 0 && successCount == 0 && ignoredNotFoundCount == 0) {
            failedCount += missingBlocks
        }

        return BatchActionResult(
            commandResult = res,
            totalCount = packages.size,
            successCount = successCount,
            ignoredNotFoundCount = ignoredNotFoundCount,
            failedCount = failedCount
        )
    }
}
