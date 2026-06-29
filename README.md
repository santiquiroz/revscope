# RevScope

> Real-time OBD2 telemetry for Android — built for drivers who actually care about what's happening under the hood.

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple.svg)](https://kotlinlang.org)
[![Status](https://img.shields.io/badge/status-in%20development-orange.svg)]()

RevScope is an open-source Android app that connects to your car or motorcycle via an ELM327 OBD2 adapter and gives you a live, beautiful dashboard of everything your vehicle's ECU is reporting — RPM, torque, boost, gear estimation, fuel trims, DTC codes, and more.

No ugly generic gauges. No paywalled features. No subscription.

---

## Screenshots

> Coming soon — v1 in development.

---

## Features (v1)

- **Live telemetry dashboard** — RPM, speed, engine load, temperatures, throttle, MAF, O2 sensors
- **Gear estimator** — infers current gear from RPM/speed ratio (no standard PID needed)
- **Boost / MAP display** — manifold pressure as boost for turbo engines, intake vacuum for NA
- **Torque analysis** — real-time torque % and reference torque in Nm (where ECU supports it)
- **Sensor graphs** — scrolling real-time charts for any PID combination
- **DTC reader/eraser** — read active, pending, and permanent fault codes with descriptions
- **Trip recorder** — auto-records sessions to local Room database with stats
- **Vehicle profiles** — save settings per vehicle (car vs motorcycle, available PIDs)
- **Multi-adapter support** — Classic Bluetooth, BLE (6 chip families), WiFi
- **Motorcycle support** — ISO 9141-2 / KWP2000 protocols auto-detected

---

## Supported Adapters

| Type | Protocol | Example |
|------|----------|---------|
| Classic Bluetooth | SPP/RFCOMM | Vgate iCar Pro 2S (Android-Vlink) |
| BLE | CC254X, VLink, Nexas, Nordic, Microchip, TIO | Most BLE OBD dongles |
| WiFi | TCP :35000 | ELM327 WiFi adapters |

**Primary development adapter**: Vgate iCar Pro 2S (Bluetooth 5.2, ELM327 v2.3)

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

- **Language**: Kotlin
- **UI**: Jetpack Compose with Material 3
- **Architecture**: MVVM + Clean Architecture
- **DI**: Hilt
- **Async**: Coroutines + StateFlow
- **BLE**: [blessed-android-coroutines](https://github.com/weliem/blessed-android-coroutines)
- **Charts**: [Vico](https://patrykandpatrick.com/vico/)
- **Database**: Room
- **Min SDK**: API 26 (Android 8.0)

---

## Architecture

```
app/
├── core/
│   ├── obd/          # ELM327 protocol: connection, parser, PID registry
│   └── data/         # Room database, DataStore
└── feature/
    ├── dashboard/    # Live gauges
    ├── gear/         # RPM/torque analyzer
    ├── sensors/      # Sensor graphs
    ├── dtc/          # Fault codes
    ├── session/      # Trip history
    ├── vehicle/      # Vehicle profiles
    └── settings/     # Adapter & preferences
```

Full architecture details in [PLAN.md](PLAN.md).

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

Derived metrics: boost estimate, gear estimate, power (kW), thermal efficiency indicator.

---

## Getting Started

### Prerequisites

- Android 8.0+ device
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

---

## Roadmap

### v1 — Phone only (current)
- [x] Architecture & plan
- [ ] Connection layer (Classic BT, BLE, WiFi)
- [ ] OBD protocol layer (ELM327, PID engine)
- [ ] Telemetry engine (polling, derived metrics, session recording)
- [ ] Dashboard UI (gauges, racing HUD style)
- [ ] Sensor graphs (Vico charts)
- [ ] Gear analyzer
- [ ] DTC reader/eraser
- [ ] Vehicle profiles (car vs motorcycle)
- [ ] Multi-adapter scanner UI

### v2 — Android Auto
- [ ] Car App Library templates (PaneTemplate — 4 metrics while driving)
- [ ] Background service for persistent connection

### v3 — Advanced
- [ ] Semantic session compression
- [ ] Discord/Telegram bot integration
- [ ] Remote session sharing
- [ ] iOS companion app (BLE mode only)

---

## Contributing

Contributions welcome. See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

Areas where help is most useful:
- Vehicle-specific proprietary PID tables (Mazda Mode 22, Renault, etc.)
- Motorcycle OBD protocol testing
- iOS port (BLE, same ELM327 protocol)
- UI/UX improvements (Compose components)

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
- [OBDForge](https://github.com/topics/elm327) — Kotlin + Compose OBD reference
- [SAE J1979](https://www.sae.org/standards/content/j1979_202209/) — OBD-II standard
