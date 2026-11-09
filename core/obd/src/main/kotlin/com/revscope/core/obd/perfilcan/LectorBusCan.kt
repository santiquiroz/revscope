package com.revscope.core.obd.perfilcan

import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.connection.Transport
import com.revscope.core.obd.escucha.ComandosMonitor
import com.revscope.core.obd.escucha.LineaMonitor
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.protocol.ResponseParser
import com.revscope.core.obd.telemetry.PollingGate
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext

class LectorBusCan(
    private val transporte: Transport,
    private val gate: PollingGate,
    perfil: PerfilCan,
    private val modo: ModoFuente,
    private val resolutor: ResolutorFuentes,
    soloVerificadas: Boolean = true,
    private val ahora: () -> Long,
) {

    private val _sinMonitor = MutableStateFlow(false)
    val sinMonitor: StateFlow<Boolean> = _sinMonitor.asStateFlow()

    private val senales = perfil.senales.filter { !soloVerificadas || it.estado == EstadoSenal.VERIFICADA }
    private val porId: Map<Int, List<SenalCan>> = senales.groupBy { it.id.toLong(16).toInt() }
    private val bits29 = senales.any { it.id.length > 3 }

    fun lecturas(): Flow<ObdReading> = decodificadas { senal, datos ->
        DecodificadorSenal.valor(senal, datos)?.let { ObdReading(senal.clave, it, senal.unidad, ahora()) }
    }

    fun textos(resolver: StringResolver): Flow<String> =
        decodificadas { senal, datos -> DecodificadorSenal.texto(senal, datos, resolver) }

    private fun <T : Any> decodificadas(decodificar: (SenalCan, ByteArray) -> T?): Flow<T> = channelFlow {
        if (senales.isEmpty() || modo == ModoFuente.ESTANDAR) return@channelFlow
        while (true) {
            val inicio = ahora()
            val soportado = gate.conceder(DUENO) { ventana(decodificar) { trySend(it) } }
            if (!soportado) {
                _sinMonitor.value = true
                return@channelFlow
            }
            esperarSiguiente(inicio)
        }
    }.buffer(CAPACIDAD_LECTURAS)

    private suspend fun <T : Any> ventana(decodificar: (SenalCan, ByteArray) -> T?, emitir: (T) -> Unit): Boolean =
        try {
            ComandosMonitor.preparar(transporte)
            transporte.exchange(ComandosMonitor.SILENCIOSO)
            escuchar(decodificar, emitir)
        } finally {
            withContext(NonCancellable) { ComandosMonitor.restaurar(transporte) }
        }

    private suspend fun <T : Any> escuchar(decodificar: (SenalCan, ByteArray) -> T?, emitir: (T) -> Unit): Boolean {
        var rechazado = false
        val final = try {
            transporte.monitorear(ComandosMonitor.MONITOR, duracionVentanaMs()) { linea ->
                if (esRechazo(linea)) rechazado = true else procesar(linea, decodificar, emitir)
            }
        } catch (e: UnsupportedOperationException) {
            return false
        }
        return !rechazado && !esRechazo(final)
    }

    private fun <T : Any> procesar(linea: String, decodificar: (SenalCan, ByteArray) -> T?, emitir: (T) -> Unit) {
        val trama = LineaMonitor.parsear(linea, 0L, bits29) ?: return
        val delId = porId[trama.id] ?: return
        val datos = ByteArray(trama.datos.size) { trama.datos[it].toByte() }
        for (senal in delId) {
            val valor = decodificar(senal, datos) ?: continue
            resolutor.registrarTrama(senal.clave)
            emitir(valor)
        }
    }

    private suspend fun esperarSiguiente(inicio: Long) {
        if (modo == ModoFuente.SOLO_BUS) delay(PAUSA_SOLO_BUS_MS)
        else delay((inicio + INTERVALO_COMBINADO_MS - ahora()).coerceAtLeast(0))
    }

    private fun duracionVentanaMs(): Long =
        if (modo == ModoFuente.SOLO_BUS) VENTANA_SOLO_BUS_MS else ventanaCombinadoMs(senales)

    private fun esRechazo(texto: String): Boolean = ResponseParser.cleanResponse(texto) == "?"

    companion object {
        const val INTERVALO_COMBINADO_MS = 500L
        const val VENTANA_SOLO_BUS_MS = 2_000L
        const val PAUSA_SOLO_BUS_MS = 50L
        private const val TOPE_VENTANA_COMBINADO_MS = 200L
        // Entre ventanas de Solo bus van la restauración, la pausa y la preparación: sin este margen
        // una señal de 100 ms caducaría cada 2 s.
        private const val HUECO_SOLO_BUS_MS = 500L
        private const val DUENO = "bus_can"
        private const val CAPACIDAD_LECTURAS = 256

        fun ventanaCombinadoMs(senales: List<SenalCan>): Long =
            minOf(TOPE_VENTANA_COMBINADO_MS, (1.5 * (senales.maxOfOrNull { it.periodoMs } ?: 0L)).toLong())

        fun intervaloVentanaMs(modo: ModoFuente): Long = when (modo) {
            ModoFuente.ESTANDAR -> 0L
            ModoFuente.COMBINADO -> INTERVALO_COMBINADO_MS
            ModoFuente.SOLO_BUS -> HUECO_SOLO_BUS_MS
        }
    }
}
