package com.rayenghanmi.rytunex.engine

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.rayenghanmi.rytunex.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WakelockSpotterEngine {

    private val wakelockLinePattern = Regex(
        """(?i)^\s*([A-Z_]+_WAKE_LOCK)\s+'([^']+)'\s+ACQ=([^\s]+)\s+\(uid=(\d+)(?:\s+pid=(\d+))?(?:[^\)]*ws=WorkSource\{(?:\d+\s+)?([^,\}\s]+))?"""
    )

    private val topAlarmPattern = Regex(
        """(?i)^\s*(\+[^\s]+)\s+running,\s*(\d+)\s+wakeups?,\s*(\d+)\s+alarms?:\s*(\d+):([^\s\*]+)\s*(\*walarm\*|\*alarm\*):([^\s]+)"""
    )

    suspend fun scanWakelocksAndDrains(context: Context): WakelockSpotterReport = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val cmd = "echo \"---POWER---\"; " +
                "dumpsys power 2>/dev/null; " +
                "echo \"---ALARMS---\"; " +
                "dumpsys alarm 2>/dev/null; " +
                "echo \"---END---\""

        val res = ShellEngine.exec(cmd, timeoutMs = 8000L)
        val output = res.stdout

        val activeWakelocks = mutableListOf<ActiveWakelock>()
        val wakeupAlarms = mutableListOf<WakeupAlarm>()

        // 1. Parse active wake locks from dumpsys power
        val sectionPower = output.substringAfter("---POWER---", "").substringBefore("---ALARMS---")
        var insideWakeLocks = false
        val powerSnippetLines = mutableListOf<String>()

        for (rawLine in sectionPower.lines()) {
            val line = rawLine.trim()
            if (line.startsWith("Wake Locks:", ignoreCase = true)) {
                insideWakeLocks = true
                powerSnippetLines.add(rawLine)
                continue
            }
            if (insideWakeLocks) {
                if (line.startsWith("Suspend Blockers:", ignoreCase = true) ||
                    line.startsWith("Display Power:", ignoreCase = true) ||
                    line.startsWith("Wireless Charger:", ignoreCase = true)
                ) {
                    insideWakeLocks = false
                    continue
                }

                if (powerSnippetLines.size < 40) {
                    powerSnippetLines.add(rawLine)
                }

                val match = wakelockLinePattern.find(rawLine)
                if (match != null) {
                    val type = match.groupValues[1]
                    val tag = match.groupValues[2]
                    val acq = match.groupValues[3]
                    val uid = match.groupValues[4].toIntOrNull() ?: 1000
                    val pid = match.groupValues[5].toIntOrNull() ?: 0
                    var pkg = match.groupValues[6].takeIf { it.isNotBlank() && it != "null" }

                    if (pkg == null) {
                        pkg = resolvePackageNameForUid(pm, uid)
                    }

                    val appLabel = resolveAppLabel(pm, pkg)
                    val durationSec = parseDurationSeconds(acq)
                    val isStuck = durationSec >= 300L // Active for 5+ minutes continuous

                    activeWakelocks.add(
                        ActiveWakelock(
                            tag = tag,
                            type = type,
                            uid = uid,
                            pid = pid,
                            packageName = pkg,
                            appLabel = appLabel,
                            durationText = acq.trimStart('-'),
                            durationSeconds = durationSec,
                            isStuck = isStuck
                        )
                    )
                }
            }
        }

        // 2. Parse top wakeup alarms from dumpsys alarm
        val sectionAlarms = output.substringAfter("---ALARMS---", "").substringBefore("---END---")
        var insideTopAlarms = false
        val alarmSnippetLines = mutableListOf<String>()

        for (rawLine in sectionAlarms.lines()) {
            val line = rawLine.trim()
            if (line.startsWith("Top Alarms:", ignoreCase = true)) {
                insideTopAlarms = true
                alarmSnippetLines.add(rawLine)
                continue
            }
            if (insideTopAlarms) {
                if (line.startsWith("Alarm Stats:", ignoreCase = true) ||
                    line.startsWith("Past-due non-wakeup alarms:", ignoreCase = true) ||
                    line.isEmpty()
                ) {
                    insideTopAlarms = false
                    continue
                }

                if (alarmSnippetLines.size < 40) {
                    alarmSnippetLines.add(rawLine)
                }

                val match = topAlarmPattern.find(rawLine)
                if (match != null) {
                    val runningTime = match.groupValues[1]
                    val wakeups = match.groupValues[2].toIntOrNull() ?: 0
                    val alarms = match.groupValues[3].toIntOrNull() ?: 0
                    val uid = match.groupValues[4].toIntOrNull() ?: 1000
                    var pkg = match.groupValues[5].trim()
                    val alarmType = match.groupValues[6]
                    val action = match.groupValues[7]

                    if (pkg.isBlank() || pkg == "null") {
                        pkg = resolvePackageNameForUid(pm, uid)
                    }

                    // Only consider wakeup alarms (*walarm*) or alarms with positive wakeups
                    if (alarmType.contains("walarm", ignoreCase = true) || wakeups > 0) {
                        val appLabel = resolveAppLabel(pm, pkg)
                        wakeupAlarms.add(
                            WakeupAlarm(
                                packageName = pkg,
                                appLabel = appLabel,
                                uid = uid,
                                action = action,
                                runningTime = runningTime.trimStart('+'),
                                wakeupsCount = wakeups,
                                alarmsCount = alarms
                            )
                        )
                    }
                }
            }
        }

        // 3. Aggregate into RogueDrainItems per package
        val allPackages = (activeWakelocks.map { it.packageName } + wakeupAlarms.map { it.packageName }).distinct()
        val items = allPackages.mapNotNull { pkg ->
            val appLocks = activeWakelocks.filter { it.packageName == pkg }
            val appAlarms = wakeupAlarms.filter { it.packageName == pkg }
            val uid = appLocks.firstOrNull()?.uid ?: appAlarms.firstOrNull()?.uid ?: 1000

            val appInfo = try {
                pm.getApplicationInfo(pkg, 0)
            } catch (_: Exception) {
                null
            }

            val isSystem = if (appInfo != null) {
                (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            } else {
                pkg == "android" || uid < 10000
            }

            val appLabel = appLocks.firstOrNull()?.appLabel ?: appAlarms.firstOrNull()?.appLabel ?: resolveAppLabel(pm, pkg)
            val totalWakeups = appAlarms.sumOf { it.wakeupsCount }
            val maxDuration = appLocks.maxOfOrNull { it.durationSeconds } ?: 0L

            val severity = when {
                appLocks.any { it.isStuck } || totalWakeups >= 1000 -> DrainSeverity.CRITICAL
                appLocks.any { it.durationSeconds >= 60L } || totalWakeups >= 250 -> DrainSeverity.HIGH
                appLocks.isNotEmpty() || totalWakeups >= 50 -> DrainSeverity.MODERATE
                else -> DrainSeverity.NORMAL
            }

            RogueDrainItem(
                packageName = pkg,
                appLabel = appLabel,
                uid = uid,
                isSystemApp = isSystem,
                activeWakelocks = appLocks,
                wakeupAlarms = appAlarms,
                totalWakeups = totalWakeups,
                maxLockDurationSeconds = maxDuration,
                drainSeverity = severity
            )
        }.sortedWith(
            compareByDescending<RogueDrainItem> { it.drainSeverity.ordinal }
                .thenByDescending { it.maxLockDurationSeconds }
                .thenByDescending { it.totalWakeups }
        )

        val totalActiveLocks = activeWakelocks.size
        val totalWakeupsCount = wakeupAlarms.sumOf { it.wakeupsCount }
        val isDeepSleepBlocked = activeWakelocks.any { it.type == "PARTIAL_WAKE_LOCK" && it.durationSeconds >= 60L }

        val curatedDump = buildString {
            append("--- ACTIVE CPU WAKE LOCKS (dumpsys power) ---\n")
            if (powerSnippetLines.isNotEmpty()) {
                append(powerSnippetLines.joinToString("\n"))
            } else {
                append("No active wake locks currently held.\n")
            }
            append("\n\n--- TOP WAKEUP ALARMS (dumpsys alarm) ---\n")
            if (alarmSnippetLines.isNotEmpty()) {
                append(alarmSnippetLines.joinToString("\n"))
            } else {
                append("No top wakeup alarms found.\n")
            }
        }

        WakelockSpotterReport(
            activeLocksCount = totalActiveLocks,
            totalWakeupsCount = totalWakeupsCount,
            isDeepSleepBlocked = isDeepSleepBlocked,
            items = items,
            rawDump = curatedDump
        )
    }

    private fun resolvePackageNameForUid(pm: PackageManager, uid: Int): String {
        if (uid == 1000) return "android"
        return try {
            val pkgs = pm.getPackagesForUid(uid)
            pkgs?.firstOrNull() ?: "uid.$uid"
        } catch (_: Exception) {
            "uid.$uid"
        }
    }

    private fun resolveAppLabel(pm: PackageManager, packageName: String): String {
        if (packageName == "android") return "Android System"
        return try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }

    private fun parseDurationSeconds(raw: String): Long {
        val clean = raw.trim().trimStart('-', '+')
        var totalSec = 0L

        val hMatch = Regex("""(\d+)h""").find(clean)
        val mMatch = Regex("""(\d+)m(?!s)""").find(clean)
        val sMatch = Regex("""(\d+)s""").find(clean)

        if (hMatch != null) totalSec += (hMatch.groupValues[1].toLongOrNull() ?: 0L) * 3600L
        if (mMatch != null) totalSec += (mMatch.groupValues[1].toLongOrNull() ?: 0L) * 60L
        if (sMatch != null) totalSec += (sMatch.groupValues[1].toLongOrNull() ?: 0L)

        if (totalSec == 0L && clean.endsWith("ms")) {
            val ms = clean.removeSuffix("ms").toLongOrNull() ?: 0L
            totalSec = ms / 1000L
        }
        return totalSec
    }

    suspend fun forceStopApp(packageName: String): CommandResult = withContext(Dispatchers.IO) {
        ShellEngine.exec("am force-stop $packageName")
    }

    suspend fun revokeWakelockPermission(packageName: String): CommandResult = withContext(Dispatchers.IO) {
        ShellEngine.exec("cmd appops set $packageName WAKE_LOCK ignore")
    }

    suspend fun allowWakelockPermission(packageName: String): CommandResult = withContext(Dispatchers.IO) {
        ShellEngine.exec("cmd appops set $packageName WAKE_LOCK allow")
    }

    suspend fun restrictBackground(packageName: String): CommandResult = withContext(Dispatchers.IO) {
        ShellEngine.exec("cmd appops set $packageName RUN_IN_BACKGROUND ignore")
    }
}
