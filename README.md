<p align="center">
  <img src="https://github.com/user-attachments/assets/12bf0f27-463e-4b11-bdd5-ef6221a4ff0d" alt="RyTuneX Logo" width="128" height="128" />
</p>

<h1 align="center">RyTuneX for Android</h1>

  <p align="center">
    <b>Take Absolute Control of Android.</b><br>
    An advanced, stateless system tuning and debloating engine built with Kotlin and Jetpack Compose (Material 3 Expressive). Powered by Shizuku (ADB UID 2000) with zero root and zero background daemons.
  </p>

  <p align="center">
    <img src="https://img.shields.io/badge/Release-v1.0.2-FE6093?style=for-the-badge&logo=github&logoColor=white" alt="Version 1.0.2" />
    <img src="https://img.shields.io/badge/Android-8.0%2B%20to%2016-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android 8.0+" />
    <img src="https://img.shields.io/badge/Kotlin-Jetpack_Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin Jetpack Compose" />
    <img src="https://img.shields.io/badge/Shizuku-UID_2000-008080?style=for-the-badge" alt="Shizuku UID 2000" />
    <img src="https://img.shields.io/badge/License-AGPL_3.0-blue?style=for-the-badge" alt="License" />
  </p>
</div>

> **One engine, every screen.** RyTuneX for Android is the mobile counterpart to **[RyTuneX for Windows](https://github.com/rayenghanmi/RyTuneX)** | the same stateless architecture, the same granular control, and the same open-source foundation.

| Edition | Framework & Stack | Target Platform |
| :--- | :--- | :--- |
| 🖥️ **[RyTuneX for Windows](https://github.com/rayenghanmi/RyTuneX)** | WinUI 3 \| .NET \| C# | Windows 10 (20H1+) & Windows 11 |
| 📱 **RyTuneX for Android** | Jetpack Compose \| Material 3 Expressive \| Kotlin | Android 8.0 -> 16+ via [Shizuku](https://shizuku.rikka.app/) (No Root) |

---

## ℹ️ Introduction

**RyTuneX** is an advanced, open-source system tuning engine designed for power users, developers, and gamers. **RyTuneX for Android** brings hardware-level control to mobile devices without requiring an unlocked bootloader or root access. Operating on-demand over privileged Shizuku ADB IPC (`UID 2000`), it locks display refresh rates, cuts input latency, pre-compiles ART bytecode, and debloats deeply embedded OEM packages with zero persistent background services and zero battery drain.

---

## ✨ Key Features

* ⚡ **Adaptive Intelligence Profiles**: Deploy one-tap hardware-aware tuning profiles (*Extreme Gaming*, *Adaptive Endurance*, *RyTuneX Fluid*, and *Reset to Stock*) tailored dynamically to your display panel, SoC, and Android version.
* 🖥️ **Display, GPU & Input Latency Tuning**: Lock hardware panel refresh rates (60Hz / 90Hz / 120Hz / 144Hz+), fine-tune window animation scales, force Vulkan HWUI (`skiavk`) and 2D GPU hardware rendering, disable compositor window blurs, and boost touchscreen pointer responsiveness.
* 🧹 **Universal Debloater (UAD Next-Gen)**: Surgically freeze (`pm disable-user`) or uninstall (`--user 0`) pre-installed OEM and carrier bloatware directly on-device. Powered by an offline-bundled, live-updatable database of 1,500+ package definitions categorized by safety (*Recommended*, *Advanced*, *Expert*, *Unsafe*).
* 🚀 **ART Runtime & Storage Maintenance**: Eliminate runtime JIT micro-stutters via AOT bytecode pre-compilation (`speed-profile`, `speed`, `bg-dexopt`), run hardware NAND flash `fstrim`, compact kernel ZRAM, and bypass Android's Phantom Process Killer (`2.1B` sub-process limit).
* 🌐 **Network, Doze & Telemetry Control**: Configure system-wide encrypted Private DNS (Cloudflare, AdGuard, Quad9, Google), suppress background Wi-Fi/BLE scanning, enforce aggressive Doze deep sleep, and silence system dropbox/error loggers.
* 📊 **Live Hardware Telemetry & Privileged Shell**: Monitor real-time CPU load, core frequency scaling, RAM utilization, battery health/thermals, and active panel Hz, alongside a built-in interactive `UID 2000` terminal with diagnostic presets.

---

## 📥 Installation & Usage

1. Download and install **[Shizuku](https://shizuku.rikka.app/)**, then start the service via **Wireless Debugging** (inside Android *Developer Options*).
2. Download the latest **RyTuneX APK** from **GitHub Releases**.
3. Open **RyTuneX** and tap **Grant Shizuku UID** when prompted.
4. Navigate through the core modules:
   * **Dashboard**: Live hardware telemetry, one-tap Adaptive Intelligence Profiles, and quick maintenance actions.
   * **Tweaks**: Display & animations, input latency & GPU acceleration, Private DNS, power management, storage/ZRAM, and ART compilation.
   * **Debloat**: Search, filter, inspect, freeze, uninstall, or restore system packages using UAD Next-Gen safety tiers.
   * **Terminal**: Interactive `UID 2000` shell console with one-tap diagnostic queries.
   * **Settings**: Theme mode, Material You dynamic colors, telemetry polling interval, safety prompts, and **Restore Default System State**.

> [!WARNING]
> Always review package descriptions before batch-uninstalling system components in the **Expert** or **Unsafe** tiers. For testing unfamiliar OEM packages, use **Freeze (Safe Disable)** first.

---

## 🏗️ Technical Architecture

* **UI Layer**: Native Kotlin & Jetpack Compose implementing Google's **Material 3 Expressive** design system with the signature RyTuneX accent (`#FE6093`).
* **Core Engine**: `ShellEngine`, `RyTuneXDeepEngine`, `AdaptiveOptimizer`, `AdaptiveDeviceInspector`, and `UadDatabaseManager` execute asynchronously on `Dispatchers.IO` with zero main-thread blocking.
* **Privilege Model**: Stateless IPC via Shizuku (`ADB UID 2000`). No persistent background services or wake-locks.

### Build from Source

Requires Android Studio, JDK 17+, and Android SDK 35+.

```bash
git clone https://github.com/rayenghanmi/RyTuneX-Android.git
cd RyTuneX-Android
./gradlew assembleDebug
```

Compiled APK output: `app/build/outputs/apk/debug/app-debug.apk`

---

## 📄 License

Licensed under the **[GNU Affero General Public License v3.0](LICENSE.md)**.  
Bloatware package definitions are sourced from the [Universal Android Debloater Next-Generation](https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation) project.

## 📫 Contact & Community

* **Website**: [rayenghanmi.github.io/rytunex](https://rayenghanmi.github.io/rytunex/)
* **Discord**: [Join the RyTuneX Community](https://discord.gg/gyBzyd364t)
* **Email**: [rytunex@gmail.com](mailto:rytunex@gmail.com)

---
<p align="center">© 2026 Rayen Ghanmi. Android is a trademark of Google LLC.</p>
