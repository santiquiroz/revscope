package com.revscope.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.revscope.core.data.db.dao.GpsDao
import com.revscope.core.data.db.dao.LapDao
import com.revscope.core.data.db.dao.SessionDao
import com.revscope.core.data.db.dao.TelemetryDao
import com.revscope.core.data.db.dao.VehicleProfileDao
import com.revscope.core.data.db.entities.GpsPointEntity
import com.revscope.core.data.db.entities.LapEntity
import com.revscope.core.data.db.entities.SessionEntity
import com.revscope.core.data.db.entities.TelemetryPointEntity
import com.revscope.core.data.db.entities.VehicleProfileEntity

@Database(
    entities = [
        SessionEntity::class,
        TelemetryPointEntity::class,
        VehicleProfileEntity::class,
        GpsPointEntity::class,
        LapEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun telemetryDao(): TelemetryDao
    abstract fun vehicleProfileDao(): VehicleProfileDao
    abstract fun gpsDao(): GpsDao
    abstract fun lapDao(): LapDao
}
