package com.rayenghanmi.rytunex.engine

import com.rayenghanmi.rytunex.model.AppOpCategory
import com.rayenghanmi.rytunex.model.AppOpItem
import com.rayenghanmi.rytunex.model.AppOpMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RyTuneXAppOpsEngine {

    val availableOps: List<AppOpItem> = listOf(
        // Battery & Background
        AppOpItem(
            opCode = "RUN_IN_BACKGROUND",
            displayName = "Run in Background",
            description = "Disallow background services, jobs, and alarms from running when app is closed",
            category = AppOpCategory.BATTERY_BACKGROUND,
            isHighImpact = true
        ),
        AppOpItem(
            opCode = "RUN_ANY_IN_BACKGROUND",
            displayName = "Run Any in Background",
            description = "Deep background restriction for aggressive apps on Android 8+",
            category = AppOpCategory.BATTERY_BACKGROUND,
            isHighImpact = true
        ),
        AppOpItem(
            opCode = "WAKE_LOCK",
            displayName = "Keep CPU Awake (WakeLock)",
            description = "Prevent app from holding CPU wakelocks that drain battery during sleep",
            category = AppOpCategory.BATTERY_BACKGROUND,
            isHighImpact = true
        ),
        AppOpItem(
            opCode = "BOOT_COMPLETED",
            displayName = "Start at Boot",
            description = "Allow or prevent the app from launching automatically on device restart",
            category = AppOpCategory.BATTERY_BACKGROUND
        ),

        // Privacy & Hardware
        AppOpItem(
            opCode = "READ_CLIPBOARD",
            displayName = "Read Clipboard",
            description = "Prevent app from silently snooping on copied text and passwords",
            category = AppOpCategory.PRIVACY_SENSORS,
            isHighImpact = true
        ),
        AppOpItem(
            opCode = "WRITE_CLIPBOARD",
            displayName = "Modify Clipboard",
            description = "Control whether the app can write content to the system clipboard",
            category = AppOpCategory.PRIVACY_SENSORS
        ),
        AppOpItem(
            opCode = "FINE_LOCATION",
            displayName = "Precise Location (GPS)",
            description = "Access high-accuracy GPS coordinates",
            category = AppOpCategory.PRIVACY_SENSORS
        ),
        AppOpItem(
            opCode = "COARSE_LOCATION",
            displayName = "Approximate Location",
            description = "Access network and cell-tower approximate location",
            category = AppOpCategory.PRIVACY_SENSORS
        ),
        AppOpItem(
            opCode = "RECORD_AUDIO",
            displayName = "Microphone Access",
            description = "Record audio through device microphones",
            category = AppOpCategory.PRIVACY_SENSORS
        ),
        AppOpItem(
            opCode = "CAMERA",
            displayName = "Camera Access",
            description = "Capture photos and videos from camera sensors",
            category = AppOpCategory.PRIVACY_SENSORS
        ),

        // System & Overlays
        AppOpItem(
            opCode = "SYSTEM_ALERT_WINDOW",
            displayName = "Display Over Other Apps",
            description = "Draw floating windows and overlays on top of other applications",
            category = AppOpCategory.SYSTEM_INTERACTION,
            isHighImpact = true
        ),
        AppOpItem(
            opCode = "POST_NOTIFICATION",
            displayName = "Send Notifications",
            description = "Post alerts and heads-up notifications (Android 13+)",
            category = AppOpCategory.SYSTEM_INTERACTION
        ),
        AppOpItem(
            opCode = "VIBRATE",
            displayName = "Haptic Vibration",
            description = "Trigger device vibration motor for alerts",
            category = AppOpCategory.SYSTEM_INTERACTION
        ),
        AppOpItem(
            opCode = "GET_USAGE_STATS",
            displayName = "App Usage Statistics",
            description = "Monitor which apps are running and time spent in them",
            category = AppOpCategory.SYSTEM_INTERACTION
        ),
        AppOpItem(
            opCode = "PROJECT_MEDIA",
            displayName = "Screen Capture & Cast",
            description = "Capture or stream device display content",
            category = AppOpCategory.SYSTEM_INTERACTION
        )
    )

    suspend fun getAppOpsForPackage(packageName: String): List<AppOpItem> = withContext(Dispatchers.IO) {
        val res = ShellEngine.exec("cmd appops get $packageName", timeoutMs = 4000L)
        val modeMap = parseAppOpsOutput(res.stdout)

        availableOps.map { def ->
            def.copy(mode = modeMap[def.opCode] ?: AppOpMode.DEFAULT)
        }
    }

    private fun parseAppOpsOutput(stdout: String): Map<String, AppOpMode> {
        val map = mutableMapOf<String, AppOpMode>()
        stdout.lines().forEach { rawLine ->
            val clean = rawLine.trim().removePrefix("Uid mode:").trim()
            if (clean.isBlank() || clean.startsWith("No operations", ignoreCase = true)) return@forEach

            val colonIndex = clean.indexOf(':')
            if (colonIndex > 0) {
                val opName = clean.substring(0, colonIndex).trim()
                val rest = clean.substring(colonIndex + 1).trim()
                val token = rest.split(';', ' ', ',').firstOrNull()?.trim()
                map[opName] = AppOpMode.fromRaw(token)
            }
        }
        return map
    }

    suspend fun setAppOp(packageName: String, opCode: String, mode: AppOpMode): CommandResult = withContext(Dispatchers.IO) {
        ShellEngine.exec("cmd appops set $packageName $opCode ${mode.raw}")
    }

    suspend fun resetAppOps(packageName: String): CommandResult = withContext(Dispatchers.IO) {
        ShellEngine.exec("cmd appops reset $packageName")
    }

    suspend fun restrictBackgroundDrain(packageName: String, restrict: Boolean): CommandResult = withContext(Dispatchers.IO) {
        val mode = if (restrict) "ignore" else "allow"
        ShellEngine.exec("cmd appops set $packageName RUN_IN_BACKGROUND $mode; cmd appops set $packageName RUN_ANY_IN_BACKGROUND $mode; cmd appops set $packageName WAKE_LOCK $mode")
    }
}
