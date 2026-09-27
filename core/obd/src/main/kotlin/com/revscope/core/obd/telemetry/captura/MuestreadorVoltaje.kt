package com.revscope.core.obd.telemetry.captura

import com.revscope.core.obd.connection.Transport
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.obd.session.VoltagePoller
import com.revscope.core.obd.taller.pruebas.CapturaPrueba
import com.revscope.core.obd.telemetry.PollingGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import timber.log.Timber
import java.io.IOException

/**
 * Ráfaga de AT RV para la prueba de batería: la captura rápida solo lee PIDs de modo 01 y el voltaje lo responde
 * el propio ELM. Repite AT RV lo más rápido que deja el adaptador (timeout de 300 ms), cada petición bajo la
 * compuerta del sondeo, con el programador y el VoltagePoller en pausa y el mismo turno de «una captura a la
 * vez» que la captura rápida. Un adaptador que deja de responder se reporta como enlace perdido.
 */
class MuestreadorVoltaje(
    private val enlace: EnlaceCaptura,
    private val gate: PollingGate,
    private val turno: TurnoCaptura,
    private val nuevoSumidero: () -> SumideroCaptura,
    private val relojNanos: () -> Long = System::nanoTime,
    private val relojEpochMs: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : CapturaPrueba, PaginasCaptura {

    private class Sesion(
        val inicio: InicioCaptura,
        val inicioEpochMs: Long,
        val inicioNanos: Long,
        val transporte: Transport,
        val buffer: FastCaptureBuffer,
        val sumidero: SumideroCaptura,
    ) {
        var lecturas = 0L
        var fallasSeguidas = 0
        val latenciasMs = mutableListOf<Double>()
    }

    private class DetencionMuestreo(val motivo: String) : Exception(motivo)

    private val arranque = Mutex()
    private var job: Job? = null
    @Volatile private var sesion: Sesion? = null
    @Volatile private var motivoFin: String? = null

    private val _ultimoResumen = MutableStateFlow<ResumenCaptura?>(null)
    override val ultimoResumen: StateFlow<ResumenCaptura?> = _ultimoResumen.asStateFlow()

    override suspend fun iniciar(config: ConfigCaptura): Result<InicioCaptura> = arranque.withLock {
        if (job?.isActive == true) return Result.failure(IllegalStateException("Ya hay una ráfaga de voltaje activa"))
        val bt = enlace.transporte()
        val scope = enlace.scopeEnlace()
        if (bt == null || scope == null) return Result.failure(IllegalStateException("Sin adaptador conectado"))
        if (!turno.tomar(TurnoCaptura.RAFAGA_VOLTAJE)) return Result.failure(IllegalStateException(TurnoCaptura.ocupado(turno.ocupadoPor)))
        val nueva = try {
            pausar(true)
            abrir(bt, config)
        } catch (e: Throwable) {
            soltarTodo()
            if (e is CancellationException) throw e
            return Result.failure(e)
        }
        sesion = nueva
        motivoFin = null
        job = scope.launch { ejecutar(nueva) }
        Result.success(nueva.inicio)
    }

    override suspend fun detener(motivo: String): ResumenCaptura? {
        val activo = job?.takeIf { it.isActive } ?: return null
        if (motivoFin == null) motivoFin = motivo
        activo.cancelAndJoin()
        return _ultimoResumen.value
    }

    override fun activa(): Boolean = job?.isActive == true

    override fun transcurridoMs(): Long? = sesion?.takeIf { activa() }?.let(::transcurridoMs)

    override fun muestrasActuales(): List<MuestraCaptura> = sesion?.buffer?.todas().orEmpty()

    override fun capturaEnMemoria(): InicioCaptura? = sesion?.inicio

    override fun pagina(id: String, desdeSeq: Long, max: Int, pids: Set<String>?): PaginaCaptura? =
        sesion?.takeIf { it.inicio.id == id }?.buffer?.leerDesde(desdeSeq, max, pids)

    // ── Arranque ────────────────────────────────────────────────────────────

    // La primera lectura confirma que el adaptador responde a AT RV antes de dar la prueba por empezada.
    private suspend fun abrir(bt: Transport, config: ConfigCaptura): Sesion {
        val primera = gate.sondear { leer(bt, TIMEOUT_PRIMERA_MS) }
            ?: throw IllegalStateException("El adaptador no responde a AT RV: sin voltaje no hay prueba de batería")
        val inicioEpochMs = relojEpochMs()
        val inicio = InicioCaptura(
            id = "rv-$inicioEpochMs",
            pidsAceptados = listOf(PID),
            pidsNoSoportados = emptyList(),
            lotes = listOf(listOf(PID)),
            tecnicas = emptySet(),
            duracionMaxMs = config.duracionMaxMs.coerceIn(LimitesCaptura.MIN_DURACION_MS, LimitesCaptura.MAX_DURACION_MS),
        )
        val sumidero = nuevoSumidero().also {
            it.abrir(CapturaCsv.lineasIniciales(CapturaCsv.metadatos(enlace.info(), listOf(PID), inicioEpochMs, emptySet())))
        }
        val s = Sesion(inicio, inicioEpochMs, relojNanos(), bt, FastCaptureBuffer(listOf(PID)), sumidero)
        registrar(s, primera, latenciaMs = 0.0)
        return s
    }

    // ── Ejecución ───────────────────────────────────────────────────────────

    private suspend fun ejecutar(s: Sesion) {
        try {
            coroutineScope {
                launch { vigilar(s) }
                while (true) {
                    medir(s)
                    yield()
                }
            }
        } catch (e: DetencionMuestreo) {
            motivoFin = motivoFin ?: e.motivo
        } catch (e: CancellationException) {
            motivoFin = motivoFin ?: CapturaRapida.MOTIVO_ENLACE
            throw e
        } catch (e: Exception) {
            Timber.w(e, "MuestreadorVoltaje: terminó por error")
            motivoFin = motivoFin ?: "${CapturaRapida.MOTIVO_ENLACE}: ${e.message}"
        } finally {
            withContext(NonCancellable) { cerrar(s) }
        }
    }

    private suspend fun medir(s: Sesion) {
        val t0 = relojNanos()
        val voltios = gate.sondear { leer(s.transporte, TIMEOUT_MS) }
        val latenciaMs = (relojNanos() - t0) / 1_000_000.0
        if (voltios != null) return registrar(s, voltios, latenciaMs)
        s.fallasSeguidas++
        if (s.fallasSeguidas >= MAX_FALLAS_SEGUIDAS) throw DetencionMuestreo(MOTIVO_SIN_RESPUESTA)
    }

    // null = sin respuesta útil (timeout o texto sin voltaje); un transporte cerrado sí corta la ráfaga.
    private suspend fun leer(bt: Transport, timeoutMs: Long): Double? = try {
        VoltagePoller.parseVoltage(bt.exchange(COMANDO, timeoutMs))
    } catch (e: IOException) {
        if (!bt.isConnected) throw e
        null
    }

    private fun registrar(s: Sesion, voltios: Double, latenciaMs: Double) {
        s.fallasSeguidas = 0
        val tMicros = (relojNanos() - s.inicioNanos) / 1_000
        val numero = s.lecturas++
        s.buffer.agregar(tMicros, PID, voltios, numero, latenciaMs.toInt())
        synchronized(s.latenciasMs) { s.latenciasMs += latenciaMs }
        enlace.publicar(ObdReading(PID, voltios, UNIDAD))
        val muestra = MuestraCaptura(numero, tMicros, PID, voltios, numero, latenciaMs.toInt())
        s.sumidero.escribir(listOf(CapturaCsv.lineaLarga(muestra, s.inicioEpochMs, null)))
    }

    private suspend fun vigilar(s: Sesion) {
        while (true) {
            delay(TICK_MS)
            withContext(io) { s.sumidero.volcar() }
            if (transcurridoMs(s) >= s.inicio.duracionMaxMs) throw DetencionMuestreo(MOTIVO_DURACION)
        }
    }

    private fun transcurridoMs(s: Sesion): Long = (relojNanos() - s.inicioNanos) / 1_000_000

    // ── Cierre ──────────────────────────────────────────────────────────────

    private suspend fun cerrar(s: Sesion) {
        val ruta = runCatching { withContext(io) { s.sumidero.cerrar() } }.getOrNull()
        soltarTodo()
        _ultimoResumen.value = resumen(s, ruta)
    }

    private fun pausar(pausado: Boolean) {
        enlace.pausarSondeo(pausado)
        enlace.pausarVoltaje(pausado)
    }

    private fun soltarTodo() {
        pausar(false)
        turno.soltar(TurnoCaptura.RAFAGA_VOLTAJE)
    }

    private fun resumen(s: Sesion, ruta: String?): ResumenCaptura {
        val duracionMs = transcurridoMs(s)
        val valores = s.buffer.todas().map { it.valor }
        val latencias = synchronized(s.latenciasMs) { s.latenciasMs.sorted() }
        val porPid = if (valores.isEmpty()) emptyList() else listOf(
            ResumenPid(PID, valores.size, if (duracionMs > 0) valores.size * 1_000.0 / duracionMs else 0.0, valores.min(), valores.max(), valores.average()),
        )
        return ResumenCaptura(
            id = s.inicio.id,
            duracionMs = duracionMs,
            porPid = porPid,
            latenciaP50Ms = RateMeter.percentil(latencias, 0.50),
            latenciaP95Ms = RateMeter.percentil(latencias, 0.95),
            motivoFin = motivoFin ?: CapturaRapida.MOTIVO_USUARIO,
            rutaCsv = ruta,
            guiada = true,
        )
    }

    companion object {
        const val PID = ObdSessionManager.VBAT_PID
        const val TIMEOUT_MS = 300L
        const val MAX_FALLAS_SEGUIDAS = 10
        const val MOTIVO_SIN_RESPUESTA = "${CapturaRapida.MOTIVO_ENLACE}: el adaptador dejó de responder a AT RV"
        const val MOTIVO_DURACION = "duración máxima alcanzada"
        private const val COMANDO = "AT RV\r"
        private const val UNIDAD = "V"
        private const val TIMEOUT_PRIMERA_MS = 1_000L
        private const val TICK_MS = 1_000L
    }
}
