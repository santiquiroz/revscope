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

    fun encode(preferences: Preferences): String {
        val root = JSONObject()
        preferences.asMap().forEach { (key, value) ->
            if (key.name == PreferencesKeys.CLAUDE_API_KEY.name) return@forEach
            encodeEntry(value)?.let { root.put(key.name, it) }
        }
        return root.toString()
    }

    suspend fun restore(json: String, settings: DataStore<Preferences>) {
        val root = JSONObject(json)
        settings.edit { mutablePrefs ->
            mutablePrefs.clear()
            root.keys().forEach { keyName ->
                if (keyName == PreferencesKeys.CLAUDE_API_KEY.name) return@forEach
                applyEntry(mutablePrefs, keyName, root.getJSONObject(keyName))
            }
        }
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
