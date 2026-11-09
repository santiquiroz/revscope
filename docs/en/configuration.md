> 🇪🇸 [Leer en español](../configuracion.md)

# Configuration

> Index: [Vehicle](#vehicle) · [Audio and vibration alerts](#audio-and-vibration-alerts) · [Voice alerts by category](#voice-alerts-by-category) · [Custom PID alerts](#custom-pid-alerts) · [Speed cameras](#speed-cameras) · [Fuel](#fuel) · [Artificial intelligence](#artificial-intelligence) · [Crash detection](#crash-detection) · [Map](#map) · [Background notifications](#background-notifications) · [OBD sampling](#obd-sampling) · [MCP server](#local-network-mcp-server) · [Backup](#backup) · [Custom PIDs](#custom-pids) · [Pico y placa](#pico-y-placa)
>
> See also: [Installation](installation.md) · [User manual](user-manual.md) · [FAQ](faq.md) · [Development](development.md)

This page describes each section of the **Settings** tab, in the order they appear in the app, with their default values.

## Vehicle

| Option | What it does | Default |
|---|---|---|
| Active vehicle | Navigation row to Vehicle profiles — displays active vehicle name or "None" | — |
| Ask for vehicle at startup | If enabled, the app prompts which vehicle to use every time it starts from scratch | Enabled |
| Auto-start trip when moving (Tools) | With adapter connected and no active trip (after **End trip (stay connected)**), starts a trip when maintaining 10 km/h for 5 s | Enabled |

CAN profiles, data reading modes (Standard, Combined, or Bus only), and signal source routing are not located in Settings: they belong to each vehicle profile under the **CAN bus data** card (see [Manual → CAN bus data](user-manual.md#can-bus-data)). A vehicle without a CAN profile uses Standard mode. The **GPS** speed source is the same preference as the OBD/GPS chip on the Drive speedometer.

## Audio and vibration alerts

Two master toggles precede the category list:

| Option | What it does | Default |
|---|---|---|
| Active alerts | Master toggle — if disabled, no alert will sound or vibrate, regardless of category settings | Enabled |
| Voice (TTS) | If disabled, alerts still play tones/vibrate but do not speak the message | Enabled |

### Voice alerts by category

Each category can be toggled independently:

| Category | What it announces | Default |
|---|---|---|
| Temperature | Engine overheating | **Enabled** |
| Battery and charging | Low voltage / charging issue | **Enabled** |
| Speed cameras | Only when heading TOWARD a registered camera (GPS heading within ±60° of camera and approaching) — cameras behind you or on cross streets do not trigger alerts | **Enabled** |
| Custom thresholds | Any PID outside the range defined in [Custom PID alerts](#custom-pid-alerts) | **Enabled** |
| 0-100 times and laps | Acceleration times and Track mode lap results | **Enabled** |
| Pico y placa when entering another city | Alert when GPS detects entry into a city with an active restriction for your license plate (Colombia) | **Enabled** |
| Smart anomalies | Anomalous behavior detected by the intelligence engine | Disabled |
| Check engine light (MIL) | When the check engine light illuminates while driving | Disabled |
| Redline | Warning when nearing redline (in addition to visual *shift light*) | Disabled |
| Local info when changing cities | Uses your AI provider with web search to report relevant road events/closures upon entering a new municipality | Disabled |
| AI-powered Pico y placa in any city | Your AI provider (with web search) looks up driving restrictions for the city you are in — Pico y placa (Colombia), hoy no circula, rodízio… — and caches the rules until expiration (local cache, ~1-2 queries per month) | Disabled |

> 💡 "Local info" is the only category that queries your AI provider (with web search) each time you change municipalities — that is why it is disabled by default and its subtitle in the app indicates estimated cost. It is not available with generic "OpenAI-compatible" providers (LM Studio, etc.) because they do not offer managed web search.

Below the categories are three numeric fields and a **Save alerts** button:

| Field | Purpose |
|---|---|
| Max temp °C | Overheating threshold (60–150 °C valid) |
| Min volt | Low-voltage threshold (8–15 V valid) |
| Redline RPM | Redline RPM — also controls the visual *shift light* on the Drive dashboard (3,000–20,000 RPM valid) |

## Custom PID alerts

Text editor accepting a JSON array of thresholds for any parameter:

```json
[{"pid":"0A","min":200,"nombre":"Presión de combustible"}]
```

Each object accepts `pid`, `min` and/or `max` (omit thresholds you do not wish to monitor), and `nombre` (the spoken alert text). For proprietary manufacturer sensors (e.g. tire pressure if exposed by your vehicle), first define the [custom PID](#custom-pids) via Mode 22 and then configure its alert here. Button: **Validate and apply**.

## Speed cameras

Downloads speed cameras within a 50 km radius of your current location, combining two sources:

| Source | What it provides |
|---|---|
| OpenStreetMap | Community-reported cameras |
| ANSV (official registry) | Official fixed speed cameras from the National Road Safety Agency (ANSV, Colombia) |

Updates **automatically once per week** and works offline after the initial download. The **"Download cameras for my area"** button triggers an immediate manual download; total camera counts by source display below upon completion.

Additionally, during a trip the app detects when you travel **more than 35 km** from the center of your last download (for example, driving Medellín → Bogotá) and **automatically re-downloads** speed cameras around your new coordinates in the background, retrying every 30 minutes if mobile data is unavailable. The weekly refresh automatically tracks this updated center. Outside Colombia, only the OpenStreetMap source applies.

The **Alert radius (m)** field adjusts the distance at which voice alerts trigger when approaching a camera (100–1,000 m, default 250 m). Shorter distances provide more timely alerts with less chatter in urban areas; the red circle on the map outlines this exact alert radius.

## Engine sound

SoundRacer-style procedural sound generator without requiring separate hardware: synthesizes real-time engine audio following actual OBD2 RPM, outputting through phone speakers, car stereo, or Bluetooth helmet intercom.

- **Engine sound while driving** — disabled by default; only plays with active OBD telemetry.
- **Sound pack** — performance engines (V8 muscle, V10 F1, V12 GT) and humorous options (flatulent engine, podracer, 8-bit arcade).
- **Volume (0-100)** — with **Save volume** button; sound pack changes take effect immediately.

Uses zero pre-recorded audio loops: audio is procedurally synthesized, keeping engine pitch in sync with RPM without audio cuts or looping artifacts.

## Fuel

Three numeric fields, one per fuel grade, used to estimate trip costs based on the fuel type assigned to the active vehicle:

| Field | Default (COP/gallon, Colombia) |
|---|---|
| Regular gas price per gallon | 16,000 |
| Premium gas price per gallon | 20,000 |
| Diesel price per gallon | 10,500 |

> There is currently no active official online feed on datos.gov.co (Colombia) for municipal fuel prices — adjust these values manually according to your local station. Button: **Save prices**.

## Artificial intelligence

Optional integration that explains trouble codes, powers the AI mechanic, and — if supported by the provider — provides local en-route information alerts. Without an API key, those features run without AI explanations.

| Provider | Default model | Web search |
|---|---|---|
| Claude (Anthropic) | `claude-haiku-4-5-20251001` | Yes |
| OpenAI | `gpt-5-mini` | Yes |
| Gemini (Google) | `gemini-2.5-flash` | Yes |
| OpenAI-compatible (LM Studio, DeepSeek, Groq, OpenRouter…) | Per your server | No |

Fields:
- **AI provider**: dropdown menu featuring the four options in the table.
- **API key**: password field — stores individual encrypted keys per provider on device.
- **Model**: optional; leaving it blank selects the table default.
- **Base URL**: only appears with "OpenAI-compatible" — points to your server endpoint, such as `https://api.deepseek.com/v1`. Android blocks unencrypted `http://` traffic to any device other than the phone itself (only `localhost`, `127.0.0.1`, and emulator `10.0.2.2` are permitted), so an LM Studio or Ollama instance on your PC will **not** work via `http://<pc-ip>:1234/v1`: expose it over `https` (using a reverse proxy with TLS) or an HTTPS tunnel. If attempted over `http`, **Test connection** reports an explicit explanation rather than a generic network error.

Buttons: **Save AI settings** and **Test connection** (executes a minimal ping to verify correct API responses).

## Crash detection

Designed for motorcyclists. **Disabled by default.**

**Requirements:**
- Saved emergency contact phone number.
- SMS dispatch permission (`SEND_SMS`) and, on Android 13+, notification permissions — prompted when enabling the toggle if missing.
- Active recording session (OBD or [GPS trip](user-manual.md#drive)): the detector consumes motion and velocity samples from the recording service, so **monitoring is inactive without an ongoing trip**.

**How it works:**
1. An impact is registered only when **all three** conditions are met: total acceleration exceeds **6G**, the **horizontal** component of that peak exceeds **2.5G**, and speed exceeded **20 km/h** within the prior 5 seconds. The horizontal threshold distinguishes a crash (which converts forward momentum into severe longitudinal/lateral deceleration) from road bumps: **speed bumps and potholes produce almost purely vertical spikes** and will not trigger an alarm. Acceleration peaks exceeding **12G** register as impacts regardless of horizontal vectors, as road imperfections cannot produce forces of that magnitude.
2. Following impact, speed must **drop to a complete standstill within 8 seconds** — genuine collisions halt a motorcycle almost instantly. If movement continues past this window, the impact is dismissed, ensuring ordinary stops further down the road (traffic lights, parking) are not misconstrued as crash outcomes.
3. If the vehicle remains stationary (speed under 3 km/h and acceleration under 1.3G) for **30 sustained seconds**, the emergency alarm triggers. If normal riding resumes after an impact (e.g. moving above 10 km/h), the event is classified as a false positive and monitoring resumes silently.
4. A full-screen notification launches with an audible siren and a **60-second** countdown featuring an **"I'M OK"** button. The countdown **is announced via voice and vibration** every 10 seconds: voice guidance routes over the media stream to reach **Bluetooth helmet intercoms**, allowing cancellation while your phone remains in your pocket. This spoken alarm **intentionally bypasses** the master alert toggle and category filters.
5. If uncancelled before the timer expires, an **SMS** (split into multipart messages if required) is sent to your emergency contact with your last known coordinates: *"RevScope: possible crash detected (‹vehicle›). Location: https://maps.google.com/?q=lat,lon"*.

Fields and controls: **Emergency contact** + **Save phone** button, **Enable crash detection** toggle, and **"Test (without sending real SMS)"** button to simulate the complete alarm sequence safely.

> ⚠️ This feature serves as supplementary assistance, not a certified life-safety emergency system: it depends on an active recording session, GPS/cellular coverage at the incident location, and fixed mathematical thresholds that may not capture every accident scenario. It does not replace cautious driving or protective riding gear.

## Map

Map rendering is powered by **MapLibre** (open source, no API key required).

Map tiles load via a fallback cascade — the first available tier is used:

| Tier | Source | Works offline |
|---|---|---|
| 1 | `colombia.pmtiles` file on local device storage | **Yes** |
| 2 | `revscope-server` serving the same file | No |
| 3 | OpenStreetMap raster tiles | No |
| 4 | Background only — map is blank but route, speed cameras, and road hazards are plotted | — |

Currently tier 3 is active: the same raster tiles previously served by osmdroid. Tiers 1 and 2 activate once offline vector map files for Colombia are downloaded, upgrading night mode to a true vector dark style rather than the current rendering approximation (reduced brightness and saturation).

> The custom server is **not** required. Without it and without internet access, all other app functionality remains operational: telemetry, voice camera alerts, crash detection, and Pico y placa function from local databases.

## Background notifications

The daily **"Vehicle status"** summary (today's Pico y placa, documents expiring within 30/15/7/1/0 days) triggers at **5:30 AM Colombia time** without requiring the app to be open.

It is scheduled via an **exact system alarm** that pierces Android Doze mode, re-arming automatically after firing, on device reboot, and upon app updates. WorkManager periodic workers serve as a fallback; notifications deduplicate per calendar day to prevent duplicate alerts.

Two Android system settings can delay delivery, and the section reflects their status with ✓ or ✗:

| Setting | Effect if missing |
|---|---|
| **Exact alarms** (Android 12+) | Delivery falls back to inexact alarms: arrives with up to an hour variance. The **"Allow exact alarms"** button navigates to system settings. |
| **Battery optimization** | Aggressive vendor battery managers (e.g. Samsung) may freeze background jobs. The **"Battery optimization"** button opens system lists to set RevScope to *Unrestricted*. |

The **"Test notification (15 s)"** button schedules an actual notification 15 seconds into the future using the identical execution chain as the daily alarm — useful to verify background delivery on your device after granting permissions.

> If alerts only appear **when opening the app**, system battery management is delaying background execution: verify both settings above.

## Local network MCP server

Exposes vehicle telemetry and diagnostics to desktop AI assistants (Claude Desktop, LM Studio…) connected to your local Wi-Fi network using the [MCP](https://modelcontextprotocol.io) (Model Context Protocol) over HTTP. **Disabled by default.**

Enabling the **"MCP server active"** toggle displays the connection URL (local IP, port `8765`) and an auto-generated authentication token, with one-tap copy buttons for both.

Example configuration for Claude Desktop (`claude_desktop_config.json` or your MCP client's configuration file):

```json
{
  "mcpServers": {
    "revscope": {
      "type": "streamable-http",
      "url": "http://192.168.1.20:8765/mcp",
      "headers": {
        "Authorization": "Bearer <your-token>"
      }
    }
  }
}
```

Read-only tools, always available while the server is active:

| Tool | Returns |
|---|---|
| `get_estado` | Current vehicle status: connection state, trip state (`grabando` / `sin_viaje` / `sin_enlace`), sampling preset and quick capture status, MCP permissions, active profile, and live sensor values with age in ms. Provides a point-in-time snapshot. For high-frequency sensor streams, use `iniciar_captura` + `get_captura` |
| `get_viajes` | Recent trips for the active vehicle with summary statistics (distance, speed, eco score) |
| `get_viaje_detalle` | Detailed metrics for a specific trip ID (distance, speed, fuel consumption, acceleration runs) |
| `get_chequeo_salud` | Latest vehicle health check — findings by subsystem with status levels (OK/ATENCION/FALLA). Format 2 (`formato: 2`) includes `metricas`: `stft`/`ltft` trims and `correccionTotal` in %, `voltaje` with `motorEncendido`, `ect`, complete/total monitors, and trouble codes (`dtcs`, with `dtcsLeidos: false` on read failures). Format 1 legacy checks remain readable without metrics |
| `get_dtc` | Live trouble codes: active (Mode 03), pending (Mode 07), permanent (Mode 0A), MIL lamp status, ECU reported count, and freeze frame data. **Operates during or after trips without dropping the adapter link**: telemetry polling pauses automatically during reads. Optional arguments: `modos` (`["activos","pendientes","permanentes"]`), `freeze_frame` (defaults to `true`), and `incluir_crudo` (raw ECU responses). 10 s cache per argument set; maximum one physical ECU read every 5 s. With an open Workshop session, physical reads log to the timeline with MCP origin |
| `get_guia_dtc` | Offline diagnostic guide for a trouble code (`{"codigo": "P0122"}`), without adapter connection: SAE J2012 title, urgency level, ordered probable causes, numbered checklist steps referencing guided tests, multimeter templates, or clearing routines (`accion`: `PRUEBA:TPS_BARRIDO`, `MULTIMETRO:TPS`, `DTC:BORRAR`…), and motorcycle notes. Database covers ~45 generic P0 codes (MAP, IAT, ECT, TPS P0120–P0124, O2 B1S1, fuel trim, injectors, fuel pump, ignition, CKP, vehicle speed, idle air, battery voltage, ECU) labeled «General guidance, not manufacturer procedure»; reference values cite typical bands. Unlisted codes return standard SAE structural breakdown (P/C/B/U network, generic vs manufacturer, subsystem); manufacturer codes never display fabricated text |
| `get_sesion_taller` | Workshop diagnostic session data, without adapter connection: returns active session for current vehicle or specified `sesion_id`. Includes reported symptoms, notes, `codigosActuales`, `comparacion` against baseline health check (new code = FAULT; ±5 pp trims and ±0.5 V battery deviations are editable typical thresholds citing their source), `pruebasSugeridas` with rationale («due to P0122 and "Hesitation under acceleration"`), and paginated timeline (`desde_evento`, `max_eventos` up to 50; `incluir_payload` returns raw ECU responses, metrics, and downsampled series, up to 5 events per page) |
| `get_prueba_guiada` | Status of active Workshop guided test: preconditions with troubleshooting advice, current step (`paso` of `total`, instructions, `fase` `POSICIONANDO`/`SOSTENIENDO`/`GRABANDO`, and `restanteMs`) with live sensor values, and post-test verdict: pattern (e.g. `SENAL_BAJA_TODO_EL_RECORRIDO`, `SE_APAGA` during idle, `SALTOS_ECT` during cranking, `ADAPTADOR_REINICIADO` or `SOBRECARGA` for battery, and `MAP_BAJO` or `NO_CONCLUYENTE` for MAP, citing barometric reference and origin), interpretation («consistent with...»), next steps, step table in % and V, checks against reference bands («Typical (editable)», «Source: …», or «Edited by you»), signal dropouts, spikes, and dead zones, and measured sample rate (under 8 Hz detections are marked low confidence). Notes if saved to session |
| `get_mantenimiento` | Configured maintenance schedule items and remaining kilometers per item |
| `get_documentos` | Active vehicle document status: Mandatory insurance (SOAT), Roadworthiness inspection, Pico y placa (driving restriction), comprehensive insurance, and driver's license |
| `get_muestreo` | Telemetry polling preset, active quick capture status (with Hz ceiling if constrained by safeguards), measured rate per PID, requests/second, p50/p95 latency, limiting factor, and adapter hardware metadata (name, ELM version, protocol) |
| `get_captura` | Quick capture buffer samples from a cursor: `capturaId`, `desde_seq` (0 initially, then `seqSiguiente`), `max` (up to 2,000), and optional `pids`. Returns time series per PID with `t_ms` (relative to start) and `v`, plus `perdidas` > 0 if cursor fell behind ring buffer. Polling every 1–2 s retrieves **all** samples recorded between calls. Remains queryable after capture stops while retained in memory |
| `get_info_ecu` | ECU identification (Mode 09): VIN, software calibration IDs (CALID), calibration verification numbers (CVN), ECU name, protocol (`AT DPN`), and adapter voltage. Queries run independently: unsupported fields return `null` and list under `errores` |
| `get_mode06` | On-board diagnostic test results (Mode 06): misfire counters, catalyst monitors, O2 sensor monitors, with measured value, minimum, maximum, and pass/fail status. If unsupported by ECU, reports `soportado: false` |
| `descubrir_modulos` | Probes candidate 11-bit CAN headers (7E0–7E7 and common proprietary addresses) reporting responsive modules and response headers. Functional on 11-bit CAN protocols (6 and 8) |
| `leer_did` | Reads up to 32 manufacturer data identifiers (UDS `22`, `{"dids": ["F190","F187"]}`) from engine or specific module (`header` in 3 hex chars). Returns positive response bytes and ASCII text, or negative response code (e.g. `31` out of range = non-existent DID). Used to inspect proprietary parameters, such as riding modes |
| `comando_lectura` | Executes a single **read-only** diagnostic command (`comando`, optional `header`): services `01 02 03 06 07 09 0A 19 1A 21 22 23 24` and `AT RV`/`AT DPN`/`AT I`. All other commands are rejected, including session control (`10`) and *tester present* (`3E`), which modify ECU state and route through `comando_escritura`. Mode 01 responses return decoded with parameter names and engineering units |
| `get_catalogo` | Manufacturer operation catalog (proprietary DIDs, write operations, and routines) filterable by `marca`, `modelo`, `tipo`, and `texto`. See [Manufacturer catalog](#manufacturer-catalog) |
| `leer_catalogo` | Executes a catalog read entry by `id` and applies its conversion formula: returns calculated value, unit, raw bytes, verification status, and data source |
| `get_registro_escrituras` | Audit log of recent write operations (`n`, up to 100): calling tool, target module, executed commands, authorization method (user tap, bypass, rejected, or timed out), and ECU response |
| `listar_capturas_can` | Stored raw CAN captures (from MCP listen mode and app **Track CAN capture**), ordered newest to oldest, with metadata: protocol, duration, frame counts, buffer overruns, gaps, `origen` (`mcp` or `app`), and `muestrasGps` (0 if recorded without GPS) |
| `leer_captura_can` | Paginated frames from a CAN capture in candump format (`id`, `desde`, `limite` 1–1,000, default 500) with identical metadata including `origen` and `muestrasGps` |
| `leer_gps_captura_can` | Paginated GPS samples from a track capture (`id`, `desde`, `limite` 1–1,000, default 500): `{id, total, desde, muestras: [{tMs, velocidadKmh, precisionM, rumboGrados}]}`. `tMs` aligns with frame timestamps; missing GPS fields return `null`. Never returns latitude or longitude (coordinates are not recorded). Error if capture does not exist or lacks GPS |

Control tools, **disabled by default** (master setting labeled «Allow MCP remote control (trips, sampling, capture)»). These only appear in `tools/list` if permissions are explicitly granted in **Settings → Advanced & diagnostics → MCP server**; client calls without permission return an error indicating where to enable them. Permissions **are not included in app backups**: restoring a backup never grants remote control.

| Tool | Permission | Action |
|---|---|---|
| `finalizar_viaje` | Allow MCP remote control | Concludes current trip (saved to history with statistics) while keeping adapter connected: polling continues and `get_dtc` remains responsive. Idempotent: does nothing if no trip is active |
| `iniciar_viaje` | Allow MCP remote control | Starts a new trip over the connected adapter. Fails if disconnected or if a trip is already recording |
| `set_muestreo` | Allow MCP remote control | Changes standard polling preset: `estandar_2s`, `1s`, `500ms`, `250ms`, or `maximo` (see [OBD sampling](#obd-sampling)) |
| `iniciar_captura` | Allow MCP remote control | Starts high-rate capture of 1 to 6 Mode 01 PIDs (`pids: ["49","4A","11"]`), with `duracion_s` from 1 to 600 (default 60). Returns `capturaId`, accepted/unsupported PIDs, frame batching layout (`lotes`), and accepted adapter acceleration techniques |
| `detener_captura` | Allow MCP remote control | Halts capture and restores standard polling. Returns sample count, Hz, min, max, mean per PID, p50/p95 latency, termination cause, and CSV file path on device |
| `iniciar_sesion_taller` | Allow MCP remote control | Opens a diagnostic session for active vehicle with optional `sintomas` (keys such as `SE_APAGA_AL_SOLTAR`; unrecognized keys return in `sintomasIgnorados`), `sintomas_texto`, `notas`, and `titulo`. Uses previous health check as baseline and suggests relevant tests. Preserves existing open sessions unless `cerrar_anterior: true` |
| `agregar_nota_taller` | Allow MCP remote control | Appends a timestamped note (`texto`, up to 2,000 characters) to the active session, such as physical inspection findings |
| `registrar_medicion` | Allow MCP remote control | Logs a multimeter measurement to the active session: `plantilla` (`TPS`, `MAP`, `ECT`, `IAT`, `INYECTOR`, or `BATERIA`), wire `funcion` (`REF_5V`, `MASA`, `SENAL`, `ALIMENTACION_12V` for battery terminals, `OTRO` for injector coil), `condicion` (`CERRADO`/`MEDIO`/`FONDO` for TPS, `CONTACTO` for MAP, ECT, and IAT, `DESCONECTADO` for injector, `CONTACTO`/`ARRANQUE`/`CARGA` for battery), `valor`, and `unidad` (defaults to V; ohms for injectors). Evaluates cell against reference bands citing source («Typical (editable)», «Source: …», or «Edited by you»; half throttle has no fixed band and must fall between closed and wide open), wire color if defined in model wiring (or generic uncolored template), remaining unmeasured cells, and cumulative verdict for that sensor template in the session (e.g. «Reference and ground correct; low signal throughout travel»). If a completed TPS sweep exists, compares against ECU readings (±0.1 V typical at closed and wide open): «ECU receives identical values» or «points to wiring harness or ECU-side connector fault» |
| `iniciar_prueba_guiada` | Allow MCP remote control | Launches a guided diagnostic test (`tipo`): `TPS_BARRIDO`, throttle sweep with engine off and ignition on in 5 steps (closed, half, wide open, closed, 8 s slow sweep); `MINIMO_RETORNO`, engine running in neutral, 45 s idle and 3 return cycles from ~3,000 RPM (drift, oscillation, stalls, idle TPS, undershoot valley, recovery time); `ARRANQUE_FRIO`, ignition 10 s, cranking (attempts and duration), warm-up to 60 °C (warm-start warning if engine and air differ ≥ 5 °C, fast-idle profile, ECT jumps); `BATERIA_CARGA`, high-speed `AT RV` queries with ignition 10 s, cranking 8 s, idle 15 s, high revs 10 s (resting, cranking valley, charging, and overcharging against typical bands, logging adapter reboots as findings and using vehicle calibration offset if set); `MAP_BARO`, 10 s ignition on comparing MAP against PID 33 barometric pressure, phone barometer, or estimated GPS altitude, with ±3 kPa typical tolerance plus source uncertainty. Checks preconditions before execution (adapter, engine state, vehicle stationary, PID availability; neutral gear and warm engine display warnings); execution blocks if preconditions fail. `voz` (defaults to `true`) delivers spoken guidance on phone, and `vref_v` sets reference voltage for % to V conversion (5.0 V typical). Runs atop quick capture architecture (battery test uses `AT RV` bursts sharing the single-capture lock): tests run one at a time and cannot run during active manual captures. Vehicle motion aborts tests; connection loss flags incomplete status allowing re-runs, except during battery cranking where partial data is analyzed for adapter dropouts. Results and CSVs log to the active Workshop session |
| `avanzar_prueba_guiada` | Allow MCP remote control | Equivalent to tapping «Ready»: once throttle is held in target position, starts 5 s countdown ring. During cranking and warm-up of cold start, acts as «Finish». Slow sweeps and idle phases advance automatically |
| `cancelar_prueba_guiada` | Allow MCP remote control | Aborts active test and restores normal polling without recording data to session |
| `borrar_dtc` | Allow clearing codes via MCP (requires previous permission) | Clears trouble codes (Mode 04) and rescans active codes before and after. Requires `confirmar: "BORRAR"`, vehicle **stationary** (speed 0 confirmed within 2 s), and **user approval on phone notification** (or bypass mode, see [Workshop writes](#workshop-writes)). If ECU returns `7F 04 22` (conditions not correct, common with engine running), reports failure. Leaves a system notification. **Resets readiness monitors**: roadworthiness inspection stations (e.g. Colombia) may reject the vehicle until drive cycles complete |
| `guardar_en_catalogo` | Allow MCP remote control | Saves or updates a discovered diagnostic command in your local catalog (e.g. DID found via `leer_did`): `entrada` containing `id`, `marca`, `tipo`, `pasos`, `descripcion`, and optional fields; `verificado` can only be set to `true` if verified on vehicle. Validates before saving |
| `borrar_dtc_modulo` | Allow workshop writes via MCP | Clears trouble codes on **a specific ECU module** by header (UDS `14 FF FF FF` or specified `grupo`), e.g. ABS or body controllers outside Mode 04 scope |
| `reiniciar_ecu` | Allow workshop writes via MCP | Reboots an ECU or module (UDS `11`): `tipo` `suave` (03, default), `apagado_encendido` (02), or `duro` (01); `header` defaults to `7E0`. Requires **engine off** with ignition on. Unresponsiveness during reboot is normal |
| `prueba_a_bordo` | Allow workshop writes via MCP | Mode 08: omitting `tid` lists supported routine IDs (`08 00`, without confirmation); specifying `tid` (and optional `datos`) executes routine. Few production vehicles implement Mode 08 |
| `ejecutar_catalogo` | Allow workshop writes via MCP | Executes a catalog write or routine by `id`. Notification indicates risk level and flags **UNVERIFIED** entries; medium or high risk requires engine off |
| `comando_escritura` | Allow workshop writes via MCP | Freeform sequence of 1 to 10 commands (`pasos`) to a module (optional `header`) with a `descripcion` visible on approval notification: session control, security access `27`, write `2E`, actuator control `2F`, routines `31`, reboot `11`, clear `14`… Execution halts at first non-positive response and reports failed step. **ECU flashing and reprogramming (`10 02`, `34`–`38`, `3D`) is permanently blocked**: Bluetooth ELM327 adapters lack reliable bandwidth for flashing, and interrupted transfers can brick ECU modules |

### Workshop writes

Every write operation executed via MCP (`borrar_dtc` and tools under «Allow workshop writes via MCP») follows an identical enforcement path:

1. **Hardware safety guards**, non-bypassable: adapter connected and vehicle **stationary** (speed 0 confirmed within 2 s); `reiniciar_ecu` and medium/high-risk catalog routines additionally require **engine off** (RPM 0 confirmed recently).
2. **Flash lock**: commands `10 02`, `34`–`38`, and `3D` are permanently rejected.
3. **User tap confirmation**: phone displays a notification reading «AI requests permission to write to vehicle» detailing action, target module, and exact command bytes, with **Allow** / **Deny** buttons (dismissing the notification denies access). Requests time out and cancel after 45 s. If app notifications are disabled, writes are automatically rejected and the tool reports the cause.
4. **Audit logging**: every write attempt is logged (`get_registro_escrituras`), noting authorizing entity and ECU responses.

**Skip confirmation prompts (bypass mode)**: located below the write permission switch, requiring confirmation of a warning dialog. With bypass active, writes execute without interactive prompts (hardware safety guards and flash locks remain strictly enforced); the server notification displays «BYPASS: unconfirmed writes enabled». Stored in volatile memory only: **automatically disables upon stopping MCP server**, losing Wi-Fi, or closing the app.

### Manufacturer catalog

`get_catalogo` compiles brand-specific diagnostic operations not defined in standard OBD:

- **Built into the app** (`catalogo_propietario.json`): 461 readings curated from public sources, all marked `verificado: false`. 263 cover Mazda CX-30 PCM and transmission parameters (oil life, fuel dilution, timing chain stretch, per-cylinder misfires…, largely validated on Mazda3), and 198 cover generic Renault and Nissan commands, unconfirmed on Kardian or March. **No public definitions exist for the TVS Apache** (beyond identifying Bosch fuel injection) and no public write routines exist for any of these vehicles. Source documentation is detailed in [catalogo-propietario-fuentes.md](../catalogo-propietario-fuentes.md).
- **User catalog**: custom definitions saved via `guardar_en_catalogo` (or by AI clients with control permissions). If an `id` collides with a built-in entry, user definitions take precedence.

To discover proprietary channels on your vehicle: `descubrir_modulos` → `leer_did` across DID ranges → `guardar_en_catalogo` for meaningful responses. Read commands execute without interactive confirmation; writes and routines (`ejecutar_catalogo`, `comando_escritura`) require explicit approval.

Built-in entries derive from [OBDb](https://github.com/OBDb) and are distributed under **CC-BY-SA-4.0** (data definitions; RevScope application code remains licensed under Apache-2.0).

**Security**: the server binds exclusively to your **Wi-Fi** IP address (never mobile data), and an internal monitor verifies IP consistency every 60 seconds — disconnecting from Wi-Fi or changing networks immediately shuts down the server. Enable only on trusted networks (home, personal workshop).

## OBD sampling

**Settings → Advanced & diagnostics → OBD sampling.** Controls polling frequency for standard driving telemetry (all PIDs polled concurrently). Enforcement follows `interval = min(group base interval, preset)`:

| Preset | RPM, speed, throttle | Load, coolant, MAF, MAP, torque | Temperatures, trims, O2, fuel rate | Workshop | Note |
|---|---|---|---|---|---|
| **Standard (2 s)**, default | 100 ms | 500 ms | 2 s | 1 s | Same as up to v1.19 |
| 1 s | 100 ms | 500 ms | 1 s | 1 s | |
| 500 ms | 100 ms | 500 ms | 500 ms | 500 ms | |
| 250 ms | 100 ms | 250 ms | 250 ms | 250 ms | Higher battery draw and thermal load |
| Maximum | no delay | no delay | no delay | no delay | Real rate bounded by adapter speed: higher battery and thermal load. Self-throttles (see below) |

These represent target cycle times: intervals measure from cycle start, eliminating cumulative request duration latency, though all PID groups share a single half-duplex serial Bluetooth link (each round-trip takes 30–150 ms) with adapter throughput setting the ceiling. With the screen off, intervals expand (×5, ×3, and ×2) to conserve battery, **unless** an MCP client has called a tool within the past 60 s. Following an adapter `BUFFER FULL` response, intervals double (up to ×8), halving back every 30 s without errors. On **Maximum**, fallback rules scale from 250 ms baseline intervals (with no delay there is nothing to multiply): after `BUFFER FULL` or with the screen off and no MCP client, Maximum behaves as 250 ms with scaling factors applied. Additionally, device thermal state is checked every 5 s using quick capture rules: under moderate thermal rise or battery below 30% without charging, polling steps down to 250 ms; under severe thermal rise or battery below 15% without charging, it returns to Standard (2 s) until temperatures normalize.

**Quick capture.** To inspect individual sensors at professional diagnostic scan rates (such as accelerator pedal PIDs `49`/`4A` alongside throttle position `11`), use **Workshop → Sensors → Quick capture** (top bar button) or MCP tools `iniciar_captura` / `get_captura` / `detener_captura`:

- Polls only 1 to 6 selected PIDs; other dashboard gauges pause during capture. Coolant temperature continues polling every 5 s (overheat protection) and battery voltage every 10 s.
- Acceleration techniques, each skipped if the adapter returns `?`: multi-PID CAN packaging (3 single-byte PIDs fit into one frame), response count suffixes (`01 49 4A 11 1`), ECM physical addressing (`AT SH 7E0` + `AT CRA 7E8`, 11-bit CAN only), `AT AT 2`, and low-latency Bluetooth reading. All settings revert upon conclusion, including on connection drops. Because stopping often interrupts mid-query exchanges, the app resynchronizes with the adapter and retries commands if responses lack `OK` (e.g. `STOPPED`); if ECM filters fail to clear, subsequent DTC reads clear them before scanning. DTC reads during capture use functional headers and capture resumes afterward.
- Displays **measured throughput** (Hz per PID, requests/second, p50/p95 latency, and limiting bottlenecks). Actual rates depend on adapter hardware, protocol, and ECU capabilities. As an unverified baseline, a genuine classic Bluetooth ELM adapter on 500k CAN may achieve 20–35 requests/s, whereas cheap clones or K-line vehicles achieve substantially less.
- Writes a streaming CSV during execution to `cache/exports/revscope-captura-YYYYMMDD-HHmmss.csv` (retains the 5 most recent files): header metadata `# revscope-captura v1; adaptador=…; elm=…; protocolo=…; pids=…; inicio=…; tecnicas=…` followed by records `epoch_ms,t_ms,pid,nombre,valor,unidad,lote,latencia_ms`, with integer millisecond timestamps. Upon completion, share this CSV or an unrolled **wide** format (`epoch_ms,t_ms,<pid>,…`, one row per request).
- During trip recording, Room database storage caps insertion rates to ~1 row every 100 ms per PID, preventing capture runs from inflating storage.

| Setting | Default | Description |
|---|---|---|
| Sampling preset | Standard (2 s) | Table above. Preserved in backups |
| Quick capture: maximum duration | 5 min | 1, 5, 10, or 30 min. MCP maximum is 10 min |
| Last measured throughput | — | Hz per PID and p50 latency from latest capture in session |

**Capture safeguards** (evaluated every second): stops upon reaching maximum duration, phone battery dropping below 15% without charging, severe phone thermal throttle states (Android 10+ SEVERE state), or if over 30% of at least 50 requests fail ("adapter unable to sustain capture throughput"). Under moderate thermal rise or battery below 30% without charging, rate is capped at 10 Hz. Stopping for any reason restores standard polling and displays termination rationale.

## Backup

Packages trips, vehicle profiles, health reports, and settings into a single `.zip` archive. **AI API keys are never included** (stored encrypted on device) — re-enter keys after restoring onto a new phone.

| Action | Description |
|---|---|
| Export backup | Saves `revscope-backup-YYYY-MM-DD.zip` to selected destination (system file picker) |
| Import backup | Overwrites **all** current app data with contents of selected backup file, prompting confirmation; app restarts automatically upon completion |
| Weekly automatic backup | Toggle, **enabled by default** — exports a backup weekly to `Downloads/RevScope`, retaining the **4** most recent archives |

Workshop sessions are included in backups with timelines, metrics, and downsampled series. Full raw CSV captures copied into session directories (app internal storage) are **not** included in backups.

## Custom PIDs

JSON editor following RevScope's internal Mode 01 schema (`pids_mode01.json`), allowing configuration of proprietary manufacturer parameters — typically identified using Workshop [Mode 22 Scanner](user-manual.md#workshop). Includes **Validate and apply**, **Share pack** (exports definitions as plain text via email/messaging), and **Import** (loads shared `.json` or `.txt` files).

## Pico y placa

RevScope includes built-in restriction rules for two Colombian cities and recognizes a third pending confirmed schedules:

| City | Scheme | Operating hours | Restriction digit | Validity |
|---|---|---|---|---|
| **Medellín** | Weekly rotation (H1-2026) | 5:00–20:00 | Cars: **last** digit of license plate · Motorcycles: **first** digit | Through **July 31, 2026** |
| **Bogotá** | Odd/even day of the month | 6:00–21:00 | Last digit of license plate (motorcycles **always exempt**) | Full year 2026 |
| **Cali** | Unconfirmed | 6:00–19:00 (source) | — | — |

Active Medellín rotation (H1-2026, restrictions lifted on weekends and public holidays):

| Day | Restricted digits |
|---|---|
| Monday | 1, 7 |
| Tuesday | 0, 3 |
| Wednesday | 4, 6 |
| Thursday | 5, 9 |
| Friday | 2, 8 |

Bogotá alternates based on the **day of the month**: on odd calendar days, plates ending in 1–5 are permitted (restricted: 6, 7, 8, 9, 0); on even calendar days, the restriction reverses. Motorcycles are never restricted in Bogotá.

> ⚠️ Upon reaching August 1, 2026 (or designated expiration dates), the app ceases enforcing expired Medellín rules. If **"AI-powered Pico y placa in any city"** is enabled (Settings → Voice alerts), the app queries your AI provider for updated schedules and applies them automatically — displaying a confirmation notification outlining the new rotation for verification. Without this toggle, the Vehicle status card indicates "rules expired" until schedules are updated manually via JSON (below) or delivered via an app update.

**Cali** appears in the city catalog without built-in rotation rules — the Vehicle status card indicates "no data", prompting manual schedule entry. The internal engine (`PicoYPlacaEngine`) supports custom city rules defined in JSON, following this verified schema (fields use internal defaults when omitted):

```json
{
  "cityId": "cali",
  "displayName": "Cali",
  "scheme": "WEEKDAY_ROTATION",
  "rotation": { "2": [1, 2], "3": [3, 4], "4": [5, 6], "5": [7, 8], "6": [9, 0] },
  "startHour": 6,
  "endHour": 19,
  "carDigit": "LAST",
  "motoDigit": "FIRST",
  "motosExentas": false,
  "validFromMs": 1767243600000,
  "validUntilMs": 1798779599000
}
```

For Bogotá-style rules (odd/even day of month), configure `"scheme": "DATE_PARITY"` with `dateParityRestricted: {"ODD_DAY": [...], "EVEN_DAY": [...]}` instead of `rotation`.

> ⚠️ As of this writing, the engine and JSON format above are fully implemented end-to-end (stored in `PICO_PLACA_RULES_JSON` and consumed by both Vehicle status cards and city-change voice warnings), but **a dedicated text field in Settings to paste this JSON has not yet been exposed in the UI** — unlike Custom PIDs and Custom Alerts, which provide in-app editors. Until that settings interface is added, this remains a platform capability without a user-facing input form.

Each vehicle's Pico y placa city is selected in **Workshop → Vehicle profiles**, alongside license plate details, using a dropdown menu ("None" plus listed cities).

---

Something not behaving as described here? Check the [FAQ](faq.md) or review implementation details in [Development](development.md).
