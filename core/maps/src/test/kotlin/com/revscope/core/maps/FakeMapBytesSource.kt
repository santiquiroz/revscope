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
 */
class FakeMapBytesSource(
    private val totalBytes: Long,
    private val chunkBytes: Long = totalBytes,
    private val delayPerChunkMs: Long = 0L,
    private val failWith: IOException? = null,
) : MapBytesSource {

    override suspend fun download(
        url: String,
        destination: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit,
    ) {
        failWith?.let { throw it }
        destination.outputStream().use { output ->
            var written = 0L
            while (written < totalBytes) {
                currentCoroutineContext().ensureActive()
                if (delayPerChunkMs > 0) delay(delayPerChunkMs)
                val next = minOf(chunkBytes, totalBytes - written)
                output.write(ByteArray(next.toInt()))
                written += next
                onProgress(written, totalBytes)
            }
        }
    }
}
