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

    // ── Vehículo al día ──────────────────────────────────────────────────────

    /** Driver's license expiration, epoch ms — app-wide, not per vehicle profile */
    val LICENSE_EXPIRES_AT = longPreferencesKey("license_expires_at")

    /** User-editable override of PicoYPlacaEngine.CityRules as JSON (rotation changes each semester) */
    val PICO_PLACA_RULES_JSON = stringPreferencesKey("pico_placa_rules_json")

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

    /** Latitud del último centro de descarga manual de radares (refresco semanal automático) */
    val LAST_CAMERA_LAT = doublePreferencesKey("last_camera_lat")

    /** Longitud del último centro de descarga manual de radares (refresco semanal automático) */
    val LAST_CAMERA_LON = doublePreferencesKey("last_camera_lon")

    // ── Copia de seguridad ───────────────────────────────────────────────────

    /** Copia de seguridad automática semanal a Descargas/RevScope (default true) */
    val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")

    // ── Onboarding ───────────────────────────────────────────────────────────

    /** Se completó la pantalla de permisos del primer arranque (default false) */
    val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")

    // ── Detección de caída ───────────────────────────────────────────────────

    /** Detección de caída con SMS de emergencia activa (default false — SAFETY-CRITICAL) */
    val CRASH_DETECTION_ENABLED = booleanPreferencesKey("crash_detection_enabled")

    /** Número de teléfono del contacto de emergencia para el SMS de caída */
    val EMERGENCY_PHONE = stringPreferencesKey("emergency_phone")
}
