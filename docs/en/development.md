> 🇪🇸 [Leer en español](../desarrollo.md)

# Development

> Index: [Modules](#modules) · [Stack](#stack) · [Build](#build) · [Tests](#tests) · [OBD pipeline](#obd-pipeline) · [Room and migrations](#room-and-migrations) · [Extension guides](#extension-guides) · [docs/superpowers](#docssuperpowers)
>
> See also: [Installation](installation.md) · [User manual](user-manual.md) · [Configuration](configuration.md) · [FAQ](faq.md)

This page targets contributors. The app UI itself is Spanish-only (see [FAQ](faq.md#what-languages-are-available)), and this doc assumes you are comfortable reading Kotlin.

## Modules

Multi-module Gradle project, declared in `settings.gradle.kts` (20 modules):

| Module | Contents |
|---|---|
| `:app` | Entry point, navigation (`RevScopeNavGraph`), onboarding, `MainActivity` |
| `:core:common` | Shared utilities with no Android dependencies |
| `:core:data` | Room (`AppDatabase`, entities, DAOs, migrations), DataStore preferences, backup |
| `:core:intelligence` | AI providers, anomaly detection, driving efficiency |
| `:core:obd` | Complete OBD2/GPS/IMU pipeline: Bluetooth transport, `PidScheduler`, `ObdSessionManager`, alerts, Pico y placa (Colombian driving restriction), MCP, safety (crash detection) |
| `:core:maps` | MapLibre map engine: styles, tile cascade (`.pmtiles` local > `.pmtiles` remote > OSM raster), and offline Colombia map download |
| `:core:navigation` | Turn-by-turn navigation over Ferrostar: OSRM route parsing, maneuvers, and voice |
| `:core:designsystem` | Tokens (`RevScopeColors`, `RevScopeType` with tabular figures), `RevScopeTheme`, and shared components (vehicle selector, chips, banners, label/value rows, auto-sizing text, `NivelBadge`, `EmptyState`, `ErrorState`, `ConfirmarDestructivoDialog`, `BarraConVolver`, `IndicadorPasos`) |
| `:core:ui-testing` | `MatrizCaptura`: JVM screenshots with Roborazzi on Robolectric (`testImplementation` only) |
| `:feature:dashboard` | Drive screen, adapter scanner, Track mode |
| `:feature:map` | Map tab (MapLibre): live map, search (Photon), routes (OSRM), navigation, social map |
| `:feature:workshop` | Workshop tab — 14 diagnostic tools, "Vehicle status", AI chat |
| `:feature:session` | Trip history and reporting (Trips tab) |
| `:feature:vehicle` | Vehicle profiles |
| `:feature:settings` | Settings tab |
| `:feature:dtc`, `:feature:sensors`, `:feature:gear` | Specific diagnostic tools mounted inside Workshop |
| `:feature:auto` | `RevScopeCarAppService` — dashboard for Android Auto |
| `:wear` | Standalone Wear OS app (same `applicationId`, heart rate streaming) |

## Stack

Versions in `gradle/libs.versions.toml`: Kotlin 2.2.21 with KSP · AGP 8.10.1 · Jetpack Compose (BOM 2025.05.00, Material 3) · Hilt 2.56.2 for DI · Room 2.7 with real migrations · Vico 2.1 (charts) · MapLibre Android 13.4.1 (OpenGL variant) with PMTiles vector tiles · Ferrostar 0.53.0 (`core` only, turn-by-turn navigation) · OkHttp 4.12 (group ride WebSocket) · NanoHTTPD 2.3 (embedded MCP server) · exp4j (PID formula evaluation) · coroutines/`StateFlow` for reactive state. compileSdk 36, minSdk 26 (watch: 30).

## Build

The repository includes the Gradle wrapper (**8.11.1**): no need to have Gradle installed, only JDK 17 or higher. From repository root (on Windows, `gradlew.bat` instead of `./gradlew`):

```bash
./gradlew :app:assembleDebug          # Phone debug APK
./gradlew :wear:assembleDebug         # Watch debug APK
./gradlew :core:obd:testDebugUnitTest # Module unit test suite
./gradlew test                        # Entire unit test suite
```

To debug broken builds quickly, target the specific module first (`:core:obd:compileDebugKotlin`, `:feature:workshop:compileDebugKotlin`, etc.) rather than compiling the entire project.

## Tests

**890+ JVM unit tests** (JUnit4 `@Test`, `./gradlew testDebugUnitTest`), more than half in `core/obd/src/test/kotlin/...` — the module housing pure and offline logic (PID engines, Pico y placa, diagnostics, crash detection, eco-score calculation, etc.). The rest reside in `feature/map`, `core/navigation`, `core/maps`, `core/intelligence`, `feature/settings`, `core/data`, `core/common`, `feature/workshop`, and `app`. The few tests in `androidTest` require an emulator or device.

### Screenshots (Roborazzi)

Screens and touched Compose components render on the JVM via `MatrizCaptura` (`:core:ui-testing`): two phones (360×640 dp xhdpi and 412×915 dp xxhdpi) across three font scales (1.0 · 1.3 · 2.0) with the RevScope theme. PNGs reside in `<module>/src/test/screenshots/<Component>_<w360|w412>_fs<100|130|200>.png`.

```bash
./gradlew :feature:dashboard:recordRoborazziDebug   # Regenerate screenshots for a module
./gradlew verifyRoborazziDebug                      # Compare against saved screenshots; fails on diff
```

- Screens with `hiltViewModel()` split into `XxxScreen` (with ViewModel) and stateless `XxxContent(state, actions)`, which is what gets captured.
- Content that scrolls on a phone is captured completely using `altoMinimoDp` (lengthens window, does not alter width or density).
- Before committing new or regenerated screenshots, **review them**: screenshots prove there are no clipping, overlapping, or split words, but only if inspected. Google Fonts do not download in Robolectric (system default font is rendered) and antialiasing may differ from physical devices.

### `Row` rule with text and actions

In a `Row` containing text and an action (button, icon, or link), **the text takes `Modifier.weight(1f)`** (or `weight(1f, fill = false)` if it should not expand). `Row` measures unweighted children first in order: unweighted text monopolizes available width, leaving the action residual space that Compose wraps letter by letter (which occurred on Drive's «Configure adapter» card). If the action can expand — long labels or 200% font scale — stack in a `Column` or use `FlowRow`. Label/value pairs: use design system `FilaEtiquetaValor`. 48 dp touch targets: prefer `Button`, `IconButton`, `TextButton`, `FilterChip`, or `Surface(onClick)` with `heightIn(min = 48.dp)` over `Modifier.clickable` on a `Text`.

### Semantics, contrast, and states

- **States with icon and text**: `NivelBadge(NivelEstado.OK | ATENCION | FALLA | SIN_DATO)`, never a standalone colored dot.
- **Informational text in `TextSecondary`** (5.8:1 on `SurfaceHigh`); `TextMuted` (3.45:1) is reserved for borders, decorative elements, and disabled states. Minimum 12 sp; 10 sp only for OSM attribution. Text on `Danger` uses `Background`.
- **Destructive actions** use `ConfirmarDestructivoDialog`: title with question, what is lost and preserved, red action verb button («Delete trip»), and «Cancel». Dialog state lives in stateless content (`remember`) or ViewModel if logic requires (DTC: `ReglasBorradoDtc`, shared with MCP).
- **Secondary screens** use `BarraConVolver` (description «Back»); empty states use `EmptyState(message, AccionEstado)`; error states use `ErrorState(message, onRetry)` to retry; progress text uses `liveRegion = Polite`; guided flows use `IndicadorPasos`.
- Rows with `Switch`, `Checkbox`, or `RadioButton`: apply `Modifier.toggleable`/`selectable` to the row with its `Role` and pass `onCheckedChange = null`/`onClick = null` to the control so TalkBack reads label and state together.
- Spanish `contentDescription` on any icon lacking text.
- JVM screenshots do not wait for infinite animations (an indeterminate `LinearProgressIndicator` halts Robolectric for minutes): do not capture indeterminate progress states. Dialogs are captured with `MatrizCaptura.dialogo`, capturing the window with all layers.

Conventions:
- **`org.junit.Assert`** (`assertEquals`, `assertTrue`, `assertNull`…) as foundation, not Truth or Kotest — follow existing test style in the module before writing new tests.
- **TDD for pure logic**: engines without Android dependencies (`PicoYPlacaEngine`, `DiagnosticRules`, `EcoScoreCalculator`, `CrashDetector`, `ReadinessParser`…) are written test-first — red, minimal implementation, green, refactor.
- Objects/classes with Android side-effects (notifications, Bluetooth, Room) remain thin and delegate calculation to pure testable functions.

## OBD pipeline

The core of the project lives in `core/obd/src/main/kotlin/com/revscope/core/obd/`:

- **Transport** (`connection/ClassicBtTransport.kt`): ELM327 communicates **half-duplex** over classic Bluetooth (SPP) — one command, one response, never parallel. Transport serializes access via a coroutine `Mutex` preventing Workshop tools and background polling from conflicting with telemetry.
- **Priority-based polling** (`telemetry/PidScheduler.kt`): four PID groups, each in its own `launch`:

  | Priority | Interval | Purpose |
  |---|---|---|
  | 1 | 100 ms | RPM, speed, throttle — feeds gauges |
  | 2 | 500 ms | Load, coolant, MAF, MAP, torque |
  | 3 | 2,000 ms | Temperatures, fuel trims, O2, fuel rate |
  | 4 | 1,000 ms | Workshop PIDs (including pedal and throttle `45`/`47`/`49`/`4A`/`4B`/`4C`/`5A`) — **only polled with `setWorkshopMode(true)`**, i.e. while a diagnostic screen is open, saving link bandwidth when unobserved |

  Voltage is not a standard PID: it is queried by `VoltagePoller` via `AT RV` every 10 s. These intervals define preset `SamplingPreset.ESTANDAR_2S`; other presets (1 s, 500 ms, 250 ms, Maximum) apply `min(base, preset)` and the running scheduler adopts them via `setPreset`. Cycle timing is **fixed-rate**: the next cycle begins at `cycleStart + interval` (monotonic clock injectable for tests), without adding query latency. With the screen off (`setIdleMode`), intervals scale ×5/×3/×2, unless an MCP observer is active (`McpActivityTracker`: tool called within 60 s). The `BUFFER FULL` multiplier doubles up to ×8 and halves every 30 s without errors. `MAXIMO` (interval 0) does not scale in empty cycles: under multiplier > 1 or idle factor > 1 it adopts `CUARTO_SEGUNDO` intervals, and with an injected `LectorDispositivo` the scheduler evaluates `CaptureSafeguards.decidirMuestreo` every 5 s (Throttle → `CUARTO_SEGUNDO`, Stop → `ESTANDAR_2S`). `setPaused(true)` pauses groups before queries (used by quick capture).

- **CAN batching**: on CAN vehicles, `PidScheduler` batches Mode 01 PIDs into single queries (`packIntoFrames`), using the fact that **one CAN frame carries 7 usable payload bytes**; if the adapter rejects multi-PID syntax, batching disables automatically and reverts to individual polling.
- **Diagnostic gate and lease** (`telemetry/PollingGate.kt`, `session/DiagnosticLease.kt`): polling acquires the gate per query (`GatedTransport`); DTC reads hold it throughout the read sequence (03 → 07 → 0A → 02…), bounded by timeout, without dropping adapter or trip. Non-standard adapter states register an `AjusteConcesion` to restore defaults during the lease.
- **Quick capture** (`telemetry/captura/`): `CapturaRapida` pauses groups, applies speed tuning (`ElmSpeedTuning`: `AT SH 7E0` + `AT CRA 7E8` on 11-bit CAN only, `AT AT 2`; rejected commands are omitted), and `FastPoller` queries 1–6 PIDs with multi-PID framing and `1` suffix, guarded by coolant reads every 5 s. Timestamps and latency are captured inside `PollingGate` (`enCanal`), preventing lease DTC reads or queued `AT RV` calls from skewing sample timing. `RateMeter` tracks Hz per PID and p50/p95 latency, `FastCaptureBuffer` provides a 200,000-sample ring buffer paginated by `get_captura`, `CapturaCsv` + `ArchivoCaptura` write CSV records with millisecond precision, and `CaptureSafeguards` regulates capture lifecycle. Operates within the connection lifecycle scope, reverting settings in a NonCancellable `finally`: `ElmSpeedTuning.revertir` resynchronizes (`AT E0` until clean `OK`, clearing `STOPPED` and latent responses), retries unacknowledged AT commands, and reports un-reverted states; if ECM filters cannot be cleared, `PollingGate` registers an `AjusteConcesion` retrying before the next lease. `FakeElmTransport.modelarInterrupcion` simulates busy adapter conditions in unit tests. Samples route into the standard telemetry stream; `SessionRecorder` writes at most one row every ~90 ms per PID.
- **Circuit breaker**: `MAX_CONSECUTIVE_LINK_FAILURES = 3` — three consecutive failed request/response cycles prompt `PidScheduler` to declare the link dead, triggering loss classification in `ObdSessionManager`.
- **Engine-off vs transient fault classification**: before releasing transport, `ObdSessionManager.classifyLinkLoss` queries `AT RV\r` (voltage — if adapter responds, power is present) followed by `010C\r` (RPM). Adapter responsive + ECU silent = **engine off** → clean shutdown (`finalShutdown`, stops GPS/IMU, triggers summary notification). Any other response = **transient fault** → retry.
- **Reconnection backoff**: `15s → 30s → 60s → 60s` (`AUTO_RECONNECT_BACKOFF_MS`), with 15 s final grace period before initiating clean shutdown.

## Room and migrations

Database at **version 14** (`core/data/.../db/AppDatabase.kt`), with migrations accumulated since version 9 (`Migrations.kt`: `MIGRATION_9_10` … `MIGRATION_13_14`) — **without `fallbackToDestructiveMigration()`**: version jumps without explicit migrations must fail loudly rather than silently deleting user data (this rule originated directly from an earlier data loss incident).

Pattern for schema changes:
1. `ALTER TABLE ... ADD COLUMN` additive change (never destructive `DROP`/`RENAME`) or `CREATE TABLE IF NOT EXISTS` for new tables, within an `object MIGRATION_N_M : Migration(N, M)`.
2. Increment `version` in `AppDatabase` and register migration in `DataModule` (`.addMigrations(...)`).
3. **Verify migration SQL against exported schema** in `core/data/schemas/com.revscope.core.data.db.AppDatabase/<version>.json` (Room generates this upon compilation) — column by column, verifying exact types and `DEFAULT` clauses.
4. Install over a populated real-device database, not merely a clean emulator, confirming existing tables (trips, vehicle profiles) remain intact after migration.

## Extension guides

### Adding a PID (OBD parameter)

Edit `core/obd/src/main/assets/pids_mode01.json`, adding an object with `mode`, `pid`, `name`, `nameEs`, `bytes`, `formula` (exp4j expression using variables `A`–`D`), `unit`, `min`, `max`, and `priority` (1–4, see [table above](#obd-pipeline)). For proprietary manufacturer parameters rather than standard OBD PIDs, first discover them with the "Mode 22 Scanner" tool and consider whether they belong as user custom PIDs rather than in the base JSON.

### Adding a diagnostic rule

`core/obd/src/main/kotlin/com/revscope/core/obd/workshop/DiagnosticRules.kt` — pure object with an `evaluarX(...): Diagnosis` function per parameter, each developed via TDD in `DiagnosticRulesTest.kt`. Follow existing patterns: `Nivel` (OK/ATENCION/FALLA), thresholds as named companion constants, and probable cause text in Spanish.

### Adding a Pico y placa city

`core/obd/src/main/kotlin/com/revscope/core/obd/legal/PicoYPlacaEngine.kt` defines the engine (`CityRules`, `Scheme.WEEKDAY_ROTATION` or `Scheme.DATE_PARITY`, `check(...)`) and `CityRegistry.kt` manages built-in cities (id, name, coordinates, GPS detection radius, and `CityRules`, or `null` if schedules remain unconfirmed — as Cali is configured today). Any new city requires TDD in `PicoYPlacaEngineTest.kt` covering: active restriction, exempt conditions, outside hours, weekends, and expired validity.

### Adding an AI provider

`core/intelligence/src/main/kotlin/com/revscope/core/intelligence/provider/` — implement `AiProvider` (interface in `AiProvider.kt`, alongside `AnthropicProvider`, `OpenAiProvider`, `GeminiProvider`, and `OpenAiCompatibleProvider` as references) and register it in `AiProviderFactory.kt`. Provider response parsers live in `AiResponseParsers.kt` with dedicated tests against synthetic JSON fixtures.

### Adding an MCP tool

`core/obd/src/main/kotlin/com/revscope/core/obd/mcp/` — each tool is a class implementing `McpTool` (`name`, `description`, `inputSchema`, `call(arguments): String`), following patterns in `GetEstadoTool.kt` / `GetViajesTool.kt`. Register it in `core/obd/src/main/kotlin/com/revscope/core/obd/di/McpModule.kt` for `McpDispatcher` to expose it in `tools/list` and `tools/call`. See complete tool listing in [Configuration → Local network MCP server](configuration.md#local-network-mcp-server).

## docs/superpowers

`docs/superpowers/` preserves project design history: `specs/` (feature specifications prior to implementation) and `plans/` (step-by-step execution plans with build commands and acceptance criteria, executed chronologically). It is not end-user documentation — it is the permanent record of **why** the architecture took shape, explaining design decisions (e.g. why Pico y placa distinguishes city schemes, or why `ObdSessionManager` classifies link loss before reconnecting) without needing to reconstruct context from commit histories.

---

Looking for user guides rather than architecture documentation? See the [User manual](user-manual.md).
