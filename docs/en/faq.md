> 🇪🇸 [Leer en español](../faq.md)

# Frequently asked questions (FAQ)

> Index: [Adapter](#adapter-and-connection) · [Daily use](#daily-use) · [Data and AI](#data-and-artificial-intelligence) · [Other platforms](#other-platforms) · [Project](#the-project)
>
> See also: [Installation](installation.md) · [User manual](user-manual.md) · [Configuration](configuration.md) · [Development](development.md)

## Adapter and connection

### My adapter will not connect, what should I do?

First confirm that the adapter is **paired in Android system Bluetooth**, not only inside RevScope — without this prior step it will not appear in the list. Next, verify that the adapter has power (the ELM327 draws power directly from the OBD2 port, so the vehicle ignition switch must be in the ON/ACC position). If it still does not connect, open the adapter scanner (top pill → Adapter → Manage) and tap **"Choose another device"** to start fresh.

### It connects but I do not see any data, why?

The vehicle may not support the PIDs you are attempting to view (not all models expose every parameter), or the engine may be off — many adapters remain "connected" over Bluetooth even while the ECU is asleep without ignition. Start the engine and wait a few seconds; if the issue persists, run the [Mode 22 Scanner](user-manual.md#workshop) to test whether the ECU responds to raw commands.

### What adapter should I buy?

Any **classic Bluetooth ELM327** (SPP profile) works. RevScope recommends the **Vgate iCar Pro 2S** for its connection stability and power efficiency, but any brand-name classic ELM327 adapter is suitable. Avoid **BLE (Bluetooth Low Energy)** or **Wi-Fi** adapters — RevScope does not yet support them, regardless of "ELM327 compatible" packaging claims.

### Does it work without buying an adapter?

Yes, partially: the **"Start GPS trip"** button in Drive records routes, speed, lean angle, and G-forces using only your phone's built-in sensors, including speed cameras, Pico y placa (Colombian driving restriction), and crash detection. Without an adapter you will not receive RPM, engine temperature, gear position, trouble codes, or any Workshop tools that query the ECU.

## Daily use

### Will my trips and profiles be lost when updating the app?

No: each release modifying the database structure includes an explicit additive migration (preserving existing tables), verified against the exported schema prior to release. Nonetheless, as a best practice, enable [weekly automatic backups](configuration.md#backup) (enabled by default) or export a manual backup before updating for complete peace of mind.

### Does keeping the app open drain a lot of battery?

RevScope automatically terminates recording, GPS polling, and sensor listeners as soon as it detects the engine has turned off (or after several stationary minutes during GPS trips). Keeping the app open while not driving will not noticeably drain battery. While an active trip is running, continuous GPS/Bluetooth usage draws power similar to any turn-by-turn navigation app. High-frequency sampling presets (250 ms, Maximum) and Workshop quick captures draw more power and increase device temperature: quick capture enforces duration caps and auto-halts when battery falls below 15% or device thermals spike (see [OBD sampling](configuration.md#obd-sampling)).

### Can I see tire pressure?

Not through standard channels: tire pressure monitoring (TPMS) **is not part** of the standard OBD2 specification and most vehicles do not broadcast it over standard PIDs — availability depends entirely on the vehicle manufacturer. If your vehicle exposes TPMS over proprietary PIDs, you can discover them using the [Mode 22 Scanner](user-manual.md#workshop), configure them as a [custom PID](configuration.md#custom-pids), and set up a [threshold alert](configuration.md#custom-pid-alerts) to warn of pressure deviations.

### Is the mileage displayed accurate?

RevScope can read the **factory ECU odometer** (PID `A6`) via the "Mileage verification" tool in Workshop, but this PID is only supported on relatively recent vehicles (standardized under J1979-DA, roughly 2015 onward) — many motorcycles do not report it. When available, RevScope logs readings over time and warns if the odometer rolls backward or advances significantly less than GPS distance tracked by the app, signaling potential odometer tampering.

### Why do the dashboard (OBD) and Map (GPS) speedometers not match?

This is expected: virtually all factory vehicle speedometers are calibrated to read **slightly higher** than true road speed, whereas GPS calculates actual velocity over ground. Use **"Speedometer comparison"** in Workshop along a flat, straight road at steady speed to measure your vehicle's factory speedometer error.

## Data and artificial intelligence

### How much does it cost to use the AI features?

Very little: each trouble code explanation or AI mechanic query typically costs fractions of a cent, as lightweight and fast models are configured by default (`claude-haiku-4-5`, `gpt-5-mini`, or `gemini-2.5-flash`, depending on provider). You pay your AI provider directly using **your personal API key** — RevScope charges nothing and does not intermediate payments. If you prefer zero costs, connect a local inference server (such as LM Studio on your PC) as an "OpenAI-compatible" provider using open-weights models running on your own hardware; note that traffic must be routed over `https` or a tunnel, as Android blocks unencrypted `http://` connections to local network hosts ([details](configuration.md#artificial-intelligence)).

### I enabled the MCP server but my AI client (Claude Desktop, etc.) does not respond, what should I check?

Check in order: (1) verify that your phone and computer share the **same Wi-Fi network** (the server does not operate over mobile data or across different subnets); (2) verify that the **"MCP server active"** toggle remains enabled — it shuts down automatically if Wi-Fi disconnects; (3) confirm that the authentication token in your desktop client matches the token shown in Settings; and (4) ensure RevScope remains open — the server runs inside the app process, not as an independent background daemon.

### Speed cameras do not appear or are outdated, how do I update them?

RevScope aggregates camera locations from OpenStreetMap and the official registry of the ANSV (National Road Safety Agency, Colombia), updating automatically once per week; force an immediate update using **"Download cameras for my area"** in Settings. If a real-world camera is missing, add it directly to [OpenStreetMap](https://www.openstreetmap.org) — RevScope reads from this open global database rather than maintaining an isolated proprietary database.

### The Pico y placa for my city is not on the list, what should I do?

Currently RevScope provides built-in rules for Medellín and Bogotá; Cali is listed without confirmed schedules. The underlying engine supports fully custom rules in JSON (see [Configuration → Pico y placa](configuration.md#pico-y-placa) for the schema), but as of this release **a dedicated input field in Settings** has not yet been implemented in the UI.

### Are typical values factory specifications?

No. **Typical** represents a general reference range; **Source** indicates citations or provenance; **Edited by you** marks adjustments you made for your specific model. RevScope does not invent manufacturer specifications: to confirm values for your vehicle, consult its official service manual.

## Other platforms

### Does it have reliable crash detection?

It serves as an assistive safety feature, not a certified emergency response system: it relies on fixed mathematical thresholds for acceleration and immobility (detailed in [Configuration](configuration.md#crash-detection)) and requires an active recording session with cellular/GPS reception at the incident scene. False negatives (unregistered falls) and false positives (harsh braking interpreted as impact) can occur. Test it using **"Test (without sending real SMS)"** before relying on it, and never treat it as a replacement for defensive driving and protective gear.

### Does RevScope work with Android Auto?

Yes — via sideloading, since the app is not hosted on Google Play. It displays speed, RPM, engine temperature, and battery voltage on the dashboard screen. See step-by-step setup in [Installation](installation.md#android-auto-optional).

### What about smartwatches?

With **Galaxy Watch (Wear OS)**, yes: a companion module streams heart rate from the watch to your phone, where it integrates into dashboard gauges and trip lap reports. It is installed by building from source and deploying via `adb` — see [Installation](installation.md#galaxy-watch-optional). There is currently no support for Apple Watch or non-standard smartwatch platforms outside compatible Wear OS hardware.

### Is there an iPhone version?

No. RevScope is built as a native Android application (Kotlin/Jetpack Compose) and there are no published plans for an iOS port.

### What languages are available?

Currently in **Spanish** only. The entire user interface, voice alerts, and diagnostic explanations are in Colombian Spanish.

## The project

### Is it free and open source?

Yes, completely. The source code is released under the **Apache 2.0** license — you can use, modify, and redistribute it freely within the terms of that license (including attribution and notice of modifications).

### I found a bug, how do I report it?

Open an **issue on GitHub** in the project repository, detailing expected behavior, actual behavior, and if possible, your vehicle model and OBD2 adapter. The more detail provided, the faster issues can be reproduced and resolved.

### I want to help improve RevScope, where do I start?

Review [development.md](development.md) for architectural overviews, build instructions, and step-by-step guides for adding PIDs, diagnostic rules, Pico y placa schedules, AI providers, or MCP tools. Pull requests accompanied by unit tests (the project follows TDD for pure logic) are the most direct path to contribute.

### Does my data leave my phone?

RevScope requires no accounts, maintains no private cloud, and collects no telemetry: your trips, vehicle profiles, OBD readings, and trouble codes remain strictly on your device, leaving only when you manually export CSV files or backup archives. However, the app accesses the internet to query public services, and certain queries include location coordinates. Below is the complete manifest of outbound connections:

| Connection | Data sent | Timing | Can it be avoided? |
|---|---|---|---|
| **Base map over internet** (`colombia.pmtiles` on GitHub Releases; fallback to raster tiles from `tile.openstreetmap.org`) | Map view bounding coordinates | By default, when opening Map or viewing trip maps without downloading the offline map | Yes: download the Colombia map in **Settings → Map** to serve tiles locally |
| **Map fonts and icons** (`protomaps.github.io`) | Non-personal: generic fonts and sprites | When rendering vector maps, including with downloaded maps | No; without network access, map geometry renders without text labels |
| **Speed cameras** (OpenStreetMap Overpass API and ANSV registry) | Current position sent to Overpass to request cameras in a 50 km radius; no personal data sent to ANSV (full registry is downloaded and filtered locally) | Manual downloads; automatic weekly updates around last downloaded center; and automatic downloads when moving > 35 km away during trips or with Map open | No master switch; without mobile data, cameras simply do not update |
| **Rain alerts** (Open-Meteo, `api.open-meteo.com`) | Position (rounded to ~10 m) | Every 10 minutes while recording GPS trips | No; the "Rain" voice alert toggle silences speech but does not stop queries |
| **Place search** (Photon, `photon.komoot.io`) | Search text and coordinates to rank nearby results | Only when executing searches in Map | Yes: do not use the search bar |
| **Routing and navigation** (OSRM, `router.project-osrm.org`) | Route origin and destination coordinates | Only when calculating routes or during navigation recalculations | Yes: do not request routes |
| **Update check** (GitHub API) | Non-personal: installed app version | On app launch (at most once every 12 hours) and via manual button in Settings | No disable toggle |
| **AI provider** (Anthropic, OpenAI, Google, or configured compatible endpoint) | Prompt text and vehicle parameters included with query; municipality name for en-route local info | Only if an API key is configured | Yes: without an API key, no AI network requests occur |
| **Municipality name** (Android system geocoder, typically resolved via Google Play Services) | Coordinates | During trips, for municipality-dependent features (local info, travel companion, AI Pico y placa) | Yes: those features are optional |
| **revscope-server** (user-configured collaborative server) | Group ride name, coordinates, and shared road hazards; traversed municipality for zone digests | Only if a custom URL is configured in **Settings → Collaborative server** | Yes: empty by default |
| **UI font** (Google Fonts via Google Play Services) | Non-personal: font family name | When rendering UI if Google Play Services has not cached the font | No; without network access, system default fonts are used |

Two features that never transmit data over the internet: the **MCP server** binds only to your local Wi-Fi and is disabled by default, and **crash detection** sends direct SMS text messages to emergency contacts.

---

Question not answered here? Check the [User manual](user-manual.md) or [Configuration](configuration.md), or open an issue on GitHub.
