package com.revscope.core.obd.road

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.revscope.core.data.datastore.PreferencesKeys
import com.revscope.core.obd.alerts.AlertsEngine
import com.revscope.core.obd.motion.MotionMetricsHub
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

private const val WET_LEAN_FACTOR = 0.75f
private const val MIN_THRESHOLD_DEG = 22f
private const val MAX_PLAUSIBLE_LEAN_DEG = 65f
private const val ALERT_COOLDOWN_MS = 60_000L

/**
 * Guardián de inclinación en mojado: con lluvia activa, avisa si el lean supera el
 * 75% del máximo personal EN SECO (guía estándar: recortar 25-30% la inclinación
 * con piso mojado). El máximo en seco se aprende solo: al cerrar una sesión sin
 * lluvia, el pico de lean de la sesión actualiza la referencia. En carro el lean
 * es ~0 y nunca dispara.
 */
@Singleton
class WetLeanGuard @Inject constructor(
    private val motionHub: MotionMetricsHub,
    private val rainWatcher: RainWatcher,
    private val alertsEngine: AlertsEngine,
    private val settings: DataStore<Preferences>,
) {

    private val persistScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    @Volatile private var lastAlertMs = 0L
    @Volatile private var dryMaxLeanDeg = 0f
    @Volatile private var rainedDuringSession = false

    fun start(scope: CoroutineScope) {
        job?.cancel()
        rainedDuringSession = rainWatcher.rainActive.value
        job = scope.launch {
            runCatching {
                dryMaxLeanDeg = settings.data.first()[PreferencesKeys.MAX_DRY_LEAN_DEG] ?: 0f
            }
            motionHub.snapshot.collect { motion ->
                if (!rainWatcher.rainActive.value) return@collect
                rainedDuringSession = true
                val threshold = maxOf(MIN_THRESHOLD_DEG, dryMaxLeanDeg * WET_LEAN_FACTOR)
                if (motion.leanDeg < threshold) return@collect
                val now = System.currentTimeMillis()
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
