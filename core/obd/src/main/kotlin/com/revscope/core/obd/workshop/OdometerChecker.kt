package com.revscope.core.obd.workshop

import com.revscope.core.data.db.dao.SessionDao
import com.revscope.core.obd.connection.Transport
import com.revscope.core.obd.pid.PidRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * ECU odometer read + tamper-evidence check (PID 01 A6).
 *
 * Not a Hilt-injected singleton — instantiated inline by
 * [com.revscope.core.obd.session.ObdSessionManager] alongside its other bt-driven
 * collaborators (MilWatcher, VoltagePoller, SessionAggregator), since it needs the live
 * [Transport] that only the manager holds. Runs as a one-shot `exchange` (never via the
 * routine [com.revscope.core.obd.telemetry.PidScheduler] loop) once per connection right
 * after protocol negotiation, plus on demand for the "Leer ahora" button.
 */
class OdometerChecker(
    private val registry: PidRegistry,
    private val historyStore: OdometerHistoryStore,
    private val sessionDao: SessionDao,
) {

    data class Result(
        val reading: OdometerVerifier.Reading,
        val diagnosis: DiagnosticRules.Diagnosis,
        val historial: List<OdometerVerifier.Reading>,
    )

    private val _lastResult = MutableStateFlow<Result?>(null)
    val lastResult: StateFlow<Result?> = _lastResult.asStateFlow()

    /** Only meaningful once ECU support has been negotiated (i.e. while connected). */
    fun isSupported(): Boolean = registry.isSupported(ODOMETER_PID)

    /** Returns null when the PID isn't supported or the read failed — never throws. */
    suspend fun check(bt: Transport, profileId: Long): Result? {
        if (!isSupported()) return null
        val reading = readOdometer(bt) ?: return null
        val result = evaluate(profileId, reading)
        _lastResult.value = result
        return result
    }

    private suspend fun readOdometer(bt: Transport): OdometerVerifier.Reading? {
        val raw = try {
            bt.exchange("01 $ODOMETER_PID\r", TIMEOUT_MS)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "OdometerChecker: exchange failed")
            return null
        }
        val km = registry.parseAndEvaluate(ODOMETER_PID, raw)?.value ?: return null
        return OdometerVerifier.Reading(epochMs = System.currentTimeMillis(), km = km)
    }

    private suspend fun evaluate(profileId: Long, nueva: OdometerVerifier.Reading): Result {
        val historialPrevio = historyStore.historialPara(profileId)
        val distanciaAppKm = distanciaAppEntre(profileId, historialPrevio.lastOrNull(), nueva)
        val diagnosis = OdometerVerifier.evaluar(historialPrevio, nueva, distanciaAppKm)
        // Same open trip as the last reading (<30 min): app-km hasn't accrued yet, so persisting
        // a new entry would just add an inert same-trip reading — evaluate but don't store it.
        val historialActualizado = if (esMismoViajeQueUltimaLectura(historialPrevio, nueva)) {
            historialPrevio
        } else {
            historyStore.agregar(profileId, nueva)
        }
        return Result(nueva, diagnosis, historialActualizado)
    }

    private fun esMismoViajeQueUltimaLectura(historial: List<OdometerVerifier.Reading>, nueva: OdometerVerifier.Reading): Boolean {
        val ultima = historial.lastOrNull() ?: return false
        return nueva.epochMs - ultima.epochMs < VENTANA_MISMO_VIAJE_MS
    }

    private suspend fun distanciaAppEntre(
        profileId: Long,
        anterior: OdometerVerifier.Reading?,
        nueva: OdometerVerifier.Reading,
    ): Double {
        anterior ?: return 0.0
        return runCatching { sessionDao.sumDistanceKmBetween(profileId, anterior.epochMs, nueva.epochMs) }
            .getOrDefault(0.0)
    }

    companion object {
        const val ODOMETER_PID = "A6"
        private const val TIMEOUT_MS = 5_000L
        private const val VENTANA_MISMO_VIAJE_MS = 30 * 60 * 1_000L
    }
}
