package com.rayenghanmi.rytunex.model

data class ActiveWakelock(
    val tag: String,
    val type: String,
    val uid: Int,
    val pid: Int,
    val packageName: String,
    val appLabel: String,
    val durationText: String,
    val durationSeconds: Long,
    val isStuck: Boolean
)

data class WakeupAlarm(
    val packageName: String,
    val appLabel: String,
    val uid: Int,
    val action: String,
    val runningTime: String,
    val wakeupsCount: Int,
    val alarmsCount: Int
)

data class RogueDrainItem(
    val packageName: String,
    val appLabel: String,
    val uid: Int,
    val isSystemApp: Boolean,
    val activeWakelocks: List<ActiveWakelock> = emptyList(),
    val wakeupAlarms: List<WakeupAlarm> = emptyList(),
    val totalWakeups: Int = 0,
    val maxLockDurationSeconds: Long = 0L,
    val drainSeverity: DrainSeverity = DrainSeverity.NORMAL
) {
    val hasActiveWakelock: Boolean get() = activeWakelocks.isNotEmpty()
    val hasStuckWakelock: Boolean get() = activeWakelocks.any { it.isStuck }
}

enum class DrainSeverity {
    NORMAL,
    MODERATE,
    HIGH,
    CRITICAL
}

data class WakelockSpotterReport(
    val activeLocksCount: Int = 0,
    val totalWakeupsCount: Int = 0,
    val isDeepSleepBlocked: Boolean = false,
    val items: List<RogueDrainItem> = emptyList(),
    val rawDump: String = ""
)
