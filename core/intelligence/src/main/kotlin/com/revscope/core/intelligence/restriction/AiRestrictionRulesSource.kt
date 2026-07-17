package com.revscope.core.intelligence.restriction

import com.revscope.core.obd.alerts.AlertsEngine
import com.revscope.core.obd.legal.AiRulesCache
import com.revscope.core.obd.legal.CityRegistry
import com.revscope.core.obd.legal.GpsEvaluationThrottle
import com.revscope.core.obd.legal.LocalityDetector
import com.revscope.core.obd.legal.PicoYPlacaEngine
import com.revscope.core.obd.legal.RestrictionRulesSource
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

private const val FAILED_FETCH_COOLDOWN_MS = 6L * 60 * 60 * 1000

/**
 * [RestrictionRulesSource] respaldado por IA: resuelve el municipio actual (Geocoder,
 * throttled), sirve desde [AiRulesCache] mientras esté fresco y solo consulta la IA con
 * ciudad nueva, reglas vencidas o NONE caducado. Un fetch fallido enfría ese municipio
 * 6 h para no quemar cuota reintentando cada minuto.
 *
 * DI shape: vive en :core:intelligence (necesita [RestrictionRulesFetcher]); el cache y
 * el gate llegan como lambdas porque este módulo no ve :core:data — mismo patrón que
 * CityInfoAlerter. El binding se hace en el módulo :app.
 */
class AiRestrictionRulesSource(
    private val localityDetector: LocalityDetector,
    private val fetcher: RestrictionRulesFetcher,
    private val alertsEngine: AlertsEngine,
    private val gateProvider: suspend () -> Boolean,
    private val readCache: suspend () -> String?,
    private val writeCache: suspend (String) -> Unit,
) : RestrictionRulesSource {

    private val throttle = GpsEvaluationThrottle()

    @Volatile private var lastLocality: LocalityDetector.Locality? = null
    private val failedFetchAt = mutableMapOf<String, Long>()
    private val fetching = AtomicBoolean(false)

    override suspend fun rulesFor(latitude: Double, longitude: Double): PicoYPlacaEngine.CityRules? {
        if (!gateProvider()) return null
        val locality = resolveThrottled(latitude, longitude) ?: return null
        return rulesForMunicipio(locality.municipio, locality.departamento, locality.pais)
    }

    override suspend fun rulesForCity(cityId: String): PicoYPlacaEngine.CityRules? {
        if (!gateProvider()) return null
        val city = CityRegistry.CITIES.firstOrNull { it.id == cityId } ?: return null
        return rulesForMunicipio(city.nombre, region = null, pais = "Colombia")
    }

    private suspend fun rulesForMunicipio(
        municipio: String,
        region: String?,
        pais: String?,
    ): PicoYPlacaEngine.CityRules? {
        val now = System.currentTimeMillis()

        val cache = AiRulesCache.parse(readCache())
        cache[municipio]?.let { entry ->
            if (AiRulesCache.isFresh(entry, now)) return AiRulesCache.rules(entry)
        }

        if (recentlyFailed(municipio, now)) return null
        if (!fetching.compareAndSet(false, true)) return null
        return try {
            fetchAndCache(municipio, region, pais, cache, now)
        } finally {
            fetching.set(false)
        }
    }

    private suspend fun resolveThrottled(latitude: Double, longitude: Double): LocalityDetector.Locality? {
        val now = System.currentTimeMillis()
        if (!throttle.shouldEvaluate(latitude, longitude, now)) return lastLocality
        throttle.recordEvaluation(latitude, longitude, now)
        val locality = localityDetector.resolveLocality(latitude, longitude) ?: return lastLocality
        lastLocality = locality
        return locality
    }

    private fun recentlyFailed(municipio: String, now: Long): Boolean =
        synchronized(failedFetchAt) { now - (failedFetchAt[municipio] ?: 0L) < FAILED_FETCH_COOLDOWN_MS }

    private suspend fun fetchAndCache(
        municipio: String,
        region: String?,
        pais: String?,
        cache: Map<String, AiRulesCache.Entry>,
        now: Long,
    ): PicoYPlacaEngine.CityRules? {
        Timber.i("AiRestrictionRulesSource: fetching rules for $municipio")
        return when (val result = fetcher.fetchRules(municipio, region, pais)) {
            is RestrictionFetchResult.Rules -> acceptRules(municipio, result, cache, now)
            RestrictionFetchResult.None -> {
                persist(cache, municipio, AiRulesCache.Entry(now, null))
                null
            }
            RestrictionFetchResult.Unavailable -> {
                markFailed(municipio, now)
                null
            }
        }
    }

    /** Reglas ya vencidas al llegar cuentan como fallo — cachearlas causaría re-fetch en bucle. */
    private suspend fun acceptRules(
        municipio: String,
        result: RestrictionFetchResult.Rules,
        cache: Map<String, AiRulesCache.Entry>,
        now: Long,
    ): PicoYPlacaEngine.CityRules? {
        if (now > result.rules.validUntilMs) {
            markFailed(municipio, now)
            return null
        }
        persist(cache, municipio, AiRulesCache.Entry(now, result.json))
        alertsEngine.announceAiRulesUpdated(result.rules)
        return result.rules
    }

    private fun markFailed(municipio: String, now: Long) {
        synchronized(failedFetchAt) { failedFetchAt[municipio] = now }
    }

    private suspend fun persist(cache: Map<String, AiRulesCache.Entry>, municipio: String, entry: AiRulesCache.Entry) {
        runCatching { writeCache(AiRulesCache.serialize(cache + (municipio to entry))) }
            .onFailure { Timber.w(it, "AiRestrictionRulesSource: cache write failed") }
    }
}
