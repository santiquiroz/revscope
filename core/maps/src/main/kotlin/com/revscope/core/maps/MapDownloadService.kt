package com.revscope.core.maps

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Gestiona el `.pmtiles` offline de Colombia en disco: descarga streaming con progreso, borrado
 * y estado observable vía [state]. El wiring Android real (Context → [mapsDir], detección de
 * WiFi real) llega en T4/T5 — acá el constructor solo pide dependencias explícitas para poder
 * testear todo sin Android ni red real (ver [MapBytesSource]).
 *
 * Descarga a `colombia.pmtiles.part` en el mismo directorio y hace un rename atómico al
 * completar: nunca expone un archivo parcial con el nombre final. Un `.part` huérfano de un
 * intento anterior (p. ej. app matada a mitad) se borra al arrancar la siguiente descarga — v1
 * no soporta resume por rangos.
 */
class MapDownloadService(
    private val mapsDir: File,
    private val isOnWifi: () -> Boolean,
    private val bytesSource: MapBytesSource = OkHttpMapBytesSource(),
    private val usableSpaceBytes: () -> Long = { mapsDir.usableSpace },
) {

    companion object {
        const val PMTILES_URL =
            "https://github.com/santiquiroz/revscope/releases/download/tiles-v1/colombia.pmtiles"

        /** Tamaño real del extracto (ver docs/superpowers/plans/2026-08-20-pmtiles-colombia.md).
         * Solo se usa para el chequeo de espacio previo — el progreso real usa Content-Length. */
        const val EXPECTED_SIZE_BYTES = 913_398_631L
    }

    private val targetFile = File(mapsDir, MapStyleProvider.PMTILES_FILE_NAME)
    private val partFile = File(mapsDir, "${MapStyleProvider.PMTILES_FILE_NAME}.part")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // `lock` serializa las escrituras de estado terminal para que un cancel()/delete() disparado
    // por el caller nunca quede pisado por una escritura tardía de la corrutina de descarga que
    // ya debería estar muerta (y viceversa) — sin esto, `cancel corta la descarga...` es flaky:
    // la corrutina puede alcanzar a escribir un `Downloading` más justo después del Idle de
    // cancel(). publishState compara identidad contra downloadJob bajo el mismo lock, así que
    // solo el intento "vigente" logra publicar.
    private val lock = Any()
    private var downloadJob: Job? = null
    private var lastEmittedPercent = -1

    private val _state = MutableStateFlow<MapDownloadState>(idleStateFromDisk())
    val state: StateFlow<MapDownloadState> = _state.asStateFlow()

    /** Arranca la descarga si no hay una en curso ya; no-op si ya está descargando. */
    fun download(allowCellular: Boolean) {
        synchronized(lock) {
            if (downloadJob?.isActive == true) return
            downloadJob = scope.launch { runDownload(allowCellular) }
        }
    }

    /** Corta la descarga en curso (no-op si no había ninguna) y borra el `.part`. */
    fun cancel() {
        synchronized(lock) {
            downloadJob?.cancel()
            downloadJob = null
            partFile.delete()
            _state.value = idleStateFromDisk()
        }
    }

    /** Borra el mapa ya descargado (y cualquier `.part` residual) y corta una descarga activa. */
    fun delete() {
        synchronized(lock) {
            downloadJob?.cancel()
            downloadJob = null
            partFile.delete()
            targetFile.delete()
            _state.value = idleStateFromDisk()
        }
    }

    /** Re-lee el disco y publica el estado Idle correspondiente. No pisa una descarga activa. */
    fun refresh() {
        synchronized(lock) {
            if (downloadJob?.isActive == true) return
            _state.value = idleStateFromDisk()
        }
    }

    private suspend fun runDownload(allowCellular: Boolean) {
        val myJob = currentCoroutineContext()[Job]
        mapsDir.mkdirs()
        partFile.delete() // .part huérfano de un intento anterior — v1 no hace resume.
        if (MapDownloadDecider.shouldBlockOnCellular(allowCellular, isOnWifi())) {
            publishState(
                myJob,
                MapDownloadState.Error("Descarga bloqueada: conectate a WiFi o permití datos móviles en Ajustes"),
            )
            return
        }
        if (!MapDownloadDecider.canStart(usableSpaceBytes(), EXPECTED_SIZE_BYTES)) {
            publishState(myJob, MapDownloadState.Error("Espacio insuficiente para descargar el mapa offline"))
            return
        }
        lastEmittedPercent = -1
        publishState(myJob, MapDownloadState.Downloading(0f, 0L, EXPECTED_SIZE_BYTES))
        try {
            bytesSource.download(PMTILES_URL, partFile) { downloaded, total -> emitProgress(myJob, downloaded, total) }
            promote(myJob)
        } catch (e: CancellationException) {
            partFile.delete()
            throw e
        } catch (e: Exception) {
            partFile.delete()
            publishState(
                myJob,
                MapDownloadState.Error(e.message?.takeIf { it.isNotBlank() } ?: "Error de red descargando el mapa offline"),
            )
        }
    }

    private fun emitProgress(myJob: Job?, bytesDownloaded: Long, totalBytes: Long) {
        val progress = if (totalBytes > 0) (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
        val percent = (progress * 100).toInt()
        val isFinalChunk = totalBytes > 0 && bytesDownloaded >= totalBytes
        if (percent == lastEmittedPercent && !isFinalChunk) return
        lastEmittedPercent = percent
        publishState(myJob, MapDownloadState.Downloading(progress, bytesDownloaded, totalBytes))
    }

    private fun promote(myJob: Job?) {
        val renameOk = partFile.renameTo(targetFile)
        when (MapDownloadDecider.promoteOutcome(renameOk)) {
            MapDownloadPromoteOutcome.PROMOTED -> publishState(myJob, idleStateFromDisk())
            MapDownloadPromoteOutcome.RENAME_FAILED -> {
                partFile.delete()
                publishState(myJob, MapDownloadState.Error("No se pudo finalizar la descarga del mapa offline"))
            }
        }
    }

    /** Solo publica si [myJob] sigue siendo la descarga vigente — evita que una corrutina ya
     * cancelada/reemplazada pise el estado terminal que cancel()/delete() ya declaró. */
    private fun publishState(myJob: Job?, newState: MapDownloadState) {
        synchronized(lock) {
            if (downloadJob !== myJob) return
            _state.value = newState
        }
    }

    private fun idleStateFromDisk(): MapDownloadState.Idle {
        val exists = targetFile.isFile
        return MapDownloadState.Idle(
            exists = exists,
            sizeBytes = if (exists) targetFile.length() else 0L,
            downloadedAtMs = if (exists) targetFile.lastModified() else null,
        )
    }
}
