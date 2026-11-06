package com.revscope.core.obd.taller.sesion

import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R
import com.revscope.core.obd.diagnostics.BorradoDtc
import com.revscope.core.obd.diagnostics.DtcScan
import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.protocol.DtcServicio
import com.revscope.core.obd.mcp.DtcScanJson
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.common.i18n.LocaleNumbers
import com.revscope.core.obd.workshop.DiagnosticRules
import com.revscope.core.obd.workshop.HealthReportFormato
import com.revscope.core.obd.workshop.MetricasChequeo
import org.json.JSONArray
import org.json.JSONObject

data class NuevoEvento(
    val tipo: TipoEvento,
    val titulo: String,
    val resumen: String = "",
    val veredicto: Veredicto = Veredicto.INFO,
    val payload: JSONObject = JSONObject(),
    val origen: OrigenEvento = OrigenEvento.APP,
    val adjuntoOrigen: String? = null,
)

object EventosTaller {

    fun lecturaDtc(scan: DtcScan, origen: OrigenEvento, nombrePid: (String) -> String?, resolver: StringResolver) = NuevoEvento(
        tipo = TipoEvento.DTC_LECTURA,
        titulo = resolver.get(R.string.obd_session_event_read_codes),
        resumen = resumenLectura(scan, resolver),
        veredicto = veredictoLectura(scan),
        payload = DtcScanJson.scan(scan, incluirCrudo = true, nombrePid = nombrePid).put("enlacePerdido", scan.enlacePerdido),
        origen = origen,
    )

    fun borradoDtc(borrado: BorradoDtc, origen: OrigenEvento, nombrePid: (String) -> String?, resolver: StringResolver) = NuevoEvento(
        tipo = TipoEvento.DTC_BORRADO,
        titulo = resolver.get(R.string.obd_session_event_clear_codes),
        resumen = resumenBorrado(borrado, resolver),
        veredicto = veredictoBorrado(borrado),
        payload = JSONObject()
            .put("antes", DtcScanJson.scan(borrado.antes, incluirCrudo = true, nombrePid = nombrePid))
            .put("despues", DtcScanJson.scan(borrado.despues, incluirCrudo = true, nombrePid = nombrePid))
            .put("respuesta", borrado.respuestaCruda ?: JSONObject.NULL)
            .put("rechazadoPorCondiciones", borrado.rechazadoPorCondiciones),
        origen = origen,
    )

    fun chequeo(
        chequeoId: Long?,
        items: List<DiagnosticRules.Diagnosis>,
        metricas: MetricasChequeo,
        dtc: DtcScan?,
        nombrePid: (String) -> String?,
        resolver: StringResolver,
    ) = NuevoEvento(
        tipo = TipoEvento.CHEQUEO,
        titulo = resolver.get(R.string.obd_session_event_health_check),
        resumen = resumenChequeo(items, resolver),
        veredicto = peorNivel(items),
        payload = JSONObject()
            .put("healthReportId", chequeoId ?: JSONObject.NULL)
            .put("v", HealthReportFormato.VERSION_ACTUAL)
            .put("items", HealthReportFormato.itemsJson(items))
            .put("metricas", HealthReportFormato.metricasJson(metricas))
            .put("dtc", dtc?.let { DtcScanJson.scan(it, incluirCrudo = true, nombrePid = nombrePid) } ?: JSONObject.NULL),
    )

    fun nota(texto: String, origen: OrigenEvento, resolver: StringResolver) = NuevoEvento(
        tipo = TipoEvento.NOTA,
        titulo = resolver.get(R.string.obd_session_event_note),
        resumen = texto.trim(),
        payload = JSONObject().put("texto", texto.trim()),
        origen = origen,
    )

    fun sintomas(sintomas: Set<Sintoma>, texto: String, origen: OrigenEvento, resolver: StringResolver) = NuevoEvento(
        tipo = TipoEvento.SINTOMAS,
        titulo = resolver.get(R.string.obd_session_event_symptoms),
        resumen = (sintomas.sortedBy { it.ordinal }.map { it.etiqueta(resolver) } + listOf(texto.trim()).filter { it.isNotEmpty() })
            .joinToString(" · "),
        payload = JSONObject()
            .put("sintomas", JSONArray(sintomas.sortedBy { it.ordinal }.map { it.name }))
            .put("texto", texto.trim()),
        origen = origen,
    )

    fun instantanea(
        lecturas: Map<String, ObdReading>,
        ahora: Long,
        nombrePid: (String) -> String?,
        resolver: StringResolver,
    ): NuevoEvento {
        val ordenadas = lecturas.values.sortedBy { it.pid }
        return NuevoEvento(
            tipo = TipoEvento.INSTANTANEA_SENSORES,
            titulo = resolver.get(R.string.obd_session_event_sensor_snapshot),
            resumen = ordenadas.joinToString(" · ") { "${nombrePid(it.pid) ?: it.pid} ${textoValor(it, resolver)}" }
                .ifEmpty { resolver.get(R.string.obd_session_snapshot_no_readings) },
            payload = JSONObject().put("lecturas", JSONArray(ordenadas.map { lecturaJson(it, ahora, nombrePid) })),
        )
    }

    private fun resumenLectura(scan: DtcScan, resolver: StringResolver): String {
        val partes = buildList {
            lecturaIncompleta(scan, resolver)?.let(::add)
            if (scan.activos.isNotEmpty()) add(resolver.get(R.string.obd_session_code_active, scan.activos.joinToString { it.code }))
            if (scan.pendientes.isNotEmpty()) add(resolver.get(R.string.obd_session_code_pending, scan.pendientes.joinToString { it.code }))
            if (scan.permanentes.isNotEmpty()) add(resolver.get(R.string.obd_session_code_permanent, scan.permanentes.joinToString { it.code }))
            sinCodigos(scan, resolver)?.let(::add)
            serviciosSinRespuesta(scan, resolver)?.let(::add)
            scan.milEncendida?.let {
                add(resolver.get(if (it) R.string.obd_session_mil_on else R.string.obd_session_mil_off))
            }
        }
        return partes.joinToString(" · ")
    }

    private fun lecturaIncompleta(scan: DtcScan, resolver: StringResolver): String? = when {
        scan.enlacePerdido -> resolver.get(R.string.obd_session_scan_incomplete_link)
        !scan.activosConfirmados -> resolver.get(R.string.obd_session_scan_incomplete_ecu)
        else -> null
    }

    private fun sinCodigos(scan: DtcScan, resolver: StringResolver): String? = when {
        scan.enlacePerdido || !scan.activosConfirmados -> null
        scan.todos.isNotEmpty() -> null
        scan.serviciosFallidos.isEmpty() -> resolver.get(R.string.obd_session_no_codes).takeIf { scan.todos.isEmpty() }
        else -> {
            val leidos = DtcServicio.entries.filterNot { it in scan.serviciosFallidos }.map { servicio ->
                resolver.get(
                    when (servicio) {
                        DtcServicio.ACTIVOS -> R.string.obd_session_service_active
                        DtcServicio.PENDIENTES -> R.string.obd_session_service_pending
                        DtcServicio.PERMANENTES -> R.string.obd_session_service_permanent
                    },
                )
            }
            resolver.get(R.string.obd_session_no_codes_services, listaServicios(leidos, resolver, and = false))
        }
    }

    private fun serviciosSinRespuesta(scan: DtcScan, resolver: StringResolver): String? {
        if (scan.enlacePerdido || !scan.activosConfirmados || scan.serviciosFallidos.isEmpty()) return null
        val etiquetas = scan.serviciosFallidos.sortedBy { it.ordinal }.map { servicio ->
            resolver.get(
                when (servicio) {
                    DtcServicio.ACTIVOS -> R.string.obd_session_service_active
                    DtcServicio.PENDIENTES -> R.string.obd_session_service_pending
                    DtcServicio.PERMANENTES -> R.string.obd_session_service_permanent
                },
            )
        }
        return resolver.get(R.string.obd_session_no_response_services, listaServicios(etiquetas, resolver, and = true))
    }

    private fun listaServicios(servicios: List<String>, resolver: StringResolver, and: Boolean): String {
        if (servicios.size < 2) return servicios.joinToString()
        val connector = resolver.get(if (and) R.string.obd_session_service_join_and else R.string.obd_session_service_join_or)
        return resolver.get(R.string.obd_session_service_list, servicios.dropLast(1).joinToString(", "), connector, servicios.last())
    }

    private fun veredictoLectura(scan: DtcScan): Veredicto = when {
        scan.enlacePerdido -> Veredicto.ATENCION
        scan.activos.isNotEmpty() || scan.permanentes.isNotEmpty() -> Veredicto.FALLA
        scan.pendientes.isNotEmpty() || !scan.activosConfirmados -> Veredicto.ATENCION
        else -> Veredicto.OK
    }

    private fun resumenBorrado(borrado: BorradoDtc, resolver: StringResolver): String {
        val antes = if (borrado.antes.activosConfirmados) {
            borrado.antes.activos.joinToString { it.code }.ifEmpty { resolver.get(R.string.obd_session_no_codes) }
        } else {
            resolver.get(R.string.obd_session_codes_unavailable)
        }
        val despues = borrado.despues.activos.joinToString { it.code }
        return when {
            borrado.rechazadoPorCondiciones ->
                resolver.get(R.string.obd_session_clear_rejected)
            borrado.respuestaCruda == null -> resolver.get(R.string.obd_session_clear_no_response)
            despues.isNotEmpty() -> resolver.get(R.string.obd_session_clear_codes_returned, antes, despues)
            !borrado.despues.activosConfirmados ->
                resolver.get(R.string.obd_session_clear_unconfirmed, antes)
            else -> resolver.get(R.string.obd_session_clear_monitors_incomplete, antes)
        }
    }

    private fun veredictoBorrado(borrado: BorradoDtc): Veredicto = when {
        borrado.rechazadoPorCondiciones || borrado.respuestaCruda == null -> Veredicto.ATENCION
        borrado.despues.activos.isNotEmpty() || !borrado.despues.activosConfirmados -> Veredicto.ATENCION
        else -> Veredicto.OK
    }

    private fun resumenChequeo(items: List<DiagnosticRules.Diagnosis>, resolver: StringResolver): String {
        if (items.isEmpty()) return resolver.get(R.string.obd_session_no_findings)
        val fallas = items.count { it.nivel == DiagnosticRules.Nivel.FALLA }
        val atencion = items.count { it.nivel == DiagnosticRules.Nivel.ATENCION }
        val ok = items.count { it.nivel == DiagnosticRules.Nivel.OK }
        return listOf(
            fallas to resolver.plural(R.plurals.obd_session_failures, fallas, fallas),
            atencion to resolver.plural(R.plurals.obd_session_attention, atencion, atencion),
            ok to resolver.plural(R.plurals.obd_session_ok, ok, ok),
        ).filter { it.first > 0 }.joinToString(" · ") { it.second }
    }

    private fun peorNivel(items: List<DiagnosticRules.Diagnosis>): Veredicto = when {
        items.any { it.nivel == DiagnosticRules.Nivel.FALLA } -> Veredicto.FALLA
        items.any { it.nivel == DiagnosticRules.Nivel.ATENCION } -> Veredicto.ATENCION
        items.isEmpty() -> Veredicto.INFO
        else -> Veredicto.OK
    }

    private fun lecturaJson(r: ObdReading, ahora: Long, nombrePid: (String) -> String?): JSONObject = JSONObject()
        .put("pid", r.pid)
        .put("nombre", nombrePid(r.pid) ?: JSONObject.NULL)
        .put("valor", FormatoTaller.redondear(r.value, 3))
        .put("unidad", r.unit)
        .put("edadS", ((ahora - r.timestamp).coerceAtLeast(0) / 1_000))

    private fun textoValor(r: ObdReading, resolver: StringResolver): String {
        val decimales = if (r.value % 1.0 == 0.0) 0 else 1
        return "${LocaleNumbers.decimal(r.value, decimales, resolver.locale)} ${r.unit}".trim()
    }
}
