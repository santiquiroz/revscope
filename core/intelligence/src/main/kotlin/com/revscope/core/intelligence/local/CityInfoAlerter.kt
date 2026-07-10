package com.revscope.core.intelligence.local

import com.revscope.core.obd.alerts.AlertsEngine
import com.revscope.core.obd.legal.CityAlertPolicy
import com.revscope.core.obd.legal.LocalInfoAlertPolicy
import com.revscope.core.obd.legal.LocalityDetector
import com.revscope.core.obd.service.GpsInfoSink
import com.revscope.core.obd.session.ObdSessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Announces AI-generated local info (festivals, road closures…) by voice when GPS
 * detects the user entered a new municipality. Opt-in, off by default — consumes the
 * user's Claude API key (~$0.02/call with web_search).
 *
 * DI shape: lives in :core:intelligence (needs [LocalInfoFetcher], which calls the
 * Claude API) and implements [GpsInfoSink] — defined in :core:obd — so
 * GpsTrackRecorder/ObdForegroundService (both in :core:obd) can receive GPS fixes
 * without :core:obd depending on :core:intelligence (that would cycle back against
 * :core:intelligence's existing dependency on :core:obd, e.g. for AlertsEngine). The
 * GpsInfoSink → CityInfoAlerter binding is resolved in :app's Hilt module, the only
 * module that sees :core:data (SecureKeyStore/DataStore), :core:obd and
 * :core:intelligence at once. [gateProvider] arrives as a lambda for the same reason
 * DtcExplainer takes an `apiKeyProvider` lambda instead of injecting SecureKeyStore
 * directly — :core:intelligence has no compile-time visibility into :core:data.
 */
class CityInfoAlerter(
    private val localityDetector: LocalityDetector,
    private val localInfoFetcher: LocalInfoFetcher,
    private val alertsEngine: AlertsEngine,
    private val sessionManager: ObdSessionManager,
    private val gateProvider: suspend () -> Boolean,
) : GpsInfoSink {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile private var lastAnnouncement: LocalInfoAlertPolicy.LastAnnouncement? = null

    override fun onGpsFix(latitude: Double, longitude: Double) {
        if (!localityDetector.shouldEvaluate(latitude, longitude)) return
        localityDetector.markEvaluationAttempt(latitude, longitude)
        if (sessionManager.currentSessionId.value == null) return
        scope.launch { evaluate(latitude, longitude) }
    }

    private suspend fun evaluate(latitude: Double, longitude: Double) {
        try {
            if (!gateProvider()) return
            val locality = localityDetector.detectLocalityChange(latitude, longitude) ?: return
            val now = System.currentTimeMillis()
            if (!LocalInfoAlertPolicy.shouldAnnounce(locality.municipio, lastAnnouncement, now)) return
            val frase = localInfoFetcher.fetchLocalInfo(locality.municipio, locality.departamento) ?: return
            lastAnnouncement = LocalInfoAlertPolicy.LastAnnouncement(locality.municipio, CityAlertPolicy.dayKey(now))
            Timber.i("CityInfoAlerter: announcing local info for ${locality.municipio}")
            alertsEngine.announceLocalInfo(locality.municipio, frase)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "CityInfoAlerter: evaluate failed")
        }
    }
}
