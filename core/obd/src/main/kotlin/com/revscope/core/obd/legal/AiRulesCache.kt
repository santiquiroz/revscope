package com.revscope.core.obd.legal

import org.json.JSONObject
import timber.log.Timber

private const val NONE_TTL_MS = 30L * 24 * 60 * 60 * 1000
private const val KEY_FETCHED_AT = "fetchedAtMs"
private const val KEY_RULES_JSON = "rulesJson"

/**
 * Codec puro del cache de reglas de restricción generadas por IA, persistido como un
 * único JSON en DataStore: municipio → { fetchedAtMs, rulesJson | null }. rulesJson null
 * significa "la IA confirmó que la ciudad no tiene restricción" (NONE), válido 30 días;
 * unas reglas son frescas hasta su propio validUntilMs.
 */
object AiRulesCache {

    data class Entry(val fetchedAtMs: Long, val rulesJson: String?)

    fun parse(json: String?): Map<String, Entry> {
        if (json.isNullOrBlank()) return emptyMap()
        return try {
            val root = JSONObject(json)
            buildMap {
                root.keys().forEach { municipio ->
                    val obj = root.getJSONObject(municipio)
                    val rulesJson = if (obj.isNull(KEY_RULES_JSON)) null else obj.getString(KEY_RULES_JSON)
                    put(municipio, Entry(obj.getLong(KEY_FETCHED_AT), rulesJson))
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "AiRulesCache: corrupt cache — starting empty")
            emptyMap()
        }
    }

    fun serialize(entries: Map<String, Entry>): String {
        val root = JSONObject()
        entries.forEach { (municipio, entry) ->
            root.put(
                municipio,
                JSONObject()
                    .put(KEY_FETCHED_AT, entry.fetchedAtMs)
                    .put(KEY_RULES_JSON, entry.rulesJson ?: JSONObject.NULL),
            )
        }
        return root.toString()
    }

    fun isFresh(entry: Entry, nowMs: Long): Boolean {
        val json = entry.rulesJson ?: return nowMs - entry.fetchedAtMs < NONE_TTL_MS
        val rules = PicoYPlacaEngine.parseRulesJson(json) ?: return false
        return nowMs <= rules.validUntilMs
    }

    fun rules(entry: Entry): PicoYPlacaEngine.CityRules? =
        entry.rulesJson?.let(PicoYPlacaEngine::parseRulesJson)
}
