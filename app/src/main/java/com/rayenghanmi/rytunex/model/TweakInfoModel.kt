package com.rayenghanmi.rytunex.model

import androidx.compose.ui.graphics.Color
import com.rayenghanmi.rytunex.theme.SemanticAmber
import com.rayenghanmi.rytunex.theme.SemanticBlue
import com.rayenghanmi.rytunex.theme.SemanticGreen
import com.rayenghanmi.rytunex.theme.SemanticRed

enum class TweakSafetyLevel(
    val title: String,
    val color: Color,
    val description: String
) {
    SAFE(
        title = "Safe",
        color = SemanticGreen,
        description = "Universal compatibility, zero negative impact & 100% reversible baseline."
    ),
    MODERATE(
        title = "Moderate",
        color = SemanticBlue,
        description = "Safe and fully reversible, but comes with known functional or background trade-offs."
    ),
    ADVANCED(
        title = "Advanced",
        color = SemanticAmber,
        description = "For power users. Hardware dependent; review OEM notes before enabling."
    ),
    EXPERIMENTAL(
        title = "Experimental",
        color = SemanticRed,
        description = "Forces developer-level overrides that may increase battery consumption or cause driver anomalies on select OEMs."
    )
}

data class TweakInfo(
    val id: String,
    val title: String,
    val category: String,
    val safetyLevel: TweakSafetyLevel,
    val summary: String,
    val mechanism: String,
    val shellCommand: String,
    val benefits: List<String>,
    val tradeOffs: List<String>,
    val oemNotes: String? = null,
    val requiresReboot: Boolean = false
)

object TweakRegistry {

    private val tweaks = listOf(
        TweakInfo(
            id = "display_refresh_rate",
            title = "Display Refresh Rate",
            category = "Display & Animations",
            safetyLevel = TweakSafetyLevel.SAFE,
            summary = "Locks or sets peak and minimum display refresh rates (e.g. 60Hz, 90Hz, 120Hz, 144Hz) to prevent stuttery dynamic switching or conserve battery.",
            mechanism = "Writes to Android system settings peak_refresh_rate and min_refresh_rate. On supported LTPO and high-refresh panels, this instructs SurfaceFlinger to cap or lock the refresh frequency.",
            shellCommand = "settings put system peak_refresh_rate <hz>; settings put system min_refresh_rate <hz>",
            benefits = listOf(
                "Eliminates micro-stutter from dynamic refresh rate fluctuations",
                "Forces maximum fluidity across all apps when locked to peak Hz",
                "Enforces battery endurance when locked to 60Hz"
            ),
            tradeOffs = listOf(
                "Locking to 120Hz/144Hz consumes slightly more display panel power",
                "Locking to 60Hz reduces touch motion fluidity"
            ),
            oemNotes = "On Samsung One UI, Adaptive Refresh may reset after display sleep unless minimum Hz matches peak Hz. On Xiaomi HyperOS/MIUI, refresh rate is locked cleanly."
        ),
        TweakInfo(
            id = "anim_scale",
            title = "Window Animation Duration Scale",
            category = "Display & Animations",
            safetyLevel = TweakSafetyLevel.SAFE,
            summary = "Speeds up or disables transition, window, and animator scales across the entire Android OS.",
            mechanism = "Modifies global settings window_animation_scale, transition_animation_scale, and animator_duration_scale to accelerate WindowManager Choreographer timing.",
            shellCommand = "settings put global window_animation_scale <val>; settings put global transition_animation_scale <val>; settings put global animator_duration_scale <val>",
            benefits = listOf(
                "Makes the entire device feel twice as fast and responsive",
                "Cuts navigation frame wait times between app transitions",
                "Zero risk or system stability impact"
            ),
            tradeOffs = listOf(
                "Setting to 0.0x turns off animation effects completely"
            ),
            oemNotes = "Universal across all Android 8–16 devices."
        ),
        TweakInfo(
            id = "predictive_back",
            title = "Predictive Back Navigation Gesture",
            category = "Display & Animations",
            safetyLevel = TweakSafetyLevel.SAFE,
            summary = "Enables Android 13–15 fluid cross-activity and in-app predictive swipe back previews.",
            mechanism = "Enables enable_predictive_back_animation in both global and secure settings namespaces.",
            shellCommand = "settings put global enable_predictive_back_animation 1; settings put secure enable_predictive_back_animation 1",
            benefits = listOf(
                "Preview the destination screen before completing the back swipe gesture",
                "Material 3 Expressive transition fluidity",
                "Fully native AOSP gesture implementation"
            ),
            tradeOffs = listOf(
                "Apps that have not implemented predictive back APIs fall back to legacy back press"
            ),
            oemNotes = "Requires Android 13 or newer. Harmlessly ignored on older Android builds."
        ),
        TweakInfo(
            id = "resolution_override",
            title = "Output Resolution Override",
            category = "Render Resolution & Scaling",
            safetyLevel = TweakSafetyLevel.ADVANCED,
            summary = "Scales the rendered frame buffer to lower resolutions (e.g. 80%, 75%) to relieve GPU burden in heavy 3D titles and lower heat.",
            mechanism = "Invokes WindowManager 'wm size <width>x<height>' to reconfigure the hardware display pipeline output frame buffer.",
            shellCommand = "wm size <width>x<height> (or 'wm size reset' to restore)",
            benefits = listOf(
                "Significantly boosts FPS in demanding 3D games and emulators",
                "Lowers GPU operating temperatures and prevents thermal throttling",
                "Extends gaming battery life on high-density 1440p+ displays"
            ),
            tradeOffs = listOf(
                "Slightly softer text and UI sharpness on high-PPI displays",
                "Certain OEM lock screens or keyboards may require a screen off/on cycle to rescale"
            ),
            oemNotes = "Always maintains native aspect ratio. If UI scaling looks unusual, tapping Reset immediately restores factory baseline."
        ),
        TweakInfo(
            id = "touch_boost",
            title = "Touchscreen Latency Boost",
            category = "Input Latency & GPU Acceleration",
            safetyLevel = TweakSafetyLevel.SAFE,
            summary = "Maximizes pointer tracking speed and reduces long-press gesture delay thresholds for near-instant touch response.",
            mechanism = "Adjusts system setting pointer_speed to 7 (maximum) and tunes secure setting long_press_timeout to 250ms (from standard 400ms).",
            shellCommand = "settings put system pointer_speed 7; settings put secure long_press_timeout 250",
            benefits = listOf(
                "Eliminates tap latency in rhythm games, competitive shooters, and rapid typing",
                "Faster gesture recognition and snappier scrolling response"
            ),
            tradeOffs = listOf(
                "Slightly more sensitive touch response may require brief adaptation"
            ),
            oemNotes = "Universal across all OEMs and touch digitizers."
        ),
        TweakInfo(
            id = "disable_blurs",
            title = "Disable GPU Window Blurs",
            category = "Input Latency & GPU Acceleration",
            safetyLevel = TweakSafetyLevel.MODERATE,
            summary = "Disables real-time background blur shaders on quick settings panels, volume sliders, and app switchers.",
            mechanism = "Sets disable_window_blurs 1 and accessibility_reduce_transparency 1 in global settings.",
            shellCommand = "settings put global disable_window_blurs 1; settings put global accessibility_reduce_transparency 1",
            benefits = listOf(
                "Eliminates heavy multi-pass blur shaders",
                "Drastically cuts GPU frame drops when pulling down notification shade or opening launcher",
                "Reduces GPU heat during multitasking"
            ),
            tradeOffs = listOf(
                "Translucent frosted glass visuals are replaced with solid tinted surfaces"
            ),
            oemNotes = "Highly recommended for mid-range MediaTek, Exynos, and older Adreno GPUs."
        ),
        TweakInfo(
            id = "game_driver",
            title = "Force Hardware Game Driver",
            category = "Input Latency & GPU Acceleration",
            safetyLevel = TweakSafetyLevel.SAFE,
            summary = "Instructs Android's Graphics Environment to route all application draw calls through the updatable OEM GPU game driver.",
            mechanism = "Writes game_driver_all_apps 1 to global settings.",
            shellCommand = "settings put global game_driver_all_apps 1",
            benefits = listOf(
                "Leverages vendor-optimized GPU game driver shaders",
                "Improved Vulkan and OpenGL pipeline throughput",
                "Zero risk; framework handles fallbacks automatically"
            ),
            tradeOffs = listOf(
                "None. Default driver is retained if vendor game driver is absent"
            ),
            oemNotes = "Available on devices with Qualcomm Snapdragon Elite Gaming or MediaTek HyperEngine drivers (Android 10+)."
        ),
        TweakInfo(
            id = "force_hw_ui",
            title = "Hardware 2D GPU Rendering",
            category = "Input Latency & GPU Acceleration",
            safetyLevel = TweakSafetyLevel.SAFE,
            summary = "Offloads all 2D canvas drawing from CPU software rasterizer to GPU hardware acceleration.",
            mechanism = "Sets force_hw_ui 1 in global settings.",
            shellCommand = "settings put global force_hw_ui 1",
            benefits = listOf(
                "Reduces CPU load during UI layout drawing and complex lists",
                "Smoother 120Hz scrolling across older applications",
                "Lower CPU thermal profile"
            ),
            tradeOffs = listOf(
                "Negligible increase in GPU power consumption during active scrolling"
            ),
            oemNotes = "Universal Android standard tunable."
        ),
        TweakInfo(
            id = "vulkan_backend",
            title = "Vulkan HWUI Graphics Backend",
            category = "Input Latency & GPU Acceleration",
            safetyLevel = TweakSafetyLevel.ADVANCED,
            summary = "Switches Android's HWUI rendering engine from OpenGL ES (skiagl) to Vulkan (skiavk).",
            mechanism = "Sets system property debug.hwui.renderer skiavk and global setting debug.hwui.renderer skiavk.",
            shellCommand = "setprop debug.hwui.renderer skiavk; settings put global debug.hwui.renderer skiavk",
            benefits = listOf(
                "Lower CPU driver overhead on draw-calls",
                "Faster app startup on modern Adreno 7xx and Immortalis GPUs",
                "Modern graphics pipeline"
            ),
            tradeOffs = listOf(
                "May cause visual glitches or black boxes on outdated GPU drivers or buggy OEM implementations (e.g. early MIUI builds)"
            ),
            oemNotes = "Works great on modern Snapdragon 8 Gen 1/2/3 and Dimensity 9000 series. If any UI artifact appears, toggle back to skiagl (OpenGL)."
        ),
        TweakInfo(
            id = "disable_hw_overlays",
            title = "Force GPU Compositing (Disable HW Overlays)",
            category = "Input Latency & GPU Acceleration",
            safetyLevel = TweakSafetyLevel.EXPERIMENTAL,
            summary = "Forces the GPU to composite all screen layers instead of using the Hardware Composer (HWC) display processor.",
            mechanism = "Sets disable_hw_overlays 1 in global settings.",
            shellCommand = "settings put global disable_hw_overlays 1",
            benefits = listOf(
                "Fixes screen tearing, jitter, and frame pacing issues in certain emulators and 3D games",
                "Bypasses buggy HWC drivers"
            ),
            tradeOffs = listOf(
                "Higher GPU power consumption because GPU must remain active for every frame compositing"
            ),
            oemNotes = "Android developer option. Reverts automatically on device reboot."
        ),
        TweakInfo(
            id = "app_freezer",
            title = "Cached Apps Freezer",
            category = "Input Latency & GPU Acceleration",
            safetyLevel = TweakSafetyLevel.SAFE,
            summary = "Freezes the CPU execution of cached background applications using Linux cgroups v2.",
            mechanism = "Writes cached_apps_freezer_enabled 1 to global settings.",
            shellCommand = "settings put global cached_apps_freezer_enabled 1",
            benefits = listOf(
                "Prevents background apps from silently draining CPU cycles and battery while suspended",
                "Reduces background thermal buildup",
                "Leaves RAM cached for instant reopening"
            ),
            tradeOffs = listOf(
                "Cached apps take a split-second to thaw when switched back"
            ),
            oemNotes = "Native AOSP Linux cgroups feature (Android 11+)."
        ),
        TweakInfo(
            id = "private_dns",
            title = "Private Encrypted DNS",
            category = "Network & Latency Control",
            safetyLevel = TweakSafetyLevel.SAFE,
            summary = "Encrypts all DNS queries via DNS-over-TLS (DoT) using Cloudflare, AdGuard, Quad9, or Google.",
            mechanism = "Configures private_dns_mode hostname and private_dns_specifier in global settings.",
            shellCommand = "settings put global private_dns_mode hostname; settings put global private_dns_specifier <host>",
            benefits = listOf(
                "Blocks intrusive web and in-app advertisements system-wide without VPN (AdGuard)",
                "Ultra-fast DNS resolution and lookup latency (Cloudflare 1.1.1.1)",
                "Protects privacy against ISP snooping and MITM attacks"
            ),
            tradeOffs = listOf(
                "Certain captive portal Wi-Fi login networks (hotels, airports) require setting to Off temporarily to authenticate"
            ),
            oemNotes = "Fully native Android feature (Android 9+)."
        ),
        TweakInfo(
            id = "radio_saver",
            title = "Wi-Fi & Bluetooth Radio Saver",
            category = "Network & Latency Control",
            safetyLevel = TweakSafetyLevel.MODERATE,
            summary = "Stops continuous background Wi-Fi and Bluetooth LE beacon scans when radios are idle, and disables mobile data keepalive while Wi-Fi is active.",
            mechanism = "Sets wifi_scan_always_enabled 0, ble_scan_always_enabled 0, and mobile_data_always_on 0.",
            shellCommand = "settings put global wifi_scan_always_enabled 0; settings put global ble_scan_always_enabled 0; settings put global mobile_data_always_on 0",
            benefits = listOf(
                "Significantly cuts standby battery drain caused by background radio wakeups",
                "Reduces Wi-Fi and cellular modem chip operating temperatures",
                "Prevents silent background location tracking via Wi-Fi beacons"
            ),
            tradeOffs = listOf(
                "Cellular data switch may take 1-2 seconds longer when walking out of Wi-Fi range",
                "Some smartwatch companion apps may take longer to discover nearby accessories"
            ),
            oemNotes = "Safe on all devices; toggle off if you rely on geofencing indoor navigation."
        ),
        TweakInfo(
            id = "wifi_scan_throttle",
            title = "Wi-Fi Scan Throttle Bypass",
            category = "Network & Latency Control",
            safetyLevel = TweakSafetyLevel.MODERATE,
            summary = "Disables Android's 4-scan per 2-minute throttling limit on background Wi-Fi scans.",
            mechanism = "Sets wifi_scan_throttle_enabled 0 in global settings.",
            shellCommand = "settings put global wifi_scan_throttle_enabled 0",
            benefits = listOf(
                "Faster network roaming and instant AP switching in large mesh networks",
                "Rapid Wi-Fi triangulation for GPS navigation apps"
            ),
            tradeOffs = listOf(
                "Slightly higher radio power consumption if apps scan repeatedly"
            ),
            oemNotes = "Default is enabled (throttled). Disable only if you experience slow network handoffs."
        ),
        TweakInfo(
            id = "phantom_killer",
            title = "Phantom Process Killer Bypass",
            category = "System & Power Management",
            safetyLevel = TweakSafetyLevel.MODERATE,
            summary = "Raises the Android 12–15 phantom process ceiling from 32 processes to 2,147,483,647 (unlimited).",
            mechanism = "Uses device_config put activity_manager max_phantom_processes 2147483647 and persistent test sync flags.",
            shellCommand = "device_config put activity_manager max_phantom_processes 2147483647; settings put global settings_enable_monitor_phantom_procs false",
            benefits = listOf(
                "Prevents Termux, Linux chroots, torrent downloaders, and background compilation tasks from being abruptly killed by Android",
                "Zero crashes in power applications and terminal environments"
            ),
            tradeOffs = listOf(
                "Allows complex child processes to consume available RAM without framework intervention"
            ),
            oemNotes = "Crucial fix for Termux and power tools on Android 12+."
        ),
        TweakInfo(
            id = "block_telemetry",
            title = "Block Telemetry & Error Logging",
            category = "System & Power Management",
            safetyLevel = TweakSafetyLevel.SAFE,
            summary = "Suppresses continuous app crash reports, dropbox diagnostic dumps, and stats logging to NAND flash.",
            mechanism = "Sets send_action_app_error 0, dropbox_max_files 0, and activity_starts_logging_enabled 0.",
            shellCommand = "settings put global send_action_app_error 0; settings put global dropbox_max_files 0; settings put global activity_starts_logging_enabled 0",
            benefits = listOf(
                "Reduces flash storage write amplification and I/O wear",
                "Prevents periodic analytics transmission to OEM servers",
                "Saves storage space in /data/system/dropbox"
            ),
            tradeOffs = listOf(
                "Crash logs for debugging won't be saved in system dropbox"
            ),
            oemNotes = "Universal and completely harmless."
        ),
        TweakInfo(
            id = "aggressive_doze",
            title = "Aggressive Doze & Deep Sleep",
            category = "System & Power Management",
            safetyLevel = TweakSafetyLevel.MODERATE,
            summary = "Forces Android to enter deep sleep Doze mode within 30 seconds of screen-off instead of waiting 30–60 minutes.",
            mechanism = "Tunes device_idle_constants with inactive_to=30000,sensing_to=0,locating_to=0,motion_inactive_to=0.",
            shellCommand = "settings put global device_idle_constants inactive_to=30000,sensing_to=0,locating_to=0,motion_inactive_to=0,idle_after_inactive_to=0",
            benefits = listOf(
                "Drastically cuts overnight battery drain (down to 0.2%–0.5% per hour)",
                "Puts CPU clusters into deep sleep immediately"
            ),
            tradeOffs = listOf(
                "Non-high-priority background notifications (e.g. promotional emails) will arrive in batches when the screen is turned on"
            ),
            oemNotes = "High-priority messages (WhatsApp, Telegram, Phone calls, SMS, Alarms) bypass Doze via FCM high priority and continue ringing normally."
        ),
        TweakInfo(
            id = "app_standby",
            title = "App Standby & Doze Buckets",
            category = "System & Power Management",
            safetyLevel = TweakSafetyLevel.SAFE,
            summary = "Enforces Android framework standby buckets (Active, Working Set, Frequent, Rare, Restricted) on idle background apps.",
            mechanism = "Enables app_standby_enabled 1 in global settings.",
            shellCommand = "settings put global app_standby_enabled 1",
            benefits = listOf(
                "Restricts CPU, jobs, and alarms for rarely opened apps",
                "Improves overall device standby longevity"
            ),
            tradeOffs = listOf(
                "Apps placed into the Rare bucket have restricted background sync frequencies"
            ),
            oemNotes = "AOSP standard battery architecture."
        ),
        TweakInfo(
            id = "adaptive_battery",
            title = "Adaptive On-Device Battery",
            category = "System & Power Management",
            safetyLevel = TweakSafetyLevel.SAFE,
            summary = "Uses on-device machine learning to predict which apps you will use in the coming hours and optimizes background execution accordingly.",
            mechanism = "Enables adaptive_battery_management_enabled 1 in global settings.",
            shellCommand = "settings put global adaptive_battery_management_enabled 1",
            benefits = listOf(
                "Prevents apps you rarely use from waking the CPU",
                "Learns and adapts to your daily routine"
            ),
            tradeOffs = listOf(
                "May take several days of learning after a factory reset to reach optimal efficiency"
            ),
            oemNotes = "Universal on Android 9+."
        ),
        TweakInfo(
            id = "netstats_disable",
            title = "Disable Network Stats Collection",
            category = "System & Power Management",
            safetyLevel = TweakSafetyLevel.MODERATE,
            summary = "Stops periodic netstats traffic accounting and its continuous database flash writes.",
            mechanism = "Writes netstats_enabled 0 to global settings.",
            shellCommand = "settings put global netstats_enabled 0",
            benefits = listOf(
                "Eliminates frequent SQLite database writes to /data/system/netstats",
                "Reduces background I/O latency"
            ),
            tradeOffs = listOf(
                "The 'Data Usage' graph in Android Settings won't record daily per-app megabytes while disabled"
            ),
            oemNotes = "Ideal for power users who don't monitor carrier data caps in Android Settings."
        ),
        TweakInfo(
            id = "stay_awake_charging",
            title = "Stay Awake While Charging",
            category = "System & Power Management",
            safetyLevel = TweakSafetyLevel.MODERATE,
            summary = "Keeps the screen active while plugged into AC, USB, or wireless chargers.",
            mechanism = "Writes stay_on_while_plugged_in 7 to global settings.",
            shellCommand = "settings put global stay_on_while_plugged_in 7",
            benefits = listOf(
                "Useful for desk docks, monitoring dashboards, navigation, and terminal compiling"
            ),
            tradeOffs = listOf(
                "Display remains illuminated; screen burn-in risk if left unattended on OLED panels for days"
            ),
            oemNotes = "Android developer tunable."
        ),
        TweakInfo(
            id = "sticky_battery_saver",
            title = "Sticky Battery Saver",
            category = "System & Power Management",
            safetyLevel = TweakSafetyLevel.SAFE,
            summary = "Prevents Android from automatically turning off Battery Saver mode once the battery reaches 80%–90% charge.",
            mechanism = "Writes low_power_sticky 1 to global settings.",
            shellCommand = "settings put global low_power_sticky 1",
            benefits = listOf(
                "Keeps power conservation and dark theme active continuously throughout the day"
            ),
            tradeOffs = listOf(
                "Manual toggle required to turn Battery Saver off"
            ),
            oemNotes = "Supported across all Android 10+ devices."
        ),
        TweakInfo(
            id = "auto_battery_saver_trigger",
            title = "Automatic Battery Saver Trigger",
            category = "System & Power Management",
            safetyLevel = TweakSafetyLevel.SAFE,
            summary = "Configures the exact battery percentage threshold (e.g. 15%, 20%, 30%) at which Android enters Battery Saver automatically.",
            mechanism = "Writes low_power_trigger_level <pct> to global settings.",
            shellCommand = "settings put global low_power_trigger_level <pct>",
            benefits = listOf(
                "Automatic defense against unexpected shutdown when battery is low"
            ),
            tradeOffs = listOf(
                "None. Fully user customizable"
            ),
            oemNotes = "Universal Android standard."
        ),
        TweakInfo(
            id = "flush_log_buffers",
            title = "Flush Log Buffers",
            category = "System & Power Management",
            safetyLevel = TweakSafetyLevel.SAFE,
            summary = "Clears accumulated RAM and storage buffers in system logcat and kernel dmesg ring.",
            mechanism = "Executes logcat -b all -c and dmesg -c.",
            shellCommand = "logcat -b all -c; dmesg -c",
            benefits = listOf(
                "Reclaims system RAM used by bloated circular log buffers",
                "Clears old sensitive logs"
            ),
            tradeOffs = listOf(
                "Previous crash logs are purged from memory"
            ),
            oemNotes = "Instantly executed, 100% safe."
        ),
        TweakInfo(
            id = "storage_trim",
            title = "UFS & eMMC Flash Storage TRIM",
            category = "Storage & Memory Optimization",
            safetyLevel = TweakSafetyLevel.SAFE,
            summary = "Issues kernel discard (fstrim) across all flash partitions to inform the NAND controller which dirty blocks can be erased.",
            mechanism = "Executes sm fstrim via storage manager or pm trim-caches fallback.",
            shellCommand = "sm fstrim || pm trim-caches 999999999999",
            benefits = listOf(
                "Restores fast random write speeds on UFS and eMMC storage",
                "Reduces storage write amplification",
                "Prevents long-term I/O slowdowns"
            ),
            tradeOffs = listOf(
                "Takes 2–10 seconds to execute while the flash controller purges blocks"
            ),
            oemNotes = "Android runs this periodically, but running it manually after heavy updates or file downloads restores immediate peak flash performance."
        ),
        TweakInfo(
            id = "memory_compaction",
            title = "Kernel ZRAM Memory Compaction",
            category = "Storage & Memory Optimization",
            safetyLevel = TweakSafetyLevel.SAFE,
            summary = "Forces memory compaction across kernel heap pages and compresses anonymous memory into ZRAM.",
            mechanism = "Invokes cmd activity compact full and triggers Linux kernel vm compaction.",
            shellCommand = "cmd activity compact full || echo 1 > /proc/sys/vm/compact_memory",
            benefits = listOf(
                "Defragments physical RAM pages",
                "Frees up continuous RAM blocks for heavy apps and games",
                "Reduces Out-Of-Memory (OOM) killer terminations"
            ),
            tradeOffs = listOf(
                "Brief spike in CPU activity during compaction"
            ),
            oemNotes = "Works on all Android versions."
        ),
        TweakInfo(
            id = "art_compilation",
            title = "AOT Bytecode Pre-Compilation",
            category = "ART Runtime Compilation",
            safetyLevel = TweakSafetyLevel.SAFE,
            summary = "Ahead-Of-Time (AOT) compiles all installed application DEX bytecode into native ARM machine code using dex2oat.",
            mechanism = "Executes cmd package compile -m <mode> -a via PackageManager.",
            shellCommand = "cmd package compile -m speed-profile -a (or -m speed -a)",
            benefits = listOf(
                "Completely eliminates JIT compilation stutter and jank",
                "Cuts app launch times by 30%–50%",
                "Peak execution speed for all apps"
            ),
            tradeOffs = listOf(
                "Requires phone to be plugged in or on good battery while compiling",
                "Uses slightly more internal storage for native ELF/ODEX artifacts"
            ),
            oemNotes = "Takes 2–8 minutes depending on CPU core count and number of apps. 100% reversible via cmd package compile --reset."
        )
    )

    private val tweakMap = tweaks.associateBy { it.id }

    fun get(id: String): TweakInfo? = tweakMap[id]

    fun getAll(): List<TweakInfo> = tweaks
}
