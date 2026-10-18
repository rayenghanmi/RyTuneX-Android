package com.rayenghanmi.rytunex.engine

import android.content.Context
import com.rayenghanmi.rytunex.model.DisplayInfo
import com.rayenghanmi.rytunex.model.ProfileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

object AdaptiveOptimizer {

    suspend fun applyProfile(context: Context? = null, profile: ProfileType, display: DisplayInfo): CommandResult = withContext(Dispatchers.IO) {
        val maxHz = display.supportedRates.maxOrNull() ?: 60f
        val minHz = display.supportedRates.minOrNull() ?: 60f

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

        val hzFormatted = String.format(Locale.US, "%.1f", maxHz)
        val hzInt = maxHz.toInt()
        val minHzFormatted = String.format(Locale.US, "%.1f", minHz)
        val minHzInt = minHz.toInt()

        val command = when (profile) {
            ProfileType.EXTREME_GAMING -> {
                // Synchronize telemetry preference
                context?.let { SettingsManager.setTelemetrySuppressed(it, true) }

                // 1. Lock display to max supported hardware refresh rate
                "settings put system peak_refresh_rate $hzFormatted; " +
                        "settings put system min_refresh_rate $hzFormatted; " +
                        "settings put secure peak_refresh_rate $hzFormatted; " +
                        "settings put secure min_refresh_rate $hzFormatted; " +
                        "settings put system user_refresh_rate $hzInt; " +
                        "settings put secure user_refresh_rate $hzInt; " +
                        "settings put secure refresh_rate_mode 1; " +
                        "settings put system refresh_rate_mode 1; " +

                        // 2. Ultra-responsive 0.25x animation scale
                        "settings put global window_animation_scale 0.25; " +
                        "settings put global transition_animation_scale 0.25; " +
                        "settings put global animator_duration_scale 0.25; " +

                        // 3. Touch latency boost
                        "settings put system pointer_speed 7; " +
                        "settings put secure tap_duration_threshold 0; " +
                        "settings put secure touch_blocking_period 0; " +
                        "settings put secure long_press_timeout 250; " +

                        // 4. Window blurs disabled for GPU compositor speed
                        "settings put global disable_window_blurs 1; " +
                        "settings put global accessibility_reduce_transparency 1; " +

                        // 5. Force Game Driver for all apps
                        "settings put global game_driver_all_apps 1; " +

                        // 6. Force 2D HWUI GPU acceleration
                        "settings put global force_hw_ui 1; " +

                        // 7. Hardware Vulkan rendering
                        "setprop debug.hwui.renderer skiavk; " +
                        "settings put global debug.hwui.renderer skiavk; " +

                        // 8. Bypass Phantom Process Killer
                        "device_config put activity_manager max_phantom_processes 2147483647; " +
                        "device_config set_sync_disabled_for_tests persistent; " +
                        "settings put global settings_enable_monitor_phantom_procs false; " +

                        // 9. Suppress background telemetry & error logging
                        "settings put global send_action_app_error 0; " +
                        "settings put global activity_starts_logging_enabled 0; " +
                        "settings put global dropbox_max_files 0; " +
                        "device_config put dropbox max_files 0 2>/dev/null; " +
                        "device_config put activity_manager max_error_dialogs 0 2>/dev/null; " +
                        "setprop log.tag.stats_log OFF 2>/dev/null; " +

                        // 10. Low-latency gaming DNS (Cloudflare 1.1.1.1)
                        "settings put global private_dns_mode hostname; " +
                        "settings put global private_dns_specifier 1dot1dot1dot1.cloudflare-dns.com; " +

                        // 11. Radio saver / Doze: keep radios at peak performance
                        "settings put global wifi_scan_always_enabled 1; " +
                        "settings put global mobile_data_always_on 1; " +
                        "settings delete global device_idle_constants; " +

                        // 12. Purge memory caches
                        "am kill-all && pm trim-caches 999999999999"
            }
            ProfileType.BATTERY_SAVER -> {
                context?.let { SettingsManager.setTelemetrySuppressed(it, true) }

                // 1. Lock display to baseline 60Hz / minHz
                "settings put system peak_refresh_rate $minHzFormatted; " +
                        "settings put system min_refresh_rate $minHzFormatted; " +
                        "settings put secure peak_refresh_rate $minHzFormatted; " +
                        "settings put secure min_refresh_rate $minHzFormatted; " +
                        "settings put system user_refresh_rate $minHzInt; " +
                        "settings put secure user_refresh_rate $minHzInt; " +
                        "settings put secure refresh_rate_mode 0; " +
                        "settings put system refresh_rate_mode 0; " +

                        // 2. Eliminate animations (0.0x) for zero GPU overhead
                        "settings put global window_animation_scale 0.0; " +
                        "settings put global transition_animation_scale 0.0; " +
                        "settings put global animator_duration_scale 0.0; " +

                        // 3. Disable blurs
                        "settings put global disable_window_blurs 1; " +
                        "settings put global accessibility_reduce_transparency 1; " +

                        // 4. Radio saver: disable background radio scans
                        "settings put global wifi_scan_always_enabled 0; " +
                        "settings put global ble_scan_always_enabled 0; " +
                        "settings put global mobile_data_always_on 0; " +

                        // 5. Aggressive Doze constants
                        "settings put global device_idle_constants inactive_to=30000,sensing_to=0,locating_to=0,motion_inactive_to=0,idle_after_inactive_to=0; " +
                        "settings put global app_standby_enabled 1; " +

                        // 6. Suppress logging
                        "settings put global send_action_app_error 0; " +
                        "settings put global activity_starts_logging_enabled 0; " +
                        "settings put global dropbox_max_files 0; " +

                        // 7. Reset Game Driver & HW UI
                        "settings delete global game_driver_all_apps; " +
                        "settings put global force_hw_ui 0; " +

                        // 8. Revert Vulkan to standard OpenGL
                        "setprop debug.hwui.renderer skiagl; " +
                        "settings delete global debug.hwui.renderer; " +

                        // 9. Standard touch response
                        "settings put system pointer_speed 0; " +
                        "settings delete secure tap_duration_threshold; " +
                        "settings delete secure touch_blocking_period; " +
                        "settings put secure long_press_timeout 400; " +

                        // 10. Battery-saving ad/tracker blocking DNS (AdGuard)
                        "settings put global private_dns_mode hostname; " +
                        "settings put global private_dns_specifier dns.adguard.com; " +

                        // 11. Purge background memory
                        "am kill-all && pm trim-caches 999999999999"
            }
            ProfileType.RYTUNEX_BALANCED -> {
                context?.let { SettingsManager.setTelemetrySuppressed(it, true) }

                // 1. Dynamic adaptive refresh rate (min to max)
                "settings put system peak_refresh_rate $hzFormatted; " +
                        "settings put system min_refresh_rate $minHzFormatted; " +
                        "settings put secure peak_refresh_rate $hzFormatted; " +
                        "settings put secure min_refresh_rate $minHzFormatted; " +
                        "settings put secure refresh_rate_mode 2; " +
                        "settings put system refresh_rate_mode 2; " +

                        // 2. Fluid 0.50x animations
                        "settings put global window_animation_scale 0.50; " +
                        "settings put global transition_animation_scale 0.50; " +
                        "settings put global animator_duration_scale 0.50; " +

                        // 3. Force HW UI & Touch boost
                        "settings put global force_hw_ui 1; " +
                        "settings put system pointer_speed 7; " +
                        "settings put secure tap_duration_threshold 0; " +

                        // 4. Keep blurs for Material You aesthetics
                        "settings delete global disable_window_blurs; " +
                        "settings delete global accessibility_reduce_transparency; " +

                        // 5. Game driver & Vulkan renderer
                        "settings put global game_driver_all_apps 1; " +
                        "setprop debug.hwui.renderer skiavk; " +
                        "settings put global debug.hwui.renderer skiavk; " +

                        // 6. Suppress logging
                        "settings put global send_action_app_error 0; " +
                        "settings put global activity_starts_logging_enabled 0; " +
                        "settings put global dropbox_max_files 0; " +

                        // 7. Balanced radio settings
                        "settings put global wifi_scan_always_enabled 1; " +
                        "settings delete global device_idle_constants; " +

                        // 8. Low-latency DNS (Cloudflare)
                        "settings put global private_dns_mode hostname; " +
                        "settings put global private_dns_specifier 1dot1dot1dot1.cloudflare-dns.com; " +

                        // 9. Trim caches
                        "am kill-all && pm trim-caches 999999999999"
            }
            ProfileType.STOCK_RESET -> ""
        }

        val res = ShellEngine.exec(command)

        // Force refresh system state cache so all toggles in OptimizationsScreen update immediately
        RyTuneXDeepEngine.readCurrentSystemState(forceRefresh = true)

        if (res.isSuccess) {
            CommandResult(
                command = "Profile: ${profile.name}",
                stdout = "${profile.title} applied successfully. All profile performance toggles synchronized.",
                stderr = "",
                exitCode = 0,
                isSuccess = true
            )
        } else {
            res
        }
    }

    suspend fun setAnimationScale(factor: Float): CommandResult = withContext(Dispatchers.IO) {
        val v = String.format(Locale.US, "%.2f", factor)
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
                "settings put secure refresh_rate_mode 2; " +
                "settings put system refresh_rate_mode 2; " +
                "settings put system min_refresh_rate 60.0"
        ShellEngine.exec(cmd)
    }

    suspend fun runArtOptimization(mode: String): CommandResult = withContext(Dispatchers.IO) {
        val cmd = when (mode) {
            "speed" -> "cmd package compile -m speed -a"
            "quick" -> "cmd package compile -m quicken -a"
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
