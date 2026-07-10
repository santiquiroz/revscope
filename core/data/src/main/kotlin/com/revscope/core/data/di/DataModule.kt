package com.revscope.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.revscope.core.data.db.AppDatabase
import com.revscope.core.data.db.MIGRATION_10_11
import com.revscope.core.data.db.MIGRATION_11_12
import com.revscope.core.data.db.MIGRATION_12_13
import com.revscope.core.data.db.MIGRATION_9_10
import com.revscope.core.data.db.dao.GpsDao
import com.revscope.core.data.db.dao.HealthReportDao
import com.revscope.core.data.db.dao.HrDao
import com.revscope.core.data.db.dao.ImuDao
import com.revscope.core.data.db.dao.LapDao
import com.revscope.core.data.db.dao.MaintenanceDao
import com.revscope.core.data.db.dao.SessionDao
import com.revscope.core.data.db.dao.SpeedCameraDao
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
            .addMigrations(MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
            // Sin fallback destructivo: toda migración debe ser explícita (incidente 2026-07-08).
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

    @Provides
    fun provideImuDao(db: AppDatabase): ImuDao = db.imuDao()

    @Provides
    fun provideHrDao(db: AppDatabase): HrDao = db.hrDao()

    @Provides
    fun provideSpeedCameraDao(db: AppDatabase): SpeedCameraDao = db.speedCameraDao()

    @Provides
    fun provideHealthReportDao(db: AppDatabase): HealthReportDao = db.healthReportDao()

    @Provides
    fun provideMaintenanceDao(db: AppDatabase): MaintenanceDao = db.maintenanceDao()
}
