package com.revscope.core.data.db

import android.database.Cursor
import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun `9 a 10 conserva datos`() = assertStepPreservesRows(9, MIGRATION_9_10)

    @Test
    fun `10 a 11 conserva datos`() = assertStepPreservesRows(10, MIGRATION_10_11)

    @Test
    fun `11 a 12 conserva datos`() = assertStepPreservesRows(11, MIGRATION_11_12)

    @Test
    fun `12 a 13 conserva datos`() = assertStepPreservesRows(12, MIGRATION_12_13)

    @Test
    fun `13 a 14 conserva datos`() = assertStepPreservesRows(13, MIGRATION_13_14)

    @Test
    fun `14 a 15 conserva datos`() = assertStepPreservesRows(14, MIGRATION_14_15)

    @Test
    fun `15 a 16 conserva datos y marca los huecos existentes como LOCAL`() {
        helper.createDatabase(TEST_DB, 15).use { db ->
            seedMotoWithSession(db)
            seedPothole(db)
        }

        helper.runMigrationsAndValidate(TEST_DB, 16, true, MIGRATION_15_16).use { db ->
            assertSeedRowsSurvived(db)
            assertEquals("LOCAL", db.queryString("SELECT source FROM potholes"))
            assertTrue(db.queryIsNull("SELECT syncedAt FROM potholes"))
        }
    }

    @Test
    fun `16 a 17 conserva datos`() = assertStepPreservesRows(16, MIGRATION_16_17)

    @Test
    fun `17 a 18 conserva datos`() = assertStepPreservesRows(17, MIGRATION_17_18)

    @Test
    fun `9 a 18 encadenado conserva la moto y la sesion con los defaults nuevos`() {
        helper.createDatabase(TEST_DB, 9).use(::seedMotoWithSession)

        helper.runMigrationsAndValidate(TEST_DB, 18, true, *ALL_MIGRATIONS).use { db ->
            assertSeedRowsSurvived(db)
            assertEquals("Moto", db.queryString("SELECT name FROM vehicle_profiles"))
            assertEquals("MOTORCYCLE", db.queryString("SELECT type FROM vehicle_profiles"))
            assertEquals(6, db.queryInt("SELECT gearCount FROM vehicle_profiles"))
            assertEquals("CORRIENTE", db.queryString("SELECT fuelType FROM vehicle_profiles"))
            assertEquals(0, db.queryInt("SELECT odometerBaseKm FROM vehicle_profiles"))
            assertTrue(db.queryIsNull("SELECT plate FROM vehicle_profiles"))
            assertTrue(db.queryIsNull("SELECT fuelCostCop FROM sessions"))
            assertEquals(12_345, db.queryInt("SELECT maxRpm FROM sessions"))
            assertEquals(0, db.queryInt("SELECT COUNT(*) FROM saved_places"))
            assertEquals(0, db.queryInt("SELECT COUNT(*) FROM potholes"))
            assertEquals(0, db.queryInt("SELECT COUNT(*) FROM maintenance_items"))
            assertEquals(0, db.queryInt("SELECT COUNT(*) FROM health_reports"))
        }
    }

    private fun assertStepPreservesRows(fromVersion: Int, migration: Migration) {
        helper.createDatabase(TEST_DB, fromVersion).use(::seedMotoWithSession)

        helper.runMigrationsAndValidate(TEST_DB, fromVersion + 1, true, migration).use(::assertSeedRowsSurvived)
    }

    private fun seedMotoWithSession(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO vehicle_profiles (id, name, type, vin, enabledPids, gearRatios, createdAt, maxRpm, redlineRpm) " +
                "VALUES (1, 'Moto', 'MOTORCYCLE', NULL, '0C,0D', NULL, 1700000000000, 13000, 12000)",
        )
        db.execSQL(
            "INSERT INTO sessions (id, vehicleProfileId, startedAt, endedAt, adapterName, maxRpm, maxSpeed, distanceKm, best0to60Ms, best0to100Ms) " +
                "VALUES (1, 1, 1700000100000, 1700000200000, 'vLinker', 12345, 140, 42.5, NULL, NULL)",
        )
    }

    private fun seedPothole(db: SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO potholes (id, latitude, longitude, severityG, hits, lastHitAt) " +
                "VALUES (1, 6.2442, -75.5812, 1.8, 3, 1700000300000)",
        )
    }

    private fun assertSeedRowsSurvived(db: SupportSQLiteDatabase) {
        assertEquals(1, db.queryInt("SELECT COUNT(*) FROM vehicle_profiles WHERE id = 1 AND type = 'MOTORCYCLE'"))
        assertEquals(1, db.queryInt("SELECT COUNT(*) FROM sessions WHERE id = 1 AND vehicleProfileId = 1"))
    }

    private fun SupportSQLiteDatabase.queryInt(sql: String): Int = firstColumn(sql) { it.getInt(0) }

    private fun SupportSQLiteDatabase.queryString(sql: String): String = firstColumn(sql) { it.getString(0) }

    private fun SupportSQLiteDatabase.queryIsNull(sql: String): Boolean = firstColumn(sql) { it.isNull(0) }

    private fun <T> SupportSQLiteDatabase.firstColumn(sql: String, read: (Cursor) -> T): T =
        query(sql).use { cursor ->
            assertTrue("Sin filas para: $sql", cursor.moveToFirst())
            read(cursor)
        }

    private companion object {
        const val TEST_DB = "migration-test.db"

        val ALL_MIGRATIONS = arrayOf(
            MIGRATION_9_10,
            MIGRATION_10_11,
            MIGRATION_11_12,
            MIGRATION_12_13,
            MIGRATION_13_14,
            MIGRATION_14_15,
            MIGRATION_15_16,
            MIGRATION_16_17,
            MIGRATION_17_18,
        )
    }
}
