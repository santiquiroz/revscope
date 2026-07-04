package com.revscope.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.revscope.core.data.datastore.PreferencesKeys
import com.revscope.core.intelligence.IntelligenceCapability
import com.revscope.core.intelligence.IntelligenceOrchestrator
import com.revscope.core.intelligence.dtc.DtcExplainer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
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
    ): IntelligenceOrchestrator {
        val tier = IntelligenceCapability.deviceTier(context)

        val apiKeyProvider: suspend () -> String? = {
            try {
                settings.data.first()[PreferencesKeys.CLAUDE_API_KEY]
                    ?.takeIf { it.isNotBlank() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "IntelligenceModule: failed to read Claude API key")
                null
            }
        }

        return IntelligenceOrchestrator(
            tier = tier,
            dtcExplainer = DtcExplainer(apiKeyProvider),
        )
    }
}
