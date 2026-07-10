package com.revscope.core.data.backup

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.revscope.core.data.datastore.PreferencesKeys
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import timber.log.Timber

private const val BACKUP_FILE_PREFIX = "revscope-auto-"
private const val BACKUP_RELATIVE_PATH = "Download/RevScope"
private const val BACKUP_MIME_TYPE = "application/zip"
private const val MAX_KEPT_BACKUPS = 4

/**
 * Corre cada 7 días (ver RevScopeApp) y exporta una copia de seguridad automática a
 * Descargas/RevScope: MediaStore en API 29+, almacenamiento propio de la app en API 26-28
 * (Scoped Storage no permite escribir en Descargas compartidas antes de API 29).
 * Silencioso: solo corre si AUTO_BACKUP_ENABLED está activo (default true); conserva
 * únicamente las 4 copias más recientes por nombre de archivo.
 */
@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val settings: DataStore<Preferences>,
    private val backupManager: BackupManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!isAutoBackupEnabled()) return Result.success()
        val fileName = backupFileName()
        val exported = exportBackup(fileName)
        if (exported) pruneOldBackups() else Timber.w("AutoBackupWorker: weekly export failed")
        return Result.success()
    }

    private suspend fun isAutoBackupEnabled(): Boolean =
        settings.data.first()[PreferencesKeys.AUTO_BACKUP_ENABLED] ?: true

    private fun backupFileName(): String {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return "$BACKUP_FILE_PREFIX$date.zip"
    }

    private suspend fun exportBackup(fileName: String): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            exportToMediaStore(fileName)
        } else {
            exportToLegacyStorage(fileName)
        }

    private fun pruneOldBackups() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            pruneMediaStoreBackups()
        } else {
            pruneLegacyBackups()
        }
    }

    // ── API 29+: MediaStore Downloads ───────────────────────────────────────

    private suspend fun exportToMediaStore(fileName: String): Boolean {
        val result = runCatching {
            val resolver = applicationContext.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, mediaStoreValues(fileName))
                ?: return@runCatching false
            val exported = resolver.openOutputStream(uri)?.use { out -> backupManager.export(out).isSuccess } ?: false
            if (!exported) resolver.delete(uri, null, null)
            exported
        }
        result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        return result.getOrDefault(false)
    }

    private fun mediaStoreValues(fileName: String) = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
        put(MediaStore.Downloads.MIME_TYPE, BACKUP_MIME_TYPE)
        put(MediaStore.Downloads.RELATIVE_PATH, BACKUP_RELATIVE_PATH)
    }

    private fun pruneMediaStoreBackups() {
        val idsToDelete = runCatching { staleMediaStoreBackupIds() }.getOrNull().orEmpty()
        val resolver = applicationContext.contentResolver
        idsToDelete.forEach { id ->
            val uri = ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
            runCatching { resolver.delete(uri, null, null) }
        }
    }

    private fun staleMediaStoreBackupIds(): List<Long> {
        val resolver = applicationContext.contentResolver
        val projection = arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME)
        val selection = "${MediaStore.Downloads.RELATIVE_PATH} = ? AND ${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
        val args = arrayOf("$BACKUP_RELATIVE_PATH/", "$BACKUP_FILE_PREFIX%")
        val sortOrder = "${MediaStore.Downloads.DISPLAY_NAME} DESC"
        return resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, args, sortOrder)
            ?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                generateSequence { if (cursor.moveToNext()) cursor.getLong(idIndex) else null }.toList()
            }
            .orEmpty()
            .drop(MAX_KEPT_BACKUPS)
    }

    // ── API 26-28: app-scoped external storage fallback ────────────────────

    private suspend fun exportToLegacyStorage(fileName: String): Boolean {
        val result = runCatching {
            val dir = legacyBackupDir()
            dir.mkdirs()
            val file = File(dir, fileName)
            val exported = file.outputStream().use { out -> backupManager.export(out).isSuccess }
            if (!exported) file.delete()
            exported
        }
        result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        return result.getOrDefault(false)
    }

    private fun legacyBackupDir(): File =
        File(applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "RevScope")

    private fun pruneLegacyBackups() {
        val dir = legacyBackupDir()
        val stale = dir.listFiles { file -> file.name.startsWith(BACKUP_FILE_PREFIX) }
            ?.sortedByDescending { it.name }
            ?.drop(MAX_KEPT_BACKUPS)
            .orEmpty()
        stale.forEach { it.delete() }
    }

    companion object {
        const val WORK_NAME = "auto_backup"
    }
}
