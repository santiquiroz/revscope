package com.revscope.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `health_reports` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`vehicleProfileId` INTEGER NOT NULL, " +
                "`timestamp` INTEGER NOT NULL, " +
                "`resultsJson` TEXT NOT NULL)"
        )
    }
}

/**
 * `vehicle_profiles` already had a `type` column ("CAR" | "MOTORCYCLE") since v1 — only
 * the adapter link is new. Adding a second, redundant vehicle-type column here would
 * duplicate that field, so this migration only appends `adapterAddress`.
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `vehicle_profiles` ADD COLUMN `adapterAddress` TEXT")
    }
}

/**
 * Adds "Vehículo al día" document tracking fields: plate, pico y placa city, and three
 * expiration dates (SOAT, tecnomecánica, todo riesgo). All nullable, no default — purely
 * additive so existing rows on the owner's device keep their data intact.
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `vehicle_profiles` ADD COLUMN `plate` TEXT")
        db.execSQL("ALTER TABLE `vehicle_profiles` ADD COLUMN `picoPlacaCity` TEXT")
        db.execSQL("ALTER TABLE `vehicle_profiles` ADD COLUMN `soatExpiresAt` INTEGER")
        db.execSQL("ALTER TABLE `vehicle_profiles` ADD COLUMN `rtmExpiresAt` INTEGER")
        db.execSQL("ALTER TABLE `vehicle_profiles` ADD COLUMN `insuranceExpiresAt` INTEGER")
    }
}

/**
 * Adds trip cost/eco tracking: fuel cost in COP and eco-score per session, plus the
 * odometer baseline needed to compute total vehicle km for maintenance tracking, and a
 * new `maintenance_items` table (mirrors `health_reports`'s plain vehicleProfileId
 * column — no FK — since maintenance items are edited independently of profile CRUD).
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `vehicle_profiles` ADD COLUMN `odometerBaseKm` REAL NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `sessions` ADD COLUMN `fuelLiters` REAL")
        db.execSQL("ALTER TABLE `sessions` ADD COLUMN `fuelCostCop` REAL")
        db.execSQL("ALTER TABLE `sessions` ADD COLUMN `ecoScore` INTEGER")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `maintenance_items` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`vehicleProfileId` INTEGER NOT NULL, " +
                "`nombre` TEXT NOT NULL, " +
                "`intervaloKm` REAL NOT NULL, " +
                "`ultimoServicioKm` REAL NOT NULL)"
        )
    }
}
