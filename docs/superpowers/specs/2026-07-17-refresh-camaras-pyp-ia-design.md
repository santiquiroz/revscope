# Auto-refresh de cámaras por viaje + pico y placa por IA — diseño

**Fecha:** 2026-07-17
**Aprobado por el usuario:** A+B completos; fuente de pico y placa = IA con web search, alcance global, tráfico mínimo ("modo caveman").

## Problema

1. La base de radares se descarga alrededor de un punto (radio 50 km) y el `CameraRefreshWorker` semanal re-descarga alrededor del **último punto manual** (`LAST_CAMERA_LAT/LON`). Viajar a Bogotá deja la BD anclada a Medellín.
2. Las reglas de pico y placa son constantes hardcoded (`MEDELLIN_2026_S1` vence 2026-07-31, `BOGOTA_2026`, Cali sin reglas). Rotación de agosto en Medellín → app desactualizada hasta un release. Fuera de esas 3 ciudades no hay cobertura.

## A. Cámaras siguen la ubicación

- **`CameraCoverage`** (objeto puro, `:core:obd/cameras`): `needsRefresh(centerLat?, centerLon?, lat, lon)` → true si no hay centro guardado o si la distancia al centro supera 35 km (70 % del radio de descarga de 50 km).
- **`CameraCoverageTracker`** (@Singleton): recibe fixes GPS; cooldown síncrono de 30 min entre intentos; si `needsRefresh` → `SpeedCameraUpdater.downloadAround(pos)` y al éxito persiste el nuevo centro en `LAST_CAMERA_LAT/LON`. Fallo de red → silencioso, reintenta tras el cooldown.
- Wiring: parámetro nuevo `coverageTracker` en `GpsTrackRecorder` (patrón de `cameraAlerter`), inyectado desde `ObdForegroundService`.
- El worker semanal existente no cambia: al seguir el centro auto-actualizado, refresca la región donde realmente estás.
- Global: OSM cubre el mundo; ANSV solo Colombia y su fallo ya está aislado por fuente.

## B. Pico y placa por IA (global)

### Fuente y esquema

- **`RestrictionRulesFetcher`** (`:core:intelligence/restriction`, patrón `LocalInfoFetcher`): pregunta al `AiProvider` activo (requiere `supportsWebSearch`) por la restricción vehicular por placa vigente en {municipio, región, país}.
- **Modo caveman**: system prompt "responde SOLO JSON o NONE, sin prosa ni markdown"; user prompt compacto con el esquema exacto de `parseRulesJson`; `maxTokens` 400. Respuesta `NONE` = ciudad sin restricción.
- **`CityRules.timeZoneId`** (campo nuevo, default `America/Bogota`): ciudades fuera de Colombia evalúan la restricción en su zona horaria. `parseRulesJson` lo lee opcional; `PicoYPlacaEngine.check` lo usa como default.
- **`LocalityDetector`**: se extrae `resolveLocality(lat, lon)` público (municipio, departamento/región, país vía `Address.countryName`); `detectLocalityChange` lo reutiliza.

### Cache (anti-tráfico)

- **`AiRulesCache`** (objeto puro, `:core:obd/legal`): codec de un JSON map `municipio → {fetchedAtMs, rulesJson|NONE}` guardado en la key nueva `AI_RESTRICTION_RULES_JSON`.
- Frescura: reglas → hasta su `validUntilMs`; `NONE` → 30 días. Solo se consulta la IA con ciudad nueva, reglas vencidas o `NONE` caducado. Uso normal ≈ 1-2 llamadas/mes.

### Resolución de reglas (prioridad)

1. Override manual del usuario (Ajustes, JSON) — sin cambios.
2. `CityRegistry` hardcoded **si están vigentes**.
3. Fallback IA: `RestrictionRulesSource` (interfaz en `:core:obd/legal`, implementada por `AiRestrictionRulesSource` en `:core:intelligence`, bound en `IntelligenceModule` — mismo patrón `GpsInfoSink`/`CityInfoAlerter`).
   `CityEnforcementAlerter` la usa cuando no hay ciudad del registro o sus reglas están vencidas (caso Medellín 1-ago).

### Gate y avisos

- Toggle opt-in nuevo `AI_PICO_PLACA_ENABLED` (default off) en Ajustes, junto al de info local. Hint: "Requiere proveedor de IA con búsqueda web — recomendamos Gemini por su capa gratuita".
- Toggle ON sin proveedor válido → aviso en Ajustes (patrón del toggle de info local).
- Transparencia (dato legal): al aplicar reglas nuevas de IA se emite notificación silenciosa con el resumen de la rotación + TTS corto gated por `voicePicoPlaca`.

## Pruebas

- `CameraCoverageTest`: sin centro → refresh; 10 km → no; 40 km → sí; borde 35 km.
- `PicoYPlacaEngineTest`: `timeZoneId` en JSON parseado y usado por `check` (caso CDMX UTC-6); default Bogotá intacto.
- `RestrictionRulesFetcherTest` (provider fake): NONE → None; JSON válido → reglas; JSON con fences markdown → reglas; basura → Unavailable; sin web search → Unavailable.
- `AiRulesCacheTest`: roundtrip, frescura por validUntilMs, NONE 30 días, entrada corrupta → miss.
- `CityEnforcementAlerter`: la lógica de fallback se cubre vía las piezas puras (registro vigente vs vencido).

## Fuera de alcance

- Esquemas de restricción no basados en dígito de placa (ej. zonas de bajas emisiones por calcomanía) — el esquema `CityRules` no los modela; la IA responde NONE si no puede mapear.
- UI de revisión/edición de reglas IA más allá del override JSON existente.
