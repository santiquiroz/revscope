# Tipo de vehículo auto/moto — sub-proyecto C (2026-08-18)

Tercero de cuatro sub-proyectos (A mapa base → B UX navegación → C tipo de vehículo → D onboarding). Alcance definido por el usuario: **IMU/seguridad + dashboard/gauges. NO incluye PIDs por tipo.**

## Problema

El campo `type: String` ("CAR" | "MOTORCYCLE") existe en `VehicleProfileEntity` desde schema v1, pero solo gobierna pico y placa, ítems de mantenimiento y dos prompts de IA. Todo el pipeline de sensores y gauges lo ignora:

- `CrashDetector`: umbrales hardcoded diseñados para moto (comentarios "highside", "caída"), corren idénticos en auto. Copy de settings dice "accidente de moto" siempre.
- Lean angle: se calcula y muestra en autos (TrackMode, reportes de sesión, share card) donde no significa nada.
- `WetLeanGuard`: moto por diseño, corre en auto confiando en que la física nunca dispare.
- `AdaptiveGearLearner`: hardcodea exactamente 6 marchas con ratios de auto; `isCalibrated()` exige 30 observaciones en las 6 → **una moto 5 velocidades jamás calibra** y `GearDisplay` queda bloqueado para siempre. `gearRatios` del perfil se escribe `null` y nunca se lee.
- Gauges: solo RPM usa el perfil. Banda roja dibujada a 85% fijo del arco (ignora `redlineRpm` real). Speed max 260 fijo. Fallbacks de redline inconsistentes: entidad 6500 vs `AlertsEngine`/`SessionAggregator` 10500 (valor moto).

## Diseño

### C1. Fuente de verdad

- Enum `VehicleType { CAR, MOTORCYCLE }` en `core:data` (o `core:common`), parseado desde el `type` String existente con default CAR.
- Se propaga por `ObdSessionManager._activeProfile` (ya existe) a los consumidores. Componentes sin acceso al manager reciben el tipo por parámetro/flow, no leyendo DB por su cuenta.

### C2. Detección de caída por tipo

- Los umbrales de `CrashDetector` pasan de `companion object` a un `CrashThresholds` por tipo:
  - `MOTORCYCLE`: valores actuales (fueron diseñados para moto).
  - `CAR`: mismo esqueleto inicial sin supuestos de caída lateral; queda como set independiente tuneable.
- `CrashResponder`/`CrashDetector` reciben el tipo del perfil activo al arrancar sesión.
- Copy de settings dinámico según perfil activo: "posible accidente de moto" ↔ "posible choque".

### C3. Lean solo moto

- UI de lean angle y max lean oculta cuando el perfil activo es CAR: `TrackModeScreen`, `SessionDetailScreen`, `SessionCompareScreen`, `TripShareCard`. (El debrief IA ya está gated por tipo.)
- `WetLeanGuard` no se instala para CAR (gate explícito en el wiring de sesión, no confiar en la física).
- Sesiones históricas: el gate usa el tipo del perfil de la sesión si está disponible; si no, el del perfil activo.

### C4. Marchas por perfil

- Nuevo campo `gearCount: Int` en `VehicleProfileEntity` (default CAR 6, MOTO 5; editable en el form de vehículo). Migración de schema.
- `AdaptiveGearLearner`: clusters parametrizados por `gearCount`; ratios default escalados por tipo (moto usa escala de ratios más alta); `isCalibrated()` exige `gearCount` clusters con `MIN_OBSERVATIONS_PER_GEAR`.
- `DerivedMetricsEngine`: tabla de marchas del mismo tamaño; elimina la duplicación con el learner (una sola fuente de defaults).
- `GearDisplay`: colores/render para 1..gearCount.
- La columna huérfana `gearRatios` queda para persistir lo aprendido (si el wiring lo permite barato); si no, se documenta como pendiente — no es objetivo de este spec.

### C5. Gauges por tipo

- Defaults al crear perfil según el chip Auto/Moto del form: MOTO `maxRpm=12000, redlineRpm=10500`; CAR `8000, 6500` (hoy siempre 8000/6500 aunque elijas Moto).
- `RpmGauge`: banda naranja/roja y tick calculados desde `redlineRpm/maxRpm` reales, no 60/25/15% fijo del arco.
- `SpeedGauge`: `maxSpeed` por tipo — CAR 260 (actual), MOTO 299.
- Fallbacks unificados: `AlertsEngine.DEFAULT_REDLINE_RPM` (10500) y `SessionAggregator` (10500) usan el perfil activo; sin perfil, fallback por tipo (CAR 6500, MOTO 10500).

## Fuera de alcance

- PIDs por tipo / `enabledPids` (descartado por el usuario).
- Pack de sonido de motor mono/bicilíndrico.
- Temp gauge por tipo de refrigeración.
- Fila coolant en Android Auto para motos (problema de PID ausente, no de tipo).
- Umbral por-perfil de `MAX_DRY_LEAN_DEG` (hoy global; queda global).

## Archivos

| Archivo | Cambio |
|---|---|
| `core/data/.../VehicleProfileEntity.kt` | `gearCount` nuevo + migración |
| `core/data` o `core/common` | `VehicleType` enum nuevo |
| `feature/vehicle/.../VehicleViewModel.kt` + `VehicleProfileScreen.kt` | defaults por tipo, campo gearCount |
| `core/obd/.../safety/CrashDetector.kt` + `CrashResponder.kt` | `CrashThresholds` por tipo |
| `core/obd/.../road/WetLeanGuard.kt` + wiring en `ObdForegroundService` | gate por tipo |
| `core/intelligence/.../gear/AdaptiveGearLearner.kt` | gearCount + ratios por tipo |
| `core/obd/.../telemetry/DerivedMetricsEngine.kt` | tabla unificada |
| `feature/dashboard/.../gauges/{RpmGauge,SpeedGauge,GearDisplay}.kt` | banda roja real, max por tipo, 1..N |
| `feature/dashboard/.../DashboardScreen.kt` + `TrackModeScreen.kt` | gates de lean, speed max |
| `feature/session/.../{SessionDetailScreen,SessionCompareScreen,TripShareCard}.kt` | gates de lean |
| `core/obd/.../alerts/AlertsEngine.kt` + `session/SessionAggregator.kt` | fallback unificado |
| `feature/settings/.../SettingsScreen.kt` | copy caída dinámico |

## Tests

- `AdaptiveGearLearner` calibra con `gearCount=5` (bug actual: imposible).
- Banda roja de `RpmGauge` posicionada según `redlineRpm` real (casos 8000/6500 y 12000/10500).
- Defaults por tipo al crear perfil moto vs auto.
- `CrashThresholds` seleccionado por tipo; copy correcto.
- Lean invisible con perfil CAR en cada superficie listada.
- Fallback de redline unificado (sin 10500 fantasma en autos).
