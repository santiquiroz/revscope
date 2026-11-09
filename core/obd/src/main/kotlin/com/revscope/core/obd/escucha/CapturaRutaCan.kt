package com.revscope.core.obd.escucha

import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.session.ObdSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

sealed interface EstadoCapturaRuta {
    data object Inactiva : EstadoCapturaRuta

    data class Capturando(
        val etiqueta: String,
        val inicioEpochMs: Long,
        val tramas: Int,
        val desbordes: Int,
        val velocidadGpsKmh: Float?,
    ) : EstadoCapturaRuta

    data class Terminada(val id: String, val tramas: Int, val muestrasGps: Int, val duracionMs: Long) : EstadoCapturaRuta

    data class Fallo(val mensaje: String) : EstadoCapturaRuta
}

@Singleton
class CapturaRutaCan internal constructor(
    private val sessionManager: ObdSessionManager,
    private val servicio: ServicioEscuchaCan,
    private val repositorio: CapturasCanRepositorio,
    private val gps: MuestreadorGpsCaptura,
    private val resolver: StringResolver,
    private val ahora: () -> Long,
    private val scope: CoroutineScope,
) {

    @Inject
    constructor(
        sessionManager: ObdSessionManager,
        servicio: ServicioEscuchaCan,
        repositorio: CapturasCanRepositorio,
        gps: MuestreadorGpsCaptura,
        resolver: StringResolver,
    ) : this(
        sessionManager, servicio, repositorio, gps, resolver,
        System::currentTimeMillis, CoroutineScope(SupervisorJob() + Dispatchers.Default),
    )

    private val _estado = MutableStateFlow<EstadoCapturaRuta>(EstadoCapturaRuta.Inactiva)
    val estado: StateFlow<EstadoCapturaRuta> = _estado.asStateFlow()

    private var trabajo: Job? = null
    private val detenerPedido = AtomicBoolean(false)
    private val tramas = AtomicInteger(0)
    private val desbordes = AtomicInteger(0)

    @Synchronized
    fun iniciar(etiqueta: String): Result<Unit> {
        motivoRechazo()?.let { motivo ->
            _estado.value = EstadoCapturaRuta.Fallo(motivo)
            return Result.failure(IllegalStateException(motivo))
        }
        val nombre = etiqueta.trim().ifEmpty { ETIQUETA_POR_DEFECTO }
        val inicio = ahora()
        detenerPedido.set(false)
        tramas.set(0)
        desbordes.set(0)
        gps.iniciar(inicio)
        _estado.value = EstadoCapturaRuta.Capturando(nombre, inicio, 0, 0, null)
        trabajo = scope.launch { capturar(nombre, inicio) }
        return Result.success(Unit)
    }

    fun detener() {
        detenerPedido.set(true)
    }

    // El estado no sirve de guarda: un rechazo lo pasa a Fallo mientras la captura sigue.
    private fun motivoRechazo(): String? = when {
        trabajo?.isActive == true -> resolver.get(R.string.obd_capture_route_already_active)
        sessionManager.connectionState.value !is ConnectionState.Connected ->
            resolver.get(R.string.obd_capture_route_not_connected)
        else -> servicio.motivoRechazo()
    }

    private suspend fun capturar(etiqueta: String, inicio: Long) = coroutineScope {
        val reporte = launch { reportarProgreso(etiqueta, inicio) }
        val resultado = try {
            servicio.capturar(opciones(etiqueta, inicio))
        } finally {
            reporte.cancelAndJoin()
        }
        val muestras = withContext(NonCancellable) { gps.detener() }
        _estado.value = resultado.fold(
            onSuccess = { guardar(it, rebasar(muestras, inicio, it.inicioEpochMs)) },
            onFailure = { EstadoCapturaRuta.Fallo(it.message ?: resolver.get(R.string.obd_capture_route_failed)) },
        )
    }

    private fun opciones(etiqueta: String, inicio: Long) = OpcionesEscucha(
        etiqueta = etiqueta,
        duracionMs = MAX_RUTA_MS,
        filtro = null,
        detener = { detenerPedido.get() || ahora() - inicio >= MAX_RUTA_MS },
        alProgreso = { t, d ->
            tramas.set(t)
            desbordes.set(d)
        },
    )

    private suspend fun reportarProgreso(etiqueta: String, inicio: Long) {
        while (true) {
            delay(INTERVALO_ESTADO_MS)
            _estado.value = EstadoCapturaRuta.Capturando(
                etiqueta, inicio, tramas.get(), desbordes.get(), gps.velocidadActualKmh,
            )
        }
    }

    private fun guardar(captura: CapturaCan, muestras: List<MuestraGps>): EstadoCapturaRuta =
        runCatching {
            val final = captura.copy(origen = CapturaCan.ORIGEN_APP, muestrasGps = muestras.size)
            repositorio.guardar(final, muestras)
            EstadoCapturaRuta.Terminada(final.id, final.totalTramas, muestras.size, final.duracionMs)
        }.getOrElse { EstadoCapturaRuta.Fallo(it.message ?: resolver.get(R.string.obd_capture_route_save_failed)) }

    // El GPS arranca antes que el monitor; sus tiempos se pasan al inicio real de la captura.
    private fun rebasar(muestras: List<MuestraGps>, inicioGps: Long, inicioCaptura: Long): List<MuestraGps> =
        muestras.map { it.copy(tMs = it.tMs + inicioGps - inicioCaptura) }.filter { it.tMs >= 0 }

    companion object {
        const val MAX_RUTA_MS = 15 * 60 * 1_000L
        const val ETIQUETA_POR_DEFECTO = "ruta"
        private const val INTERVALO_ESTADO_MS = 500L
    }
}
