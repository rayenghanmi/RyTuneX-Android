package com.rayenghanmi.rytunex.engine

import android.content.Context
import com.rayenghanmi.rytunex.model.DeviceInfo
import com.rayenghanmi.rytunex.model.ProfileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

object AdaptiveOptimizer {

    private data class ProfileCheck(
        val label: String,
        val probe: String,
        val predicate: (String) -> Boolean,
        val expected: String,
        val critical: Boolean = false
    )

    suspend fun applyProfile(
        context: Context? = null,
        profile: ProfileType,
        device: DeviceInfo
    ): CommandResult = withContext(Dispatchers.IO) {
        if (profile == ProfileType.STOCK_RESET) {
            val res = RyTuneXDeepEngine.restoreSystemDefaults(context)
            return@withContext if (res.isSuccess) {
                CommandResult(
                    command = "Profile: STOCK_RESET",
                    stdout = "Reset to Stock complete. All system settings, refresh rates, animations, and toggles reverted to OEM defaults.",
                    stderr = "",
                    exitCode = 0,
                    isSuccess = true
                )
            } else {
                res
            }
        }

        // Values derived from the connected device
        val rates = device.display.supportedRates
            .map { Math.round(it).toFloat() }
            .filter { it > 30f }
            .distinct()
            .sorted()
        val fallbackPeak = device.display.currentRefreshRate.coerceIn(60f, 240f)
        val peakHz = rates.maxOrNull() ?: fallbackPeak
        // Best rate at or below 60Hz (avoids locking to a low LTPO floor)
        val lowHz = rates.filter { it <= 60f }.maxOrNull() ?: peakHz
        val multiMode = rates.size > 1
        val vulkanOk = isVulkanSupported(context)

        val hz = String.format(Locale.US, "%.1f", peakHz)
        val lowHzStr = String.format(Locale.US, "%.1f", lowHz)
        val hzInt = peakHz.toInt()
        val lowHzInt = lowHz.toInt()

        val steps = mutableListOf<String>()
        val checks = mutableListOf<ProfileCheck>()
        val notes = mutableListOf<String>()

        // Profiles below also disable system logging
        context?.let { SettingsManager.setTelemetrySuppressed(it, true) }

        fun refreshLock(target: Float, mode: Int): String =
            "settings put system peak_refresh_rate ${fmt(target)}; " +
                    "settings put system min_refresh_rate ${fmt(target)}; " +
                    "settings put secure peak_refresh_rate ${fmt(target)}; " +
                    "settings put secure min_refresh_rate ${fmt(target)}; " +
                    "settings put system user_refresh_rate ${target.toInt()}; " +
                    "settings put secure user_refresh_rate ${target.toInt()}; " +
                    "settings put secure refresh_rate_mode $mode; " +
                    "settings put system refresh_rate_mode $mode"

        fun animCmd(scale: Float): String =
            "settings put global window_animation_scale ${fmt(scale)}; " +
                    "settings put global transition_animation_scale ${fmt(scale)}; " +
                    "settings put global animator_duration_scale ${fmt(scale)}"

        fun loggingCmd(): String =
            "settings put global send_action_app_error 0; " +
                    "settings put global activity_starts_logging_enabled 0; " +
                    "settings put global dropbox_max_files 0; " +
                    "device_config put dropbox max_files 0 2>/dev/null; " +
                    "device_config put activity_manager max_error_dialogs 0 2>/dev/null"

        fun phantomCmd(): String =
            "device_config put activity_manager max_phantom_processes 2147483647; " +
                    "device_config set_sync_disabled_for_tests persistent; " +
                    "settings put global settings_enable_monitor_phantom_procs false"

        fun cacheCmd(): String = "am kill-all; pm trim-caches 999999999999"

        when (profile) {
            ProfileType.EXTREME_GAMING -> {
                if (multiMode) {
                    steps.add(refreshLock(peakHz, 1))
                    checks += ProfileCheck("Peak refresh", "settings get system peak_refresh_rate 2>/dev/null", nearHz(peakHz), "$hz", critical = true)
                    checks += ProfileCheck("Min refresh", "settings get system min_refresh_rate 2>/dev/null", nearHz(peakHz), "$hz", critical = true)
                    checks += ProfileCheck("Refresh mode", "settings get secure refresh_rate_mode 2>/dev/null", eq("1"), "1")
                } else {
                    notes.add("Fixed-mode panel (${hzInt}Hz), refresh override skipped")
                }

                steps.add(animCmd(0.25f))
                checks += ProfileCheck("Animations", "settings get global window_animation_scale 2>/dev/null", nearFloat(0.25f), "0.25x", critical = true)

                steps.add("settings put system pointer_speed 7; settings put secure long_press_timeout 250")
                checks += ProfileCheck("Pointer speed", "settings get system pointer_speed 2>/dev/null", { (it.trim().toIntOrNull() ?: 0) >= 6 }, ">= 6")

                steps.add("settings put global disable_window_blurs 1; settings put global accessibility_reduce_transparency 1")
                checks += ProfileCheck("Blur disable", "settings get global disable_window_blurs 2>/dev/null", eq("1"), "1")

                steps.add("settings put global game_driver_all_apps 1; settings put global force_hw_ui 1")
                checks += ProfileCheck("Game driver", "settings get global game_driver_all_apps 2>/dev/null", eq("1"), "1")
                checks += ProfileCheck("Force GPU 2D", "settings get global force_hw_ui 2>/dev/null", eq("1"), "1")

                if (vulkanOk) {
                    steps.add("setprop debug.hwui.renderer skiavk; settings put global debug.hwui.renderer skiavk")
                    checks += ProfileCheck("HWUI renderer", "getprop debug.hwui.renderer 2>/dev/null", { it.contains("vulkan", true) || it.contains("skiavk", true) }, "skiavk (active for new app processes)")
                } else {
                    notes.add("No Vulkan feature declared, renderer left on OpenGL")
                }

                steps.add(phantomCmd())
                checks += ProfileCheck("Phantom process limit", "device_config get activity_manager max_phantom_processes 2>/dev/null", { it.contains("2147483647") }, "2147483647")

                steps.add(loggingCmd())
                checks += ProfileCheck("Error logging", "settings get global send_action_app_error 2>/dev/null", eq("0"), "0")

                steps.add("settings put global wifi_scan_always_enabled 1; settings put global mobile_data_always_on 1; settings delete global device_idle_constants")
                checks += ProfileCheck("Doze", "settings get global device_idle_constants 2>/dev/null", eq("null"), "deleted")

                steps.add(cacheCmd())
            }

            ProfileType.BATTERY_SAVER -> {
                if (multiMode && (peakHz - lowHz) > 5f) {
                    steps.add(refreshLock(lowHz, 0))
                    checks += ProfileCheck("Peak refresh", "settings get system peak_refresh_rate 2>/dev/null", nearHz(lowHz), "$lowHzStr", critical = true)
                    checks += ProfileCheck("Min refresh", "settings get system min_refresh_rate 2>/dev/null", nearHz(lowHz), "$lowHzStr", critical = true)
                    checks += ProfileCheck("Refresh mode", "settings get secure refresh_rate_mode 2>/dev/null", eq("0"), "0")
                } else {
                    notes.add("Panel already runs at its most efficient rate, refresh override skipped")
                }

                steps.add(animCmd(0f))
                checks += ProfileCheck("Animations", "settings get global window_animation_scale 2>/dev/null", { (it.trim().toFloatOrNull() ?: 1f) <= 0.05f }, "0.00x", critical = true)

                steps.add("settings put global disable_window_blurs 1; settings put global accessibility_reduce_transparency 1")
                checks += ProfileCheck("Blur disable", "settings get global disable_window_blurs 2>/dev/null", eq("1"), "1")

                steps.add("settings put global wifi_scan_always_enabled 0; settings put global ble_scan_always_enabled 0; settings put global mobile_data_always_on 0")
                checks += ProfileCheck("Wi-Fi scans", "settings get global wifi_scan_always_enabled 2>/dev/null", eq("0"), "0")

                steps.add("settings put global device_idle_constants inactive_to=30000,sensing_to=0,locating_to=0,motion_inactive_to=0,idle_after_inactive_to=0; settings put global app_standby_enabled 1")
                checks += ProfileCheck("Doze", "settings get global device_idle_constants 2>/dev/null", { it.contains("inactive_to=30000") }, "inactive_to=30000")

                steps.add("settings delete global game_driver_all_apps; settings put global force_hw_ui 0")
                checks += ProfileCheck("Game driver", "settings get global game_driver_all_apps 2>/dev/null", eq("null"), "deleted")

                steps.add("setprop debug.hwui.renderer skiagl; settings delete global debug.hwui.renderer")
                checks += ProfileCheck("HWUI renderer", "getprop debug.hwui.renderer 2>/dev/null", { it.contains("skiagl") || it.isBlank() }, "skiagl (active for new app processes)")

                steps.add("settings put system pointer_speed 0; settings put secure long_press_timeout 400")
                checks += ProfileCheck("Pointer speed", "settings get system pointer_speed 2>/dev/null", { (it.trim().toIntOrNull() ?: 0) <= 1 }, "<= 1")

                steps.add(loggingCmd())
                checks += ProfileCheck("Error logging", "settings get global send_action_app_error 2>/dev/null", eq("0"), "0")

                steps.add(cacheCmd())
            }

            ProfileType.RYTUNEX_BALANCED -> {
                if (multiMode && (peakHz - lowHz) > 5f) {
                    steps.add(
                        "settings put system peak_refresh_rate $hz; " +
                                "settings put system min_refresh_rate $lowHzStr; " +
                                "settings put secure peak_refresh_rate $hz; " +
                                "settings put secure min_refresh_rate $lowHzStr; " +
                                "settings put system user_refresh_rate $hzInt; " +
                                "settings put secure user_refresh_rate $lowHzInt; " +
                                "settings put secure refresh_rate_mode 2; " +
                                "settings put system refresh_rate_mode 2"
                    )
                    checks += ProfileCheck("Peak refresh", "settings get system peak_refresh_rate 2>/dev/null", nearHz(peakHz), "$hz", critical = true)
                    checks += ProfileCheck("Min refresh", "settings get system min_refresh_rate 2>/dev/null", nearHz(lowHz), "$lowHzStr", critical = true)
                    checks += ProfileCheck("Refresh mode", "settings get secure refresh_rate_mode 2>/dev/null", eq("2"), "2")
                } else {
                    notes.add("Panel already runs at its best rate, refresh override skipped")
                }

                steps.add(animCmd(0.5f))
                checks += ProfileCheck("Animations", "settings get global window_animation_scale 2>/dev/null", nearFloat(0.5f), "0.50x", critical = true)

                steps.add("settings put global force_hw_ui 1; settings put system pointer_speed 7")
                checks += ProfileCheck("Force GPU 2D", "settings get global force_hw_ui 2>/dev/null", eq("1"), "1")

                steps.add("settings delete global disable_window_blurs; settings delete global accessibility_reduce_transparency; settings put global game_driver_all_apps 1")
                checks += ProfileCheck("Game driver", "settings get global game_driver_all_apps 2>/dev/null", eq("1"), "1")

                if (vulkanOk) {
                    steps.add("setprop debug.hwui.renderer skiavk; settings put global debug.hwui.renderer skiavk")
                    checks += ProfileCheck("HWUI renderer", "getprop debug.hwui.renderer 2>/dev/null", { it.contains("vulkan", true) || it.contains("skiavk", true) }, "skiavk (active for new app processes)")
                } else {
                    notes.add("No Vulkan feature declared, renderer left on OpenGL")
                }

                steps.add("settings put global wifi_scan_always_enabled 1; settings delete global device_idle_constants")
                checks += ProfileCheck("Doze", "settings get global device_idle_constants 2>/dev/null", eq("null"), "deleted")

                steps.add(loggingCmd())
                checks += ProfileCheck("Error logging", "settings get global send_action_app_error 2>/dev/null", eq("0"), "0")

                steps.add(cacheCmd())
            }

            ProfileType.STOCK_RESET -> ""
        }

        val command = steps.joinToString("; ")
        val res = ShellEngine.exec(command, timeoutMs = 30000L)

        // Read settings back and build the verification report
        val verifyOut = if (command.isNotBlank() && checks.isNotEmpty()) {
            try {
                val parts = arrayOfNulls<String>(checks.size)
                checks.forEachIndexed { i, c -> parts[i] = "RTXV_$i:\$(${c.probe})" }
                ShellEngine.exec(parts.joinToString("; "), timeoutMs = 15000L)
            } catch (_: Exception) {
                null
            }
        } else null

        val verifyMap = mutableMapOf<String, String>()
        verifyOut?.stdout?.lines()?.forEach { line ->
            val idx = line.indexOf(':')
            if (line.startsWith("RTXV_") && idx > 5) {
                verifyMap[line.substring(5, idx).trim()] = line.substring(idx + 1).trim()
            }
        }

        val report = mutableListOf<String>()
        var failed = 0
        var criticalFailed = 0
        checks.forEachIndexed { index, check ->
            val value: String? = if (verifyOut != null) verifyMap[index.toString()] else null
            if (value == null) {
                report.add("${check.label}: not verified")
                return@forEachIndexed
            }
            if (check.predicate(value)) {
                report.add("${check.label}: ${value.ifBlank { "default" }}")
            } else {
                failed++
                if (check.critical) criticalFailed++
                report.add("${check.label}: FAILED (expected ${check.expected}, got $value)")
            }
        }
        notes.forEach { report.add("Note: $it") }

        // Sync the tuning cache so the Optimize screen switches match the new state
        RyTuneXDeepEngine.readCurrentSystemState(forceRefresh = true)

        val success = res.isSuccess && criticalFailed == 0

        if (success) {
            CommandResult(
                command = "Profile: ${profile.name}",
                stdout = "${profile.title} applied.\n" + report.joinToString("\n"),
                stderr = res.stderr,
                exitCode = 0,
                isSuccess = true
            )
        } else {
            CommandResult(
                command = "Profile: ${profile.name}",
                stdout = report.joinToString("\n"),
                stderr = res.stderr.ifBlank {
                    "${failed}/${checks.size} settings fell back to system defaults (guarded by this OEM)."
                },
                exitCode = if (res.exitCode != 0) res.exitCode else 1,
                isSuccess = false
            )
        }
    }

    private fun fmt(v: Float): String = String.format(Locale.US, "%.2f", v)

    private fun nearHz(expected: Float): (String) -> Boolean = { raw ->
        val v = raw.trim().toFloatOrNull()
        v != null && abs(v - expected) < 1.5f
    }

    private fun nearFloat(expected: Float, tol: Float = 0.06f): (String) -> Boolean = { raw ->
        val v = raw.trim().toFloatOrNull()
        v != null && abs(v - expected) <= tol
    }

    private fun eq(value: String): (String) -> Boolean = { raw ->
        raw.trim().equals(value, ignoreCase = true)
    }

    fun isVulkanSupported(context: Context?): Boolean {
        context ?: return false
        return try {
            context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_VULKAN_HARDWARE_VERSION)
        } catch (_: Exception) {
            false
        }
    }

    suspend fun setAnimationScale(factor: Float): CommandResult = withContext(Dispatchers.IO) {
        val v = fmt(factor)
        val cmd = "settings put global window_animation_scale $v; " +
                "settings put global transition_animation_scale $v; " +
                "settings put global animator_duration_scale $v"
        ShellEngine.exec(cmd)
    }

    suspend fun setDisplayRefreshRate(peakHz: Float, minHz: Float): CommandResult = withContext(Dispatchers.IO) {
        val peak = String.format(Locale.US, "%.1f", peakHz)
        val min = String.format(Locale.US, "%.1f", minHz)
        val hzInt = peakHz.toInt()
        val cmd = "settings put system peak_refresh_rate $peak; " +
                "settings put system min_refresh_rate $min; " +
                "settings put secure peak_refresh_rate $peak; " +
                "settings put secure min_refresh_rate $min; " +
                "settings put system user_refresh_rate $hzInt; " +
                "settings put secure user_refresh_rate $hzInt; " +
                "settings put secure refresh_rate_mode 1; " +
                "settings put system refresh_rate_mode 1"
        ShellEngine.exec(cmd)
    }

    suspend fun resetDisplayRefreshRate(): CommandResult = withContext(Dispatchers.IO) {
        val cmd = "settings delete system peak_refresh_rate; " +
                "settings delete system min_refresh_rate; " +
                "settings delete secure peak_refresh_rate; " +
                "settings delete secure min_refresh_rate; " +
                "settings delete system user_refresh_rate; " +
                "settings delete secure user_refresh_rate; " +
                "settings delete system refresh_rate_mode; " +
                "settings delete secure refresh_rate_mode"
        ShellEngine.exec(cmd)
    }

    suspend fun runArtOptimization(mode: String): CommandResult = withContext(Dispatchers.IO) {
        val cmd = when (mode) {
            "speed" -> "cmd package compile -m speed -a"
            "quicken" -> "cmd package compile -m quicken -a"
            "bg-dexopt" -> "cmd package bg-dexopt-job"
            else -> "cmd package compile -m speed-profile -a"
        }
        ShellEngine.exec(cmd, timeoutMs = 60000L)
    }

    suspend fun trimMemoryCaches(): CommandResult = withContext(Dispatchers.IO) {
        val cmd = "am kill-all && pm trim-caches 999999999999"
        val res = ShellEngine.exec(cmd)
        if (res.isSuccess) {
            CommandResult(
                command = cmd,
                stdout = "Background cached processes purged and RAM caches reclaimed.",
                stderr = "",
                exitCode = 0,
                isSuccess = true
            )
        } else {
            res
        }
    }

    suspend fun forceDozeIdle(enable: Boolean): CommandResult = withContext(Dispatchers.IO) {
        val cmd = if (enable) "dumpsys deviceidle force-idle" else "dumpsys deviceidle unforce"
        ShellEngine.exec(cmd)
    }

    suspend fun toggleLoggingOverhead(suppress: Boolean): CommandResult = withContext(Dispatchers.IO) {
        val flag = if (suppress) "0" else "1"
        val cmd = "settings put global send_action_app_error $flag; " +
                "settings put global activity_starts_logging_enabled $flag"
        ShellEngine.exec(cmd)
    }
}