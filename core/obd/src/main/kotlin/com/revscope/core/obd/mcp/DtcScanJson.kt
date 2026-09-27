package com.revscope.core.obd.mcp

import com.revscope.core.obd.diagnostics.DtcScan
import com.revscope.core.obd.diagnostics.FreezeFrame
import com.revscope.core.obd.model.DtcCode
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.session.EstadoViaje
import org.json.JSONArray
import org.json.JSONObject

/** JSON compartido por get_dtc y borrar_dtc. */
internal object DtcScanJson {

    fun scan(scan: DtcScan, incluirCrudo: Boolean, nombrePid: (String) -> String?): JSONObject {
        val json = JSONObject()
            .put("codigos", codigos(scan.activos))
            .put("activos", codigos(scan.activos))
            .put("pendientes", codigos(scan.pendientes))
            .put("permanentes", codigos(scan.permanentes))
            .put("mil", scan.milEncendida ?: JSONObject.NULL)
            .put("conteoSegunEcu", scan.conteoSegunEcu ?: JSONObject.NULL)
            .put("freezeFrame", scan.freezeFrame?.let { freezeFrame(it, nombrePid) } ?: JSONObject.NULL)
            .put("errores", JSONArray(scan.errores))
            .put("completa", scan.serviciosFallidos.isEmpty() && !scan.enlacePerdido)
            .put("serviciosSinRespuesta", JSONArray(scan.serviciosFallidos.sortedBy { it.ordinal }.map { it.comando }))
        if (incluirCrudo) json.put("crudo", JSONObject(scan.crudo as Map<*, *>))
        return json
    }

    fun estadoViaje(estado: EstadoViaje): String = when (estado) {
        EstadoViaje.SinEnlace -> "sin_enlace"
        EstadoViaje.EnlaceSinViaje -> "sin_viaje"
        is EstadoViaje.Grabando -> "grabando"
    }

    private fun codigos(codes: List<DtcCode>): JSONArray = JSONArray(codes.map { it.code })

    private fun freezeFrame(ff: FreezeFrame, nombrePid: (String) -> String?): JSONObject =
        JSONObject()
            .put("dtcCausante", ff.dtcCausante ?: JSONObject.NULL)
            .put("valores", JSONArray(ff.valores.map { valor(it, nombrePid) }))

    private fun valor(reading: ObdReading, nombrePid: (String) -> String?): JSONObject =
        JSONObject()
            .put("pid", reading.pid)
            .put("nombre", nombrePid(reading.pid) ?: JSONObject.NULL)
            .put("valor", reading.value)
            .put("unidad", reading.unit)
}
