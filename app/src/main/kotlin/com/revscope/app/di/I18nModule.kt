package com.revscope.app.di

import android.content.Context
import com.revscope.core.common.i18n.AndroidStringResolver
import com.revscope.core.common.i18n.AppLanguage
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.common.units.AppUnits
import com.revscope.core.common.units.UnitFormatter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object I18nModule {
    @Provides
    @Singleton
    fun provideStringResolver(@ApplicationContext context: Context): StringResolver =
        AndroidStringResolver(context)

    @Provides
    @Singleton
    fun provideUnitFormatter(@ApplicationContext context: Context): UnitFormatter =
        UnitFormatter(AppUnits.current(context), AppLanguage.current(context))
}
