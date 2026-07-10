package com.revscope.core.common.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val EXPORTS_DIR = "exports"
private const val CSV_MIME_TYPE = "text/csv"
private const val CHOOSER_TITLE = "Exportar CSV"
private const val EXPORT_FAILED_MESSAGE = "No se pudo exportar el CSV"
private const val FILE_NAME_TIMESTAMP_PATTERN = "yyyyMMdd-HHmm"
private const val ISO_TIMESTAMP_PATTERN = "yyyy-MM-dd'T'HH:mm:ss"

/**
 * Shared CSV export path for every measured/displayed metric across RevScope — writes to
 * cache/exports (same FileProvider authority as trip/report image shares) and opens the
 * system share sheet. Comma-separated, dot decimals, filenames `revscope-<tipo>-<AAAAMMDD-HHmm>.csv`.
 */
object CsvShare {

    /**
     * Writes [header] + [rows] as CSV and opens the ACTION_SEND chooser. [comment], when present,
     * is written as a leading `# key=value` line before the header (e.g. O2 crossings/min).
     */
    suspend fun shareCsv(
        context: Context,
        tipo: String,
        header: List<String>,
        rows: Sequence<List<Any?>>,
        comment: String? = null,
    ) {
        val uri = writeCsvFile(context, tipo, header, rows, comment)
        if (uri == null) {
            Toast.makeText(context, EXPORT_FAILED_MESSAGE, Toast.LENGTH_SHORT).show()
            return
        }
        launchShareChooser(context, uri)
    }

    /** ISO-8601 local timestamp for a CSV `timestamp_iso` column — pair with the raw `epoch_ms`. */
    fun isoTimestamp(epochMs: Long): String =
        SimpleDateFormat(ISO_TIMESTAMP_PATTERN, Locale.US).format(Date(epochMs))

    private suspend fun writeCsvFile(
        context: Context,
        tipo: String,
        header: List<String>,
        rows: Sequence<List<Any?>>,
        comment: String?,
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val exportDir = File(context.cacheDir, EXPORTS_DIR).apply { mkdirs() }
            val file = File(exportDir, fileName(tipo))
            writeRows(file, header, rows, comment)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } catch (e: Exception) {
            Timber.e(e, "CsvShare: export failed for tipo=$tipo")
            null
        }
    }

    private fun writeRows(file: File, header: List<String>, rows: Sequence<List<Any?>>, comment: String?) {
        file.bufferedWriter().use { out ->
            comment?.let { out.appendLine("# $it") }
            out.appendLine(toCsvLine(header))
            rows.forEach { row -> out.appendLine(toCsvLine(row.map(::formatCell))) }
        }
    }

    private fun launchShareChooser(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = CSV_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, CHOOSER_TITLE))
    }

    private fun fileName(tipo: String): String {
        val stamp = SimpleDateFormat(FILE_NAME_TIMESTAMP_PATTERN, Locale.US).format(Date())
        return "revscope-$tipo-$stamp.csv"
    }

    private fun formatCell(value: Any?): String = when (value) {
        null -> ""
        is Double -> "%.4f".format(Locale.US, value)
        is Float -> "%.4f".format(Locale.US, value)
        else -> value.toString()
    }

    private fun toCsvLine(fields: List<String>): String = fields.joinToString(",", transform = ::escapeCsvField)

    private fun escapeCsvField(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
}
