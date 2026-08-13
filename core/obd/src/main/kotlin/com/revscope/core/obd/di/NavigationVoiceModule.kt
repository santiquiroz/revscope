package com.revscope.core.obd.di

import com.revscope.core.navigation.NavigationVoice
import com.revscope.core.obd.alerts.AlertsEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * La navegación habla por el mismo motor que el resto de la app, que ya está resuelto para
 * llegar al intercomunicador del casco por el stream de media.
 */
@Module
@InstallIn(SingletonComponent::class)
object NavigationVoiceModule {

    @Provides
    @Singleton
    fun provideNavigationVoice(alerts: AlertsEngine): NavigationVoice =
        NavigationVoice { instruction -> alerts.announceNavigation(instruction) }
}
