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
