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
