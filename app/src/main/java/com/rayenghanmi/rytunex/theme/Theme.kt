package com.rayenghanmi.rytunex.theme

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme

enum class AccentColor(val label: String, val storedValue: String) {
    Dynamic("Dynamic", "dynamic"),
    Pink("RyTuneX Pink", "pink");

    companion object {
        fun fromStoredValue(value: String?): AccentColor =
            entries.firstOrNull { it.storedValue == value } ?: Pink
    }
}

enum class AppThemeMode(val label: String, val storedValue: String) {
    System("System", "system"),
    Light("Light", "light"),
    Dark("Dark", "dark");

    companion object {
        fun fromStoredValue(value: String?): AppThemeMode =
            entries.firstOrNull { it.storedValue == value } ?: System
    }
}

fun accentSeed(context: Context, accentColor: AccentColor): Color = when (accentColor) {
    AccentColor.Dynamic -> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Color(context.getColor(android.R.color.system_accent1_500))
        } else {
            RyTuneXLogoColor
        }
    }
    AccentColor.Pink -> RyTuneXLogoColor
}

@Composable
fun RyTuneXTheme(
    accentColor: AccentColor = AccentColor.Pink,
    themeMode: AppThemeMode = AppThemeMode.System,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDarkTheme = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        AppThemeMode.System -> systemDarkTheme
        AppThemeMode.Light -> false
        AppThemeMode.Dark -> true
    }

    val colors: ColorScheme = if (accentColor == AccentColor.Dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        val seed = accentSeed(context, accentColor)
        val generatedColors = rememberDynamicColorScheme(
            seedColor = seed,
            isDark = darkTheme,
            isAmoled = false,
            style = PaletteStyle.TonalSpot,
        )
        if (darkTheme) {
            generatedColors
        } else {
            generatedColors.copy(
                onSurfaceVariant = lerp(generatedColors.surface, generatedColors.onSurface, 0.8f),
            )
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography
    ) {
        ProvideTextStyle(value = AppTypography.bodyMedium, content = content)
    }
}

// Backward compatibility overload for existing call sites
@Composable
fun RyTuneXTheme(
    darkTheme: Boolean,
    useDynamicColor: Boolean,
    content: @Composable () -> Unit
) {
    val accent = if (useDynamicColor) AccentColor.Dynamic else AccentColor.Pink
    val mode = if (darkTheme) AppThemeMode.Dark else AppThemeMode.Light
    RyTuneXTheme(
        accentColor = accent,
        themeMode = mode,
        content = content
    )
}
