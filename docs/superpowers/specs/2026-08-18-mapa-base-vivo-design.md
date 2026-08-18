# Mapa base vivo — sub-proyecto A (2026-08-18)

Primero de cuatro sub-proyectos (A mapa base → B UX navegación → C tipo de vehículo → D onboarding). Este spec cubre solo A.

## Problema

Instalación limpia sin adaptador OBD y sin key de IA:

1. **Radares nunca aparecen.** El mapa lee `cameraDao.all()` una sola vez en `LiveMapViewModel.init` (suspend, no Flow). No hay fetch inicial: los únicos productores son el botón manual en Ajustes, el worker semanal (no-op sin descarga previa) y `CameraCoverageTracker` (solo durante viaje activo). Usuario nuevo sin viaje = tabla vacía para siempre. Además el ViewModel sobrevive cambios de tab (`restoreState`), así que reabrir el mapa no recarga: solo un reinicio de proceso.
2. **El mapa no centra.** Sin `requestLocationUpdates` ni `LocationComponent` en `feature:map`: solo un `getLastKnownLocation` sincrónico en init. Sin fix cacheado el mapa abre en vista mundo (0,0). El puck `ICON_ME` solo existe durante un viaje activo (lo alimenta `GpsTrackRecorder` vía `ObdForegroundService`).
3. Bugs de plumbing: `initialCenter` leído como `.value` (no recompone), `mapRef` ausente de las keys del `LaunchedEffect` de datos, `INITIAL_ZOOM = 16.0` declarado y nunca usado.

## Objetivo

El mapa funciona standalone como Google Maps abierto: puck vivo, centrado al abrir, radares presentes sin tocar Ajustes ni iniciar viaje. Cero consumo de GPS con el mapa cerrado.

## Diseño

### A1. GPS vivo — `MapLocationProvider`

Nuevo `MapLocationProvider` en `feature:map` (decisión: pipeline propio, NO `LocationComponent` de MapLibre — un solo camino de render, sin duplicar puck durante viajes, testeable).

- Envuelve `LocationManager.requestLocationUpdates` (GPS_PROVIDER, ~1 s / ~5 m), expone `StateFlow<GeoPoint?>`.
- Ciclo de vida atado a la pantalla: arranca/para con `DisposableEffect` mientras `LiveMapScreen` está compuesta. Mapa cerrado = cero GPS extra.
- Fusión en el ViewModel: con sesión activa manda el feed del service (`LiveRouteHolder`, como hoy); sin sesión, el provider alimenta el mismo puck `ICON_ME` y la lógica de follow existente.

### A2. Centrado inicial

- Al abrir: centra en `lastKnownLocation` a `IDLE_ZOOM` (13) — como hoy, pero `initialCenter` pasa a `collectAsState`.
- Primer fix vivo: recentra una vez a `INITIAL_ZOOM` (16) — la constante muerta cobra uso.
- `mapRef` entra a las keys del `LaunchedEffect` de datos.
- El follow y el FAB MyLocation no cambian de semántica (pan lo apaga, FAB lo rearma).

### A3. Radares reactivos

- `SpeedCameraDao`: nueva query `observeAll(): Flow<List<SpeedCameraEntity>>` (invalidation de Room). El ViewModel colecta el Flow en vez del one-shot.
- Cualquier descarga (manual, worker, auto) aparece en el mapa al instante. Mata los tres bugs: one-shot, ViewModel que sobrevive tabs, reinicio necesario.
- Mismo patrón para `PotholeDao`.

### A4. Auto-descarga de radares

- Al primer fix del mapa (o de una sesión): si no hay cámaras cerca en DB, o la última descarga está vieja o a >35 km (lógica existente de re-descarga por viaje), dispara `SpeedCameraUpdater.downloadAround()` automáticamente.
- Reusa el throttle de `CameraCoverageTracker` (60 s entre evaluaciones / cooldown 30 min), desacoplándolo de la sesión activa: el provider del mapa también le entrega fixes.
- El botón manual en Ajustes queda como está (diagnóstico/forzar).

### A5. Errores

- Permiso de ubicación ausente: banner en el mapa "Ubicación desactivada" con botón que lanza el request ahí mismo (hoy el mapa nunca pide permiso).
- Sin red durante auto-fetch: silencioso; la DB local sigue sirviendo; reintento en próxima apertura respetando cooldown.
- Sin fix (interior): se queda en lastKnown; sin spinner ni estados extra.

## Archivos

| Archivo | Cambio |
|---|---|
| `feature/map/.../MapLocationProvider.kt` | nuevo |
| `feature/map/.../LiveMapViewModel.kt` | Flow de cámaras/potholes, fusión de fuentes GPS, hook auto-fetch |
| `feature/map/.../LiveMapScreen.kt` | DisposableEffect provider, centrado, keys de efectos, banner permiso |
| `core/data/.../SpeedCameraDao.kt` | `observeAll(): Flow` |
| `core/data/.../PotholeDao.kt` | `observeAll(): Flow` |
| `core/obd/.../CameraCoverageTracker.kt` | desacople de sesión (o `CameraAutoFetcher` nuevo si el desacople ensucia) |

## Tests

- Unit: máquina de centrado (lastKnown → primer fix → no recentrar tras pan), Flow de radares refleja inserts en `LiveMapData`, decisión de auto-fetch (DB vacía / stale / lejos) como función pura.
- Manual: instalación limpia sin adaptador → abrir mapa → centrado + radares visibles sin tocar Ajustes.

## Fuera de alcance (specs siguientes)

- B: auto-reroute, cámara dinámica en maniobras, búsqueda prominente, ETA viva.
- C: `type` CAR/MOTORCYCLE gobernando IMU/seguridad y gauges (hoy huérfano: crash constants moto-hardcoded, lean en autos, gear learner 6-marchas, gauges fijos).
- D: onboarding wizard (tipo vehículo, modo sin adaptador, paso IA con propuesta de valor).
