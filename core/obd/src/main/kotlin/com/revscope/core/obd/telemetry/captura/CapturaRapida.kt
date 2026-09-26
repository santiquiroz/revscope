package com.revscope.core.obd.telemetry.captura

import com.revscope.core.obd.connection.Transport
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.pid.PidDefinition
import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.protocol.ProtocolInfo
import com.revscope.core.obd.telemetry.AjusteConcesion
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
import timber.log.Timber

/** Lo que la captura necesita del enlace vivo; [com.revscope.core.obd.session.ObdSessionManager] lo implementa. */
interface EnlaceCaptura {
    fun transporte(): Transport?
    fun scopeEnlace(): CoroutineScope?
    fun pausarSondeo(pausado: Boolean)
    fun publicar(reading: ObdReading)
    fun info(): InfoAdaptador
}

/**
 * Captura rápida tipo escáner profesional: 1 a 6 PIDs a la máxima tasa del adaptador, con los grupos
 * normales en pausa (el bus tiene un solo dueño), guardia de refrigerante cada 5 s, tasa medida,
 * anillo de alta tasa, CSV en ms y salvaguardas. Una sola captura a la vez, compartida por la UI y
 * el MCP. Corre en el scope del enlace: si el enlace muere, la captura termina y todo se revierte.
 */
class CapturaRapida(
    private val enlace: EnlaceCaptura,
    private val gate: PollingGate,
    private val registry: PidRegistry,
    private val nuevoSumidero: () -> SumideroCaptura,
    private val dispositivo: LectorDispositivo,
    private val relojNanos: () -> Long = System::nanoTime,
    private val relojEpochMs: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private class Sesion(
        val inicio: InicioCaptura,
        val inicioEpochMs: Long,
        val defs: List<PidDefinition>,
        val transporte: Transport,
        val poller: FastPoller,
        val tecnicasElm: Set<TecnicaCaptura>,
        val buffer: FastCaptureBuffer,
        val medidor: RateMeter,
        val sumidero: SumideroCaptura,
        val inicioNanos: Long,
    ) {
        val latenciasMs = mutableListOf<Double>()
    }

    private class DetencionCaptura(val motivo: String) : Exception(motivo)

    private val arranque = Mutex()
    private var job: Job? = null
    @Volatile private var sesion: Sesion? = null
    @Volatile private var motivoFin: String? = null

    private val _estado = MutableStateFlow<EstadoCaptura>(EstadoCaptura.Inactiva)
    val estado: StateFlow<EstadoCaptura> = _estado.asStateFlow()

    private val _estadisticas = MutableStateFlow<EstadisticasCaptura?>(null)
    val estadisticas: StateFlow<EstadisticasCaptura?> = _estadisticas.asStateFlow()

    private val _ultimoResumen = MutableStateFlow<ResumenCaptura?>(null)
    val ultimoResumen: StateFlow<ResumenCaptura?> = _ultimoResumen.asStateFlow()

    suspend fun iniciar(config: ConfigCaptura): Result<InicioCaptura> = arranque.withLock {
        if (job?.isActive == true) return Result.failure(IllegalStateException("Ya hay una captura rápida activa"))
        val bt = enlace.transporte()
        val scope = enlace.scopeEnlace()
        if (bt == null || scope == null) return Result.failure(IllegalStateException("Sin adaptador conectado"))
        val (defs, noSoportados) = seleccionar(config.pids).getOrElse { return Result.failure(it) }
        val nueva = prepararSesion(bt, defs, noSoportados, config.duracionMaxMs).getOrElse { return Result.failure(it) }
        sesion = nueva
        motivoFin = null
        _estado.value = EstadoCaptura.Activa(nueva.inicio, nueva.inicioEpochMs, limiteHz = null)
        job = scope.launch { ejecutar(nueva) }
        Result.success(nueva.inicio)
    }

    suspend fun detener(motivo: String = MOTIVO_USUARIO): ResumenCaptura? {
        val activo = job?.takeIf { it.isActive } ?: return null
        if (motivoFin == null) motivoFin = motivo
        activo.cancelAndJoin()
        return _ultimoResumen.value
    }

    /** Página del anillo de la captura [id] (activa o la última); null si ya no está en memoria. */
    fun pagina(id: String, desdeSeq: Long, max: Int, pids: Set<String>?): PaginaCaptura? =
        sesion?.takeIf { it.inicio.id == id }?.buffer?.leerDesde(desdeSeq, max, pids)

    fun inicioEpochMs(id: String): Long? = sesion?.takeIf { it.inicio.id == id }?.inicioEpochMs

    fun muestrasActuales(): List<MuestraCaptura> = sesion?.buffer?.todas().orEmpty()

    fun capturaEnMemoria(): InicioCaptura? = sesion?.inicio

    fun activa(): Boolean = job?.isActive == true

    // ── Arranque ────────────────────────────────────────────────────────────

    private fun seleccionar(pids: List<String>): Result<Pair<List<PidDefinition>, List<String>>> {
        val pedidos = pids.map { it.trim().uppercase() }.filter { it.isNotEmpty() }.distinct()
        if (pedidos.isEmpty()) return Result.failure(IllegalArgumentException("Elige al menos un PID"))
        if (pedidos.size > LimitesCaptura.MAX_PIDS) {
            return Result.failure(IllegalArgumentException("Como máximo ${LimitesCaptura.MAX_PIDS} PIDs a la vez"))
        }
        val (aceptados, rechazados) = pedidos.partition(::esCapturable)
        if (aceptados.isEmpty()) {
            return Result.failure(IllegalArgumentException("Ningún PID pedido está soportado por este vehículo: $rechazados"))
        }
        return Result.success(aceptados.mapNotNull(registry::getDefinition) to rechazados)
    }

    private fun esCapturable(pid: String): Boolean {
        val def = registry.getDefinition(pid) ?: return false
        return def.mode == "01" && registry.isSupported(pid)
    }

    private suspend fun prepararSesion(
        bt: Transport,
        defs: List<PidDefinition>,
        noSoportados: List<String>,
        duracionMaxMs: Long,
    ): Result<Sesion> {
        val info = enlace.info()
        enlace.pausarSondeo(true)
        val tecnicasElm = try {
            aplicarAfinado(bt, ProtocolInfo.esCan11Bit(info.protocoloDpn))
        } catch (e: CancellationException) {
            enlace.pausarSondeo(false)
            throw e
        } catch (e: Exception) {
            enlace.pausarSondeo(false)
            return Result.failure(e)
        }
        runCatching { bt.setLowLatency(true) }
        return Result.success(nuevaSesion(bt, defs, noSoportados, duracionMaxMs, info, tecnicasElm))
    }

    private fun nuevaSesion(
        bt: Transport,
        defs: List<PidDefinition>,
        noSoportados: List<String>,
        duracionMaxMs: Long,
        info: InfoAdaptador,
        tecnicasElm: Set<TecnicaCaptura>,
    ): Sesion {
        val poller = FastPoller(
            exchange = { cmd -> bt.exchange(cmd, TIMEOUT_PETICION_MS) },
            registry = registry,
            relojNanos = relojNanos,
            relojEpochMs = relojEpochMs,
            esCan = info.esCan,
            enCanal = { bloque -> gate.sondear { bloque() } },
        )
        val inicioEpochMs = relojEpochMs()
        val pids = defs.map { it.pid }
        val tecnicas = tecnicasElm + poller.tecnicas() + TecnicaCaptura.BAJA_LATENCIA
        val inicio = InicioCaptura(
            id = "cap-$inicioEpochMs",
            pidsAceptados = pids,
            pidsNoSoportados = noSoportados,
            lotes = poller.lotesPara(defs).map { lote -> lote.map { it.pid } },
            tecnicas = tecnicas,
            duracionMaxMs = duracionMaxMs.coerceIn(LimitesCaptura.MIN_DURACION_MS, LimitesCaptura.MAX_DURACION_MS),
        )
        val sumidero = nuevoSumidero().also {
            it.abrir(CapturaCsv.lineasIniciales(CapturaCsv.metadatos(info, pids, inicioEpochMs, tecnicas)))
        }
        return Sesion(
            inicio, inicioEpochMs, defs, bt, poller, tecnicasElm, FastCaptureBuffer(pids), RateMeter(info.esCan),
            sumidero, relojNanos(),
        )
    }

    /** Afinado y ajuste de concesión bajo el mismo turno del canal: una lectura DTC nunca ve el ELM a medias. */
    private suspend fun aplicarAfinado(bt: Transport, esCan11Bit: Boolean): Set<TecnicaCaptura> {
        val afinado = afinadoDe(bt)
        return gate.sondear {
            afinado.aplicar(esCan11Bit).also { aplicadas ->
                if (TecnicaCaptura.DIRECCION_FISICA in aplicadas) gate.fijarAjusteConcesion(ajusteConcesion(afinado))
            }
        }
    }

    private fun afinadoDe(bt: Transport) = ElmSpeedTuning { cmd -> bt.exchange(cmd, TIMEOUT_AT_MS) }

    private fun ajusteConcesion(afinado: ElmSpeedTuning) = object : AjusteConcesion {
        override suspend fun antesDeConceder() = afinado.quitarDireccionFisica()
        override suspend fun despuesDeConceder() {
            afinado.aplicarDireccionFisica()
        }
    }

    // ── Ejecución ───────────────────────────────────────────────────────────

    private suspend fun ejecutar(s: Sesion) {
        try {
            coroutineScope {
                launch { vigilar(s) }
                s.poller.correr(s.defs, guardias(s.defs), enlace::publicar) { lote -> registrarLote(s, lote) }
            }
        } catch (e: DetencionCaptura) {
            motivoFin = motivoFin ?: e.motivo
        } catch (e: CancellationException) {
            motivoFin = motivoFin ?: MOTIVO_ENLACE
            throw e
        } catch (e: Exception) {
            Timber.w(e, "CapturaRapida: terminó por error")
            motivoFin = motivoFin ?: "$MOTIVO_ENLACE: ${e.message}"
        } finally {
            withContext(NonCancellable) { cerrar(s) }
        }
    }

    private fun guardias(defs: List<PidDefinition>): List<Pair<PidDefinition, Long>> {
        if (defs.any { it.pid == PID_REFRIGERANTE } || !registry.isSupported(PID_REFRIGERANTE)) return emptyList()
        val def = registry.getDefinition(PID_REFRIGERANTE) ?: return emptyList()
        return listOf(def to GUARDIA_REFRIGERANTE_MS)
    }

    private fun registrarLote(s: Sesion, lote: LoteRapido) {
        val tMs = lote.tMicros / 1_000
        val latenciaMs = lote.latenciaMicros / 1_000.0
        s.medidor.registrarPeticion(tMs, latenciaMs, lote.falla)
        synchronized(s.latenciasMs) { s.latenciasMs += latenciaMs }
        val lineas = lote.lecturas.map { r ->
            s.buffer.agregar(lote.tMicros, r.pid, r.value, lote.numero, latenciaMs.toInt())
            s.medidor.registrarMuestra(r.pid, tMs)
            enlace.publicar(r)
            CapturaCsv.lineaLarga(
                MuestraCaptura(0, lote.tMicros, r.pid, r.value, lote.numero, latenciaMs.toInt()),
                s.inicioEpochMs,
                registry.getDefinition(r.pid),
            )
        }
        if (lineas.isNotEmpty()) s.sumidero.escribir(lineas)
    }

    private suspend fun vigilar(s: Sesion) {
        var tick = 0L
        while (true) {
            delay(TICK_MS)
            _estadisticas.value = s.medidor.estadisticas(transcurridoMs(s))
            if (++tick % TICKS_POR_EVALUACION == 0L) evaluar(s)
        }
    }

    private suspend fun evaluar(s: Sesion) {
        withContext(io) { s.sumidero.volcar() }
        val stats = _estadisticas.value ?: return
        val lectura = dispositivo.leer()
        val estado = EstadoDispositivo(
            lectura.bateriaPct, lectura.cargando, lectura.termico, transcurridoMs(s), stats.ratioErrores, stats.peticionesTotales,
        )
        when (val decision = CaptureSafeguards.decidir(estado, s.inicio.duracionMaxMs)) {
            is DecisionSalvaguarda.Detener -> throw DetencionCaptura(decision.motivo)
            is DecisionSalvaguarda.Limitar -> limitar(s, decision.maxHz)
            DecisionSalvaguarda.Continuar -> limitar(s, null)
        }
    }

    private fun limitar(s: Sesion, maxHz: Int?) {
        if (s.poller.maxHz == maxHz) return
        s.poller.maxHz = maxHz
        _estado.value = EstadoCaptura.Activa(s.inicio, s.inicioEpochMs, maxHz)
    }

    private fun transcurridoMs(s: Sesion): Long = (relojNanos() - s.inicioNanos) / 1_000_000

    // ── Cierre ──────────────────────────────────────────────────────────────

    private suspend fun cerrar(s: Sesion) {
        revertirAfinado(s)
        enlace.pausarSondeo(false)
        val ruta = runCatching { withContext(io) { s.sumidero.cerrar() } }.getOrNull()
        _estadisticas.value = s.medidor.estadisticas(transcurridoMs(s))
        _ultimoResumen.value = resumen(s, ruta)
        _estado.value = EstadoCaptura.Inactiva
    }

    private suspend fun revertirAfinado(s: Sesion) {
        runCatching {
            gate.sondear {
                gate.fijarAjusteConcesion(null)
                afinadoDe(s.transporte).revertir(s.tecnicasElm)
            }
        }.onFailure { Timber.w(it, "CapturaRapida: no se pudo revertir el afinado del ELM") }
        runCatching { s.transporte.setLowLatency(false) }
    }

    private fun resumen(s: Sesion, ruta: String?): ResumenCaptura {
        val duracionMs = transcurridoMs(s)
        val latencias = synchronized(s.latenciasMs) { s.latenciasMs.sorted() }
        val porPid = s.buffer.todas().groupBy { it.pid }.map { (pid, muestras) ->
            val valores = muestras.map { it.valor }
            ResumenPid(
                pid = pid,
                n = muestras.size,
                hz = if (duracionMs > 0) muestras.size * 1_000.0 / duracionMs else 0.0,
                min = valores.min(),
                max = valores.max(),
                media = valores.average(),
            )
        }
        return ResumenCaptura(
            id = s.inicio.id,
            duracionMs = duracionMs,
            porPid = porPid,
            latenciaP50Ms = RateMeter.percentil(latencias, 0.50),
            latenciaP95Ms = RateMeter.percentil(latencias, 0.95),
            motivoFin = motivoFin ?: MOTIVO_USUARIO,
            rutaCsv = ruta,
        )
    }

    companion object {
        const val MOTIVO_USUARIO = "detenida por el usuario"
        const val MOTIVO_ENLACE = "enlace perdido"
        private const val PID_REFRIGERANTE = "05"
        private const val GUARDIA_REFRIGERANTE_MS = 5_000L
        private const val TIMEOUT_PETICION_MS = 1_000L
        private const val TIMEOUT_AT_MS = 1_000L
        private const val TICK_MS = 500L
        private const val TICKS_POR_EVALUACION = 2L
    }
}
