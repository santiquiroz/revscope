package com.revscope.core.data.datastore

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object PreferencesKeys {
    /** Adapter type: "CLASSIC_BT" | "BLE" | "WIFI" */
    val ADAPTER_TYPE = stringPreferencesKey("adapter_type")

    /** Adapter hardware address (Bluetooth MAC) or IP for WiFi adapters */
    val ADAPTER_ADDRESS = stringPreferencesKey("adapter_address")

    /** Display name of the last successfully connected adapter */
    val ADAPTER_NAME = stringPreferencesKey("adapter_name")

    /** ID of the currently selected VehicleProfileEntity */
    val ACTIVE_PROFILE_ID = longPreferencesKey("active_profile_id")

    /** Show the vehicle picker sheet on app start (default true) */
    val ASK_VEHICLE_ON_START = booleanPreferencesKey("ask_vehicle_on_start")

    /** Polling interval for high-priority PIDs in milliseconds (default 200) */
    val POLLING_INTERVAL_MS = intPreferencesKey("polling_interval_ms")

    /** true = metric units (km/h, °C), false = imperial (mph, °F) */
    val UNITS_METRIC = booleanPreferencesKey("units_metric")

    /** Always true in v1 — dark luxury theme only */
    val THEME_DARK = booleanPreferencesKey("theme_dark")

    // ── AI / Intelligence ────────────────────────────────────────────────────

    /** User's Anthropic API key for Claude-powered DTC explanations (optional) */
    val CLAUDE_API_KEY = stringPreferencesKey("claude_api_key")

    /**
     * User-selected intelligence tier override.
     * Blank = auto-detect from device RAM.
     * Values: "MINIMAL" | "ON_DEVICE" | "FULL"
     */
    val INTELLIGENCE_TIER_OVERRIDE = stringPreferencesKey("intelligence_tier_override")

    /** Master kill switch for all AI/ML features (default true) */
    val AI_FEATURES_ENABLED = booleanPreferencesKey("ai_features_enabled")

    /**
     * User-defined PID definitions (JSON array, same schema as pids_mode01.json).
     * Used for manufacturer-specific parameters, e.g. TVS ride mode via Mode 22.
     */
    val CUSTOM_PIDS_JSON = stringPreferencesKey("custom_pids_json")

    // ── Proveedor de IA (multi-proveedor: Claude, OpenAI, Gemini, compatible-OpenAI) ──

    /** Proveedor de IA activo: "anthropic" | "openai" | "gemini" | "custom" (default anthropic) */
    val AI_PROVIDER = stringPreferencesKey("ai_provider")

    /** Override de modelo para Claude/Anthropic (vacío = usa el default del proveedor) */
    val AI_MODEL_ANTHROPIC = stringPreferencesKey("ai_model_anthropic")

    /** Override de modelo para OpenAI (vacío = usa el default del proveedor) */
    val AI_MODEL_OPENAI = stringPreferencesKey("ai_model_openai")

    /** Override de modelo para Gemini (vacío = usa el default del proveedor) */
    val AI_MODEL_GEMINI = stringPreferencesKey("ai_model_gemini")

    /** Override de modelo para el endpoint compatible OpenAI */
    val AI_MODEL_CUSTOM = stringPreferencesKey("ai_model_custom")

    /** Base URL del endpoint compatible OpenAI (LM Studio/DeepSeek/Groq/OpenRouter…) */
    val AI_CUSTOM_BASE_URL = stringPreferencesKey("ai_custom_base_url")

    // ── Alerts ───────────────────────────────────────────────────────────────

    /** Master switch for audio/haptic alerts (default true) */
    val ALERTS_ENABLED = booleanPreferencesKey("alerts_enabled")

    /** Coolant temperature alert threshold in °C (default 105) */
    val ALERT_TEMP_MAX_C = intPreferencesKey("alert_temp_max_c")

    /** Battery voltage alert threshold in volts (default 11.8) */
    val ALERT_VOLTAGE_MIN = floatPreferencesKey("alert_voltage_min")

    /** Redline RPM for audio alert and dashboard shift light (default 10500) */
    val ALERT_REDLINE_RPM = intPreferencesKey("alert_redline_rpm")

    /** Spoken alerts and launch results via TTS (default true) */
    val ALERT_TTS_ENABLED = booleanPreferencesKey("alert_tts_enabled")

    /**
     * User-defined per-PID threshold alerts (JSON array): [{pid, min?, max?, nombre}].
     * Evaluated in AlertsEngine.process against every reading, 120s cooldown per PID.
     */
    val CUSTOM_ALERTS_JSON = stringPreferencesKey("custom_alerts_json")

    // ── Alertas de voz por categoría (gates only the spoken/TTS output; tone, ─
    // vibration and the visual banner keep firing per ALERTS_ENABLED) ────────

    /** Spoken coolant-temperature alert (default true) */
    val VOICE_TEMPERATURE = booleanPreferencesKey("voice_temperature")

    /** Spoken low-voltage/battery alert (default true) */
    val VOICE_VOLTAGE = booleanPreferencesKey("voice_voltage")

    /** Spoken speed-camera proximity warning (default true) */
    val VOICE_SPEED_CAMERAS = booleanPreferencesKey("voice_speed_cameras")

    /** Spoken AnomalyDetector alert (default false — noisy, field feedback) */
    val VOICE_ANOMALIES = booleanPreferencesKey("voice_anomalies")

    /** Spoken check-engine-light (MIL) warning (default false) */
    val VOICE_MIL = booleanPreferencesKey("voice_mil")

    /** Spoken redline/shift-point warning (default false) */
    val VOICE_REDLINE = booleanPreferencesKey("voice_redline")

    /** Spoken user-defined per-PID threshold alert (default true — opt-in by nature) */
    val VOICE_CUSTOM_THRESHOLDS = booleanPreferencesKey("voice_custom_thresholds")

    /** Spoken 0-100 launch and lap-time announcements (default true) */
    val VOICE_SPORT = booleanPreferencesKey("voice_sport")

    /** Spoken pico-y-placa warning entering a city ≠ profile's with active restriction (default true) */
    val VOICE_PICO_PLACA = booleanPreferencesKey("voice_pico_placa")

    /**
     * Spoken AI-generated local info (festivals, road closures…) on entering a new
     * municipality (default false — opt-in, uses the user's Claude API key, costs money).
     */
    val VOICE_LOCAL_INFO = booleanPreferencesKey("voice_local_info")

    /** Aviso hablado único diario ~25 min antes del atardecer (default true — pico de riesgo moto) */
    val VOICE_SUNSET = booleanPreferencesKey("voice_sunset")

    /** Aviso de hueco del mapa personal en el cono de rumbo (default true) */
    val VOICE_POTHOLES = booleanPreferencesKey("voice_potholes")

    /** Avisos de lluvia inminente + guardián de inclinación en mojado (default true) */
    val VOICE_RAIN = booleanPreferencesKey("voice_rain")

    /** Coach de fatiga: pausa a las 2 h e hidratación con calor (default true) */
    val VOICE_FATIGUE = booleanPreferencesKey("voice_fatigue")

    /** Lean máximo personal registrado en seco — referencia del guardián wet-lean */
    val MAX_DRY_LEAN_DEG = floatPreferencesKey("max_dry_lean_deg")

    // ── Servidor colaborativo (revscope-server, opcional — offline-first) ────

    /** URL base del servidor (ej. https://mi-server:8080). Vacío = sin servidor. */
    val SERVER_BASE_URL = stringPreferencesKey("server_base_url")

    /** Bearer token para AUTH_MODE=token del servidor (vacío si el server usa none) */
    val SERVER_AUTH_TOKEN = stringPreferencesKey("server_auth_token")

    /** Apodo visible para rodadas en grupo y fantasmas */
    val SERVER_RIDER_NAME = stringPreferencesKey("server_rider_name")

    /** Compañero de viaje: brief de zona (combustible/peajes/restricciones) al llegar a un lugar
     *  nuevo. Server-first + respaldo IA. Default false (el respaldo IA cuesta). */
    val ZONE_BRIEF_ENABLED = booleanPreferencesKey("zone_brief_enabled")

    /** Anuncio hablado corto al llegar el brief de zona (default true; el detalle va a notificación) */
    val VOICE_ZONE_BRIEF = booleanPreferencesKey("voice_zone_brief")

    // ── Aviso de actualización (GitHub Releases) ─────────────────────────────

    /** Epoch ms del último chequeo de versión — throttle */
    val LAST_UPDATE_CHECK_MS = longPreferencesKey("last_update_check_ms")

    /** Versión que el usuario descartó — no vuelve a avisar por esa */
    val DISMISSED_UPDATE_VERSION = stringPreferencesKey("dismissed_update_version")

    // ── Vehículo al día ──────────────────────────────────────────────────────

    /** Driver's license expiration, epoch ms — app-wide, not per vehicle profile */
    val LICENSE_EXPIRES_AT = longPreferencesKey("license_expires_at")

    /** User-editable override of PicoYPlacaEngine.CityRules as JSON (rotation changes each semester) */
    val PICO_PLACA_RULES_JSON = stringPreferencesKey("pico_placa_rules_json")

    /**
     * Pico y placa por IA para cualquier ciudad (default false — opt-in, requiere
     * proveedor de IA con búsqueda web; se recomienda Gemini por su capa gratuita).
     */
    val AI_PICO_PLACA_ENABLED = booleanPreferencesKey("ai_pico_placa_enabled")

    /** Cache de reglas de restricción generadas por IA — ver AiRulesCache (municipio → entrada) */
    val AI_RESTRICTION_RULES_JSON = stringPreferencesKey("ai_restriction_rules_json")

    // ── Combustible ──────────────────────────────────────────────────────────

    /**
     * Legada — precio único de galón (COP), pre Room v14 (tipos de combustible por
     * vehículo). `FuelPricePrefs.read` la migra a [FUEL_PRICE_CORRIENTE] la primera vez
     * que se lee y la elimina. No usar en código nuevo.
     */
    val FUEL_PRICE_COP_PER_GALLON = doublePreferencesKey("fuel_price_cop_per_gallon")

    /** Precio del galón de gasolina corriente en COP (default 16000.0) */
    val FUEL_PRICE_CORRIENTE = doublePreferencesKey("fuel_price_corriente")

    /** Precio del galón de gasolina extra en COP (default 20000.0) */
    val FUEL_PRICE_EXTRA = doublePreferencesKey("fuel_price_extra")

    /** Precio del galón de ACPM/diésel en COP (default 10500.0) */
    val FUEL_PRICE_DIESEL = doublePreferencesKey("fuel_price_diesel")

    // ── Radares de velocidad ─────────────────────────────────────────────────

    /** Latitud del centro de la última descarga de radares — manual o auto al salir de cobertura (CameraCoverageTracker) */
    val LAST_CAMERA_LAT = doublePreferencesKey("last_camera_lat")

    /** Longitud del centro de la última descarga de radares — manual o auto al salir de cobertura (CameraCoverageTracker) */
    val LAST_CAMERA_LON = doublePreferencesKey("last_camera_lon")

    /** Radio en metros del aviso por voz al acercarse a un radar (default 250, rango 100-1000) */
    val CAMERA_ALERT_RADIUS_M = intPreferencesKey("camera_alert_radius_m")

    /** Día calendario (epoch day, America/Bogota) del último aviso "Vehículo al día" — evita el aviso doble */
    val DAILY_STATUS_LAST_NOTIFIED_DAY = longPreferencesKey("daily_status_last_notified_day")

    // ── Sonido de motor ──────────────────────────────────────────────────────

    /** Simulador de sonido de motor por RPM activo durante la telemetría (default false) */
    val ENGINE_SOUND_ENABLED = booleanPreferencesKey("engine_sound_enabled")

    /** Pack de sonido activo — id de [SoundPack] (default v8_muscle) */
    val ENGINE_SOUND_PACK = stringPreferencesKey("engine_sound_pack")

    /** Volumen del sonido de motor 0-100 (default 70) */
    val ENGINE_SOUND_VOLUME = intPreferencesKey("engine_sound_volume")

    // ── Copia de seguridad ───────────────────────────────────────────────────

    /** Copia de seguridad automática semanal a Descargas/RevScope (default true) */
    val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")

    // ── Onboarding ───────────────────────────────────────────────────────────

    /** Se completó el wizard de onboarding del primer arranque (default false) */
    val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")

    /** Modo solo-GPS elegido en el wizard de onboarding — sin adaptador OBD2 (default false) */
    val GPS_ONLY_MODE = booleanPreferencesKey("gps_only_mode")

    // ── Detección de caída ───────────────────────────────────────────────────

    /** Detección de caída con SMS de emergencia activa (default false — SAFETY-CRITICAL) */
    val CRASH_DETECTION_ENABLED = booleanPreferencesKey("crash_detection_enabled")

    /** Número de teléfono del contacto de emergencia para el SMS de caída */
    val EMERGENCY_PHONE = stringPreferencesKey("emergency_phone")

    // ── Verificación de kilometraje (odómetro ECU) ──────────────────────────

    /**
     * Histórico del odómetro ECU (PID 01 A6) por perfil: un único JSON objeto
     * `{"<profileId>": [{"epochMs":..,"km":..}, ...]}` en vez de una clave por perfil —
     * evita construir claves dinámicas y no requiere limpieza al borrar un perfil.
     * Máx. 50 lecturas por perfil — ver OdometerVerifier.agregarAlHistorial.
     */
    val ODOMETER_HISTORY_JSON = stringPreferencesKey("odometer_history_json")

    // ── Velocímetro ──────────────────────────────────────────────────────────

    /**
     * Fuente del velocímetro grande del dashboard en modo OBD conectado:
     * true = GPS_SPEED, false = PID 0D (default). Sin efecto en viaje GPS sin
     * adaptador, donde la fuente siempre es GPS.
     */
    val SPEED_SOURCE_GPS = booleanPreferencesKey("speed_source_gps")

    /** Mantener la pantalla encendida mientras el dashboard esté conectado (default true) */
    val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")

    // ── Servidor MCP (red local) ────────────────────────────────────────────

    /** Apagado por defecto — expone el estado del vehículo a clientes MCP del PC (default false) */
    val MCP_SERVER_ENABLED = booleanPreferencesKey("mcp_server_enabled")

    /** Token Bearer generado una sola vez (UUID) — requerido en Authorization para /mcp */
    val MCP_TOKEN = stringPreferencesKey("mcp_token")

    // ── Mapa ─────────────────────────────────────────────────────────────────

    /** Modo nocturno del mapa: "auto" (por sol), "on", "off". */
    val MAP_NIGHT_MODE = stringPreferencesKey("map_night_mode")
}
