package com.rayenghanmi.rytunex.engine

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import com.rayenghanmi.rytunex.model.BatteryInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

object BatteryHealthManager {

    @Volatile
    private var cachedDesignCapacity: Int? = null

    @Volatile
    private var cachedCycleCount: Int? = null

    @Volatile
    private var cachedCycleIsHardware: Boolean = false

    @Volatile
    private var cachedCycleSourceText: String = "Universal AOSP Coulomb Model"

    @Volatile
    private var cachedActualCapacity: Int? = null

    @Volatile
    private var cachedAsocHealth: Float? = null

    @Volatile
    private var cachedFirstUseDate: String? = null

    @Volatile
    private var cachedCurrentMa: Int? = null

    @Volatile
    private var lastShellQueryTime: Long = 0L

    @Volatile
    var rawDiagnosticDump: String = "No diagnostic telemetry captured yet. Refresh with Shizuku connected to inspect registers."
        private set

    // Hardware uevent patterns (supporting optional brackets [..])
    private val ueventCyclePattern = Regex("""(?i)\bPOWER_SUPPLY_(?:CYCLE_COUNT|BATTERY_CYCLE|FG_CYCLE)\s*=\s*\[?(\d+)\]?""")
    private val ueventChargeFullPattern = Regex("""(?i)\bPOWER_SUPPLY_CHARGE_FULL\s*=\s*\[?(\d+)\]?""")
    private val ueventChargeFullDesignPattern = Regex("""(?i)\bPOWER_SUPPLY_CHARGE_FULL_DESIGN\s*=\s*\[?(\d+)\]?""")
    private val ueventCurrentPattern = Regex("""(?i)\bPOWER_SUPPLY_CURRENT_NOW\s*=\s*\[?(-?\d+)\]?""")
    private val ueventSohPattern = Regex("""(?i)\bPOWER_SUPPLY_SOH\s*=\s*\[?(\d+)\]?""")

    // Dumpsys and standard properties patterns (supporting optional brackets [..])
    private val cyclePattern = Regex("""(?i)\b(?:cycle_count|mcyclecount|battery cycle count|mBatteryCycle|fg_cycle|battery_cycle|batt_cycle)\s*[:=]\s*\[?(\d+)\]?""")
    private val samsungUsagePattern = Regex("""(?i)\b(?:mSavedBatteryUsage|mSecBatteryUsage)\s*[:=]\s*\[?(\d+)\]?""")
    private val samsungBsohPattern = Regex("""(?i)\b(?:mSavedBatteryBsoh|bsoh)\s*[:=]\s*\[?(\d+)\]?""")
    private val samsungAsocPattern = Regex("""(?i)\b(?:mSavedBatteryAsoc|mBatteryAsoc)\s*[:=]\s*\[?(\d+)\]?""")
    private val samsungFirstUsePattern = Regex("""(?i)\bbattery FirstUseDate\s*[:=]\s*\[?(\d{4})(\d{2})(\d{2})\]?""")
    private val xiaomiCyclePattern = Regex("""(?i)\b(?:MF_02|CC)\s*[:=]\s*\[?(\d+)\]?""")
    private val healthHalCyclePattern = Regex("""(?i)\b(?:battery cycle count|cycle count)\s*[:=]\s*\[?(\d+)\]?""")
    private val healthHalCapPattern = Regex("""(?i)\b(?:battery full charge capacity|full charge capacity)\s*[:=]\s*\[?(\d+)\]?""")
    private val currentPattern = Regex("""(?i)\b(?:current_now|batt_current|current now)\s*[:=]\s*\[?(-?\d+)\]?""")
    private val maxCurrentPattern = Regex("""(?i)\b(?:max charging current|mSavedBatteryMaxCurrent)\s*[:=]\s*\[?(\d+)\]?""")

    // Universal BatteryStats learned capacity patterns
    private val batteryStatsEstimatedCapPattern = Regex("""(?i)\bEstimated battery capacity\s*[:=]\s*(\d+(?:\.\d+)?)\s*mAh\b""")
    private val batteryStatsLearnedCapPattern = Regex("""(?i)\b(?:Min|Max|Last)?\s*learned battery capacity\s*[:=]\s*(\d+(?:\.\d+)?)\s*(?:mAh|uAh)\b""")

    fun normalizeCurrentMa(raw: Long): Int {
        if (raw == 0L || raw == Int.MIN_VALUE.toLong() || raw == Int.MAX_VALUE.toLong()) return 0
        val absVal = abs(raw)
        return when {
            absVal > 20000L -> (raw / 1000L).toInt()  // µA -> mA
            absVal in 10L..20000L -> raw.toInt()       // Already in mA
            else -> 0
        }
    }

    fun getDesignCapacity(context: Context): Int {
        cachedDesignCapacity?.let { return it }
        val capacity = try {
            val powerProfileClass = Class.forName("com.android.internal.os.PowerProfile")
            val powerProfile = powerProfileClass.getConstructor(Context::class.java).newInstance(context)
            val cap = powerProfileClass.getMethod("getBatteryCapacity").invoke(powerProfile) as Double
            cap.toInt()
        } catch (_: Throwable) {
            0
        }
        if (capacity > 0) {
            cachedDesignCapacity = capacity
        }
        return capacity
    }

    fun getLiveBatteryInfo(context: Context, isShizukuReady: Boolean = false): BatteryInfo {
        val iFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, iFilter)
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else 0

        val tempTenths = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempCelsius = tempTenths / 10.0f

        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val plugged = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val powerSource = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Fast Charger"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB-C Port"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless Dock"
            else -> if (isCharging) "Charging" else "Battery (Unplugged)"
        }

        val healthCode = batteryStatus?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
        val health = when (healthCode) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Critical"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            else -> "Normal"
        }

        val voltageMv = batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val technology = batteryStatus?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)?.takeIf { it.isNotBlank() } ?: "Li-ion"

        // 1. Current flow
        val rawCurrentNow = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)?.toLong() ?: 0L
        val rawCurrentAvg = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)?.toLong() ?: 0L

        var currentMa = normalizeCurrentMa(rawCurrentNow)
        if (currentMa == 0) {
            currentMa = normalizeCurrentMa(rawCurrentAvg)
        }
        if (currentMa == 0 && cachedCurrentMa != null && cachedCurrentMa != 0) {
            currentMa = cachedCurrentMa!!
        }

        // Align current sign with charging vs discharging state
        if (!isCharging && currentMa > 0) {
            currentMa = -currentMa
        } else if (isCharging && currentMa < 0) {
            currentMa = abs(currentMa)
        }

        // 2. Real-time Wattage
        val voltageVolts = (if (voltageMv > 0) voltageMv else 3850) / 1000f
        val currentAmps = abs(currentMa) / 1000f
        val wattage = voltageVolts * currentAmps
        val isFastCharging = isCharging && (wattage >= 12f || abs(currentMa) >= 2400)

        // 3. Capacities & Universal Coulomb Integration
        var designCapacity = getDesignCapacity(context)
        if (cachedDesignCapacity != null && cachedDesignCapacity!! > 0) {
            designCapacity = cachedDesignCapacity!!
        }

        // Universal Android AOSP Coulomb estimation:
        // When level >= 20%, calculate actual capacity dynamically: (charge_counter / level) * 100
        val rawChargeCounter = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)?.toLong() ?: 0L
        val chargeCounterMah = if (abs(rawChargeCounter) > 20000L) (abs(rawChargeCounter) / 1000L).toInt() else abs(rawChargeCounter).toInt()

        var calculatedFullCapacity = cachedActualCapacity ?: 0
        if (calculatedFullCapacity == 0 && chargeCounterMah > 400 && batteryPct in 20..100) {
            val dynamicFull = (chargeCounterMah * 100) / batteryPct
            val minReasonable = if (designCapacity > 0) (designCapacity * 0.4).toInt() else 1500
            val maxReasonable = if (designCapacity > 0) (designCapacity * 1.15).toInt() else 6500
            if (dynamicFull in minReasonable..maxReasonable) {
                calculatedFullCapacity = dynamicFull
                cachedActualCapacity = dynamicFull
            }
        }

        // 4. True Battery Health percentage
        val healthPercent: Float? = when {
            cachedAsocHealth != null && cachedAsocHealth!! in 20f..105f -> cachedAsocHealth
            calculatedFullCapacity > 0 && designCapacity > 0 -> {
                ((calculatedFullCapacity.toFloat() / designCapacity.toFloat()) * 100f).coerceIn(40f, 100f)
            }
            cachedCycleCount != null && cachedCycleIsHardware && cachedCycleCount!! > 0 -> {
                (100f - (cachedCycleCount!! * 0.035f)).coerceIn(50f, 100f)
            }
            else -> when (healthCode) {
                BatteryManager.BATTERY_HEALTH_GOOD -> 98f
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> 85f
                BatteryManager.BATTERY_HEALTH_DEAD -> 40f
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> 75f
                else -> 95f
            }
        }

        // 5. Universal Cycle Determination (Hardware Verified vs Universal AOSP Model)
        var cycleCount = if (Build.VERSION.SDK_INT >= 34) {
            batteryStatus?.getIntExtra("android.os.extra.CYCLE_COUNT", -1)?.takeIf { it > 0 }
        } else null

        var isHardwareVerified = false
        var cycleSourceText = "Universal AOSP Coulomb Model"

        if (cycleCount != null && cycleCount > 0) {
            isHardwareVerified = true
            cycleSourceText = "Android 14+ Health HAL"
        } else if (cachedCycleCount != null && cachedCycleCount!! > 0 && cachedCycleIsHardware) {
            cycleCount = cachedCycleCount
            isHardwareVerified = true
            cycleSourceText = cachedCycleSourceText
        } else if (cachedCycleCount != null && cachedCycleCount!! > 0) {
            cycleCount = cachedCycleCount
            isHardwareVerified = false
            cycleSourceText = cachedCycleSourceText
        } else if (healthPercent != null && healthPercent > 0) {
            // Universal AOSP Lifecycle Estimation:
            // Standard Li-ion degrades ~20% over 625 cycles (~0.032% per cycle)
            val wear = (100f - healthPercent).coerceAtLeast(0f)
            val estimated = (wear / 0.032f).toInt()
            cycleCount = if (estimated < 15) 15 else estimated
            isHardwareVerified = false
            cycleSourceText = "Universal AOSP Coulomb Model"
        }

        return BatteryInfo(
            level = batteryPct,
            tempCelsius = tempCelsius,
            health = health,
            isCharging = isCharging,
            voltageMv = voltageMv,
            cycleCount = cycleCount,
            isCycleHardwareVerified = isHardwareVerified,
            cycleSourceText = cycleSourceText,
            healthPercent = healthPercent,
            wattage = wattage,
            currentMa = currentMa,
            designCapacityMah = designCapacity,
            actualCapacityMah = calculatedFullCapacity,
            powerSource = powerSource,
            technology = technology,
            isFastCharging = isFastCharging,
            firstUseDate = cachedFirstUseDate
        )
    }

    suspend fun refreshHardwareMetrics(force: Boolean = false): Unit = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - lastShellQueryTime < 4000L) return@withContext
        lastShellQueryTime = now

        try {
            val cmd = "cat /sys/class/power_supply/*/uevent 2>/dev/null; " +
                    "echo \"---CYCLES---\"; " +
                    "cat /sys/class/power_supply/*/cycle_count " +
                    "/sys/class/power_supply/*/battery_cycle " +
                    "/sys/class/power_supply/*/batt_cycle " +
                    "/sys/class/power_supply/*/batt_soc_cycle " +
                    "/sys/class/power_supply/*/fg_cycle " +
                    "/sys/class/power_supply/*/count " +
                    "/sys/class/power_supply/*/soh " +
                    "/sys/class/power_supply/battery/cycle " +
                    "/sys/class/power_supply/bms/cycle 2>/dev/null; " +
                    "echo \"---CURR---\"; " +
                    "cat /sys/class/power_supply/*/current_now " +
                    "/sys/class/power_supply/*/batt_current 2>/dev/null; " +
                    "echo \"---CAP---\"; " +
                    "cat /sys/class/power_supply/*/charge_full " +
                    "/sys/class/power_supply/*/charge_full_design 2>/dev/null; " +
                    "echo \"---DUMPSYS_BATTERY---\"; " +
                    "dumpsys battery 2>/dev/null; " +
                    "echo \"---DUMPSYS_PROPS---\"; " +
                    "dumpsys batteryproperties 2>/dev/null; " +
                    "echo \"---DUMPSYS_HEALTH---\"; " +
                    "dumpsys health 2>/dev/null; " +
                    "echo \"---DUMPSYS_STATS---\"; " +
                    "dumpsys batterystats 2>/dev/null | grep -iE \"(Estimated battery capacity|learned battery capacity)\" | head -n 15; " +
                    "echo \"---NODES---\"; " +
                    "ls -la /sys/class/power_supply/ 2>/dev/null; " +
                    "echo \"---END---\""

            val res = ShellEngine.exec(cmd, timeoutMs = 8000L)
            val output = res.stdout
            if (output.isNotBlank()) {
                rawDiagnosticDump = output
            } else if (res.stderr.isNotBlank()) {
                rawDiagnosticDump = "[Shell Stderr]:\n${res.stderr}\nExit Code: ${res.exitCode}"
                return@withContext
            } else {
                return@withContext
            }

            // Section 0: Linux Kernel uevent parsing (universal driver telemetry)
            val sectionUevent = output.substringBefore("---CYCLES---")
            for (rawLine in sectionUevent.lines()) {
                val line = rawLine.trim()

                if (cachedCycleCount == null) {
                    val match = ueventCyclePattern.find(line)
                    if (match != null) {
                        val cycles = match.groupValues[1].toIntOrNull()
                        if (cycles != null && cycles in 1..9999) {
                            cachedCycleCount = cycles
                            cachedCycleIsHardware = true
                            cachedCycleSourceText = "Kernel Power Supply Driver"
                        }
                    }
                }

                if (cachedActualCapacity == null) {
                    val match = ueventChargeFullPattern.find(line)
                    if (match != null) {
                        val cap = match.groupValues[1].toLongOrNull()
                        if (cap != null && cap > 1000L) {
                            cachedActualCapacity = if (cap > 50000L) (cap / 1000L).toInt() else cap.toInt()
                        }
                    }
                }

                if (cachedDesignCapacity == null) {
                    val match = ueventChargeFullDesignPattern.find(line)
                    if (match != null) {
                        val cap = match.groupValues[1].toLongOrNull()
                        if (cap != null && cap > 1000L) {
                            cachedDesignCapacity = if (cap > 50000L) (cap / 1000L).toInt() else cap.toInt()
                        }
                    }
                }

                if (cachedCurrentMa == null || cachedCurrentMa == 0) {
                    val match = ueventCurrentPattern.find(line)
                    if (match != null) {
                        val curr = match.groupValues[1].toLongOrNull()
                        if (curr != null) {
                            val norm = normalizeCurrentMa(curr)
                            if (norm != 0) cachedCurrentMa = norm
                        }
                    }
                }

                if (cachedAsocHealth == null) {
                    val match = ueventSohPattern.find(line)
                    if (match != null) {
                        val soh = match.groupValues[1].toFloatOrNull()
                        if (soh != null && soh in 20f..105f) {
                            cachedAsocHealth = soh
                        }
                    }
                }
            }

            // Section 1: Cycle count from dedicated battery sysfs paths
            val sectionCycles = output.substringAfter("---CYCLES---", "").substringBefore("---CURR---")
            if (cachedCycleCount == null) {
                for (line in sectionCycles.lines()) {
                    val v = line.trim().toIntOrNull()
                    if (v != null && v in 1..9999) {
                        cachedCycleCount = v
                        cachedCycleIsHardware = true
                        cachedCycleSourceText = "Sysfs Cycle Register"
                        break
                    }
                }
            }

            // Section 2: Current from dedicated sysfs paths
            val sectionCurr = output.substringAfter("---CURR---", "").substringBefore("---CAP---")
            if (cachedCurrentMa == null || cachedCurrentMa == 0) {
                for (line in sectionCurr.lines()) {
                    val v = line.trim().toLongOrNull()
                    if (v != null && v != 0L) {
                        val normalized = normalizeCurrentMa(v)
                        if (normalized != 0) {
                            cachedCurrentMa = normalized
                            break
                        }
                    }
                }
            }

            // Section 3: Capacities from dedicated sysfs paths
            val sectionCap = output.substringAfter("---CAP---", "").substringBefore("---DUMPSYS_BATTERY---")
            val capValues = sectionCap.lines().mapNotNull { it.trim().toLongOrNull() }.filter { it > 1000L }
            if (capValues.isNotEmpty()) {
                if (cachedActualCapacity == null) {
                    val full = capValues.first()
                    cachedActualCapacity = if (full > 50000L) (full / 1000L).toInt() else full.toInt()
                }
                if (cachedDesignCapacity == null && capValues.size > 1) {
                    val des = capValues[1]
                    cachedDesignCapacity = if (des > 50000L) (des / 1000L).toInt() else des.toInt()
                }
            }

            // Section 4: Dumpsys battery, batteryproperties, health, and batterystats
            val sectionDump = output.substringAfter("---DUMPSYS_BATTERY---", "")
            for (rawLine in sectionDump.lines()) {
                val line = rawLine.trim()

                // 1. Samsung Galaxy cycle counter: mSavedBatteryUsage (each 100 = 1 full cycle, e.g. [68142] -> 681 cycles)
                if (cachedCycleCount == null) {
                    val samsungMatch = samsungUsagePattern.find(line)
                    if (samsungMatch != null) {
                        val rawUsage = samsungMatch.groupValues[1].toIntOrNull()
                        if (rawUsage != null && rawUsage > 0) {
                            cachedCycleCount = rawUsage / 100
                            cachedCycleIsHardware = true
                            cachedCycleSourceText = "Samsung Battery EFS"
                        }
                    }
                }

                // 2. Samsung Galaxy BSOH calibrated battery health (e.g. mSavedBatteryBsoh: 93 -> 93% health)
                if (cachedAsocHealth == null) {
                    val bsohMatch = samsungBsohPattern.find(line)
                    if (bsohMatch != null) {
                        val bsoh = bsohMatch.groupValues[1].toFloatOrNull()
                        if (bsoh != null && bsoh in 20f..105f) {
                            cachedAsocHealth = bsoh
                        }
                    }
                }

                // 3. Samsung Galaxy ASOC health percentage fallback (mSavedBatteryAsoc: [100])
                if (cachedAsocHealth == null) {
                    val asocMatch = samsungAsocPattern.find(line)
                    if (asocMatch != null) {
                        val asoc = asocMatch.groupValues[1].toFloatOrNull()
                        if (asoc != null && asoc in 20f..105f) {
                            cachedAsocHealth = asoc
                        }
                    }
                }

                // 4. Samsung Galaxy First Use / Activation Date (e.g. battery FirstUseDate: [20240629])
                if (cachedFirstUseDate == null) {
                    val firstUseMatch = samsungFirstUsePattern.find(line)
                    if (firstUseMatch != null) {
                        val y = firstUseMatch.groupValues[1]
                        val m = firstUseMatch.groupValues[2]
                        val d = firstUseMatch.groupValues[3]
                        cachedFirstUseDate = "$y-$m-$d"
                    }
                }

                // 5. Android Health HAL / Google Pixel cycle counter
                if (cachedCycleCount == null) {
                    val healthMatch = healthHalCyclePattern.find(line)
                    if (healthMatch != null) {
                        val cycles = healthMatch.groupValues[1].toIntOrNull()
                        if (cycles != null && cycles in 1..9999) {
                            cachedCycleCount = cycles
                            cachedCycleIsHardware = true
                            cachedCycleSourceText = "Android Health HAL"
                        }
                    }
                }

                // 6. Xiaomi dialer / kernel code (MF_02, CC)
                if (cachedCycleCount == null) {
                    val xiaomiMatch = xiaomiCyclePattern.find(line)
                    if (xiaomiMatch != null) {
                        val cycles = xiaomiMatch.groupValues[1].toIntOrNull()
                        if (cycles != null && cycles in 1..9999) {
                            cachedCycleCount = cycles
                            cachedCycleIsHardware = true
                            cachedCycleSourceText = "Xiaomi BMS Engine"
                        }
                    }
                }

                // 7. Standard cycle_count / mBatteryCycle from dumpsys
                if (cachedCycleCount == null) {
                    val match = cyclePattern.find(line)
                    if (match != null) {
                        val cycles = match.groupValues[1].toIntOrNull()
                        if (cycles != null && cycles in 1..9999) {
                            cachedCycleCount = cycles
                            cachedCycleIsHardware = true
                            cachedCycleSourceText = "BatteryService Driver"
                        }
                    }
                }

                // 8. Health HAL full charge capacity
                if (cachedActualCapacity == null) {
                    val capMatch = healthHalCapPattern.find(line)
                    if (capMatch != null) {
                        val cap = capMatch.groupValues[1].toLongOrNull()
                        if (cap != null && cap > 1000L) {
                            cachedActualCapacity = if (cap > 50000L) (cap / 1000L).toInt() else cap.toInt()
                        }
                    }
                }

                // 9. BatteryStats learned capacity (Universal Android AOSP)
                if (cachedActualCapacity == null) {
                    val estMatch = batteryStatsEstimatedCapPattern.find(line)
                    if (estMatch != null) {
                        val est = estMatch.groupValues[1].toDoubleOrNull()?.toInt()
                        if (est != null && est > 1000) {
                            cachedActualCapacity = est
                        }
                    }
                }
                if (cachedActualCapacity == null) {
                    val learnedMatch = batteryStatsLearnedCapPattern.find(line)
                    if (learnedMatch != null) {
                        val cap = learnedMatch.groupValues[1].toDoubleOrNull()?.toLong()
                        if (cap != null && cap > 1000L) {
                            cachedActualCapacity = if (cap > 50000L) (cap / 1000L).toInt() else cap.toInt()
                        }
                    }
                }

                // 10. Current_now from dumpsys
                if (cachedCurrentMa == null || cachedCurrentMa == 0) {
                    val matchCurr = currentPattern.find(line)
                    if (matchCurr != null) {
                        val currVal = matchCurr.groupValues[1].toLongOrNull()
                        if (currVal != null) {
                            val norm = normalizeCurrentMa(currVal)
                            if (norm != 0) cachedCurrentMa = norm
                        }
                    }
                }

                // 11. Max charging current fallback
                if (cachedCurrentMa == null || cachedCurrentMa == 0) {
                    val matchMax = maxCurrentPattern.find(line)
                    if (matchMax != null) {
                        val maxVal = matchMax.groupValues[1].toLongOrNull()
                        if (maxVal != null) {
                            val norm = normalizeCurrentMa(maxVal)
                            if (norm != 0) cachedCurrentMa = norm
                        }
                    }
                }
            }
        } catch (_: Throwable) {}
    }
}
