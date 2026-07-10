package com.revscope.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.revscope.core.data.datastore.PreferencesKeys
import com.revscope.core.data.secure.SecureKeyStore
import com.revscope.core.intelligence.IntelligenceCapability
import com.revscope.core.intelligence.IntelligenceOrchestrator
import com.revscope.core.intelligence.dtc.DtcExplainer
import com.revscope.core.intelligence.local.CityInfoAlerter
import com.revscope.core.intelligence.local.LocalInfoFetcher
import com.revscope.core.obd.alerts.AlertsEngine
import com.revscope.core.obd.legal.LocalityDetector
import com.revscope.core.obd.service.GpsInfoSink
import com.revscope.core.obd.session.ObdSessionManager
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
    fun provideIntelligenceOrchestrator(
        @ApplicationContext context: Context,
        settings: DataStore<Preferences>,
        secureKeyStore: SecureKeyStore,
        alertsEngine: AlertsEngine,
    ): IntelligenceOrchestrator {
        val tier = IntelligenceCapability.deviceTier(context)
        return IntelligenceOrchestrator(
            tier = tier,
            dtcExplainer = DtcExplainer(claudeApiKeyProvider(settings, secureKeyStore)),
            alertsEngine = alertsEngine,
        )
    }

    @Provides
    @Singleton
    fun provideLocalInfoFetcher(
        settings: DataStore<Preferences>,
        secureKeyStore: SecureKeyStore,
    ): LocalInfoFetcher = LocalInfoFetcher(claudeApiKeyProvider(settings, secureKeyStore))

    /**
     * Binds [GpsInfoSink] (defined in :core:obd) to [CityInfoAlerter] (built here, in
     * :core:intelligence) — see CityInfoAlerter's doc for why this indirection exists.
     */
    @Provides
    @Singleton
    fun provideGpsInfoSink(
        @ApplicationContext context: Context,
        settings: DataStore<Preferences>,
        secureKeyStore: SecureKeyStore,
        alertsEngine: AlertsEngine,
        sessionManager: ObdSessionManager,
        localInfoFetcher: LocalInfoFetcher,
    ): GpsInfoSink = CityInfoAlerter(
        localityDetector = LocalityDetector(context),
        localInfoFetcher = localInfoFetcher,
        alertsEngine = alertsEngine,
        sessionManager = sessionManager,
        gateProvider = localInfoGateProvider(settings, secureKeyStore),
    )

    private fun claudeApiKeyProvider(
        settings: DataStore<Preferences>,
        secureKeyStore: SecureKeyStore,
    ): suspend () -> String? = {
        try {
            withContext(Dispatchers.IO) {
                // Encrypted store first; plaintext DataStore only as pre-migration fallback
                secureKeyStore.getClaudeApiKey()?.takeIf { it.isNotBlank() }
                    ?: settings.data.first()[PreferencesKeys.CLAUDE_API_KEY]
                        ?.takeIf { it.isNotBlank() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "IntelligenceModule: failed to read Claude API key")
            null
        }
    }

    /** Toggle ON + API key present — CityInfoAlerter's own gate on top of this checks session state. */
    private fun localInfoGateProvider(
        settings: DataStore<Preferences>,
        secureKeyStore: SecureKeyStore,
    ): suspend () -> Boolean = {
        try {
            withContext(Dispatchers.IO) {
                val enabled = settings.data.first()[PreferencesKeys.VOICE_LOCAL_INFO] ?: false
                enabled && !secureKeyStore.getClaudeApiKey().isNullOrBlank()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "IntelligenceModule: failed to evaluate local-info gate")
            false
        }
    }
}
