> 🇪🇸 [Leer en español](../instalacion.md)

# Installation

> Index: [Requirements](#requirements) · [Install the APK](#install-the-apk) · [First run and permissions](#first-run-and-permissions) · [Pair and connect the adapter](#pair-and-connect-the-obd2-adapter) · [Galaxy Watch](#galaxy-watch-optional) · [Android Auto](#android-auto-optional)
>
> See also: [User manual](user-manual.md) · [Configuration](configuration.md) · [FAQ](faq.md) · [Development](development.md)

## Requirements

| Requirement | Detail |
|---|---|
| Operating system | Android 8.0 (Oreo) or higher |
| OBD2 adapter | ELM327 via **classic Bluetooth** (SPP profile) |
| Recommended | Vgate iCar Pro 2S — see [why in the FAQ](faq.md#what-adapter-should-i-buy) |
| Vehicle | With an OBD2 diagnostic port (most cars and motorcycles since ~2016 in Colombia) |
| Optional | Galaxy Watch (Wear OS) for heart rate, phone with Android Auto for the car dashboard |

> ⚠️ RevScope only supports **classic Bluetooth ELM327** adapters. BLE (Bluetooth Low Energy) or Wi-Fi adapters are not yet supported — check the [FAQ](faq.md#what-adapter-should-i-buy) before buying one.

## Install the APK

RevScope is not on Google Play yet: install it directly from GitHub releases.

1. Go to [Releases](https://github.com/santiquiroz/revscope/releases/latest) and download the latest `.apk`.
2. Open the downloaded file.
3. Android will block the installation because it comes from "unknown sources". Tap **Settings** in the prompt and enable **Allow from this source**.
4. Go back and tap **Install**.

> 💡 This is the same notice shown for any APK installed outside an official app store — the installer is built directly from source code on GitHub (see [development.md](development.md)).

## First run and permissions

The first time you open RevScope, a welcome screen appears requesting three permissions one by one, with an **Allow** button on each card:

| Permission | What RevScope uses it for |
|---|---|
| Location | Record your routes and alert you to speed cameras |
| Notifications | Trip summary and Pico y placa (Colombian driving restriction) notifications |
| Bluetooth | Connect to the OBD2 adapter |

You can tap **Get started** even if you do not grant all three — each permission only disables the feature that depends on it (without Location there is no map or speed cameras; without Bluetooth there is no OBD telemetry). All permissions can be changed later in system Settings.

If you already have saved vehicles, RevScope immediately asks **"Which vehicle will you use?"** — this prompt does not appear on the very first run because no vehicle profile has been created yet (created from **Workshop → Vehicle profiles**). See the selector details in the [user manual](user-manual.md#vehicle-selector).

## Pair and connect the OBD2 adapter

1. **Plug the ELM327** into the vehicle OBD2 port (under the steering wheel in cars; on motorcycles it is usually near the tank or under the seat).
2. **Pair the adapter in Android system Bluetooth** (phone Settings → Bluetooth, NOT inside RevScope): scan for devices, select the one displayed as "OBDII"/"Vgate...", and enter the PIN if prompted (usually `1234` or `0000`).
3. **Open RevScope** and tap the top pill (colored dot + vehicle icon + name).
4. In the **"Select"** bottom sheet, tap the bottom row **"Adapter: ‹status› · Manage"**.
5. Select the paired device from the list and wait for the colored dot to turn green.

> ⚠️ If you paired the adapter only within RevScope without first pairing it in system Bluetooth, it will not appear in the list — Android requires pairing at the system level.

RevScope remembers the adapter address and automatically retries reconnecting (with exponential backoff) if the connection drops while driving. You can also link it to a specific vehicle so it connects automatically — see [Vehicle profiles](user-manual.md#workshop).

## Galaxy Watch (optional)

RevScope can stream heart rate from a Galaxy Watch (Wear OS) to your phone, where it is recorded alongside OBD/GPS/IMU telemetry and displayed per lap in reports. The watch app **is not distributed separately**: build and install it manually via `adb`.

1. On the watch: **Settings → About** → tap the software version **5 times** (enables developer mode).
2. Enable **ADB debugging** and **Wireless debugging**, and note the displayed `IP:PORT`.
3. On your PC, with the repository cloned, build using the included wrapper (see [development.md](development.md#build)): `./gradlew :wear:assembleDebug` (on Windows, `gradlew.bat`).
4. Connect and install the APK: `adb connect IP:PORT` followed by `adb install wear/build/outputs/apk/debug/wear-debug.apk`.
5. Open the app on the watch — no further configuration is needed: it uses the **same `applicationId`** (`com.revscope.app`) as the phone and communicates automatically via the Wearable Data Layer API. Tap **▶** on the watch to stream heart rate; it appears integrated into the gauges and lap times in the trip report.

## Android Auto (optional)

Because RevScope is not on Google Play, displaying it on your car screen requires sideloading:

1. In the **Android Auto** app on your phone, enable **developer mode** (tap the version several times, just like in Android Settings) and enable **"Unknown sources"**.
2. With your phone connected to the car (cable or wireless), RevScope will be available on the vehicle screen.

On the car display you will see a simple dashboard with **speed, RPM, engine temperature, and battery voltage**, updated once per second (the Android Auto library does not allow arbitrary custom gauges outside navigation apps). If connection to the adapter is lost, a **Reconnect** button appears directly on the car screen.

---

Next step: the [User manual](user-manual.md) covers the five tabs of the app, or jump straight to [Configuration](configuration.md) if everything is already connected.
