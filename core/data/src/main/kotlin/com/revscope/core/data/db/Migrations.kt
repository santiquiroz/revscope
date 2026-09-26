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

/**
 * Adds the vehicle's fuel type ("CORRIENTE" | "EXTRA" | "DIESEL") — drives which of the
 * three FUEL_PRICE_* DataStore prices is used to estimate trip cost. Defaults to
 * "CORRIENTE" for every existing row (matches the owner's car; the owner's moto is
 * edited to "EXTRA" post-migration in the profile screen).
 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `vehicle_profiles` ADD COLUMN `fuelType` TEXT NOT NULL DEFAULT 'CORRIENTE'")
    }
}

/** Sync colaborativo de huecos: origen (LOCAL/REMOTE) + marca de subida al servidor. */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `potholes` ADD COLUMN `source` TEXT NOT NULL DEFAULT 'LOCAL'")
        db.execSQL("ALTER TABLE `potholes` ADD COLUMN `syncedAt` INTEGER")
    }
}

/** Mapa personal de huecos/resaltos detectados por IMU — tabla nueva, puramente aditiva. */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `potholes` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`latitude` REAL NOT NULL, " +
                "`longitude` REAL NOT NULL, " +
                "`severityG` REAL NOT NULL, " +
                "`hits` INTEGER NOT NULL, " +
                "`lastHitAt` INTEGER NOT NULL)"
        )
    }
}

/** Navegación: lugares guardados (casa, trabajo, favoritos, recientes). */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `saved_places` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`lat` REAL NOT NULL, " +
                "`lon` REAL NOT NULL, " +
                "`lastUsedAt` INTEGER NOT NULL)",
        )
    }
}

/**
 * Número de marchas por perfil — gobierna el gear learner. Default 6 (auto típico) para
 * filas existentes; el form aplica 5 para MOTORCYCLE nuevas (ver VehicleViewModel.setType).
 */
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `vehicle_profiles` ADD COLUMN `gearCount` INTEGER NOT NULL DEFAULT 6")
    }
}

/**
 * Taller profesional: sesiones de diagnóstico con su línea de tiempo, conocimiento por modelo
 * (ECU, repuestos, cableado), bandas de referencia editables y el modelo de referencia del perfil.
 * Puramente aditiva.
 */
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        MIGRATION_18_19_SQL.forEach(db::execSQL)
    }
}

private val MIGRATION_18_19_SQL = listOf(
    "CREATE TABLE IF NOT EXISTS `diag_sessions` (" +
        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`vehicleProfileId` INTEGER NOT NULL DEFAULT 0, " +
        "`knowledgeKey` TEXT, " +
        "`startedAt` INTEGER NOT NULL, " +
        "`closedAt` INTEGER, " +
        "`title` TEXT NOT NULL, " +
        "`symptomTags` TEXT NOT NULL DEFAULT '', " +
        "`symptomsText` TEXT NOT NULL DEFAULT '', " +
        "`notes` TEXT NOT NULL DEFAULT '', " +
        "`odometerKm` REAL, " +
        "`baselineHealthReportId` INTEGER, " +
        "`interpretation` TEXT NOT NULL DEFAULT '', " +
        "`checkedSteps` TEXT NOT NULL DEFAULT '')",
    "CREATE INDEX IF NOT EXISTS `index_diag_sessions_vehicleProfileId` ON `diag_sessions` (`vehicleProfileId`)",
    "CREATE TABLE IF NOT EXISTS `diag_events` (" +
        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`sessionId` INTEGER NOT NULL, " +
        "`timestamp` INTEGER NOT NULL, " +
        "`type` TEXT NOT NULL, " +
        "`source` TEXT NOT NULL DEFAULT 'APP', " +
        "`title` TEXT NOT NULL, " +
        "`summary` TEXT NOT NULL DEFAULT '', " +
        "`verdict` TEXT NOT NULL DEFAULT 'INFO', " +
        "`payloadVersion` INTEGER NOT NULL DEFAULT 1, " +
        "`payloadJson` TEXT NOT NULL DEFAULT '{}', " +
        "`attachmentPath` TEXT, " +
        "FOREIGN KEY(`sessionId`) REFERENCES `diag_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
    "CREATE INDEX IF NOT EXISTS `index_diag_events_sessionId_timestamp` ON `diag_events` (`sessionId`, `timestamp`)",
    "CREATE TABLE IF NOT EXISTS `vehicle_knowledge` (" +
        "`key` TEXT PRIMARY KEY NOT NULL, " +
        "`displayName` TEXT NOT NULL, " +
        "`vehicleType` TEXT NOT NULL, " +
        "`ecu` TEXT, `ecuSource` TEXT, " +
        "`protocolNotes` TEXT NOT NULL DEFAULT '', " +
        "`notes` TEXT NOT NULL DEFAULT '', " +
        "`wiringJson` TEXT NOT NULL DEFAULT '[]', " +
        "`seedVersion` INTEGER NOT NULL DEFAULT 0, " +
        "`userEdited` INTEGER NOT NULL DEFAULT 0)",
    "CREATE TABLE IF NOT EXISTS `vehicle_parts` (" +
        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`knowledgeKey` TEXT NOT NULL, " +
        "`role` TEXT NOT NULL, `partNumber` TEXT NOT NULL, `description` TEXT NOT NULL, " +
        "`kind` TEXT NOT NULL, `note` TEXT NOT NULL DEFAULT '', `source` TEXT NOT NULL DEFAULT '', " +
        "FOREIGN KEY(`knowledgeKey`) REFERENCES `vehicle_knowledge`(`key`) ON UPDATE NO ACTION ON DELETE CASCADE)",
    "CREATE INDEX IF NOT EXISTS `index_vehicle_parts_knowledgeKey` ON `vehicle_parts` (`knowledgeKey`)",
    "CREATE TABLE IF NOT EXISTS `reference_bands` (" +
        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`knowledgeKey` TEXT NOT NULL, `bandKey` TEXT NOT NULL, " +
        "`minValue` REAL, `maxValue` REAL, `unit` TEXT NOT NULL, " +
        "`origin` TEXT NOT NULL, `source` TEXT NOT NULL DEFAULT '')",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_reference_bands_knowledgeKey_bandKey` " +
        "ON `reference_bands` (`knowledgeKey`, `bandKey`)",
    "ALTER TABLE `vehicle_profiles` ADD COLUMN `knowledgeKey` TEXT",
)

// Al final del archivo: los val de nivel superior se inicializan en orden de aparición.
val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_9_10,
    MIGRATION_10_11,
    MIGRATION_11_12,
    MIGRATION_12_13,
    MIGRATION_13_14,
    MIGRATION_14_15,
    MIGRATION_15_16,
    MIGRATION_16_17,
    MIGRATION_17_18,
    MIGRATION_18_19,
)
