package com.revscope.core.obd.road

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.revscope.core.data.datastore.PreferencesKeys
import com.revscope.core.obd.alerts.AlertsEngine
import com.revscope.core.obd.motion.MotionMetricsHub
import com.revscope.core.obd.service.LiveRouteHolder
import com.revscope.core.obd.weather.RainWatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_PLAUSIBLE_LEAN_DEG = 65f
private const val ALERT_COOLDOWN_MS = 90_000L
private const val SUSTAINED_MS = 1_500L

/**
 * Guardián de inclinación en mojado. Con lluvia activa avisa si te inclinas más del
 * 75% de tu máximo personal en seco (guía estándar: recortar 25-30% con piso mojado).
 *
 * Endurecido tras falsos positivos con el celular en el bolsillo (2026-08-04): ahora
 * exige velocidad de curva, G lateral corroborante (curva real vs bolsillo meciéndose),
 * calibración presente y que las condiciones se sostengan ≥1.5 s — ver [WetLeanDecision].
 * En carro el lean es ~0 y nunca dispara.
 */
@Singleton
class WetLeanGuard @Inject constructor(
    private val motionHub: MotionMetricsHub,
    private val rainWatcher: RainWatcher,
    private val alertsEngine: AlertsEngine,
    private val settings: DataStore<Preferences>,
    private val routeHolder: LiveRouteHolder,
) {

    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    @Volatile private var lastAlertMs = 0L
    @Volatile private var dryMaxLeanDeg = 0f
    @Volatile private var rainedDuringSession = false
    @Volatile private var qualifyingSinceMs = -1L

    fun start(scope: CoroutineScope) {
        job?.cancel()
        rainedDuringSession = rainWatcher.rainActive.value
        qualifyingSinceMs = -1L
        job = scope.launch {
            runCatching {
                dryMaxLeanDeg = settings.data.first()[PreferencesKeys.MAX_DRY_LEAN_DEG] ?: 0f
            }
            motionHub.snapshot.collect { motion ->
                val rain = rainWatcher.rainActive.value
                if (rain) rainedDuringSession = true

                val qualifies = WetLeanDecision.qualifies(
                    rainActive = rain,
                    calibrated = motion.calibrated,
                    speedKmh = routeHolder.lastSpeedKmh.value,
                    leanDeg = motion.leanDeg,
                    lateralG = motion.gLat,
                    dryMaxLeanDeg = dryMaxLeanDeg,
                )
                val now = System.currentTimeMillis()
                if (!qualifies) {
                    qualifyingSinceMs = -1L
                    return@collect
                }
                // Sostenido: la inclinación de curva se mantiene; el ruido del bolsillo es espurio.
                if (qualifyingSinceMs < 0) {
                    qualifyingSinceMs = now
                    return@collect
                }
                if (now - qualifyingSinceMs < SUSTAINED_MS) return@collect
                if (now - lastAlertMs < ALERT_COOLDOWN_MS) return@collect
                lastAlertMs = now
                alertsEngine.announceWetLean(motion.leanDeg.toInt())
            }
        }
    }

    /** [sessionMaxLeanDeg] = pico de la sesión que cierra — solo actualiza la referencia si NO llovió. */
    fun stop(sessionMaxLeanDeg: Float) {
        job?.cancel()
        job = null
        qualifyingSinceMs = -1L
        if (rainedDuringSession) return
        if (sessionMaxLeanDeg <= dryMaxLeanDeg || sessionMaxLeanDeg > MAX_PLAUSIBLE_LEAN_DEG) return
        dryMaxLeanDeg = sessionMaxLeanDeg
        persistScope.launch {
            runCatching {
                settings.edit { it[PreferencesKeys.MAX_DRY_LEAN_DEG] = sessionMaxLeanDeg }
            }.onFailure { Timber.w(it, "WetLeanGuard: no pude persistir el lean máximo en seco") }
        }
    }
}
