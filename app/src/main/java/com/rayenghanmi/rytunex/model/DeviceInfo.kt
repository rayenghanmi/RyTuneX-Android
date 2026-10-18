package com.rayenghanmi.rytunex.model

data class DeviceInfo(
    val model: String = "",
    val manufacturer: String = "",
    val androidVersion: String = "",
    val apiLevel: Int = 0,
    val securityPatch: String = "",
    val soc: SocInfo = SocInfo(),
    val memory: MemoryInfo = MemoryInfo(),
    val battery: BatteryInfo = BatteryInfo(),
    val thermal: ThermalInfo = ThermalInfo(),
    val display: DisplayInfo = DisplayInfo(),
    val shizuku: ShizukuInfo = ShizukuInfo()
)

data class SocInfo(
    val hardware: String = "Unknown",
    val board: String = "Unknown",
    val socModel: String = "Unknown",
    val cpuCores: Int = Runtime.getRuntime().availableProcessors(),
    val abi: String = "arm64-v8a",
    val cpuLoadPercent: Float = 0f,
    val currentFreqGhz: Float = 0f
) {
    val cpuLoadFormatted: String get() = "${cpuLoadPercent.toInt()}%"
    val freqFormatted: String get() = if (currentFreqGhz > 0) String.format("%.2f GHz", currentFreqGhz) else "${cpuCores} Cores"
}

data class MemoryInfo(
    val totalRamBytes: Long = 0L,
    val availRamBytes: Long = 0L,
    val zramTotalKb: Long = 0L,
    val zramUsedKb: Long = 0L
) {
    val usedRamBytes: Long get() = (totalRamBytes - availRamBytes).coerceAtLeast(0L)
    val usedPercent: Float get() = if (totalRamBytes > 0) (usedRamBytes.toFloat() / totalRamBytes.toFloat()) * 100f else 0f
    val totalGbFormatted: String get() = String.format("%.1f GB", totalRamBytes / (1024.0 * 1024.0 * 1024.0))
    val usedGbFormatted: String get() = String.format("%.1f GB", usedRamBytes / (1024.0 * 1024.0 * 1024.0))
    val freeGbFormatted: String get() = String.format("%.1f GB", availRamBytes / (1024.0 * 1024.0 * 1024.0))
}

data class BatteryInfo(
    val level: Int = 0,
    val tempCelsius: Float = 0f,
    val health: String = "Good",
    val isCharging: Boolean = false,
    val voltageMv: Int = 0
)

data class ThermalInfo(
    val statusText: String = "Normal",
    val statusCode: Int = 0,
    val isThrottling: Boolean = false
)

data class DisplayInfo(
    val currentRefreshRate: Float = 60f,
    val supportedRates: List<Float> = listOf(60f),
    val width: Int = 1080,
    val height: Int = 2400,
    val densityDpi: Int = 420
)

data class ShizukuInfo(
    val isRunning: Boolean = false,
    val isPermissionGranted: Boolean = false,
    val uid: Int = -1,
    val selinuxContext: String = ""
)
