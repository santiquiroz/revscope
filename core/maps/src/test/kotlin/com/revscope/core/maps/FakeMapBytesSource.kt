package com.revscope.core.maps

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException

/**
 * Fake de [MapBytesSource] para tests: sin red ni MockWebServer. Escribe [totalBytes] en
 * [chunkBytes] pasos, chequeando cancelación cooperativa en cada uno (igual que
 * [OkHttpMapBytesSource]) — así [MapDownloadService.cancel] se puede testear de verdad con
 * [delayPerChunkMs] > 0 dándole tiempo al test para cancelar a mitad de camino.
 *
 * [failWith] sin [failAfterChunks] tira ANTES de abrir el stream — el `.part` nunca llega a
 * existir, útil para el caso "la conexión ni arrancó". [failAfterChunks] escribe esa cantidad de
 * chunks reales a disco y recién ahí tira — es el que ejercita la limpieza de un `.part` con
 * bytes de verdad ya escritos (falla real a mitad de descarga).
 */
class FakeMapBytesSource(
    private val totalBytes: Long,
    private val chunkBytes: Long = totalBytes,
    private val delayPerChunkMs: Long = 0L,
    private val failWith: IOException? = null,
    private val failAfterChunks: Int? = null,
) : MapBytesSource {

    override suspend fun download(
        url: String,
        destination: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit,
    ) {
        if (failAfterChunks == null) failWith?.let { throw it }
        destination.outputStream().use { output ->
            var written = 0L
            var chunkIndex = 0
            while (written < totalBytes) {
                currentCoroutineContext().ensureActive()
                if (delayPerChunkMs > 0) delay(delayPerChunkMs)
                val next = minOf(chunkBytes, totalBytes - written)
                output.write(ByteArray(next.toInt()))
                written += next
                chunkIndex++
                onProgress(written, totalBytes)
                if (failAfterChunks != null && chunkIndex >= failAfterChunks) {
                    throw failWith ?: IOException("caida de red simulada tras $chunkIndex chunks")
                }
            }
        }
    }
}
