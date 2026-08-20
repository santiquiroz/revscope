package com.revscope.core.maps

import java.io.File

/**
 * Fuente de bytes para la descarga del mapa offline, streaming directo a disco. Real:
 * [OkHttpMapBytesSource] contra GitHub Releases. En tests se reemplaza por un fake en memoria —
 * sin red ni servidor de prueba.
 */
interface MapBytesSource {

    /**
     * Descarga [url] completo escribiendo en [destination] (se sobreescribe si ya existía).
     * Invoca [onProgress] con los bytes ya escritos y el total conocido (0 si el servidor no
     * manda Content-Length). Lanza [java.io.IOException] ante cualquier falla de red o disco —
     * el caller es quien limpia el archivo parcial.
     */
    suspend fun download(
        url: String,
        destination: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit,
    )
}
