package com.rayenghanmi.rytunex.engine

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.view.Display
import com.rayenghanmi.rytunex.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object AdaptiveDeviceInspector {

    private var prevTotalCpuTime = 0L
    private var prevIdleCpuTime = 0L
    private var lastCpuUsage = 0f

    // Static specs cached across telemetry ticks
    private var cachedSocInfo: SocInfo? = null
    private var cachedDisplayInfo: DisplayInfo? = null
    private var cachedShizukuInfo: ShizukuInfo = ShizukuInfo()
    private var cachedHwDisplayModes: List<Float>? = null

    suspend fun inspectFullDevice(context: Context, isShizukuReady: Boolean = false): DeviceInfo = withContext(Dispatchers.IO) {
        val baseSoc = cachedSocInfo ?: inspectStaticSoc().also { cachedSocInfo = it }
        var baseDisplay = getDisplayInfo(context)

        // If Shizuku is ready, merge hardware panel modes (queried once and cached)
        if (isShizukuReady) {
            try {
                val hwModes = cachedHwDisplayModes ?: queryHardwareDisplayModes().also {
                    if (it.isNotEmpty()) cachedHwDisplayModes = it
                }
                if (hwModes.isNotEmpty()) {
                    val merged = (baseDisplay.supportedRates + hwModes + 60f)
                        .filter { it in STANDARD_DISCRETE_REFRESH_RATES }
                        .distinct()
                        .sorted()
                    if (merged != baseDisplay.supportedRates) {
                        baseDisplay = baseDisplay.copy(supportedRates = merged)
                        cachedDisplayInfo = baseDisplay
                    }
                }
            } catch (_: Exception) {}
        }

        // Only query privileges when the cache is uninitialized
        if (cachedShizukuInfo.uid < 0 && isShizukuReady) {
            val (uid, selinux) = ShellEngine.checkPrivileges()
            cachedShizukuInfo = ShizukuInfo(
                isRunning = uid >= 0,
                isPermissionGranted = uid >= 0,
                uid = uid,
                selinuxContext = selinux
            )
        } else if (!isShizukuReady) {
            cachedShizukuInfo = ShizukuInfo(
                isRunning = false,
                isPermissionGranted = false,
                uid = -1,
                selinuxContext = "Disconnected"
            )
        }

        val memory = inspectMemory(context)
        val battery = inspectBattery(context)
        val thermal = inspectThermal(context)
        val currentRefreshRate = readCurrentRefreshRate(context, baseDisplay.currentRefreshRate)

        val liveSoc = baseSoc.copy(
            cpuLoadPercent = calculateRealtimeCpuUsage(isShizukuReady),
            currentFreqGhz = readCurrentCpuFrequencyGhz()
        )

        val liveDisplay = baseDisplay.copy(
            currentRefreshRate = currentRefreshRate
        )

        DeviceInfo(
            model = Build.MODEL,
            manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            androidVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            securityPatch = Build.VERSION.SECURITY_PATCH ?: "N/A",
            soc = liveSoc,
            memory = memory,
            battery = battery,
            thermal = thermal,
            display = liveDisplay,
            shizuku = cachedShizukuInfo
        )
    }

    fun updateShizukuPrivilege(isReady: Boolean, uid: Int = 2000, selinux: String = "Enforcing") {
        cachedShizukuInfo = ShizukuInfo(
            isRunning = isReady,
            isPermissionGranted = isReady,
            uid = if (isReady) uid else -1,
            selinuxContext = if (isReady) selinux else "Disconnected"
        )
    }

    private fun inspectStaticSoc(): SocInfo {
        val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL
        } else {
            Build.HARDWARE
        }
        return SocInfo(
            hardware = Build.HARDWARE,
            board = Build.BOARD,
            socModel = if (socModel.isNotBlank() && socModel != "unknown") socModel else Build.HARDWARE,
            cpuCores = Runtime.getRuntime().availableProcessors(),
            abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a"
        )
    }

    fun getDisplayInfo(context: Context, forceRefresh: Boolean = false): DisplayInfo {
        val cached = cachedDisplayInfo
        if (forceRefresh || cached == null || (cached.supportedRates.size == 1 && cached.supportedRates.first() > 60f)) {
            cachedDisplayInfo = inspectStaticDisplay(context)
        }
        return cachedDisplayInfo!!
    }

    val STANDARD_DISCRETE_REFRESH_RATES = setOf(
        24f, 30f, 48f, 60f, 72f, 90f, 96f, 120f, 144f, 165f, 240f
    )

    private fun inspectStaticDisplay(context: Context): DisplayInfo {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val defaultDisplay = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)

        val rates = mutableSetOf<Float>()

        // 1. Inspect default display modes (Physical hardware panel modes only)
        defaultDisplay?.supportedModes?.forEach { mode ->
            val hz = Math.round(mode.refreshRate).toFloat()
            if (hz in STANDARD_DISCRETE_REFRESH_RATES) {
                rates.add(hz)
            }
        }

        // 2. Inspect all displays registered in DisplayManager
        try {
            displayManager?.displays?.forEach { d ->
                d.supportedModes?.forEach { mode ->
                    val hz = Math.round(mode.refreshRate).toFloat()
                    if (hz in STANDARD_DISCRETE_REFRESH_RATES) {
                        rates.add(hz)
                    }
                }
            }
        } catch (_: Exception) {}

        // 3. Inspect context.display if available (API 30+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                context.display?.supportedModes?.forEach { mode ->
                    val hz = Math.round(mode.refreshRate).toFloat()
                    if (hz in STANDARD_DISCRETE_REFRESH_RATES) {
                        rates.add(hz)
                    }
                }
            } catch (_: Exception) {}
        }

        // 4. Read current active refresh rate
        val currentRate = readCurrentRefreshRate(context, 60f)
        val roundedCurrent = Math.round(currentRate).toFloat()
        if (roundedCurrent in STANDARD_DISCRETE_REFRESH_RATES) {
            rates.add(roundedCurrent)
        }

        // 5. High-refresh panels always support 60Hz
        if (rates.any { it >= 60f } || roundedCurrent >= 60f) {
            rates.add(60f)
        }

        val sortedRates = rates.filter { it in STANDARD_DISCRETE_REFRESH_RATES }.sorted().ifEmpty { listOf(60f) }

        val width = defaultDisplay?.mode?.physicalWidth ?: 1080
        val height = defaultDisplay?.mode?.physicalHeight ?: 2400
        val density = context.resources.displayMetrics.densityDpi

        return DisplayInfo(
            currentRefreshRate = currentRate,
            supportedRates = sortedRates,
            width = width,
            height = height,
            densityDpi = density
        )
    }

    suspend fun queryHardwareDisplayModes(forceRefresh: Boolean = false): List<Float> = withContext(Dispatchers.IO) {
        val cached = cachedHwDisplayModes
        if (!forceRefresh && cached != null && cached.isNotEmpty()) {
            return@withContext cached
        }
        val detected = mutableSetOf<Float>()
        try {
            val sf = ShellEngine.exec("dumpsys SurfaceFlinger --display-modes", timeoutMs = 600L)
            if (sf.isSuccess && sf.stdout.isNotBlank()) {
                parseRefreshRatesFromDump(sf.stdout, detected)
            }

            if (detected.size <= 1) {
                val disp = ShellEngine.exec("dumpsys display | grep -iE 'supportedModes|mSupportedModes' | head -n 30", timeoutMs = 700L)
                if (disp.isSuccess && disp.stdout.isNotBlank()) {
                    parseRefreshRatesFromDump(disp.stdout, detected)
                }
            }
        } catch (_: Exception) {}
        val list = detected.filter { it in STANDARD_DISCRETE_REFRESH_RATES }.sorted()
        if (list.isNotEmpty()) {
            cachedHwDisplayModes = list
        }
        return@withContext list
    }

    private fun parseRefreshRatesFromDump(output: String, destination: MutableSet<Float>) {
        // Matches e.g. "Mode 0: 1080x2400, 60.00fps" or "DisplayMode{id=1, ..., fps=90.0}"
        val modeRegex = Regex("""(?:Mode\s+\d+:|DisplayMode\{)[^,\n]+,\s*(\d+(?:\.\d+)?)\s*fps""", RegexOption.IGNORE_CASE)
        for (match in modeRegex.findAll(output)) {
            val num = match.groupValues[1].toFloatOrNull() ?: continue
            val rounded = Math.round(num).toFloat()
            if (rounded in STANDARD_DISCRETE_REFRESH_RATES) {
                destination.add(rounded)
            }
        }
    }

    fun readCurrentRefreshRate(context: Context, fallback: Float = 60f): Float {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val display = context.display
                    val rate = display?.refreshRate ?: 0f
                    if (rate > 0f) return rate
                } catch (_: Exception) {}
            }
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            val display = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
            val liveRate = display?.refreshRate ?: 0f
            if (liveRate > 0f) return liveRate
            val modeRate = display?.mode?.refreshRate ?: 0f
            if (modeRate > 0f) return modeRate
        } catch (_: Exception) {}
        return fallback
    }

    private suspend fun calculateRealtimeCpuUsage(isShizukuReady: Boolean): Float {
        // 1. Try reading /proc/stat directly (works on rooted devices, userdebug, custom ROMs)
        var statLine: String? = null
        try {
            val file = File("/proc/stat")
            if (file.exists() && file.canRead()) {
                statLine = file.bufferedReader().use { it.readLine() }
            }
        } catch (_: Exception) {}

        // 2. If direct read failed and Shizuku is active, read /proc/stat via Shizuku shell
        if (statLine.isNullOrBlank() && isShizukuReady) {
            try {
                val res = ShellEngine.exec("cat /proc/stat", timeoutMs = 600L)
                if (res.isSuccess && res.stdout.startsWith("cpu")) {
                    statLine = res.stdout.lineSequence().firstOrNull { it.startsWith("cpu ") }
                }
            } catch (_: Exception) {}
        }

        // 3. If /proc/stat line is available, compute Linux jiffies delta
        if (!statLine.isNullOrBlank()) {
            val parts = statLine.trim().split("\\s+".toRegex())
            if (parts.size >= 5 && parts[0] == "cpu") {
                val user = parts[1].toLongOrNull() ?: 0L
                val nice = parts[2].toLongOrNull() ?: 0L
                val system = parts[3].toLongOrNull() ?: 0L
                val idle = parts[4].toLongOrNull() ?: 0L
                val iowait = parts.getOrNull(5)?.toLongOrNull() ?: 0L
                val irq = parts.getOrNull(6)?.toLongOrNull() ?: 0L
                val softirq = parts.getOrNull(7)?.toLongOrNull() ?: 0L
                val steal = parts.getOrNull(8)?.toLongOrNull() ?: 0L

                val total = user + nice + system + idle + iowait + irq + softirq + steal
                val idleTotal = idle + iowait

                if (prevTotalCpuTime > 0L) {
                    val deltaTotal = total - prevTotalCpuTime
                    val deltaIdle = idleTotal - prevIdleCpuTime
                    if (deltaTotal > 0L) {
                        val usage = ((deltaTotal - deltaIdle).toFloat() / deltaTotal.toFloat()) * 100f
                        lastCpuUsage = usage.coerceIn(0f, 100f)
                    }
                }

                prevTotalCpuTime = total
                prevIdleCpuTime = idleTotal

                if (lastCpuUsage > 0f) {
                    return lastCpuUsage
                }
            }
        }

        // 4. Sysfs frequency scaling across all CPU cores
        val sysfsLoad = calculateSysfsCpuLoad()
        if (sysfsLoad >= 0f) {
            lastCpuUsage = sysfsLoad
            return lastCpuUsage
        }

        // 5. Fallback: query /proc/loadavg
        val loadAvg = readProcLoadAvg()
        if (loadAvg > 0f) {
            lastCpuUsage = loadAvg
            return lastCpuUsage
        }

        // If completely idle or at the first tick, return a low baseline instead of 0%
        return if (lastCpuUsage > 0f) lastCpuUsage else 2.5f
    }

    private fun calculateSysfsCpuLoad(): Float {
        var totalLoad = 0f
        var countedCores = 0
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

        for (i in 0 until cores) {
            val curFreq = readFrequencyKhz("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
                ?: readFrequencyKhz("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_cur_freq")
            val minFreq = readFrequencyKhz("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_min_freq")
                ?: readFrequencyKhz("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_min_freq")
            val maxFreq = readFrequencyKhz("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_max_freq")
                ?: readFrequencyKhz("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")

            if (curFreq != null && minFreq != null && maxFreq != null && maxFreq > minFreq) {
                val coreLoad = ((curFreq - minFreq).toFloat() / (maxFreq - minFreq).toFloat()).coerceIn(0f, 1f)
                totalLoad += coreLoad
                countedCores++
            }
        }

        return if (countedCores > 0) {
            ((totalLoad / countedCores) * 100f).coerceIn(1f, 100f)
        } else {
            -1f
        }
    }

    private fun readFrequencyKhz(path: String): Long? {
        return try {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                file.readText().trim().toLongOrNull()
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun readProcLoadAvg(): Float {
        return try {
            val file = File("/proc/loadavg")
            if (file.exists() && file.canRead()) {
                val text = file.readText().trim()
                val firstVal = text.split("\\s+".toRegex()).firstOrNull()?.toFloatOrNull()
                if (firstVal != null && firstVal > 0f) {
                    val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
                    ((firstVal / cores) * 100f).coerceIn(1f, 100f)
                } else 0f
            } else 0f
        } catch (_: Exception) {
            0f
        }
    }

    private fun readCurrentCpuFrequencyGhz(): Float {
        var maxGhz = 0f
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        for (i in 0 until cores) {
            val khz = readFrequencyKhz("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
                ?: readFrequencyKhz("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_cur_freq")
            if (khz != null && khz > 0) {
                val ghz = khz / 1_000_000f
                if (ghz > maxGhz) {
                    maxGhz = ghz
                }
            }
        }
        return maxGhz
    }

    fun inspectMemory(context: Context): MemoryInfo {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)

        return MemoryInfo(
            totalRamBytes = memInfo.totalMem,
            availRamBytes = memInfo.availMem,
            zramTotalKb = 0L,
            zramUsedKb = 0L
        )
    }

    fun inspectBattery(context: Context): BatteryInfo {
        val iFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, iFilter)

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else 0

        val tempTenths = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempCelsius = tempTenths / 10.0f

        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val healthCode = batteryStatus?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
        val health = when (healthCode) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Critical"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            else -> "Normal"
        }

        val voltageMv = batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0

        return BatteryInfo(
            level = batteryPct,
            tempCelsius = tempCelsius,
            health = health,
            isCharging = isCharging,
            voltageMv = voltageMv
        )
    }

    fun inspectThermal(context: Context): ThermalInfo {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
            val status = powerManager.currentThermalStatus
            val statusText = when (status) {
                PowerManager.THERMAL_STATUS_NONE -> "Nominal"
                PowerManager.THERMAL_STATUS_LIGHT -> "Light Warmth"
                PowerManager.THERMAL_STATUS_MODERATE -> "Moderate Heat"
                PowerManager.THERMAL_STATUS_SEVERE -> "Severe Throttle"
                PowerManager.THERMAL_STATUS_CRITICAL -> "Critical Throttling"
                PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency Cooldown"
                PowerManager.THERMAL_STATUS_SHUTDOWN -> "Thermal Shutdown"
                else -> "Normal"
            }
            return ThermalInfo(
                statusText = statusText,
                statusCode = status,
                isThrottling = status >= PowerManager.THERMAL_STATUS_MODERATE
            )
        }
        return ThermalInfo(statusText = "Optimal", statusCode = 0, isThrottling = false)
    }
}
