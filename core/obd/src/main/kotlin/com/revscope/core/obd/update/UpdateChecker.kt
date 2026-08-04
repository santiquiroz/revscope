package com.revscope.core.obd.update

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.revscope.core.data.datastore.PreferencesKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

private const val RELEASES_API = "https://api.github.com/repos/santiquiroz/revscope/releases/latest"
private const val CHECK_THROTTLE_MS = 12 * 3_600_000L // 2 veces al día máx
private const val TIMEOUT_MS = 8_000

/**
 * Aviso de nueva versión desde GitHub Releases. RevScope se instala por sideload
 * (no Play Store), así que el propio app chequea. OFFLINE-FIRST: sin red no pasa
 * nada. El usuario puede descartar una versión concreta y no volver a verla.
 */
@Singleton
class UpdateChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: DataStore<Preferences>,
) {

    data class Update(val version: String, val releaseUrl: String, val apkUrl: String?, val notes: String)

    private val _available = MutableStateFlow<Update?>(null)
    val available: StateFlow<Update?> = _available.asStateFlow()

    private fun installedVersion(): String =
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "0.0.0"

    /** Chequeo automático con throttle — llamar al abrir la app. [force] lo salta (botón manual). */
    suspend fun check(force: Boolean = false) {
        val prefs = runCatching { settings.data.first() }.getOrNull() ?: return
        val now = System.currentTimeMillis()
        if (!force) {
            val last = prefs[PreferencesKeys.LAST_UPDATE_CHECK_MS] ?: 0L
            if (now - last < CHECK_THROTTLE_MS) {
                return
            }
        }
        val latest = fetchLatest() ?: return
        runCatching { settings.edit { it[PreferencesKeys.LAST_UPDATE_CHECK_MS] = now } }

        if (!VersionCompare.isNewer(latest.version, installedVersion())) {
            _available.value = null
            return
        }
        val dismissed = prefs[PreferencesKeys.DISMISSED_UPDATE_VERSION].orEmpty()
        if (!force && dismissed == latest.version) return
        _available.value = latest
        Timber.i("UpdateChecker: hay ${latest.version} (instalada ${installedVersion()})")
    }

    /** No volver a avisar por ESTA versión (siguiente release sí avisa). */
    suspend fun dismiss() {
        val version = _available.value?.version ?: return
        _available.value = null
        runCatching { settings.edit { it[PreferencesKeys.DISMISSED_UPDATE_VERSION] = version } }
    }

    private suspend fun fetchLatest(): Update? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(RELEASES_API).openConnection() as HttpURLConnection
            val json = try {
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                if (connection.responseCode != 200) return@runCatching null
                JSONObject(connection.inputStream.bufferedReader().readText())
            } finally {
                connection.disconnect()
            }
            val tag = json.optString("tag_name").ifBlank { return@runCatching null }
            val apk = json.optJSONArray("assets")?.let { assets ->
                (0 until assets.length())
                    .map { assets.getJSONObject(it) }
                    .firstOrNull { it.optString("name").endsWith(".apk") }
                    ?.optString("browser_download_url")
            }
            Update(
                version = tag,
                releaseUrl = json.optString("html_url"),
                apkUrl = apk?.takeIf { it.isNotBlank() },
                notes = json.optString("body").take(500),
            )
        }.getOrNull()
    }
}
