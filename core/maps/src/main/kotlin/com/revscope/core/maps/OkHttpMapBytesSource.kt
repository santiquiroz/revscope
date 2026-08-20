package com.revscope.core.maps

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

private const val CHUNK_SIZE_BYTES = 8 * 1024
private const val DOWNLOAD_TIMEOUT_MINUTES = 30L

/**
 * Implementación real de [MapBytesSource]: streaming manual con OkHttp (client propio, con
 * timeouts largos para un archivo de ~900 MB). GitHub Releases responde 302 a S3 — OkHttp sigue
 * redirects por default, sin código extra acá.
 */
class OkHttpMapBytesSource(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(DOWNLOAD_TIMEOUT_MINUTES, TimeUnit.MINUTES)
        .readTimeout(DOWNLOAD_TIMEOUT_MINUTES, TimeUnit.MINUTES)
        .build(),
) : MapBytesSource {

    override suspend fun download(
        url: String,
        destination: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit,
    ) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} descargando el mapa offline")
            }
            val body = response.body ?: throw IOException("Respuesta sin cuerpo descargando el mapa offline")
            val totalBytes = body.contentLength().coerceAtLeast(0L)
            body.byteStream().use { input ->
                FileOutputStream(destination).use { output ->
                    copyWithProgress(input, output, totalBytes, onProgress)
                }
            }
        }
    }

    /** Chequea cancelación en cada chunk: es el único punto de suspensión del loop, así que es
     * lo que permite a [MapDownloadService.cancel] cortar una transferencia en curso. */
    private suspend fun copyWithProgress(
        input: InputStream,
        output: FileOutputStream,
        totalBytes: Long,
        onProgress: (Long, Long) -> Unit,
    ) {
        val buffer = ByteArray(CHUNK_SIZE_BYTES)
        var bytesDownloaded = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read == -1) break
            output.write(buffer, 0, read)
            bytesDownloaded += read
            onProgress(bytesDownloaded, totalBytes)
        }
    }
}
