package com.revscope.core.obd.telemetry.captura

import com.revscope.core.obd.model.ObdReading

/** Técnicas de afinado del ELM que la captura logró aplicar (cada una se omite si el ELM responde «?»). */
enum class TecnicaCaptura(val clave: String) {
    MULTI_PID("multipid"),
    SUFIJO_1("sufijo1"),
    DIRECCION_FISICA("7E0"),
    TIMING_AGRESIVO("AT2"),
    BAJA_LATENCIA("baja_latencia"),
}

enum class FallaLote { NO_DATA, BUFFER_FULL, STOPPED, SIN_RESPUESTA, OTRA }

/** Una petición de la captura: todos sus PIDs comparten el instante (punto medio envío-recepción). */
data class LoteRapido(
    val numero: Long,
    val tMicros: Long,
    val latenciaMicros: Long,
    val lecturas: List<ObdReading>,
    val falla: FallaLote? = null,
)

data class InfoAdaptador(
    val nombre: String?,
    val elm: String?,
    val protocoloDpn: String?,
    val esCan: Boolean?,
)

// [vigilar]: PIDs de seguridad (p. ej. la velocidad) que se leen una vez por segundo sin entrar al anillo.
data class ConfigCaptura(
    val pids: List<String>,
    val duracionMaxMs: Long,
    val vigilar: List<String> = emptyList(),
    val guiada: Boolean = false,
)

data class InicioCaptura(
    val id: String,
    val pidsAceptados: List<String>,
    val pidsNoSoportados: List<String>,
    val lotes: List<List<String>>,
    val tecnicas: Set<TecnicaCaptura>,
    val duracionMaxMs: Long,
)

data class ResumenPid(val pid: String, val n: Int, val hz: Double, val min: Double, val max: Double, val media: Double)

data class ResumenCaptura(
    val id: String,
    val duracionMs: Long,
    val porPid: List<ResumenPid>,
    val latenciaP50Ms: Double?,
    val latenciaP95Ms: Double?,
    val motivoFin: String,
    val rutaCsv: String?,
    val guiada: Boolean = false,
)

sealed interface EstadoCaptura {
    data object Inactiva : EstadoCaptura
    data class Activa(val inicio: InicioCaptura, val inicioEpochMs: Long, val limiteHz: Int?) : EstadoCaptura
}

// Lo que la UI lee de una captura en memoria (la rápida o la ráfaga de voltaje) para dibujarla en vivo.
interface PaginasCaptura {
    fun capturaEnMemoria(): InicioCaptura?
    fun pagina(id: String, desdeSeq: Long, max: Int, pids: Set<String>?): PaginaCaptura?
}

object LimitesCaptura {
    const val MAX_PIDS = 6
    const val MIN_DURACION_MS = 1_000L
    const val MAX_DURACION_MS = 30 * 60_000L
    const val MAX_DURACION_MCP_MS = 10 * 60_000L
    const val DURACION_DEFAULT_MS = 5 * 60_000L
}
