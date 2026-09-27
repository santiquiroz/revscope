package com.revscope.core.obd.taller.sesion

import com.revscope.core.obd.diagnostics.AvisoLecturaDtc
import com.revscope.core.obd.diagnostics.BorradoDtc
import com.revscope.core.obd.diagnostics.DtcScan
import com.revscope.core.obd.mcp.DtcScanJson
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.taller.FormatoTaller
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

    fun lecturaDtc(scan: DtcScan, origen: OrigenEvento, nombrePid: (String) -> String?) = NuevoEvento(
        tipo = TipoEvento.DTC_LECTURA,
        titulo = "Lectura de códigos",
        resumen = resumenLectura(scan),
        veredicto = veredictoLectura(scan),
        payload = DtcScanJson.scan(scan, incluirCrudo = true, nombrePid = nombrePid).put("enlacePerdido", scan.enlacePerdido),
        origen = origen,
    )

    fun borradoDtc(borrado: BorradoDtc, origen: OrigenEvento, nombrePid: (String) -> String?) = NuevoEvento(
        tipo = TipoEvento.DTC_BORRADO,
        titulo = "Borrado de códigos",
        resumen = resumenBorrado(borrado),
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
    ) = NuevoEvento(
        tipo = TipoEvento.CHEQUEO,
        titulo = "Chequeo de salud",
        resumen = resumenChequeo(items),
        veredicto = peorNivel(items),
        payload = JSONObject()
            .put("healthReportId", chequeoId ?: JSONObject.NULL)
            .put("v", HealthReportFormato.VERSION_ACTUAL)
            .put("items", HealthReportFormato.itemsJson(items))
            .put("metricas", HealthReportFormato.metricasJson(metricas))
            .put("dtc", dtc?.let { DtcScanJson.scan(it, incluirCrudo = true, nombrePid = nombrePid) } ?: JSONObject.NULL),
    )

    fun nota(texto: String, origen: OrigenEvento) = NuevoEvento(
        tipo = TipoEvento.NOTA,
        titulo = "Nota",
        resumen = texto.trim(),
        payload = JSONObject().put("texto", texto.trim()),
        origen = origen,
    )

    fun sintomas(sintomas: Set<Sintoma>, texto: String, origen: OrigenEvento) = NuevoEvento(
        tipo = TipoEvento.SINTOMAS,
        titulo = "Síntomas",
        resumen = (sintomas.sortedBy { it.ordinal }.map { it.etiqueta } + listOf(texto.trim()).filter { it.isNotEmpty() })
            .joinToString(" · "),
        payload = JSONObject()
            .put("sintomas", JSONArray(sintomas.sortedBy { it.ordinal }.map { it.name }))
            .put("texto", texto.trim()),
        origen = origen,
    )

    fun instantanea(lecturas: Map<String, ObdReading>, ahora: Long, nombrePid: (String) -> String?): NuevoEvento {
        val ordenadas = lecturas.values.sortedBy { it.pid }
        return NuevoEvento(
            tipo = TipoEvento.INSTANTANEA_SENSORES,
            titulo = "Instantánea de sensores",
            resumen = ordenadas.joinToString(" · ") { "${nombrePid(it.pid) ?: it.pid} ${textoValor(it)}" }
                .ifEmpty { "Sin lecturas: conecta el adaptador" },
            payload = JSONObject().put("lecturas", JSONArray(ordenadas.map { lecturaJson(it, ahora, nombrePid) })),
        )
    }

    private fun resumenLectura(scan: DtcScan): String {
        val partes = buildList {
            lecturaIncompleta(scan)?.let(::add)
            if (scan.activos.isNotEmpty()) add("Activos: ${scan.activos.joinToString { it.code }}")
            if (scan.pendientes.isNotEmpty()) add("Pendientes: ${scan.pendientes.joinToString { it.code }}")
            if (scan.permanentes.isNotEmpty()) add("Permanentes: ${scan.permanentes.joinToString { it.code }}")
            sinCodigos(scan)?.let(::add)
            serviciosSinRespuesta(scan)?.let(::add)
            scan.milEncendida?.let { add(if (it) "testigo encendido" else "testigo apagado") }
        }
        return partes.joinToString(" · ")
    }

    private fun lecturaIncompleta(scan: DtcScan): String? = when {
        scan.enlacePerdido -> "Lectura incompleta: se perdió el enlace"
        !scan.activosConfirmados -> "Lectura incompleta: la ECU no respondió a los códigos activos"
        else -> null
    }

    private fun sinCodigos(scan: DtcScan): String? = when {
        scan.enlacePerdido -> null
        scan.serviciosFallidos.isEmpty() -> "Sin códigos".takeIf { scan.todos.isEmpty() }
        else -> AvisoLecturaDtc.sinCodigos(scan)
    }

    private fun serviciosSinRespuesta(scan: DtcScan): String? {
        if (scan.enlacePerdido || !scan.activosConfirmados || scan.serviciosFallidos.isEmpty()) return null
        return "sin respuesta a " + scan.serviciosFallidos.sortedBy { it.ordinal }.joinToString { AvisoLecturaDtc.etiqueta(it) }
    }

    private fun veredictoLectura(scan: DtcScan): Veredicto = when {
        scan.enlacePerdido -> Veredicto.ATENCION
        scan.activos.isNotEmpty() || scan.permanentes.isNotEmpty() -> Veredicto.FALLA
        scan.pendientes.isNotEmpty() || !scan.activosConfirmados -> Veredicto.ATENCION
        else -> Veredicto.OK
    }

    private fun resumenBorrado(borrado: BorradoDtc): String {
        val antes = if (borrado.antes.activosConfirmados) {
            borrado.antes.activos.joinToString { it.code }.ifEmpty { "sin códigos" }
        } else {
            "no se pudieron leer"
        }
        val despues = borrado.despues.activos.joinToString { it.code }
        return when {
            borrado.rechazadoPorCondiciones ->
                "La ECU rechazó el borrado (condiciones no correctas): apaga el motor y deja el contacto"
            borrado.respuestaCruda == null -> "No se pudo enviar el borrado: el adaptador no respondió"
            despues.isNotEmpty() -> "Antes: $antes · volvieron: $despues (la falla sigue presente)"
            !borrado.despues.activosConfirmados ->
                "Antes: $antes · se envió el borrado, pero no se pudo releer la ECU para confirmarlo"
            else -> "Antes: $antes · después: sin códigos. Los monitores quedan incompletos hasta completar ciclos de manejo"
        }
    }

    private fun veredictoBorrado(borrado: BorradoDtc): Veredicto = when {
        borrado.rechazadoPorCondiciones || borrado.respuestaCruda == null -> Veredicto.ATENCION
        borrado.despues.activos.isNotEmpty() || !borrado.despues.activosConfirmados -> Veredicto.ATENCION
        else -> Veredicto.OK
    }

    private fun resumenChequeo(items: List<DiagnosticRules.Diagnosis>): String {
        if (items.isEmpty()) return "Sin hallazgos"
        val fallas = items.count { it.nivel == DiagnosticRules.Nivel.FALLA }
        val atencion = items.count { it.nivel == DiagnosticRules.Nivel.ATENCION }
        val ok = items.count { it.nivel == DiagnosticRules.Nivel.OK }
        return listOf(
            fallas to (if (fallas == 1) "falla" else "fallas"),
            atencion to "atención",
            ok to "OK",
        ).filter { it.first > 0 }.joinToString(" · ") { "${it.first} ${it.second}" }
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

    private fun textoValor(r: ObdReading): String {
        val decimales = if (r.value % 1.0 == 0.0) 0 else 1
        return "${FormatoTaller.numero(r.value, decimales)} ${r.unit}".trim()
    }
}
