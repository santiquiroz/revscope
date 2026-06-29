# RevScope

> Real-time OBD2 telemetry for Android — with adaptive AI that learns your vehicle.

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple.svg)](https://kotlinlang.org)
[![Status](https://img.shields.io/badge/status-in%20development-orange.svg)]()

RevScope connects to any OBD2 adapter (Bluetooth, BLE, WiFi) and gives you a live racing HUD dashboard — RPM, speed, boost, torque, gear estimation, fuel trims, and fault codes — with an embedded AI layer that learns your specific vehicle over time.

No ugly generic gauges. No paywalled features. No subscription.

---

## What makes RevScope different

### It learns your car.

Most OBD2 apps show raw numbers. RevScope builds a statistical model of your vehicle as you drive.

After a few km of mixed driving, the adaptive gear learner has seen enough RPM/speed pairs to know exactly where your car's gear ratios sit. The gear display stops estimating and starts being accurate — for your car, not a generic lookup table.

### It watches for problems before they become problems.

The anomaly detector runs Welford's online algorithm on every sensor reading. It knows what *normal* looks like for your engine at operating temperature, and quietly flags when something drifts outside 3σ — elevated fuel trim creeping up, coolant temp running hotter than usual, MAP readings that don't match throttle position.

No false alarms on cold start. It waits for a baseline before saying anything.

### It tells you what fault codes actually mean.

Connect your Claude API key in Settings and RevScope uses Claude Haiku to explain DTCs in plain language — in context. If your current fuel trim is +18% and you get a P0171, Claude knows to suggest a vacuum leak over an injector issue. Two-sentence explanation, urgency level, done.

No API key? Fine. RevScope still works completely offline.

### It scores your driving.

Every trip ends with a drive style score (0–100) based on how often you hit high RPM, how hard you accelerate, and average engine load. Not to judge you — to give you a baseline for fuel efficiency and engine wear.

### It degrades gracefully.

All AI features are optional and tier-based:

| Tier | Triggers | Features |
|------|----------|----------|
| **MINIMAL** | < 2 GB RAM or feature disabled | Drive style scoring (rule-based, zero overhead) |
| **ON_DEVICE** | ≥ 2 GB RAM | + Adaptive gear learning + Statistical anomaly detection |
| **FULL** | ON_DEVICE + Claude API key configured | + AI-powered DTC explanations |

RevScope auto-detects which tier your device can handle. You can manually override it in Settings.

---

## Features

### Core telemetry
- Live dashboard — RPM, speed, engine load, coolant temp, intake temp, MAF, O2 sensors, throttle
- Boost / vacuum display — MAP minus atmospheric, correctly shown as vacuum on NA engines
- Torque analysis — actual torque % and reference torque in Nm (where ECU supports it)
- Estimated power output in kW
- Scrolling sensor graphs for any PID combination

### Gear intelligence
- Static estimation (all devices) — ratio table lookup from speed/RPM
- Adaptive estimation (ON_DEVICE+) — EMA clustering, vehicle-specific convergence after ~30 observations per gear
- Gear display updates in real time as the model refines

### Fault code management
- Read active, pending, and permanent DTCs (Modes 03, 07, 0A)
- Clear fault codes (Mode 04)
- Plain-language explanations — offline generic, or contextual Claude AI with API key

### Session recording
- Auto-records every session to local Room database
- Trip history with max RPM, max speed, distance, duration
- Per-trip drive style score

### Vehicle profiles
- Multiple vehicles — car or motorcycle
- Three strategies for gauge calibration:
  1. **VIN-based** — reads VIN via Mode 09, maps to known specs
  2. **Adaptive learning** — drives for 10–15 min, RevScope calibrates automatically
  3. **Manual** — enter redline, gear ratios, and fuel type yourself

### Hardware support
- Classic Bluetooth (SPP/RFCOMM) — Vgate iCar Pro 2S, ELM327 clones
- BLE (6 chip families auto-detected) — CC254X, VLink, Nexas, Nordic UART, Microchip, TIO
- WiFi (TCP :35000) — ELM327 WiFi adapters
- Motorcycles — ISO 9141-2 / KWP2000 auto-detected via AT SP 0

---

## Screenshots

> Coming soon — v1 in development.

---

## Tested Vehicles

| Vehicle | Type | Protocol |
|---------|------|----------|
| Mazda CX-30 Grand Touring | Car | ISO 15765-4 CAN |
| Renault Kardian | Car | CAN / KWP2000 |
| Nissan March | Car | CAN |
| TVS Apache 160 4V FI 2026 | Motorcycle | ISO 9141-2 / KWP2000 |

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.0 |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Clean Architecture |
| DI | Hilt |
| Async | Coroutines + StateFlow / SharedFlow |
| BLE | blessed-android-coroutines |
| Charts | Vico |
| Database | Room |
| AI | Claude Haiku (optional, via Anthropic API) |
| ML | Welford online statistics + EMA clustering (on-device, no framework) |
| Min SDK | API 26 (Android 8.0) |

---

## Architecture

```
app/
├── core/
│   ├── obd/            # ELM327 protocol: transport, parser, PID registry, telemetry engine
│   ├── intelligence/   # AI/ML: gear learning, anomaly detection, DTC explainer, drive scoring
│   └── data/           # Room database, DataStore preferences
└── feature/
    ├── dashboard/      # Live gauges (HUD style)
    ├── gear/           # Gear analyzer + adaptive calibration status
    ├── sensors/        # Sensor graphs
    ├── dtc/            # Fault codes + AI explanations
    ├── session/        # Trip history
    ├── vehicle/        # Vehicle profiles + spec setup wizard
    └── settings/       # Adapter, AI key, tier override
```

The intelligence module has no UI and no framework dependency — pure Kotlin with coroutines. It observes a `Flow<ObdReading>` from the OBD layer and emits anomaly alerts, gear table updates, and trip scores independently.

Full architecture details in [PLAN.md](PLAN.md).

---

## Getting Started

### Prerequisites

- Android 8.0+ phone
- ELM327 OBD2 adapter (Bluetooth Classic, BLE, or WiFi)
- A vehicle with a standard OBD2 port (1996+ cars, most modern motorcycles)

### Building

```bash
git clone https://github.com/santiqupgui/revscope.git
cd revscope
./gradlew assembleDebug
```

### Connecting your adapter

1. Plug the OBD2 adapter into your vehicle's OBD port (usually under the dashboard)
2. Turn the ignition to ACC or start the engine
3. Open RevScope → Settings → Adapter
4. For Vgate iCar Pro 2S: pair `Android-Vlink` (PIN: `1234`) in Android Bluetooth settings first
5. Select the adapter in RevScope and tap Connect

### Optional: Claude API key for DTC explanations

1. Get an API key at console.anthropic.com
2. Open RevScope → Settings → AI
3. Paste your key — no subscription needed, cost is fractions of a cent per DTC lookup

---

## Roadmap

### v1 — Phone only (current)
- [x] Architecture, module structure, planning
- [x] Connection layer (Classic BT, BLE, WiFi transports)
- [x] OBD protocol layer (ELM327 init, PID registry, response parser)
- [x] Telemetry engine (PidScheduler, DerivedMetricsEngine, SessionRecorder)
- [x] Intelligence layer (AdaptiveGearLearner, AnomalyDetector, DtcExplainer, DriveStyleClassifier)
- [ ] Dashboard UI (gauges, racing HUD style)
- [ ] Sensor graphs
- [ ] Gear analyzer with calibration status
- [ ] DTC screen with AI explanations
- [ ] Vehicle profiles with 3-strategy setup wizard
- [ ] Settings screen (adapter, AI key, tier override)
- [ ] Multi-adapter scanner UI

### v2 — Android Auto
- [ ] Car App Library templates (PaneTemplate — 4 metrics while driving)
- [ ] Background ForegroundService for persistent BT connection

### v3 — Extended
- [ ] Semantic session compression for long-term storage
- [ ] Discord/Telegram bot for session sharing
- [ ] iOS companion app (BLE mode only)

---

## Contributing

Contributions welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

Most useful:
- Vehicle-specific proprietary PID tables (Mazda Mode 22, Renault, Nissan CVT gear ratio)
- Motorcycle OBD protocol testing
- iOS port (BLE, same ELM327 protocol)
- Compose UI improvements

---

## Supported OBD2 PIDs

| PID | Parameter | Formula |
|-----|-----------|---------|
| 0x0C | Engine RPM | `((A×256)+B)/4` |
| 0x0D | Vehicle Speed | `A` km/h |
| 0x04 | Engine Load | `A×100/255` % |
| 0x05 | Coolant Temp | `A-40` °C |
| 0x0B | MAP (Boost) | `A` kPa |
| 0x11 | Throttle Position | `A×100/255` % |
| 0x10 | MAF Air Flow | `((A×256)+B)/100` g/s |
| 0x62 | Actual Torque % | `A-125` % |
| 0x63 | Reference Torque | `(A×256)+B` Nm |
| 0x06/07 | Fuel Trim | `(A-128)×100/128` % |
| 0x14–1B | O2 Sensors | voltage + trim |
| Mode 03 | DTC codes | active faults |
| Mode 07 | Pending DTCs | — |
| Mode 09 | VIN + ECU name | — |

Derived: boost estimate, adaptive gear, power kW, drive efficiency score.

---

## License

```
Copyright 2026 RevScope Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

---

## Acknowledgements

- [AndrOBD](https://github.com/fr3ts0n/AndrOBD) — BLE UUID family mapping reference
- [barnhill/AndroidOBD](https://github.com/barnhill/AndroidOBD) — JSON PID definition pattern
- [blessed-android-coroutines](https://github.com/weliem/blessed-android-coroutines) — BLE library
- [SAE J1979](https://www.sae.org/standards/content/j1979_202209/) — OBD-II standard
- [Welford (1962)](https://www.jstor.org/stable/1266577) — Online variance algorithm
