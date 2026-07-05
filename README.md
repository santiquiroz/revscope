# RevScope

> Real-time OBD2 racing telemetry for Android — with adaptive AI that learns your vehicle, audio alerts that reach your helmet, and an Android Auto screen for the car.

[![Release](https://img.shields.io/github/v/release/santiquiroz/revscope?color=E8FF00&label=release)](https://github.com/santiquiroz/revscope/releases)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple.svg)](https://kotlinlang.org)
[![Tests](https://img.shields.io/badge/tests-106%20passing-brightgreen.svg)]()

RevScope connects to any ELM327 Bluetooth adapter and turns your phone into a racing HUD: live RPM, speed, coolant temp, boost, adaptive gear detection, battery voltage, fault codes with AI explanations, trip reports with charts — and it talks to you through your helmet intercom when something's wrong.

Built and battle-tested on a **TVS Apache 160 4V** and a **Mazda CX-30** with a Vgate iCar Pro. No paywall. No subscription. No ads.

---

## Why RevScope

### It survives the real world.
Cheap ELM327 clones lie, hang, and drop mid-corner. RevScope's transport layer was hardened against a real adapter, not an emulator: connect watchdogs, half-duplex command serialization, `SEARCHING...` banner tolerance, dead-link circuit breaker, and automatic reconnection — turn the bike off to refuel and telemetry resumes by itself when you're back.

### It knows which vehicle it's plugged into.
On connect, RevScope reads the VIN (Mode 09) and activates the matching vehicle profile automatically. Each profile carries its own RPM gauge scale and redline — a 10,500 rpm motorcycle and a 6,500 rpm car don't share gauges.

### It learns your gears.
No gear ratio tables to type in. The adaptive gear learner clusters RPM/speed pairs while you ride; after ~30 observations per gear the gear display locks in — calibrated to *your* vehicle.

### It shouts before your engine melts.
Audio + haptic alerts on the media stream (so they reach a Bluetooth helmet intercom or the car speakers): coolant overheat, low battery voltage (read from the adapter itself via `AT RV`), and redline. Thresholds are per-vehicle. The dashboard edge doubles as a shift light — amber at 95 % of redline, red past it.

### It explains fault codes like a mechanic.
Plug in a Claude API key and DTCs come back as two sentences of plain language, *in context* — a P0171 with +18 % fuel trim gets "probable vacuum leak", not a generic dictionary entry. No key? Everything else still works fully offline.

### It reports every trip — with the route.
Sessions record to a local database automatically. Tap any trip for a report: duration, real distance (integrated from speed), max/avg speed and RPM, max coolant temp, full RPM/speed charts — and your **GPS route drawn as a racing line**, fully offline, with GPS distance and GPS max speed next to the OBD numbers (spoiler: your speedometer lies).

### It doesn't die in your pocket.
A foreground service keeps telemetry and the GPS track recording with the screen off or the app backgrounded. The persistent notification doubles as a mini-dashboard: speed, coolant temp and battery voltage, live.

### It shows up in your car.
An Android Auto screen (Car App Library) mirrors speed, RPM, temp and voltage at 1 Hz on the head unit — from the same connection the phone uses. Sideload-only (Google doesn't allow gauge apps on Play for Auto): enable *Unknown sources* in Android Auto's developer settings.

### It helps you reverse-engineer your own vehicle.
A built-in **Mode 22 scanner** sweeps manufacturer DID ranges and highlights identifiers that change while you press buttons — that's how you find proprietary parameters like ride modes. Discovered PIDs load as custom JSON definitions at runtime, no rebuild needed.

---

## Feature matrix

| | |
|---|---|
| 🏍️ **Dashboard HUD** | RPM arc (per-profile scale), speed, gear, coolant, boost, voltage, trip score, shift light, keep-screen-on |
| 📈 **Sensor graphs** | Live scrolling chart for any PID |
| ⚠️ **Alerts** | Overheat / low voltage / redline → audio (helmet intercom) + vibration, per-vehicle thresholds |
| 🔧 **Fault codes** | Read/clear DTCs, optional Claude AI contextual explanations |
| 🗂️ **Trip reports** | Distance, max/avg stats, RPM & speed charts per session |
| 🛰️ **GPS track** | Offline racing-line route map + GPS vs OBD speed/distance |
| 🔒 **Background recording** | Foreground service with live mini-dashboard notification |
| 🚗 **Vehicle profiles** | VIN auto-detection, per-vehicle gauge scale and redline |
| 🚙 **Android Auto** | Live pane on the head unit (sideload) |
| 🔬 **Mode 22 scanner** | Discover manufacturer PIDs (ride modes, etc.) + runtime custom PIDs |
| ⚡ **Multi-PID batching** | Several PIDs per CAN frame — ~2× refresh rate on priority gauges |
| 🧠 **On-device AI** | Gear learning (EMA clustering), anomaly detection (Welford 3σ), drive style score — no cloud |

---

## Getting started

### Install

Grab the APK from [Releases](https://github.com/santiquiroz/revscope/releases) and sideload it, or build:

```bash
git clone https://github.com/santiquiroz/revscope.git
cd revscope
./gradlew assembleDebug
```

### Connect

1. Plug the ELM327 adapter into the OBD port, ignition on.
2. Pair the adapter in Android Bluetooth settings (Vgate iCar Pro: `Android-Vlink`, PIN `1234`).
3. RevScope → Bluetooth icon → tap your adapter.
4. That's it. Next time, RevScope reconnects to it automatically on launch — and after fuel stops.

### Recommended setup (2 min)

- **Profiles** → create your vehicle: type, RPM gauge max, redline. Tap *Leer VIN* while connected so it auto-activates from then on.
- **Settings** → alert thresholds (temp / voltage / redline) and optional Claude API key for DTC explanations.
- Grant **location** (GPS route on reports) and **notifications** (background recording status) when asked — both optional; denying them only disables those features.

### Android Auto

Gauge apps aren't a Play-approved Auto category, so RevScope runs on Android Auto via developer mode (one-time setup):

1. On the phone: **Settings → Apps → Android Auto → Additional settings in the app** (or open the Android Auto app directly).
2. Scroll to the bottom and **tap "Version" 10 times** → accept "Allow development settings".
3. Open the **⋮ menu → Developer settings** → enable **"Unknown sources"**.
4. Connect the phone to the car (USB or wireless Auto). **RevScope** appears in the Auto app launcher.
5. Open it: speed, RPM, coolant temp and battery voltage refresh live at 1 Hz, with a *Reconectar* button if the adapter link drops.

Notes:
- The car screen reads the **same Bluetooth connection** the phone already has with the ELM327 — nothing extra to pair.
- If RevScope doesn't show up: Android Auto → Developer settings → "Application mode: Developer", then force-close and reopen Android Auto.
- Works on the desktop head unit emulator (DHU) too, for testing without a car.

---

## Tested hardware

| Vehicle | Protocol | Status |
|---------|----------|--------|
| TVS Apache 160 4V FI (2026) | CAN 11-bit | ✅ daily tested |
| Mazda CX-30 Grand Touring | ISO 15765-4 CAN | ✅ |
| Vgate iCar Pro 2S (ELM327 v2.3) | Bluetooth Classic SPP | ✅ reference adapter |

Generic ELM327 clones should work — the transport layer includes the reflection-socket fallback and timeout hardening they usually need.

---

## Architecture

```
core/
├── obd/            # Transport (BT SPP), ELM327 protocol, PID registry, telemetry
│   ├── session/    #   ObdSessionManager — app-scoped connection owner
│   └── alerts/     #   AlertsEngine — audio/haptic alert sink
├── intelligence/   # Gear learner, anomaly detector, DTC explainer, drive scoring
└── data/           # Room (sessions, telemetry, profiles), DataStore settings
feature/
├── dashboard/      # HUD gauges, shift light, adapter scan
├── sensors/        # Live graphs
├── dtc/            # Fault codes + AI
├── session/        # Trip history + reports
├── vehicle/        # Profiles + VIN detection
├── settings/       # Thresholds, API key, custom PIDs, Mode 22 scanner
└── auto/           # Android Auto (Car App Library)
```

Key design decisions:
- **Connection lives at application scope** (`ObdSessionManager`), not in a ViewModel — it survives navigation, Activity death, and feeds Android Auto without an Activity.
- **One mutex around the wire**: ELM327 is half-duplex; every command (polling, DTC, VIN, scanner) goes through a single serialized `exchange()`.
- **Everything degrades**: batching falls back to single PIDs on K-line, AI tiers scale with device RAM, alerts and Claude are optional.

| | |
|---|---|
| Language | Kotlin 2.0 · Coroutines/Flow |
| UI | Jetpack Compose + Material 3 · Vico charts |
| DI / Storage | Hilt · Room · DataStore |
| Car | androidx.car.app 1.4 |
| Min SDK | 26 (Android 8.0) |

---

## Roadmap

- [x] Hardened ELM327 transport + multi-PID batching
- [x] Auto-reconnect + dead-link circuit breaker
- [x] Audio/haptic alerts + battery voltage
- [x] Trip reports with charts
- [x] VIN-based vehicle profiles + per-vehicle gauges
- [x] Mode 22 scanner + runtime custom PIDs
- [x] Android Auto pane
- [x] Foreground service (recording survives backgrounding)
- [x] GPS track on trip reports + GPS vs OBD speed

- [x] 0–100 km/h automatic timer (self-arming, interpolated, spoken over intercom)
- [x] Lap timer / track mode (GPS finish-line crossing detection, live lap clock, spoken laps)
- [x] Spoken alerts & results over helmet intercom (TTS)
- [x] CSV trip export & sharing
- [x] DTC freeze frame (Mode 02 — engine snapshot at fault time)
- [x] Shareable PID packs (export Mode 22 discoveries)

### Next
- [ ] Ride mode display (via Mode 22 discovery)
- [ ] Handlebar / helmet media-button actions (MediaSession/AVRCP)
- [ ] Trip comparison (same route, run A vs run B)
- [ ] PID pack import from shared files
- [ ] BLE + WiFi transports

### Before any store release
- [ ] Production keystore + real Room migrations (currently debug signing + destructive migration)
- [ ] Encrypted storage for the Claude API key
- [ ] Repository layer between Room and UI
- [ ] Test coverage for core/intelligence

---

## Contributing

PRs welcome. Most valuable right now:
- Manufacturer PID tables discovered with the Mode 22 scanner (share your findings!)
- Testing on more vehicles/adapters — especially K-line motorcycles
- BLE transport implementation

---

## License

Apache 2.0 — see [LICENSE](LICENSE).

## Acknowledgements

[AndrOBD](https://github.com/fr3ts0n/AndrOBD) · [blessed-android-coroutines](https://github.com/weliem/blessed-android-coroutines) · [Vico](https://github.com/patrykandpatrick/vico) · SAE J1979 · Welford (1962)
