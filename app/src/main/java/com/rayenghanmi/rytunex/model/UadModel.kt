package com.rayenghanmi.rytunex.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.rayenghanmi.rytunex.theme.*

enum class UadRecommendation(
    val displayName: String,
    val description: String,
    val badgeColor: Color,
    val priority: Int
) {
    RECOMMENDED(
        displayName = "Recommended",
        description = "Safe to remove without functional loss or app regressions.",
        badgeColor = SemanticGreen,
        priority = 1
    ),
    ADVANCED(
        displayName = "Advanced",
        description = "Safe if you do not use this specific service or have an alternative installed.",
        badgeColor = SemanticBlue,
        priority = 2
    ),
    EXPERT(
        displayName = "Expert",
        description = "May disable secondary features (backup, casting, speech recognition).",
        badgeColor = SemanticAmber,
        priority = 3
    ),
    UNSAFE(
        displayName = "Unsafe",
        description = "Core system component or dependency. Removing may cause bootloops.",
        badgeColor = SemanticRed,
        priority = 4
    ),
    CUSTOM(
        displayName = "Unlisted",
        description = "Package not found in UAD database.",
        badgeColor = Color(0xFF73777F),
        priority = 5
    );

    companion object {
        fun fromString(str: String?): UadRecommendation = when (str?.trim()?.lowercase()) {
            "recommended" -> RECOMMENDED
            "advanced" -> ADVANCED
            "expert" -> EXPERT
            "unsafe" -> UNSAFE
            else -> CUSTOM
        }
    }
}

@Immutable
data class UadPackageEntry(
    val packageName: String,
    val list: String = "Oem", // "Oem", "Google", "Aosp", "Carrier", "Misc"
    val description: String = "",
    val removal: UadRecommendation = UadRecommendation.CUSTOM,
    val dependencies: List<String> = emptyList(),
    val neededBy: List<String> = emptyList()
)

@Immutable
data class DebloatApp(
    val packageName: String,
    val appName: String,
    val isSystem: Boolean,
    val isEnabled: Boolean,
    val isInstalled: Boolean,
    val uadEntry: UadPackageEntry?,
    val isSelected: Boolean = false
) {
    val recommendation: UadRecommendation
        get() = if (!isSystem || uadEntry?.list?.equals("User", ignoreCase = true) == true) {
            UadRecommendation.CUSTOM
        } else {
            uadEntry?.removal ?: UadRecommendation.CUSTOM
        }

    val listCategory: String
        get() = if (!isSystem || uadEntry?.list?.equals("User", ignoreCase = true) == true) {
            "User"
        } else {
            uadEntry?.list ?: "System"
        }

    val description: String
        get() = if (!isSystem) {
            "User-installed application. Not flagged as system bloatware."
        } else {
            uadEntry?.description?.ifBlank { "Pre-installed system application." } ?: "Pre-installed system application."
        }
}
