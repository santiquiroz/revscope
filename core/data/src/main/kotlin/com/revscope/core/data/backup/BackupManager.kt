package com.revscope.core.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.data.R
import com.revscope.core.data.db.AppDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber

private const val DB_FILE_NAME = "revscope.db"
private const val DB_ENTRY_NAME = "revscope.db"
private const val PREFS_ENTRY_NAME = "preferences.json"
private const val IMPORT_TEMP_DIR_PREFIX = "backup_import_"

// ALL_MIGRATIONS inicia en 9 y Room no tiene fallback destructivo: versiones < 9 crashean al iniciar.
internal const val MIN_RESTORABLE_DB_VERSION = 9

internal fun isRestorableVersion(imported: Int, current: Int): Boolean =
    imported in MIN_RESTORABLE_DB_VERSION..current

/**
 * Copia de seguridad completa: base de datos Room (checkpoint WAL → un solo archivo)
 * + todas las preferencias del DataStore, empaquetadas en un zip.
 *
 * La API key de Claude (EncryptedSharedPreferences) NUNCA se incluye: no es portable
 * entre instalaciones y su cifrado depende del Keystore de este dispositivo.
 * Tampoco viajan MCP_TOKEN ni SERVER_AUTH_TOKEN, y el import conserva los de este dispositivo.
 *
 * Tras un import exitoso, AppDatabase queda cerrada — el caller DEBE reiniciar el proceso.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val settings: DataStore<Preferences>,
    private val resolver: StringResolver,
) {

    suspend fun export(target: OutputStream): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            checkpointDatabase()
            writeZip(target)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "BackupManager: export failed")
            Result.failure(e)
        }
    }

    suspend fun import(source: InputStream): Result<Unit> = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, IMPORT_TEMP_DIR_PREFIX + System.currentTimeMillis())
        try {
            val currentVersion = db.openHelper.readableDatabase.version
            extractZip(source, tempDir)
            val dbFile = entryFileIn(tempDir, DB_ENTRY_NAME)
            require(dbFile.exists()) { resolver.get(R.string.data_invalid_database_file) }
            validateImportedVersion(dbFile, currentVersion)
            val decodedPrefs = decodePreferencesIfPresent(tempDir)
            replaceDatabaseFile(dbFile)
            applyPreferencesIfPresent(decodedPrefs)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "BackupManager: import failed")
            Result.failure(e)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // ── Export internals ────────────────────────────────────────────────────

    /** Fuerza el contenido del WAL de vuelta al archivo principal para que el zip lo capture entero. */
    private fun checkpointDatabase() {
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() } // i18n-ignore
    }

    private suspend fun writeZip(target: OutputStream) {
        ZipOutputStream(target).use { zip ->
            writeDatabaseEntry(zip)
            writePreferencesEntry(zip)
        }
    }

    private fun writeDatabaseEntry(zip: ZipOutputStream) {
        zip.putNextEntry(ZipEntry(DB_ENTRY_NAME))
        copyDatabaseFileLocked(zip)
        zip.closeEntry()
    }

    /** Sostiene una transacción exclusiva mientras copia el archivo para evitar una copia a medio escribir. */
    private fun copyDatabaseFileLocked(zip: ZipOutputStream) {
        val sdb = db.openHelper.writableDatabase
        sdb.beginTransaction()
        try {
            context.getDatabasePath(DB_FILE_NAME).inputStream().use { it.copyTo(zip) }
        } finally {
            sdb.endTransaction()
        }
    }

    private suspend fun writePreferencesEntry(zip: ZipOutputStream) {
        val json = PreferencesBackupCodec.encode(settings.data.first())
        zip.putNextEntry(ZipEntry(PREFS_ENTRY_NAME))
        zip.write(json.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    // ── Import internals ────────────────────────────────────────────────────

    private fun extractZip(source: InputStream, targetDir: File) {
        targetDir.mkdirs()
        ZipInputStream(source).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                if (!entry.isDirectory) {
                    val outFile = entryFileIn(targetDir, entry.name)
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { zip.copyTo(it) }
                }
                zip.closeEntry()
            }
        }
    }

    /** Resuelve una entrada de zip dentro de targetDir, rechazando path traversal (zip-slip). */
    private fun entryFileIn(targetDir: File, entryName: String): File {
        val outFile = File(targetDir, entryName)
        val boundary = targetDir.canonicalPath + File.separator
        require(outFile.canonicalPath.startsWith(boundary)) {
            resolver.get(R.string.data_invalid_zip_entry, entryName)
        }
        return outFile
    }

    private fun validateImportedVersion(dbFile: File, currentVersion: Int) {
        val importedVersion = SQLiteDatabase.openDatabase(
            dbFile.path,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use { it.version }
        require(importedVersion >= MIN_RESTORABLE_DB_VERSION) {
            resolver.get(R.string.data_backup_too_old, importedVersion)
        }
        require(isRestorableVersion(importedVersion, currentVersion)) {
            resolver.get(R.string.data_incompatible_database_version, importedVersion)
        }
    }

    private fun replaceDatabaseFile(newDbFile: File) {
        db.close()
        val target = context.getDatabasePath(DB_FILE_NAME)
        val backupAside = File(target.path + ".restore-bak")
        deleteIfExists(backupAside)
        if (target.exists()) target.renameTo(backupAside)
        deleteIfExists(File(target.path + "-wal"))
        deleteIfExists(File(target.path + "-shm"))
        try {
            newDbFile.copyTo(target, overwrite = true)
            deleteIfExists(backupAside)
        } catch (e: Exception) {
            deleteIfExists(target)
            backupAside.renameTo(target)
            throw e
        }
    }

    private fun deleteIfExists(file: File) {
        if (file.exists()) file.delete()
    }

    private fun decodePreferencesIfPresent(tempDir: File): Map<String, JSONObject>? {
        val prefsFile = entryFileIn(tempDir, PREFS_ENTRY_NAME)
        return if (prefsFile.exists()) {
            PreferencesBackupCodec.decode(prefsFile.readText(Charsets.UTF_8))
        } else {
            null
        }
    }

    private suspend fun applyPreferencesIfPresent(decodedPrefs: Map<String, JSONObject>?) {
        if (decodedPrefs == null) return
        try {
            PreferencesBackupCodec.apply(decodedPrefs, settings)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "BackupManager: error al aplicar preferencias tras restaurar base de datos")
        }
    }
}
