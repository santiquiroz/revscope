package com.revscope.core.maps

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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

    /**
     * `Call.execute()`/`InputStream.read()` son bloqueantes: un chequeo cooperativo entre chunks
     * solo corta cuando el loop llega a chequearlo, pero un read() colgado en un socket muerto lo
     * retiene hasta el readTimeout (30 min) aunque la corrutina ya esté cancelada.
     * `suspendCancellableCoroutine` + `invokeOnCancellation` es el mecanismo público de
     * kotlinx.coroutines para este caso: `call.cancel()` cierra el socket apenas se cancela la
     * corrutina, sin esperar al próximo chequeo del loop de streaming.
     */
    override suspend fun download(
        url: String,
        destination: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit,
    ) {
        suspendCancellableCoroutine<Unit> { continuation ->
            val call = client.newCall(Request.Builder().url(url).build())
            continuation.invokeOnCancellation { call.cancel() }
            try {
                executeAndStream(call, destination, onProgress) { continuation.isActive }
                if (continuation.isActive) continuation.resume(Unit)
            } catch (e: Throwable) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
        }
    }

    private fun executeAndStream(
        call: Call,
        destination: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit,
        isActive: () -> Boolean,
    ) {
        call.execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} descargando el mapa offline")
            }
            val body = response.body ?: throw IOException("Respuesta sin cuerpo descargando el mapa offline")
            val totalBytes = body.contentLength().coerceAtLeast(0L)
            body.byteStream().use { input ->
                FileOutputStream(destination).use { output ->
                    copyWithProgress(input, output, totalBytes, onProgress, isActive)
                }
            }
        }
    }

    /** Chequeo cooperativo adicional al corte a nivel red de [download]: cubre el caso liviano
     * (nada bloqueado, el chequeo alcanza) sin depender solo de call.cancel(). */
    private fun copyWithProgress(
        input: InputStream,
        output: FileOutputStream,
        totalBytes: Long,
        onProgress: (Long, Long) -> Unit,
        isActive: () -> Boolean,
    ) {
        val buffer = ByteArray(CHUNK_SIZE_BYTES)
        var bytesDownloaded = 0L
        while (true) {
            if (!isActive()) throw IOException("Descarga cancelada")
            val read = input.read(buffer)
            if (read == -1) break
            output.write(buffer, 0, read)
            bytesDownloaded += read
            onProgress(bytesDownloaded, totalBytes)
        }
    }
}
