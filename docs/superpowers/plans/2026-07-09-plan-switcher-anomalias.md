# Plan: Switcher de vehículo estilo R5 + alertas de anomalías por voz

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development.

**Goal:** (1) El pill flotante pasa de "estado de conexión" a "selector de vehículo" estilo R5: [punto conexión] [🏍/🚗 nombre] [⌄] → bottom sheet Selecciona con lista, agregar vehículo y fila de adaptador. (2) Alertas de audio automáticas: anomalías del AnomalyDetector habladas, vigilancia del testigo MIL en marcha, y alertas por umbral configurables sobre cualquier PID.

**Realidad TPMS:** presión de llantas NO está en OBD2 estándar; Apache sin sensores; CX-30 no lo expone como PID estándar. La vía es el umbral configurable sobre PIDs custom (Mode 22) si el vehículo lo expone.

## Global Constraints
- Gradle: `C:\Users\santi\.gradle\wrapper\dists\gradle-8.11.1-bin\bpt9gzteqjrbo1mjrsomdt32c\gradle-8.11.1\bin\gradle.bat` (=$GRADLE) desde c:/personal/OBD2.
- Commits español, NUNCA Co-Authored-By. UI español. CancellationException se relanza. Sin cambios de schema Room (todo en DataStore).
- TTS/tonos/vibración/cooldowns: SIEMPRE a través del AlertsEngine existente (leer core/obd/.../alerts/AlertsEngine.kt primero — respetar su patrón de umbrales y cooldowns).

---

### Task 1: Switcher de vehículo estilo R5

**Files:**
- Modify: `app/src/main/kotlin/com/revscope/app/navigation/ConnectionChip.kt` → renombrar conceptualmente a switcher (puede quedarse el archivo): composable `VehicleSwitcherPill(connectionState, activeProfile: VehicleProfileEntity?, onClick)`: [punto de color por estado de conexión] + [icono TwoWheeler/DirectionsCar según type, o icono genérico si null] + [nombre del perfil o "Sin vehículo"] + [Icons.Default.ExpandMore]. Mismo estilo pill semi-transparente actual.
- Modify: `app/src/main/kotlin/com/revscope/app/navigation/VehiclePickerSheet.kt` — restilizar estilo R5 y hacerlo reusable para cambio en caliente: título "Selecciona", lista de perfiles (icono tipo + nombre + placa si existe + radio/check en el activo), botón ancho accent "Agregar otro vehículo" → navega a perfiles, fila inferior "Adaptador: <estado> · Administrar" → navega a AdapterScan. Mantener el checkbox "No volver a preguntar" SOLO cuando el sheet se abre automáticamente al inicio (parámetro `isStartupPrompt: Boolean`); al abrirlo desde el pill no se muestra.
- Modify: `app/src/main/kotlin/com/revscope/app/navigation/RevScopeNavGraph.kt` — el Box overlay TopCenter ahora usa VehicleSwitcherPill (lee activeProfile desde VehiclePickerViewModel/sessionManager — el VM ya existe); tap → abre el mismo sheet con isStartupPrompt=false. El flujo de inicio existente no cambia (isStartupPrompt=true).
- El pill sigue mostrándose solo en rutas del bottom nav.

**Steps:** leer archivos → implementar → `$GRADLE :app:compileDebugKotlin` → commit `feat: selector de vehículo estilo R5 en el pill flotante con hoja de selección`.

---

### Task 2: Alertas de anomalías por voz + vigilancia MIL + umbrales por PID

**Files:**
- Modify: `core/obd/src/main/kotlin/com/revscope/core/obd/alerts/AlertsEngine.kt` (leer completo primero):
  1. Método nuevo `announceAnomaly(mensaje: String)` — TTS + tono + vibración con cooldown propio (>= 60s por mensaje idéntico, mapa de últimos anuncios). Respetar toggles ALERTS_ENABLED/ALERT_TTS_ENABLED.
  2. Método nuevo `announceMilOn()` — "Se encendió el testigo del motor. Revisa los códigos de falla." — una sola vez por sesión (flag reseteable `resetSessionFlags()`).
  3. Umbrales custom: leer de DataStore key nueva `CUSTOM_ALERTS_JSON` (array JSON `[{pid, min?, max?, nombre}]`); en `process(reading)` evaluar y anunciar "«nombre» fuera de rango: X unidad" con cooldown 120s por pid. Cargarlos en `reloadThresholds()` (ya existe y se llama al conectar y al guardar ajustes).
- Modify: `core/obd/src/main/kotlin/com/revscope/core/obd/session/ObdSessionManager.kt`:
  1. Wire AnomalyDetector: revisar cómo fluyen hoy las anomalías (core/intelligence AnomalyDetector → DashboardViewModel banner vía ConnectionViewModel.alerts — LEER ese flujo). Donde se emiten los AnomalyAlert, además del banner llamar `alertsEngine.announceAnomaly(alert.message)` — si el flujo vive en feature/dashboard, mover la llamada al punto común (p. ej. exponer un callback del manager o inyectar AlertsEngine donde se procesa la anomalía). Elegir el punto que NO duplique anuncios si hay varias pantallas.
  2. Vigilancia MIL: en startTelemetry, tras el arranque del pipeline, lanzar job liviano `milWatchJob` (cancelado en stopTelemetry/finalShutdown): cada 120s `rawExchange("01 01\r")` → `ReadinessParser.parse` → si milOn y no estaba → `alertsEngine.announceMilOn()` + emitir el estado como pseudo-reading `ObdReading("MIL", 1.0, "")` para banners. Primer chequeo a los 15s de conectar. CancellationException se relanza.
- Modify: `feature/settings/.../SettingsScreen.kt` + `SettingsViewModel.kt` — en la sección de alertas, subsección "Alertas personalizadas por PID": editor JSON simple (mismo patrón OutlinedTextField monospace + validar/guardar que los custom PIDs) para `CUSTOM_ALERTS_JSON`, con texto de ayuda: `[{"pid":"0A","min":200,"nombre":"Presión de combustible"}]` y nota "Para TPMS u otros sensores del fabricante, define primero el PID custom (Mode 22) y luego su alerta aquí". Guardar → `alertsEngine.reloadThresholds()` (patrón existente de saveAlertSettings).
- Test: `core/obd/src/test/.../alerts/` — si AlertsEngine tiene tests, extender; si es Android-heavy, extraer la evaluación de umbrales custom a función pura `CustomAlertRules.evaluate(reading, rules): String?` + parser JSON, con tests (rango, sin min, sin max, JSON inválido).

**Steps:** leer flujo de anomalías → TDD parser/reglas → wiring → `$GRADLE :core:obd:testDebugUnitTest :app:compileDebugKotlin` → commit `feat: anomalías y testigo del motor hablados, y alertas por umbral configurables por PID`.

---

### Task 3 (inline): build + install + push
