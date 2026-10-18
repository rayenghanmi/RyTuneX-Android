package com.rayenghanmi.rytunex.engine

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rayenghanmi.rytunex.theme.AccentColor
import com.rayenghanmi.rytunex.theme.AppThemeMode

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark");

    fun toAppThemeMode(): AppThemeMode = when (this) {
        SYSTEM -> AppThemeMode.System
        LIGHT -> AppThemeMode.Light
        DARK -> AppThemeMode.Dark
    }

    companion object {
        fun fromAppThemeMode(mode: AppThemeMode): ThemeMode = when (mode) {
            AppThemeMode.System -> SYSTEM
            AppThemeMode.Light -> LIGHT
            AppThemeMode.Dark -> DARK
        }
    }
}

enum class DebloatAction(val label: String) {
    FREEZE("Freeze (Safe Disable)"),
    UNINSTALL("Uninstall (User 0)")
}

/**
 * Centralized Settings and Preference State Manager for RyTuneX.
 * Persists user choices to SharedPreferences and exposes reactive Compose states.
 */
object SettingsManager {
    private const val PREFS_NAME = "rytunex_preferences"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_ACCENT_COLOR = "accent_color"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"
    private const val KEY_TELEMETRY_ENABLED = "telemetry_enabled"
    private const val KEY_TELEMETRY_INTERVAL = "telemetry_interval"
    private const val KEY_FAHRENHEIT = "is_fahrenheit"
    private const val KEY_DEBLOAT_ACTION = "debloat_action"
    private const val KEY_SHOW_SYSTEM_APPS = "show_system_apps"
    private const val KEY_CONFIRM_TWEAKS = "confirm_tweaks"
    private const val KEY_SUPPRESS_TELEMETRY = "suppress_telemetry"
    private const val KEY_AUTO_CHECK_UPDATES = "auto_check_updates"
    private const val KEY_INCLUDE_PRERELEASES = "include_prereleases"

    const val GITHUB_REPO = "rayenghanmi/RyTuneX-Android"
    val githubRepo: String get() = GITHUB_REPO

    var isInitialized by mutableStateOf(false)
        private set

    var autoCheckUpdates by mutableStateOf(true)
        private set

    var includePreReleases by mutableStateOf(false)
        private set

    var isTelemetrySuppressed by mutableStateOf(false)
        private set

    var themeMode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    var accentColor by mutableStateOf(AccentColor.Pink)
        private set

    var useDynamicColor by mutableStateOf(false)
        private set

    var telemetryEnabled by mutableStateOf(true)
        private set

    var telemetryIntervalMs by mutableLongStateOf(1200L)
        private set

    var isFahrenheit by mutableStateOf(false)
        private set

    var defaultDebloatAction by mutableStateOf(DebloatAction.FREEZE)
        private set

    var showSystemApps by mutableStateOf(false)
        private set

    var confirmTweaks by mutableStateOf(true)
        private set

    fun init(context: Context) {
        if (isInitialized) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val modeStr = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        themeMode = try { ThemeMode.valueOf(modeStr) } catch (_: Exception) { ThemeMode.SYSTEM }

        val storedAccent = prefs.getString(KEY_ACCENT_COLOR, null)
        accentColor = if (storedAccent != null) {
            AccentColor.fromStoredValue(storedAccent)
        } else {
            val dyn = prefs.getBoolean(KEY_DYNAMIC_COLOR, false)
            if (dyn) AccentColor.Dynamic else AccentColor.Pink
        }
        useDynamicColor = (accentColor == AccentColor.Dynamic)

        telemetryEnabled = prefs.getBoolean(KEY_TELEMETRY_ENABLED, true)
        telemetryIntervalMs = prefs.getLong(KEY_TELEMETRY_INTERVAL, 1200L)
        isFahrenheit = prefs.getBoolean(KEY_FAHRENHEIT, false)

        val actionStr = prefs.getString(KEY_DEBLOAT_ACTION, DebloatAction.FREEZE.name) ?: DebloatAction.FREEZE.name
        defaultDebloatAction = try { DebloatAction.valueOf(actionStr) } catch (_: Exception) { DebloatAction.FREEZE }

        showSystemApps = prefs.getBoolean(KEY_SHOW_SYSTEM_APPS, false)
        confirmTweaks = prefs.getBoolean(KEY_CONFIRM_TWEAKS, true)
        isTelemetrySuppressed = prefs.getBoolean(KEY_SUPPRESS_TELEMETRY, false)
        autoCheckUpdates = prefs.getBoolean(KEY_AUTO_CHECK_UPDATES, true)
        includePreReleases = prefs.getBoolean(KEY_INCLUDE_PRERELEASES, false)

        isInitialized = true
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        themeMode = mode
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun setAppThemeMode(context: Context, mode: AppThemeMode) {
        setThemeMode(context, ThemeMode.fromAppThemeMode(mode))
    }

    fun setAccentColor(context: Context, color: AccentColor) {
        accentColor = color
        useDynamicColor = (color == AccentColor.Dynamic)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACCENT_COLOR, color.storedValue)
            .putBoolean(KEY_DYNAMIC_COLOR, useDynamicColor)
            .apply()
    }

    fun setDynamicColor(context: Context, enabled: Boolean) {
        val color = if (enabled) AccentColor.Dynamic else AccentColor.Pink
        setAccentColor(context, color)
    }

    fun setTelemetryEnabled(context: Context, enabled: Boolean) {
        telemetryEnabled = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_TELEMETRY_ENABLED, enabled).apply()
    }

    fun setTelemetryInterval(context: Context, intervalMs: Long) {
        telemetryIntervalMs = intervalMs
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putLong(KEY_TELEMETRY_INTERVAL, intervalMs).apply()
    }

    fun setFahrenheit(context: Context, fahrenheit: Boolean) {
        isFahrenheit = fahrenheit
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_FAHRENHEIT, fahrenheit).apply()
    }

    fun setDebloatAction(context: Context, action: DebloatAction) {
        defaultDebloatAction = action
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_DEBLOAT_ACTION, action.name).apply()
    }

    fun setShowSystemApps(context: Context, show: Boolean) {
        showSystemApps = show
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SHOW_SYSTEM_APPS, show).apply()
    }

    fun setConfirmTweaks(context: Context, confirm: Boolean) {
        confirmTweaks = confirm
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_CONFIRM_TWEAKS, confirm).apply()
    }

    fun setTelemetrySuppressed(context: Context, suppressed: Boolean) {
        isTelemetrySuppressed = suppressed
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SUPPRESS_TELEMETRY, suppressed).apply()
    }

    fun setAutoCheckUpdates(context: Context, enabled: Boolean) {
        autoCheckUpdates = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_CHECK_UPDATES, enabled).apply()
    }

    fun setIncludePreReleases(context: Context, enabled: Boolean) {
        includePreReleases = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_INCLUDE_PRERELEASES, enabled).apply()
    }
}
