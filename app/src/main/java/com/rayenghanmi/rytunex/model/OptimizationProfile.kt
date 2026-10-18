package com.rayenghanmi.rytunex.model

import androidx.compose.ui.graphics.Color
import com.rayenghanmi.rytunex.theme.SemanticAmber
import com.rayenghanmi.rytunex.theme.SemanticBlue
import com.rayenghanmi.rytunex.theme.SemanticRed

enum class ProfileType(
    val title: String,
    val subtitle: String,
    val description: String,
    val accentColor: Color
) {
    EXTREME_GAMING(
        title = "Extreme Gaming",
        subtitle = "Peak Panel Hz & Minimal Input Latency",
        description = "Locks panel to maximum hardware refresh rate (120Hz/144Hz+), drops animation scale to 0.25x, and disables background telemetry logging.",
        accentColor = SemanticRed
    ),
    BATTERY_SAVER(
        title = "Adaptive Endurance",
        subtitle = "Power Conservation & Throttling Defense",
        description = "Down-clocks panel to baseline 60Hz, eliminates UI compositor transitions (0.0x), and freezes background telemetry daemons.",
        accentColor = SemanticAmber
    ),
    RYTUNEX_BALANCED(
        title = "RyTuneX Fluid",
        subtitle = "Material You Dynamic Adaptive Tuning",
        description = "Adaptive high refresh rate with 0.5x fluid animation transitions, trims active RAM caches, and applies ART speed-profile compiler.",
        accentColor = SemanticBlue
    ),
    STOCK_RESET(
        title = "Reset to Stock",
        subtitle = "OEM Default System Baseline",
        description = "Reverts animation scales to 1.0x, deletes system peak/min refresh overrides, and restores default OEM logging.",
        accentColor = Color(0xFF5F6368)
    );

    fun getDescription(maxHz: Int? = null): String {
        return if (this == EXTREME_GAMING && maxHz != null && maxHz > 0) {
            "Locks panel to maximum hardware refresh rate (${maxHz}Hz), drops animation scale to 0.25x, and disables background telemetry logging."
        } else {
            description
        }
    }
}
