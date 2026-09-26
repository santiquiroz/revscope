package com.revscope.core.obd.session

import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Máquina de estados del viaje OBD sobre un enlace vivo. Todas las transiciones van bajo un
 * mutex: la UI, el MCP, el auto-viaje y la pérdida de enlace nunca compiten.
 *
 * [publicarSesion] se llama de forma síncrona en cada apertura/cierre, y el motivo de fin se
 * publica ANTES del null para que el servicio lo lea al recibirlo.
 */
class TripController(
    private val store: SessionStore,
    private val closer: SessionCloser,
    private val launcher: RecordingLauncher,
    private val publicarSesion: (Long?) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val mutex = Mutex()
    private val _estado = MutableStateFlow<EstadoViaje>(EstadoViaje.SinEnlace)
    val estado: StateFlow<EstadoViaje> = _estado.asStateFlow()

    private val _ultimoMotivoFin = MutableStateFlow<MotivoFin?>(null)
    val ultimoMotivoFin: StateFlow<MotivoFin?> = _ultimoMotivoFin.asStateFlow()

    private var grabacion: Job? = null

    /** Enlace recién negociado: arranca el viaje como siempre (conectar = viajar). */
    suspend fun onEnlaceListo(adapterName: String): Long = mutex.withLock {
        cerrarSiGraba(MotivoFin.RECONEXION, EstadoViaje.EnlaceSinViaje)
        abrir(adapterName)
    }

    suspend fun iniciar(adapterName: String): Result<Long> = mutex.withLock {
        when (_estado.value) {
            EstadoViaje.SinEnlace -> Result.failure(ViajeNoDisponibleException("Sin adaptador conectado"))
            is EstadoViaje.Grabando -> Result.failure(ViajeNoDisponibleException("Ya hay un viaje en curso"))
            EstadoViaje.EnlaceSinViaje -> Result.success(abrir(adapterName))
        }
    }

    /** Cierra el viaje y deja el enlace vivo. Sin viaje OBD en curso no hace nada (null). */
    suspend fun finalizar(motivo: MotivoFin): Long? = mutex.withLock {
        cerrarSiGraba(motivo, EstadoViaje.EnlaceSinViaje)
    }

    suspend fun onEnlacePerdido(motivo: MotivoFin): Long? = mutex.withLock {
        cerrarSiGraba(motivo, EstadoViaje.SinEnlace).also { _estado.value = EstadoViaje.SinEnlace }
    }

    private suspend fun abrir(adapterName: String): Long {
        val id = store.create(adapterName)
        grabacion = launcher.launch(id)
        _estado.value = EstadoViaje.Grabando(id, clock())
        publicarSesion(id)
        return id
    }

    private suspend fun cerrarSiGraba(motivo: MotivoFin, destino: EstadoViaje): Long? {
        val grabando = _estado.value as? EstadoViaje.Grabando ?: return null
        grabacion?.cancelAndJoin()
        grabacion = null
        closer.close(grabando.sessionId)
        _ultimoMotivoFin.value = motivo
        _estado.value = destino
        publicarSesion(null)
        return grabando.sessionId
    }
}
