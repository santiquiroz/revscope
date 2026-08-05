package com.revscope.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.revscope.core.data.datastore.PreferencesKeys
import com.revscope.core.data.secure.SecureKeyStore
import com.revscope.core.intelligence.IntelligenceCapability
import com.revscope.core.intelligence.IntelligenceOrchestrator
import com.revscope.core.intelligence.dtc.DtcExplainer
import com.revscope.core.intelligence.local.CityInfoAlerter
import com.revscope.core.intelligence.local.LocalInfoFetcher
import com.revscope.core.intelligence.provider.AI_PROVIDER_ANTHROPIC
import com.revscope.core.intelligence.provider.AI_PROVIDER_CUSTOM
import com.revscope.core.intelligence.provider.AI_PROVIDER_GEMINI
import com.revscope.core.intelligence.provider.AI_PROVIDER_OPENAI
import com.revscope.core.intelligence.provider.AiProviderFactory
import com.revscope.core.intelligence.provider.AiProviderSelection
import com.revscope.core.intelligence.restriction.AiRestrictionRulesSource
import com.revscope.core.intelligence.restriction.RestrictionRulesFetcher
import com.revscope.core.intelligence.zone.ZoneBriefAlerter
import com.revscope.core.intelligence.zone.ZoneBriefFetcher
import com.revscope.core.obd.alerts.AlertsEngine
import com.revscope.core.obd.legal.LocalityDetector
import com.revscope.core.obd.legal.RestrictionRulesSource
import com.revscope.core.obd.service.GpsInfoSink
import com.revscope.core.obd.service.ZoneBriefHolder
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.obd.social.ZoneBriefClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object IntelligenceModule {

    @Provides
    @Singleton
    fun provideAiProviderFactory(
        settings: DataStore<Preferences>,
        secureKeyStore: SecureKeyStore,
    ): AiProviderFactory = AiProviderFactory(aiProviderSelectionProvider(settings, secureKeyStore))

    @Provides
    @Singleton
    fun provideIntelligenceOrchestrator(
        @ApplicationContext context: Context,
        aiProviderFactory: AiProviderFactory,
        alertsEngine: AlertsEngine,
    ): IntelligenceOrchestrator {
        val tier = IntelligenceCapability.deviceTier(context)
        return IntelligenceOrchestrator(
            tier = tier,
            dtcExplainer = DtcExplainer { aiProviderFactory.current() },
            alertsEngine = alertsEngine,
        )
    }

    @Provides
    @Singleton
    fun provideLocalInfoFetcher(aiProviderFactory: AiProviderFactory): LocalInfoFetcher =
        LocalInfoFetcher { aiProviderFactory.current() }

    /**
     * Binds [GpsInfoSink] (defined in :core:obd) to [CityInfoAlerter] (built here, in
     * :core:intelligence) — see CityInfoAlerter's doc for why this indirection exists.
     */
    @Provides
    @Singleton
    fun provideGpsInfoSink(
        @ApplicationContext context: Context,
        settings: DataStore<Preferences>,
        alertsEngine: AlertsEngine,
        sessionManager: ObdSessionManager,
        localInfoFetcher: LocalInfoFetcher,
        aiProviderFactory: AiProviderFactory,
        zoneBriefClient: ZoneBriefClient,
        zoneBriefHolder: ZoneBriefHolder,
    ): GpsInfoSink {
        // Info local (festivales/cierres) por voz — feature existente.
        val cityInfo = CityInfoAlerter(
            localityDetector = LocalityDetector(context),
            localInfoFetcher = localInfoFetcher,
            alertsEngine = alertsEngine,
            sessionManager = sessionManager,
            gateProvider = localInfoGateProvider(settings, aiProviderFactory),
        )
        // Compañero de viaje: brief de zona server-first + respaldo IA.
        val zoneBrief = ZoneBriefAlerter(
            localityDetector = LocalityDetector(context),
            client = zoneBriefClient,
            fetcher = ZoneBriefFetcher { aiProviderFactory.current() },
            holder = zoneBriefHolder,
            alertsEngine = alertsEngine,
            sessionManager = sessionManager,
            homeCountry = "Colombia",
            enabledProvider = zoneBriefEnabledProvider(settings),
            aiAllowedProvider = { aiProviderFactory.current()?.supportsWebSearch == true },
        )
        // Fan-out: un solo GpsInfoSink alimenta ambos alerters (cada uno tiene su throttle).
        return GpsInfoSink { lat, lon ->
            cityInfo.onGpsFix(lat, lon)
            zoneBrief.onGpsFix(lat, lon)
        }
    }

    /** Toggle maestro del compañero de viaje — el respaldo IA se decide aparte por web search. */
    private fun zoneBriefEnabledProvider(
        settings: DataStore<Preferences>,
    ): suspend () -> Boolean = {
        try {
            withContext(Dispatchers.IO) { settings.data.first()[PreferencesKeys.ZONE_BRIEF_ENABLED] ?: false }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "IntelligenceModule: fallo gate de zone brief")
            false
        }
    }

    /**
     * Binds [RestrictionRulesSource] (:core:obd) to [AiRestrictionRulesSource]
     * (:core:intelligence) — same GpsInfoSink/CityInfoAlerter indirection. Cache and gate
     * travel as lambdas because :core:intelligence can't see :core:data.
     */
    @Provides
    @Singleton
    fun provideRestrictionRulesSource(
        @ApplicationContext context: Context,
        settings: DataStore<Preferences>,
        alertsEngine: AlertsEngine,
        aiProviderFactory: AiProviderFactory,
    ): RestrictionRulesSource = AiRestrictionRulesSource(
        localityDetector = LocalityDetector(context),
        fetcher = RestrictionRulesFetcher { aiProviderFactory.current() },
        alertsEngine = alertsEngine,
        gateProvider = aiPicoPlacaGateProvider(settings, aiProviderFactory),
        readCache = { settings.data.first()[PreferencesKeys.AI_RESTRICTION_RULES_JSON] },
        writeCache = { json -> settings.edit { it[PreferencesKeys.AI_RESTRICTION_RULES_JSON] = json } },
    )

    /** Reads the active provider's raw selection from DataStore + SecureKeyStore for [AiProviderFactory]. */
    private fun aiProviderSelectionProvider(
        settings: DataStore<Preferences>,
        secureKeyStore: SecureKeyStore,
    ): suspend () -> AiProviderSelection = {
        try {
            withContext(Dispatchers.IO) {
                val prefs = settings.data.first()
                val provider = prefs[PreferencesKeys.AI_PROVIDER]?.takeIf { it.isNotBlank() } ?: AI_PROVIDER_ANTHROPIC
                AiProviderSelection(
                    provider = provider,
                    apiKey = secureKeyStore.getApiKey(provider),
                    model = prefs[modelKeyFor(provider)],
                    customBaseUrl = prefs[PreferencesKeys.AI_CUSTOM_BASE_URL],
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "IntelligenceModule: failed to read AI provider selection")
            AiProviderSelection(provider = AI_PROVIDER_ANTHROPIC, apiKey = null, model = null, customBaseUrl = null)
        }
    }

    private fun modelKeyFor(provider: String) = when (provider) {
        AI_PROVIDER_OPENAI -> PreferencesKeys.AI_MODEL_OPENAI
        AI_PROVIDER_GEMINI -> PreferencesKeys.AI_MODEL_GEMINI
        AI_PROVIDER_CUSTOM -> PreferencesKeys.AI_MODEL_CUSTOM
        else -> PreferencesKeys.AI_MODEL_ANTHROPIC
    }

    /** Toggle ON + provider with web search configured — misma forma que el gate de info local. */
    private fun aiPicoPlacaGateProvider(
        settings: DataStore<Preferences>,
        aiProviderFactory: AiProviderFactory,
    ): suspend () -> Boolean = {
        try {
            withContext(Dispatchers.IO) {
                val enabled = settings.data.first()[PreferencesKeys.AI_PICO_PLACA_ENABLED] ?: false
                enabled && aiProviderFactory.current()?.supportsWebSearch == true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "IntelligenceModule: failed to evaluate AI pico y placa gate")
            false
        }
    }

    /** Toggle ON + provider with web search configured — CityInfoAlerter's own gate on top checks session state. */
    private fun localInfoGateProvider(
        settings: DataStore<Preferences>,
        aiProviderFactory: AiProviderFactory,
    ): suspend () -> Boolean = {
        try {
            withContext(Dispatchers.IO) {
                val enabled = settings.data.first()[PreferencesKeys.VOICE_LOCAL_INFO] ?: false
                enabled && aiProviderFactory.current()?.supportsWebSearch == true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "IntelligenceModule: failed to evaluate local-info gate")
            false
        }
    }
}
