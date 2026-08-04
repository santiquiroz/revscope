package com.revscope.core.obd.weather

import com.revscope.core.obd.alerts.AlertsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val CHECK_INTERVAL_MS = 10 * 60_000L
private const val ANNOUNCE_COOLDOWN_MS = 60 * 60_000L
private const val RAIN_NOW_MM = 0.1
private const val RAIN_AHEAD_MM = 0.5
private const val FETCH_TIMEOUT_MS = 10_000
private const val LOOKAHEAD_SLOTS = 3 // 3 × 15 min = 45 min

/**
 * Nowcast de lluvia vía Open-Meteo (gratis, sin key, global — funciona igual en
 * Medellín que en carretera). Dos señales:
 *  - Transición seco→lluvia: aviso inmediato — los primeros minutos de lluvia son
 *    los más resbalosos (aceite+polvo emulsionados).
 *  - Lluvia prevista en los próximos ~45 min: aviso anticipado.
 * [rainActive] lo consume el guardián de inclinación en mojado.
 */
@Singleton
class RainWatcher @Inject constructor(
    private val alertsEngine: AlertsEngine,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fetching = AtomicBoolean(false)

    private val _rainActive = MutableStateFlow(false)
    val rainActive: StateFlow<Boolean> = _rainActive.asStateFlow()

    @Volatile private var lastCheckMs = 0L
    @Volatile private var lastAnnouncedAheadMs = 0L
    @Volatile private var lastAnnouncedStartMs = 0L

    fun onGpsFix(latitude: Double, longitude: Double) {
        val now = System.currentTimeMillis()
        if (now - lastCheckMs < CHECK_INTERVAL_MS) return
        if (!fetching.compareAndSet(false, true)) return
        lastCheckMs = now
        scope.launch {
            runCatching { check(latitude, longitude, now) }
                .onFailure { Timber.w(it, "RainWatcher: consulta fallida") }
            fetching.set(false)
        }
    }

    private fun check(lat: Double, lon: Double, nowMs: Long) {
        val json = fetch(lat, lon) ?: return
        val currentMm = json.optJSONObject("current")?.optDouble("precipitation", 0.0) ?: 0.0
        val aheadMm = upcomingPrecipitationMm(json)

        val wasRaining = _rainActive.value
        val raining = currentMm >= RAIN_NOW_MM
        _rainActive.value = raining

        if (raining && !wasRaining && nowMs - lastAnnouncedStartMs >= ANNOUNCE_COOLDOWN_MS) {
            lastAnnouncedStartMs = nowMs
            alertsEngine.announceRain(startingNow = true, minutesAhead = 0)
        } else if (!raining && aheadMm >= RAIN_AHEAD_MM && nowMs - lastAnnouncedAheadMs >= ANNOUNCE_COOLDOWN_MS) {
            lastAnnouncedAheadMs = nowMs
            alertsEngine.announceRain(startingNow = false, minutesAhead = LOOKAHEAD_SLOTS * 15)
        }
    }

    private fun upcomingPrecipitationMm(json: JSONObject): Double {
        val minutely = json.optJSONObject("minutely_15") ?: return 0.0
        val values = minutely.optJSONArray("precipitation") ?: return 0.0
        var sum = 0.0
        for (i in 0 until minOf(LOOKAHEAD_SLOTS, values.length())) {
            sum += values.optDouble(i, 0.0)
        }
        return sum
    }

    private fun fetch(lat: Double, lon: Double): JSONObject? {
        // Locale.US obligatorio: con locale es_CO "%.4f" produce comas y rompe la URL.
        val coords = String.format(java.util.Locale.US, "latitude=%.4f&longitude=%.4f", lat, lon)
        val url = "https://api.open-meteo.com/v1/forecast?$coords" +
            "&current=precipitation&minutely_15=precipitation&forecast_minutely_15=${LOOKAHEAD_SLOTS + 1}"
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = FETCH_TIMEOUT_MS
            connection.readTimeout = FETCH_TIMEOUT_MS
            if (connection.responseCode != 200) return null
            JSONObject(connection.inputStream.bufferedReader().readText())
        } finally {
            connection.disconnect()
        }
    }
}
