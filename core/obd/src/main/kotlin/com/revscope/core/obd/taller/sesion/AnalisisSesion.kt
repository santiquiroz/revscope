package com.revscope.core.obd.taller.sesion

import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.obd.workshop.HealthReportFormato
import com.revscope.core.obd.workshop.MetricasChequeo
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

data class AnalisisSesion(
    val codigos: List<String>,
    val chequeoBase: ChequeoRegistrado?,
    val metricasAhora: MetricasChequeo?,
    val comparacion: List<FilaComparacion>,
    val sugeridas: List<PruebaSugerida>,
)

class AnalizadorSesion @Inject constructor(
    private val repositorio: TallerRepository,
    private val historial: HistorialChequeos,
    private val sugeridor: SugeridorPruebas,
) {
    suspend fun analizar(sesion: SesionTaller, eventos: List<EventoTaller>, tipo: VehicleType): AnalisisSesion {
        val codigos = EstadoSesion.codigosActuales(eventos)
        val base = sesion.chequeoBaseId?.let { historial.porId(it) }
        val ahora = EstadoSesion.ultimasMetricas(eventos)
        val baseMetricas = base?.metricas
        val comparacion = if (baseMetricas != null && ahora != null) {
            val umbrales = UmbralesComparacion.desde(repositorio.bandasResueltas(sesion.claveModelo, tipo))
            ComparadorChequeo.comparar(baseMetricas, ahora, umbrales)
        } else {
            emptyList()
        }
        return AnalisisSesion(codigos, base, ahora, comparacion, sugeridor.sugerir(sesion.sintomas, codigos))
    }
}

// Lee el estado de la sesión desde los payloads de su línea de tiempo: el evento más reciente con dato manda.
object EstadoSesion {

    private val TIPOS_CON_CODIGOS = setOf(TipoEvento.DTC_LECTURA, TipoEvento.DTC_BORRADO, TipoEvento.CHEQUEO)
    private val LISTAS_DTC = listOf("activos", "pendientes", "permanentes")

    fun codigosActuales(eventos: List<EventoTaller>): List<String> =
        recientesPrimero(eventos).filter { it.tipo in TIPOS_CON_CODIGOS }.firstNotNullOfOrNull(::codigosDe).orEmpty()

    fun ultimasMetricas(eventos: List<EventoTaller>): MetricasChequeo? =
        recientesPrimero(eventos).filter { it.tipo == TipoEvento.CHEQUEO }.firstNotNullOfOrNull(::metricasDe)

    private fun recientesPrimero(eventos: List<EventoTaller>): List<EventoTaller> =
        eventos.sortedWith(compareByDescending<EventoTaller> { it.instante }.thenByDescending { it.id })

    private fun codigosDe(evento: EventoTaller): List<String>? {
        val payload = payloadDe(evento) ?: return null
        return when (evento.tipo) {
            TipoEvento.DTC_LECTURA -> LISTAS_DTC.flatMap { textos(payload.optJSONArray(it)) }.distinct()
            TipoEvento.DTC_BORRADO -> codigosTrasBorrado(payload)
            TipoEvento.CHEQUEO -> metricasDe(evento)?.takeIf { it.dtcsLeidos }?.dtcs
            else -> null
        }
    }

    private fun codigosTrasBorrado(payload: JSONObject): List<String>? {
        val lado = if (payload.optBoolean("rechazadoPorCondiciones")) "antes" else "despues"
        return payload.optJSONObject(lado)?.optJSONArray("activos")?.let(::textos)
    }

    private fun metricasDe(evento: EventoTaller): MetricasChequeo? =
        HealthReportFormato.metricasDe(payloadDe(evento)?.optJSONObject("metricas"))

    private fun payloadDe(evento: EventoTaller): JSONObject? = runCatching { JSONObject(evento.payloadJson) }.getOrNull()

    private fun textos(array: JSONArray?): List<String> =
        (0 until (array?.length() ?: 0)).mapNotNull { array?.optString(it)?.takeIf(String::isNotBlank) }
}
