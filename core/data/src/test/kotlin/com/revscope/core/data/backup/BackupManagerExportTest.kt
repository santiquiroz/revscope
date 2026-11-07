package com.revscope.core.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.revscope.core.data.R
import com.revscope.core.data.db.ALL_MIGRATIONS
import com.revscope.core.data.db.AppDatabase
import com.revscope.core.uitesting.XmlStringResolver
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
// El SQLite legacy de Robolectric (3.8.7) ignora wal_checkpoint(TRUNCATE); Android 8+ trae >= 3.18.
@SQLiteMode(SQLiteMode.Mode.NATIVE)
class BackupManagerExportTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var manager: BackupManager

    @Before
    fun setUp() {
        db = Room.databaseBuilder(context, AppDatabase::class.java, "revscope.db")
            .addMigrations(*ALL_MIGRATIONS)
            .build()
        val settings = PreferenceDataStoreFactory.create { File(context.filesDir, "backup-test.preferences_pb") }
        manager = BackupManager(context, db, settings, XmlStringResolver.en(R::class.java))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `export incluye las filas recien insertadas`() = runBlocking {
        insertPotholes(count = 5)

        val zipBytes = ByteArrayOutputStream().also { manager.export(it).getOrThrow() }.toByteArray()

        assertEquals(5, potholeCountIn(extractDatabase(zipBytes)))
    }

    @Test
    fun `export deja el WAL vacio tras copiar`() = runBlocking {
        insertPotholes(count = 3)

        manager.export(ByteArrayOutputStream()).getOrThrow()

        val wal = File(context.getDatabasePath("revscope.db").path + "-wal")
        assertTrue(shouldCopy(wal.length()))
    }

    private fun insertPotholes(count: Int) {
        val sdb = db.openHelper.writableDatabase
        repeat(count) { i ->
            sdb.execSQL(
                "INSERT INTO potholes (latitude, longitude, severityG, hits, lastHitAt, source, syncedAt) " +
                    "VALUES (6.24, -75.58, 1.5, 1, ${1_700_000_000_000L + i}, 'LOCAL', NULL)",
            )
        }
    }

    private fun extractDatabase(zipBytes: ByteArray): File {
        val out = File(context.cacheDir, "exported-${System.nanoTime()}.db")
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            generateSequence { zip.nextEntry }.first { it.name == "revscope.db" }
            out.outputStream().use { zip.copyTo(it) }
        }
        return out
    }

    private fun potholeCountIn(dbFile: File): Int =
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { sdb ->
            sdb.rawQuery("SELECT COUNT(*) FROM potholes", null).use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
        }
}
