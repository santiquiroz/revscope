package com.revscope.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.revscope.core.data.datastore.PreferencesKeys
import com.revscope.core.intelligence.IntelligenceCapability
import com.revscope.core.intelligence.IntelligenceOrchestrator
import com.revscope.core.intelligence.dtc.DtcExplainer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "revscope_settings")

@Module
@InstallIn(SingletonComponent::class)
object IntelligenceModule {

    @Provides
    @Singleton
    fun provideIntelligenceOrchestrator(
        @ApplicationContext context: Context,
    ): IntelligenceOrchestrator {
        val tier = IntelligenceCapability.deviceTier(context)

        val apiKeyProvider: () -> String? = {
            runBlocking {
                try {
                    context.settingsDataStore.data.first()[PreferencesKeys.CLAUDE_API_KEY]
                        ?.takeIf { it.isNotBlank() }
                } catch (_: Exception) {
                    null
                }
            }
        }

        return IntelligenceOrchestrator(
            tier = tier,
            dtcExplainer = DtcExplainer(apiKeyProvider),
        )
    }
}
