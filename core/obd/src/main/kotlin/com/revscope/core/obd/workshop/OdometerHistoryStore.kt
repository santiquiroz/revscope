package com.revscope.core.obd.workshop

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.revscope.core.data.datastore.PreferencesKeys
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * Persists the odometer reading history per vehicle profile in a single DataStore key:
 * `{"<profileId>": [{"epochMs":..,"km":..}, ...]}`. One shared key (instead of a
 * dynamically-built key per profile) keeps [PreferencesKeys] free of runtime-constructed
 * keys and needs no cleanup pass when a profile is deleted — its entry is just a few
 * unread bytes inside a small JSON blob.
 */
class OdometerHistoryStore(private val settings: DataStore<Preferences>) {

    suspend fun historialPara(profileId: Long): List<OdometerVerifier.Reading> {
        val json = settings.data.first()[PreferencesKeys.ODOMETER_HISTORY_JSON] ?: return emptyList()
        return parseHistorialDePerfil(json, profileId)
    }

    /** Appends [nueva] (capped by [OdometerVerifier.agregarAlHistorial]) and persists it. */
    suspend fun agregar(profileId: Long, nueva: OdometerVerifier.Reading): List<OdometerVerifier.Reading> {
        val actualizado = OdometerVerifier.agregarAlHistorial(historialPara(profileId), nueva)
        guardar(profileId, actualizado)
        return actualizado
    }

    private suspend fun guardar(profileId: Long, historial: List<OdometerVerifier.Reading>) {
        runCatching {
            settings.edit { prefs ->
                val root = parseRoot(prefs[PreferencesKeys.ODOMETER_HISTORY_JSON])
                root.put(profileId.toString(), historialToJsonArray(historial))
                prefs[PreferencesKeys.ODOMETER_HISTORY_JSON] = root.toString()
            }
        }.onFailure { Timber.w(it, "OdometerHistoryStore: failed to persist history for profile $profileId") }
    }

    private fun parseHistorialDePerfil(rootJson: String, profileId: Long): List<OdometerVerifier.Reading> =
        try {
            val array = parseRoot(rootJson).optJSONArray(profileId.toString()) ?: JSONArray()
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                OdometerVerifier.Reading(epochMs = o.getLong("epochMs"), km = o.getDouble("km"))
            }
        } catch (e: Exception) {
            Timber.w(e, "OdometerHistoryStore: failed to parse history JSON")
            emptyList()
        }

    private fun parseRoot(json: String?): JSONObject =
        json?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()

    private fun historialToJsonArray(historial: List<OdometerVerifier.Reading>): JSONArray =
        JSONArray().apply {
            historial.forEach { put(JSONObject().put("epochMs", it.epochMs).put("km", it.km)) }
        }
}
