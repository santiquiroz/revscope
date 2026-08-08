# Diseño: sonido de motor por RPM, mapa navegable y radio de radares configurable

Fecha: 2026-08-08. Sesión autónoma (usuario ausente, delegó decisiones). Tres features independientes.

## Contexto de investigación (last30days + web)

- SoundRacer (el adaptador comercial que inspiró la idea) descontinuó sus apps; recomienda RevHeadz. RevHeadz vende packs: V12 italiano, V8 muscle, rotativo RX, moto V4, motosierra — el mercado valida sonidos "serios" + "absurdos" conviviendo.
- TikTok #enginesound: 11.9M views en 12 videos del último mes — el sonido de motor es contenido viral; los sonidos graciosos (fart cans, memes de exhosto) son parte central del meme.
- Dato curioso: hay gente pagando módulos OBD2 (Carista) para *apagar* el V8 falso de fábrica — el sonido debe ser 100% opt-in y fácil de apagar.
- osmdroid quedó archivado upstream; MapLibre es el reemplazo recomendado. Migrar es proyecto v3 — hoy seguimos en osmdroid 6.1.20.

## F1 — Simulador de sonido de motor (RPM → parlantes)

**Decisión: síntesis procedural en tiempo real con AudioTrack.** Sin assets de audio (repo no tiene res/raw y no hay grabaciones licenciables); la síntesis da control total de pitch por RPM sin artefactos de resampling.

- `core:obd/sound/SoundPack.kt` — catálogo: deportivos V8_MUSCLE, V10_F1, V12_GT + graciosos FART_ENGINE, PODRACER, ARCADE_8BIT. Cada pack = perfil de armónicos, ruido, jitter de ralentí.
- `core:obd/sound/EngineSoundSynth.kt` — DSP puro y testeable: `firingFrequencyHz(rpm, cilindros)`, suavizado exponencial de RPM, generación de PCM 44.1kHz mono 16-bit.
- `core:obd/sound/EngineSoundController.kt` — @Singleton; AudioTrack MODE_STREAM en USAGE_MEDIA (llega al intercomunicador del casco, igual que AlertsEngine); se suscribe a `ObdSessionManager.readings` (PID 0C = RPM, 11 = throttle); ciclo de vida atado a la sesión de telemetría en `ObdForegroundService` (patrón GpsTrackRecorder).
- Settings: sección "Sonido de motor" — toggle, selector de pack, volumen %. Keys DataStore: `ENGINE_SOUND_ENABLED/PACK/VOLUME`.
- Apagado por defecto (lección Carista).

## F2 — Mapa usable día a día

Sin motor de routing propio (Valhalla/GraphHopper = proyecto aparte). Cuatro mejoras concretas sobre osmdroid:

1. **Follow-mode real**: hoy recentra siempre y pelea con el usuario. Toggle: pan manual desactiva follow; FAB recentrar lo reactiva.
2. **Heading-up**: rotación del mapa según rumbo GPS (`setMapOrientation`), toggle norte-arriba/rumbo-arriba.
3. **Ruta a destino**: long-press fija destino → OSRM público (`router.project-osrm.org`) → polyline + chip distancia/ETA → botón "Navegar" lanza Google Maps turn-by-turn (`google.navigation:`). Decoder de polyline puro + test.
4. **Tiles nocturnos**: ColorMatrix invertido para manejar de noche, toggle.

## F3 — Radio de alertas de fotomultas

Hoy: `ALERT_DISTANCE_M = 400.0` hardcodeado (SpeedCameraAlerter.kt:19) + duplicado como círculo dibujado (LiveMapScreen.kt:54). Usuario: "avisa demasiado".

- Nueva key DataStore `CAMERA_ALERT_RADIUS_M`, **default 250 m** (antes 400).
- `SpeedCameraAlerter` la lee al cargar y expone `reloadSettings()`; clamp 100–1000 m en función pura testeable.
- Círculo del mapa usa el mismo valor (una sola fuente de verdad).
- Campo numérico en Settings sección "Radares de velocidad".
- Cooldown por cámara (120 s) y cono ±60° quedan igual — el problema es el radio, no la dirección.

## Testing

- Unit: síntesis (frecuencia de encendido, clamps, PCM no silente/no clipeado), decoder polyline, clamp de radio de radar. TDD en los cálculos puros.
- Manual en S25 vía ADB WiFi: build installDebug + prueba de humo.

## Fuera de alcance (v3 backlog)

Migración MapLibre, búsqueda Nominatim, turn-by-turn propio, grabaciones reales de motores, sonido en Android Auto.
