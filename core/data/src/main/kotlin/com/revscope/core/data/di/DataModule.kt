package com.revscope.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.revscope.core.data.db.AppDatabase
import com.revscope.core.data.db.dao.GpsDao
import com.revscope.core.data.db.dao.LapDao
import com.revscope.core.data.db.dao.SessionDao
import com.revscope.core.data.db.dao.TelemetryDao
import com.revscope.core.data.db.dao.VehicleProfileDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Single process-wide DataStore — a second instance on the same file throws at runtime
private val Context.settingsDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "revscope_settings")

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.settingsDataStore

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "revscope.db")
            // Pre-1.0: schema changes wipe local telemetry instead of crashing.
            // Replace with real Migrations before first public release.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideSessionDao(db: AppDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideTelemetryDao(db: AppDatabase): TelemetryDao = db.telemetryDao()

    @Provides
    fun provideVehicleProfileDao(db: AppDatabase): VehicleProfileDao = db.vehicleProfileDao()

    @Provides
    fun provideGpsDao(db: AppDatabase): GpsDao = db.gpsDao()

    @Provides
    fun provideLapDao(db: AppDatabase): LapDao = db.lapDao()
}
