package com.revscope.core.intelligence.zone

import com.revscope.core.obd.alerts.AlertsEngine
import com.revscope.core.obd.legal.CityAlertPolicy
import com.revscope.core.obd.legal.LocalInfoAlertPolicy
import com.revscope.core.obd.legal.LocalityDetector
import com.revscope.core.obd.service.GpsInfoSink
import com.revscope.core.obd.service.ZoneBriefHolder
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.obd.social.ZoneBriefClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * "Compañero de viaje": al llegar a un lugar nuevo entrega un brief de conducción
 * (combustible, peajes, restricciones, normas). SERVER-FIRST — pregunta al servidor
 * colaborativo primero (gratis, instantáneo); si no hay brief fresco para la zona y la
 * IA está disponible, la genera con web search y la CONTRIBUYE de vuelta al servidor
 * para que el siguiente viajero la reciba sin gastar IA.
 *
 * Misma indirección GpsInfoSink que CityInfoAlerter: vive en :core:intelligence y las
 * dependencias hacia :core:data llegan como lambdas.
 */
class ZoneBriefAlerter(
    private val localityDetector: LocalityDetector,
    private val client: ZoneBriefClient,
    private val fetcher: ZoneBriefFetcher,
    private val holder: ZoneBriefHolder,
    private val alertsEngine: AlertsEngine,
    private val sessionManager: ObdSessionManager,
    private val homeCountry: String,
    /** Toggle maestro del compañero de viaje. */
    private val enabledProvider: suspend () -> Boolean,
    /** true si el proveedor de IA tiene web search — habilita el respaldo IA + contribución. */
    private val aiAllowedProvider: suspend () -> Boolean,
) : GpsInfoSink {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val announcedToday: MutableMap<String, String> = mutableMapOf()

    override fun onGpsFix(latitude: Double, longitude: Double) {
        if (!localityDetector.shouldEvaluate(latitude, longitude)) return
        localityDetector.markEvaluationAttempt(latitude, longitude)
        if (sessionManager.currentSessionId.value == null) return
        scope.launch { evaluate(latitude, longitude) }
    }

    private suspend fun evaluate(latitude: Double, longitude: Double) {
        try {
            if (!enabledProvider()) return
            val locality = localityDetector.detectLocalityChange(latitude, longitude) ?: return
            val now = System.currentTimeMillis()
            if (!LocalInfoAlertPolicy.shouldAnnounce(locality.municipio, announcedToday, now)) return

            // 1) Server-first: brief comunitario, gratis e instantáneo.
            var body = client.get(locality.municipio, locality.pais)
            var source = ZoneBriefHolder.Source.COMMUNITY

            // 2) Respaldo IA solo si el server no tiene y la IA está disponible.
            if (body == null && aiAllowedProvider()) {
                body = fetcher.fetch(locality.municipio, locality.departamento, locality.pais, homeCountry)
                source = ZoneBriefHolder.Source.AI
                // 3) Contribuir de vuelta: el próximo viajero lo recibe sin gastar IA.
                if (body != null) {
                    runCatching { client.contribute(locality.municipio, locality.pais, body) }
                }
            }
            if (body == null) return

            announcedToday[locality.municipio] = CityAlertPolicy.dayKey(now)
            holder.publish(locality.municipio, body, source)
            Timber.i("ZoneBriefAlerter: brief de ${locality.municipio} (${source})")
            alertsEngine.announceZoneBrief(locality.municipio, body)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "ZoneBriefAlerter: evaluate falló")
        }
    }
}
