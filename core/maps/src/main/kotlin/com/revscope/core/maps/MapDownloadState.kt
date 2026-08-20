package com.revscope.core.maps

/** Estado publicado por [MapDownloadService] mientras gestiona el `.pmtiles` offline en disco. */
sealed interface MapDownloadState {

    /** Sin descarga en curso. [exists] distingue "nunca descargado" de "descargado y listo". */
    data class Idle(
        val exists: Boolean,
        val sizeBytes: Long,
        val downloadedAtMs: Long?,
    ) : MapDownloadState

    /** [progress] en [0f, 1f]; 0f si el servidor no mandó Content-Length ([totalBytes] = 0). */
    data class Downloading(
        val progress: Float,
        val bytesDownloaded: Long,
        val totalBytes: Long,
    ) : MapDownloadState

    data class Error(val message: String) : MapDownloadState
}
