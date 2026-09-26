package com.revscope.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.revscope.core.data.db.dao.DiagSessionDao
import com.revscope.core.data.db.dao.GpsDao
import com.revscope.core.data.db.dao.HealthReportDao
import com.revscope.core.data.db.dao.HrDao
import com.revscope.core.data.db.dao.ImuDao
import com.revscope.core.data.db.dao.LapDao
import com.revscope.core.data.db.dao.MaintenanceDao
import com.revscope.core.data.db.dao.PotholeDao
import com.revscope.core.data.db.dao.SavedPlaceDao
import com.revscope.core.data.db.dao.SessionDao
import com.revscope.core.data.db.dao.SpeedCameraDao
import com.revscope.core.data.db.dao.TelemetryDao
import com.revscope.core.data.db.dao.VehicleKnowledgeDao
import com.revscope.core.data.db.dao.VehicleProfileDao
import com.revscope.core.data.db.entities.DiagEventEntity
import com.revscope.core.data.db.entities.DiagSessionEntity
import com.revscope.core.data.db.entities.GpsPointEntity
import com.revscope.core.data.db.entities.HealthReportEntity
import com.revscope.core.data.db.entities.HrPointEntity
import com.revscope.core.data.db.entities.ImuPointEntity
import com.revscope.core.data.db.entities.LapEntity
import com.revscope.core.data.db.entities.MaintenanceItemEntity
import com.revscope.core.data.db.entities.PotholeEntity
import com.revscope.core.data.db.entities.ReferenceBandEntity
import com.revscope.core.data.db.entities.SavedPlaceEntity
import com.revscope.core.data.db.entities.SessionEntity
import com.revscope.core.data.db.entities.SpeedCameraEntity
import com.revscope.core.data.db.entities.TelemetryPointEntity
import com.revscope.core.data.db.entities.VehicleKnowledgeEntity
import com.revscope.core.data.db.entities.VehiclePartEntity
import com.revscope.core.data.db.entities.VehicleProfileEntity

@Database(
    entities = [
        SessionEntity::class,
        TelemetryPointEntity::class,
        VehicleProfileEntity::class,
        GpsPointEntity::class,
        LapEntity::class,
        ImuPointEntity::class,
        HrPointEntity::class,
        SpeedCameraEntity::class,
        HealthReportEntity::class,
        MaintenanceItemEntity::class,
        PotholeEntity::class,
        SavedPlaceEntity::class,
        DiagSessionEntity::class,
        DiagEventEntity::class,
        VehicleKnowledgeEntity::class,
        VehiclePartEntity::class,
        ReferenceBandEntity::class,
    ],
    version = 19,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun telemetryDao(): TelemetryDao
    abstract fun vehicleProfileDao(): VehicleProfileDao
    abstract fun gpsDao(): GpsDao
    abstract fun lapDao(): LapDao
    abstract fun imuDao(): ImuDao
    abstract fun hrDao(): HrDao
    abstract fun speedCameraDao(): SpeedCameraDao
    abstract fun healthReportDao(): HealthReportDao
    abstract fun maintenanceDao(): MaintenanceDao
    abstract fun potholeDao(): PotholeDao
    abstract fun savedPlaceDao(): SavedPlaceDao
    abstract fun diagSessionDao(): DiagSessionDao
    abstract fun vehicleKnowledgeDao(): VehicleKnowledgeDao
}
