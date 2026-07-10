package com.revscope.core.obd.legal

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.revscope.core.data.datastore.PreferencesKeys
import com.revscope.core.obd.alerts.AlertsEngine
import com.revscope.core.obd.session.ObdSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val THROTTLE_MS = 60_000L
private const val MOTORCYCLE_TYPE = "MOTORCYCLE"

/**
 * Watches the live GPS stream and, at most once every 60s, checks whether the detected city
 * has an active pico-y-placa restriction for the active profile's plate. Mirrors
 * SpeedCameraAlerter's onGpsFix pattern. Depends on [ObdSessionManager] directly for the active
 * profile — safe because ObdSessionManager has no dependency back on this class, so there is no
 * Hilt DI cycle (unlike a hypothetical alerter that ObdSessionManager itself needed to call).
 */
@Singleton
class CityEnforcementAlerter @Inject constructor(
    private val sessionManager: ObdSessionManager,
    private val alertsEngine: AlertsEngine,
    private val settings: DataStore<Preferences>,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var lastEvaluatedAt = 0L
    @Volatile private var lastAnnouncement: CityAlertPolicy.LastAnnouncement? = null

    fun onGpsFix(latitude: Double, longitude: Double) {
        val now = System.currentTimeMillis()
        if (now - lastEvaluatedAt < THROTTLE_MS) return
        lastEvaluatedAt = now
        scope.launch { evaluate(latitude, longitude, now) }
    }

    private suspend fun evaluate(latitude: Double, longitude: Double, now: Long) {
        val city = CityRegistry.nearest(latitude, longitude) ?: return
        val profile = sessionManager.activeProfile.value ?: return
        val plate = profile.plate?.trim().orEmpty()
        if (plate.isEmpty()) return
        val rules = CityRegistry.resolveRules(city.id, loadOverrideRules()) ?: return
        val result = PicoYPlacaEngine.check(plate, profile.type == MOTORCYCLE_TYPE, rules, now)
        val announce = CityAlertPolicy.shouldAnnounce(
            detectedCityId = city.id,
            profileCityId = profile.picoPlacaCity,
            status = result.status,
            lastAnnouncement = lastAnnouncement,
            nowMs = now,
        )
        if (!announce) return
        lastAnnouncement = CityAlertPolicy.LastAnnouncement(city.id, CityAlertPolicy.dayKey(now))
        Timber.i("CityEnforcementAlerter: announcing pico y placa for ${city.nombre}")
        alertsEngine.announcePicoPlaca(city.nombre, result.status, rules.startHour, rules.endHour)
    }

    private suspend fun loadOverrideRules(): PicoYPlacaEngine.CityRules? = runCatching {
        settings.data.first()[PreferencesKeys.PICO_PLACA_RULES_JSON]?.let(PicoYPlacaEngine::parseRulesJson)
    }.getOrNull()
}
