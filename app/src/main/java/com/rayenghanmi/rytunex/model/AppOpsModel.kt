package com.rayenghanmi.rytunex.model

enum class AppOpMode(val raw: String, val displayName: String) {
    ALLOW("allow", "Allowed"),
    IGNORE("ignore", "Ignored"),
    DENY("deny", "Denied"),
    DEFAULT("default", "Default");

    val isBlocked: Boolean get() = this == IGNORE || this == DENY
    val isAllowedOrActive: Boolean get() = this == ALLOW || this == DEFAULT

    companion object {
        fun fromRaw(raw: String?): AppOpMode {
            return when (raw?.trim()?.lowercase()) {
                "allow" -> ALLOW
                "ignore" -> IGNORE
                "deny" -> DENY
                else -> DEFAULT
            }
        }
    }
}

enum class AppOpCategory(val title: String) {
    BATTERY_BACKGROUND("Background & Battery"),
    PRIVACY_SENSORS("Privacy & Hardware"),
    SYSTEM_INTERACTION("System & Overlays")
}

data class AppOpItem(
    val opCode: String,
    val displayName: String,
    val description: String,
    val category: AppOpCategory,
    val mode: AppOpMode = AppOpMode.DEFAULT,
    val isHighImpact: Boolean = false
)
