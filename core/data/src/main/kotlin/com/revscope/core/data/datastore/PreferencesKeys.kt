package com.revscope.core.data.datastore

import androidx.datastore.preferences.core.booleanPreferencesKey
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
}
