package com.rayenghanmi.rytunex.model

import androidx.compose.ui.graphics.Color
import com.rayenghanmi.rytunex.theme.SemanticAmber
import com.rayenghanmi.rytunex.theme.SemanticBlue
import com.rayenghanmi.rytunex.theme.SemanticRed

// Device facts used to build profile descriptions and apply commands
data class ProfileDeviceSpec(
    val maxHz: Int = 60,
    val lowHz: Int = 60,
    val multiMode: Boolean = false,
    val vulkanSupported: Boolean = false
)

enum class ProfileType(
    val title: String,
    val subtitle: String,
    val accentColor: Color
) {
    EXTREME_GAMING(
        title = "Extreme Gaming",
        subtitle = "Peak Panel Hz & Minimal Input Latency",
        accentColor = SemanticRed
    ),
    BATTERY_SAVER(
        title = "Adaptive Endurance",
        subtitle = "Power Conservation & Throttling Defense",
        accentColor = SemanticAmber
    ),
    RYTUNEX_BALANCED(
        title = "RyTuneX Fluid",
        subtitle = "Material You Dynamic Adaptive Tuning",
        accentColor = SemanticBlue
    ),
    STOCK_RESET(
        title = "Reset to Stock",
        subtitle = "OEM Default System Baseline",
        accentColor = Color(0xFF5F6368)
    );

    fun getDescription(spec: ProfileDeviceSpec): String = when (this) {
        EXTREME_GAMING -> buildString {
            if (spec.multiMode) {
                append("Locks the panel to peak ${spec.maxHz}Hz, ")
            } else {
                append("Panel is fixed at ${spec.maxHz}Hz (no refresh override), ")
            }
            append("sets 0.25x animations, disables window blurs, forces the GPU game driver and 2D rendering, ")
            append(if (spec.vulkanSupported) "switches HWUI to Vulkan, " else "keeps the OpenGL renderer, ")
            append("raises the phantom process limit and suppresses error logging. Private DNS is untouched.")
        }
        BATTERY_SAVER -> buildString {
            if (spec.multiMode) {
                append("Down-clocks the panel to ${spec.lowHz}Hz, ")
            } else {
                append("Panel already runs at ${spec.lowHz}Hz (no refresh override), ")
            }
            append("disables animations and blurs, turns off background Wi-Fi/BLE/mobile scans, ")
            append("enforces quick deep-sleep Doze and stops error logging. Private DNS is untouched.")
        }
        RYTUNEX_BALANCED -> buildString {
            if (spec.multiMode) {
                append("Runs adaptive refresh from ${spec.lowHz}Hz to ${spec.maxHz}Hz, ")
            } else {
                append("Keeps the panel at ${spec.maxHz}Hz, ")
            }
            append("sets 0.50x animations, keeps blurs, enables touch boost and GPU rendering, ")
            append(if (spec.vulkanSupported) "switches HWUI to Vulkan, " else "keeps the OpenGL renderer, ")
            append("and suppresses error logging. Private DNS is untouched.")
        }
        STOCK_RESET -> "Reverts animation scales to 1.0x, deletes peak/min refresh overrides, restores blurs, OpenGL rendering, stock touch timing, OEM logging, Doze and radio behavior. Also clears resolution and efficiency overrides."
    }
}