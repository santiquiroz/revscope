package com.revscope.core.obd.diagnostics

import com.revscope.core.obd.connection.Transport
import com.revscope.core.obd.model.DtcCode
import com.revscope.core.obd.model.DtcMode
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.protocol.DtcResponseParser
import com.revscope.core.obd.protocol.DtcServicio
import com.revscope.core.obd.protocol.ReadinessParser
import com.revscope.core.obd.protocol.ResponseParser
import kotlinx.coroutines.CancellationException

data class FreezeFrame(val dtcCausante: String?, val valores: List<ObdReading>)

data class DtcScan(
    val activos: List<DtcCode>,
    val pendientes: List<DtcCode>,
    val permanentes: List<DtcCode>,
    val milEncendida: Boolean?,
    val conteoSegunEcu: Int?,
    val freezeFrame: FreezeFrame?,
    val crudo: Map<String, String>,
    val errores: List<String>,
    val enlacePerdido: Boolean = false,
    val serviciosFallidos: Set<DtcServicio> = emptySet(),
) {
    val todos: List<DtcCode> get() = activos + pendientes + permanentes

    // Una lista de activos vacía solo significa «sin códigos» si el 03 tuvo respuesta válida.
    val activosConfirmados: Boolean get() = DtcServicio.ACTIVOS !in serviciosFallidos
}

data class DtcLectura(
    val servicios: Set<DtcServicio> = DtcServicio.entries.toSet(),
    val freezeFrame: Boolean = true,
)

data class BorradoDtc(
    val respuestaCruda: String?,
    val rechazadoPorCondiciones: Boolean,
    val antes: DtcScan,
    val despues: DtcScan,
)

/**
 * Lectura de diagnóstico completa, pensada para correr dentro de una concesión
 * (`ObdSessionManager.withDiagnosticLease`): 01 01 → 03 → 07 → 0A → 02 02 00 → 02 <pid> 00.
 * Una respuesta inválida se anota en `errores` y no aborta el resto; un fallo del transporte sí
 * corta la secuencia, porque sin '>' del adaptador cada paso restante solo esperaría su timeout.
 */
class DtcReader(private val registry: PidRegistry) {

    suspend fun leer(bt: Transport, opciones: DtcLectura = DtcLectura(), esCan: Boolean? = null): DtcScan =
        Secuencia(bt).run {
            val readiness = pedir("0101")?.let(ReadinessParser::parse)
            val activos = leerServicio(DtcServicio.ACTIVOS, opciones, esCan)
            val pendientes = leerServicio(DtcServicio.PENDIENTES, opciones, esCan)
            val permanentes = leerServicio(DtcServicio.PERMANENTES, opciones, esCan)
            val freezeFrame = if (opciones.freezeFrame) leerFreezeFrame(activos.isNotEmpty()) else null
            DtcScan(
                activos = activos,
                pendientes = pendientes,
                permanentes = permanentes,
                milEncendida = readiness?.milOn,
                conteoSegunEcu = readiness?.dtcCount,
                freezeFrame = freezeFrame,
                crudo = crudo.toMap(),
                errores = errores.toList(),
                enlacePerdido = enlaceCaido,
                serviciosFallidos = serviciosFallidos.toSet(),
            )
        }

    suspend fun borrar(bt: Transport, esCan: Boolean? = null): BorradoDtc {
        val soloActivos = DtcLectura(servicios = setOf(DtcServicio.ACTIVOS), freezeFrame = false)
        val antes = leer(bt, soloActivos, esCan)
        val respuesta = Secuencia(bt).pedir("04")
        return BorradoDtc(
            respuestaCruda = respuesta,
            rechazadoPorCondiciones = respuesta?.let(::esRechazoPorCondiciones) == true,
            antes = antes,
            despues = leer(bt, soloActivos, esCan),
        )
    }

    private suspend fun Secuencia.leerServicio(
        servicio: DtcServicio,
        opciones: DtcLectura,
        esCan: Boolean?,
    ): List<DtcCode> {
        if (servicio !in opciones.servicios) return emptyList()
        val raw = pedir(servicio.comando)
        if (raw == null || esFallo(raw)) serviciosFallidos += servicio
        if (raw == null) return emptyList()
        return DtcResponseParser.parse(raw, servicio, esCan).map { DtcCode(code = it, mode = modoDe(servicio)) }
    }

    private suspend fun Secuencia.leerFreezeFrame(hayActivos: Boolean): FreezeFrame? {
        val causante = pedir("020200")?.let(::causanteDe)
        val sinFrame = causante == Causante.Ninguno || (causante == null && !hayActivos)
        if (sinFrame) return null
        val dtc = (causante as? Causante.Codigo)?.codigo
        val valores = pidsDeFreezeFrame().mapNotNull { leerValorCongelado(it) }
        return FreezeFrame(dtcCausante = dtc, valores = valores).takeIf { dtc != null || valores.isNotEmpty() }
    }

    private suspend fun Secuencia.leerValorCongelado(pid: String): ObdReading? {
        val raw = pedir("02${pid}00") ?: return null
        val bytes = ResponseParser.parseFreezeFramePid(raw, pid) ?: return null
        return registry.evaluate(pid, bytes)
    }

    private fun pidsDeFreezeFrame(): List<String> =
        FREEZE_FRAME_PIDS.filter { registry.getDefinition(it) != null && registry.isSupported(it) }

    private sealed interface Causante {
        data object Ninguno : Causante
        data class Codigo(val codigo: String) : Causante
    }

    private fun causanteDe(raw: String): Causante? {
        val bytes = ResponseParser.parseFreezeFramePid(raw, "02") ?: return null
        if (bytes.size < 2) return null
        val high = bytes[0].toInt() and 0xFF
        val low = bytes[1].toInt() and 0xFF
        if (high == 0 && low == 0) return Causante.Ninguno
        return Causante.Codigo(DtcResponseParser.decodificarPar(high, low))
    }

    private fun esRechazoPorCondiciones(raw: String): Boolean =
        ResponseParser.cleanResponse(raw).contains("7F0422")

    private fun modoDe(servicio: DtcServicio): DtcMode = when (servicio) {
        DtcServicio.ACTIVOS -> DtcMode.Active
        DtcServicio.PENDIENTES -> DtcMode.Pending
        DtcServicio.PERMANENTES -> DtcMode.Permanent
    }

    /** Estado de UNA secuencia: respuestas crudas, errores y si el enlace dejó de responder. */
    private class Secuencia(private val bt: Transport) {
        val crudo = linkedMapOf<String, String>()
        val errores = mutableListOf<String>()
        val serviciosFallidos = mutableSetOf<DtcServicio>()
        var enlaceCaido = false
            private set

        suspend fun pedir(comando: String): String? {
            if (enlaceCaido) return null
            val raw = intercambiar(comando) ?: return null
            crudo[comando] = ResponseParser.cleanResponse(raw)
            if (esFallo(raw)) errores += "$comando: ${ResponseParser.cleanResponse(raw)}"
            return raw
        }

        private suspend fun intercambiar(comando: String): String? = try {
            bt.exchange("$comando\r", TIMEOUT_MS)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            enlaceCaido = true
            errores += "$comando: sin respuesta del adaptador (${e.message ?: e.javaClass.simpleName})"
            null
        }
    }

    companion object {
        private const val TIMEOUT_MS = 5_000L

        // RPM, velocidad, refrigerante, carga, mariposa, trims cortos/largos, MAP, IAT; más pedal D/E
        val FREEZE_FRAME_PIDS = listOf("0C", "0D", "05", "04", "11", "06", "07", "0B", "0F", "49", "4A")
    }
}

// NO DATA es una respuesta válida: muchas ECU la dan en vez de 43 00 cuando no hay códigos.
private fun esFallo(raw: String): Boolean = ResponseParser.isErrorResponse(raw) && !ResponseParser.isNoData(raw)
