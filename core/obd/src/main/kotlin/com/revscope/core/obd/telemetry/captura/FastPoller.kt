package com.revscope.core.obd.telemetry.captura

import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.pid.PidDefinition
import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.protocol.ResponseParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import timber.log.Timber
import java.io.IOException

/**
 * Sondea un conjunto pequeño de PIDs de modo 01 lo más rápido que permita el adaptador: varios PIDs
 * por trama en CAN y sufijo «1» de número de respuestas (el ELM devuelve el control con la primera
 * respuesta en vez de esperar su timeout). Lo que el ELM rechaza se desactiva y se sigue. Tres pares
 * petición+reintento fallidos seguidos terminan en [IOException], como el circuit breaker del scheduler.
 * [enCanal] envuelve cada intercambio con la toma del canal (la PollingGate): el reloj se lee ya
 * dentro, así la espera en cola (una lectura DTC por concesión, el voltaje) no fecha ni infla la muestra.
 */
class FastPoller(
    private val exchange: suspend (String) -> String,
    private val registry: PidRegistry,
    private val relojNanos: () -> Long,
    private val relojEpochMs: () -> Long,
    esCan: Boolean?,
    private val enCanal: suspend (suspend () -> Unit) -> Unit = { bloque -> bloque() },
) {
    private class Intercambio(val respuesta: String?, val error: Exception?, val t0: Long, val t1: Long, val epochT1Ms: Long)

    @Volatile private var multiPid = esCan != false
    @Volatile private var sufijo = true
    @Volatile var maxHz: Int? = null

    private var fallasSeguidas = 0
    private var pausaBufferFullMs = 0L
    private var numeroLote = 0L
    private val inicioNanos = relojNanos()

    fun tecnicas(): Set<TecnicaCaptura> = buildSet {
        if (multiPid) add(TecnicaCaptura.MULTI_PID)
        if (sufijo) add(TecnicaCaptura.SUFIJO_1)
    }

    fun lotesPara(defs: List<PidDefinition>): List<List<PidDefinition>> =
        if (multiPid) empaquetar(defs) else defs.map { listOf(it) }

    /** Corre hasta que lo cancelen o el enlace muera. [guardias] = PIDs de seguridad con su periodo en ms. */
    suspend fun correr(
        defs: List<PidDefinition>,
        guardias: List<Pair<PidDefinition, Long>>,
        onGuardia: (ObdReading) -> Unit,
        emitir: suspend (LoteRapido) -> Unit,
    ): Nothing {
        val ultimaGuardia = LongArray(guardias.size) { Long.MIN_VALUE / 2 }
        while (true) {
            val inicioCiclo = ahoraMs()
            lotesPara(defs).forEach { lote -> emitir(pedir(lote)) }
            guardias.forEachIndexed { i, (def, cadaMs) ->
                if (inicioCiclo - ultimaGuardia[i] >= cadaMs) {
                    ultimaGuardia[i] = inicioCiclo
                    pedir(listOf(def)).lecturas.forEach(onGuardia)
                }
            }
            esperarSiguienteCiclo(inicioCiclo)
        }
    }

    suspend fun pedir(lote: List<PidDefinition>): LoteRapido {
        val primero = intentar(lote)
        val resultado = if (primero.falla == FallaLote.SIN_RESPUESTA) intentar(lote) else primero
        registrarSalud(resultado)
        return resultado
    }

    private suspend fun intentar(lote: List<PidDefinition>): LoteRapido {
        val medido = intercambiarMedido(comando(lote))
        val respuesta = medido.respuesta ?: run {
            Timber.w(medido.error, "FastPoller: sin respuesta a ${lote.map { it.pid }}")
            return lote(medido.t0, medido.t1, emptyList(), FallaLote.SIN_RESPUESTA)
        }
        if (respuesta.contains("?") && sufijo) {
            Timber.i("FastPoller: el ELM rechaza el sufijo de respuestas, se desactiva")
            sufijo = false
            return intentar(lote)
        }
        return interpretar(lote, respuesta, medido)
    }

    private suspend fun intercambiarMedido(cmd: String): Intercambio {
        var medido: Intercambio? = null
        enCanal { medido = intercambiar(cmd) }
        return checkNotNull(medido) { "enCanal no ejecutó el intercambio" }
    }

    private suspend fun intercambiar(cmd: String): Intercambio {
        val t0 = relojNanos()
        return try {
            val respuesta = exchange(cmd)
            Intercambio(respuesta, null, t0, relojNanos(), relojEpochMs())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Intercambio(null, e, t0, relojNanos(), relojEpochMs())
        }
    }

    private fun interpretar(lote: List<PidDefinition>, respuesta: String, medido: Intercambio): LoteRapido {
        val t0 = medido.t0
        val t1 = medido.t1
        fallaDe(respuesta)?.let { falla ->
            if (lote.size > 1) desactivarMultiPidSiRechaza(falla, respuesta)
            return lote(t0, t1, emptyList(), falla)
        }
        val bytes = ResponseParser.parseMultiPidResponse(respuesta, lote.associate { it.pid to it.bytes })
        if (bytes == null && lote.size > 1) {
            Timber.i("FastPoller: el adaptador no acepta varios PIDs por petición, uno por uno")
            multiPid = false
        }
        val epochMs = medido.epochT1Ms - (t1 - t0) / 2 / 1_000_000
        val lecturas = bytes.orEmpty().mapNotNull { (pid, datos) ->
            registry.evaluate(pid, datos)?.copy(timestamp = epochMs)
        }
        return lote(t0, t1, lecturas, if (lecturas.isEmpty()) FallaLote.OTRA else null)
    }

    // NO DATA o «?» a un lote no se puede atribuir a un PID: se sigue uno por uno.
    private fun desactivarMultiPidSiRechaza(falla: FallaLote, respuesta: String) {
        if (falla != FallaLote.NO_DATA && !respuesta.contains("?")) return
        Timber.i("FastPoller: el lote falló ($falla), se sigue uno por uno")
        multiPid = false
    }

    private fun fallaDe(respuesta: String): FallaLote? = when {
        ResponseParser.isNoData(respuesta) -> FallaLote.NO_DATA
        ResponseParser.isBufferFull(respuesta) -> FallaLote.BUFFER_FULL
        ResponseParser.cleanResponse(respuesta).contains("STOPPED") -> FallaLote.STOPPED
        ResponseParser.isErrorResponse(respuesta) -> FallaLote.OTRA
        else -> null
    }

    private fun registrarSalud(lote: LoteRapido) {
        when (lote.falla) {
            FallaLote.SIN_RESPUESTA -> if (++fallasSeguidas >= MAX_FALLAS_SEGUIDAS) {
                throw IOException("Enlace perdido durante la captura rápida: $fallasSeguidas fallas seguidas")
            }
            FallaLote.BUFFER_FULL -> pausaBufferFullMs = (pausaBufferFullMs * 2).coerceIn(PAUSA_MIN_MS, PAUSA_MAX_MS)
            else -> {
                fallasSeguidas = 0
                if (lote.falla == null) pausaBufferFullMs /= 2
            }
        }
    }

    private suspend fun esperarSiguienteCiclo(inicioCiclo: Long) {
        val periodoMinMs = maxHz?.let { 1_000L / it } ?: 0L
        val esperaMs = maxOf(inicioCiclo + periodoMinMs - ahoraMs(), pausaBufferFullMs)
        if (esperaMs > 0) delay(esperaMs) else yield()
    }

    private fun comando(lote: List<PidDefinition>): String {
        val pids = lote.joinToString(" ") { it.pid }
        return if (sufijo) "01 $pids 1\r" else "01 $pids\r"
    }

    private fun lote(t0: Long, t1: Long, lecturas: List<ObdReading>, falla: FallaLote?) = LoteRapido(
        numero = ++numeroLote,
        tMicros = ((t0 + t1) / 2 - inicioNanos) / 1_000,
        latenciaMicros = (t1 - t0) / 1_000,
        lecturas = lecturas,
        falla = falla,
    )

    private fun ahoraMs(): Long = (relojNanos() - inicioNanos) / 1_000_000

    companion object {
        private const val MAX_FALLAS_SEGUIDAS = 3
        private const val PAUSA_MIN_MS = 20L
        private const val PAUSA_MAX_MS = 320L

        // «41» + pares PID+dato en una trama CAN de 7 bytes útiles, igual que el scheduler.
        private const val BYTES_TRAMA = 7

        fun empaquetar(defs: List<PidDefinition>): List<List<PidDefinition>> {
            val lotes = mutableListOf<List<PidDefinition>>()
            var actual = mutableListOf<PidDefinition>()
            var bytes = 1
            for (def in defs) {
                val costo = 1 + def.bytes
                if (actual.isNotEmpty() && bytes + costo > BYTES_TRAMA) {
                    lotes += actual
                    actual = mutableListOf()
                    bytes = 1
                }
                actual += def
                bytes += costo
            }
            if (actual.isNotEmpty()) lotes += actual
            return lotes
        }
    }
}
