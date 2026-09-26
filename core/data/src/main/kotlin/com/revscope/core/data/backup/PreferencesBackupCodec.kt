package com.revscope.core.data.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.revscope.core.data.datastore.PreferencesKeys
import org.json.JSONObject

/**
 * Serializa/restaura todas las claves de un DataStore<Preferences> como JSON plano
 * `{clave: {type, value}}`. No conoce las claves de PreferencesKeys — vuelca lo que
 * exista en el DataStore, por eso sobrevive a claves agregadas después del backup.
 */
object PreferencesBackupCodec {

    private const val TYPE_BOOLEAN = "boolean"
    private const val TYPE_INT = "int"
    private const val TYPE_LONG = "long"
    private const val TYPE_FLOAT = "float"
    private const val TYPE_DOUBLE = "double"
    private const val TYPE_STRING = "string"

    // Secretos y permisos remotos propios de este dispositivo: no viajan en el zip y restore no los pisa.
    private val DEVICE_SECRET_KEYS: List<Preferences.Key<*>> = listOf(
        PreferencesKeys.CLAUDE_API_KEY,
        PreferencesKeys.MCP_TOKEN,
        PreferencesKeys.SERVER_AUTH_TOKEN,
        PreferencesKeys.MCP_CONTROL_ENABLED,
        PreferencesKeys.MCP_CLEAR_DTC_ENABLED,
    )
    private val DEVICE_SECRET_NAMES = DEVICE_SECRET_KEYS.map { it.name }.toSet()

    fun encode(preferences: Preferences): String {
        val root = JSONObject()
        preferences.asMap().forEach { (key, value) ->
            if (key.name in DEVICE_SECRET_NAMES) return@forEach
            encodeEntry(value)?.let { root.put(key.name, it) }
        }
        return root.toString()
    }

    suspend fun restore(json: String, settings: DataStore<Preferences>) {
        val root = JSONObject(json)
        settings.edit { mutablePrefs ->
            val localSecrets = deviceSecrets(mutablePrefs)
            mutablePrefs.clear()
            root.keys().forEach { keyName ->
                if (keyName in DEVICE_SECRET_NAMES) return@forEach
                applyEntry(mutablePrefs, keyName, root.getJSONObject(keyName))
            }
            mutablePrefs.putAll(*localSecrets.toTypedArray())
        }
    }

    private fun deviceSecrets(preferences: Preferences): List<Preferences.Pair<*>> =
        DEVICE_SECRET_KEYS.mapNotNull { key -> currentPair(preferences, key) }

    @Suppress("UNCHECKED_CAST")
    private fun currentPair(preferences: Preferences, key: Preferences.Key<*>): Preferences.Pair<*>? {
        val typedKey = key as Preferences.Key<Any>
        return preferences[typedKey]?.let { typedKey to it }
    }

    private fun encodeEntry(value: Any): JSONObject? = when (value) {
        is Boolean -> jsonEntry(TYPE_BOOLEAN, value)
        is Int -> jsonEntry(TYPE_INT, value)
        is Long -> jsonEntry(TYPE_LONG, value)
        is Float -> jsonEntry(TYPE_FLOAT, value.toDouble())
        is Double -> jsonEntry(TYPE_DOUBLE, value)
        is String -> jsonEntry(TYPE_STRING, value)
        else -> null // Set<String> / ByteArray: ninguna clave actual los usa
    }

    private fun jsonEntry(type: String, value: Any): JSONObject =
        JSONObject().put("type", type).put("value", value)

    private fun applyEntry(mutablePrefs: MutablePreferences, keyName: String, entry: JSONObject) {
        when (entry.getString("type")) {
            TYPE_BOOLEAN -> mutablePrefs[booleanPreferencesKey(keyName)] = entry.getBoolean("value")
            TYPE_INT -> mutablePrefs[intPreferencesKey(keyName)] = entry.getInt("value")
            TYPE_LONG -> mutablePrefs[longPreferencesKey(keyName)] = entry.getLong("value")
            TYPE_FLOAT -> mutablePrefs[floatPreferencesKey(keyName)] = entry.getDouble("value").toFloat()
            TYPE_DOUBLE -> mutablePrefs[doublePreferencesKey(keyName)] = entry.getDouble("value")
            TYPE_STRING -> mutablePrefs[stringPreferencesKey(keyName)] = entry.getString("value")
        }
    }
}
