package com.revscope.core.obd.social

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.revscope.core.data.datastore.PreferencesKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

private const val TIMEOUT_MS = 8_000

/**
 * Cliente HTTP mínimo para revscope-server. CONTRATO OFFLINE-FIRST: toda falla
 * (sin config, sin red, server caído) retorna null/failure SILENCIOSO — el caller
 * loguea y sigue; jamás un error visible al usuario por culpa de la red.
 */
@Singleton
class ServerClient @Inject constructor(
    private val settings: DataStore<Preferences>,
) {

    data class ServerConfig(val baseUrl: String, val token: String, val riderName: String)

    suspend fun config(): ServerConfig? {
        val prefs = runCatching { settings.data.first() }.getOrNull() ?: return null
        val url = prefs[PreferencesKeys.SERVER_BASE_URL]?.trim()?.trimEnd('/') ?: return null
        if (url.isBlank()) return null
        return ServerConfig(
            baseUrl = url,
            token = prefs[PreferencesKeys.SERVER_AUTH_TOKEN]?.trim().orEmpty(),
            riderName = prefs[PreferencesKeys.SERVER_RIDER_NAME]?.trim().orEmpty().ifBlank { "rider" },
        )
    }

    suspend fun postJson(path: String, body: JSONObject): Result<JSONObject> =
        request("POST", path, body)

    suspend fun getJson(path: String): Result<JSONObject> = request("GET", path, null)

    suspend fun getJsonArray(path: String): Result<JSONArray> = withContext(Dispatchers.IO) {
        runCatching {
            val config = requireNotNull(config()) { "Servidor no configurado" }
            openAndRead(config, "GET", path, null).let(::JSONArray)
        }
    }

    private suspend fun request(method: String, path: String, body: JSONObject?): Result<JSONObject> =
        withContext(Dispatchers.IO) {
            runCatching {
                val config = requireNotNull(config()) { "Servidor no configurado" }
                openAndRead(config, method, path, body).let(::JSONObject)
            }
        }

    private fun openAndRead(config: ServerConfig, method: String, path: String, body: JSONObject?): String {
        val connection = URL(config.baseUrl + path).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            if (config.token.isNotBlank()) {
                connection.setRequestProperty("Authorization", "Bearer ${config.token}")
            }
            connection.setRequestProperty("X-Rider-Name", config.riderName)
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = connection.responseCode
            check(code in 200..299) { "HTTP $code" }
            connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }
}
