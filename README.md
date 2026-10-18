<p align="center">
  <img src="https://github.com/user-attachments/assets/12bf0f27-463e-4b11-bdd5-ef6221a4ff0d" alt="RyTuneX Logo" width="128" height="128" />
</p>

<h1 align="center">RyTuneX for Android</h1>

<p align="center">
  <b>Take Absolute Control of Android.</b><br>
  An advanced, open-source tuning engine for power users and gamers. Maximize performance, eliminate latency, and manage deeply embedded system packages and settings natively.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white" />
  <img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" />
  <img src="https://img.shields.io/badge/License-AGPL--3.0-blue?style=for-the-badge" />
</p>

> **One engine, every screen.** RyTuneX for Android is the mobile edition of [RyTuneX for Windows](https://rayenghanmi.github.io/rytunex/) — the same advanced tuning engine, the same name, the same open-source promise.

| Edition | Built with | Supports |
| --- | --- | --- |
| 🖥️ [RyTuneX for Windows](https://rayenghanmi.github.io/rytunex/) | WinUI 3 · .NET | Windows 10 (20H1+) & Windows 11 |
| 📱 **RyTuneX for Android** | Jetpack Compose · Material 3 | Android 8.0+ via [Shizuku](https://shizuku.rikka.app/) — no root |

---

## ℹ️ About

RyTuneX is an advanced, open-source tuning engine for power users and gamers. RyTuneX for Android brings that same granular, hardware-level control to your phone: tune the display panel, cut input latency, remove deeply embedded packages and manage network behaviour — stateless, with no root, no persistent daemons and no battery drain.

---

## ⚙️ Engineered for Power Users

RyTuneX provides granular, hardware-level control over your OS. Every feature is designed for absolute transparency and real-world gains.

- ⚡ **Hardware-Aware Tuning**: Live device telemetry and one-tap profiles (Extreme Gaming, Adaptive Endurance, RyTuneX Fluid, Reset to Stock) tailored to your exact panel, SoC and Android version. Lock true hardware refresh rates, tune animation scales, force GPU rendering paths and pre-compile apps with ART AOT.
- 🧹 **Advanced Package Control & Debloat Presets**: Surgically freeze, uninstall or restore pre-installed packages with safety ratings (Recommended, Advanced, Expert, Unsafe), powered by the Universal Android Debloater Next-Generation database (1,500+ definitions, bundled offline and updatable). Export debloated lists as shareable presets, copy package text, or import lists to auto-select matching apps instantly.
- 🛡️ **Root-Level Transparency**: 100% open-source (AGPL-3.0), live system telemetry, a built-in privileged terminal log, confirmation before every change and a one-tap **Roll Back All Changes**.
- 🌐 **Network & Latency Control**: System-wide encrypted DNS (Cloudflare, AdGuard, Quad9, Google or custom DoT), touchscreen latency boost, and background radio and telemetry reduction.

---

## 🧭 Usage

1. Install **[Shizuku](https://shizuku.rikka.app/)** and start it via **Wireless Debugging** (Developer Options).
2. Launch RyTuneX and tap **Grant Access**.
3. Use the tabs:
   - **Dashboard** – device health, live telemetry, hardware-aware profiles and quick actions.
   - **Optimize** – display & animations, input latency & GPU, network & latency control, system & power, storage & memory, ART compilation.
   - **Debloat** – advanced package control by safety category.
   - **Terminal** – privileged shell (UID 2000) with diagnostic presets.
   - **Settings** – theme, dynamic color, telemetry, safety and **Roll Back All Changes**.

> [!WARNING]
> Review each confirmation dialog before applying changes, especially for packages marked Expert or Unsafe.

---

## 🏗️ Technical Architecture

- 🖥️ **UI**: Jetpack Compose with Material 3 Expressive and the signature RyTuneX pink accent (`#FE6093`).
- ⚙️ **Engine layer**: `RyTuneXDeepEngine`, `AdaptiveOptimizer`, `UadDatabaseManager` and `ShellEngine` handle tweaks, profiles, package parsing and privileged execution.
- 🔧 **Privileged access**: Shizuku (ADB UID 2000), stateless — no root required.

### Build

Requires JDK 25 and Android SDK 35.

```bash
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

---

## 📄 License

Licensed under the [GNU Affero General Public License v3.0](LICENSE.md). Universal Android Debloater package definitions are maintained by the Universal Debloater Alliance.

## 📫 Contact & Community

- E-mail: <rytunex@gmail.com>
- Discord: [RyTuneX](https://discord.gg/gyBzyd364t)
- Website: [rayenghanmi.github.io/rytunex](https://rayenghanmi.github.io/rytunex/)

© 2026 Rayen Ghanmi. Android is a trademark of Google LLC.
