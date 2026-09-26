package com.revscope.core.obd.taller.sesion

import com.revscope.core.obd.mcp.CapturaJson
import com.revscope.core.obd.pid.PidDefinition
import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.ReductorSerie
import com.revscope.core.obd.taller.SerieReducidaJson
import com.revscope.core.obd.telemetry.captura.MuestraCaptura
import com.revscope.core.obd.telemetry.captura.ResumenCaptura
import com.revscope.core.obd.telemetry.captura.ResumenPid
import org.json.JSONObject

// El CSV completo va como adjunto; el payload guarda estadísticas y una serie reducida para el informe.
object EventoCaptura {

    fun de(resumen: ResumenCaptura, muestras: List<MuestraCaptura>, definicion: (String) -> PidDefinition?) = NuevoEvento(
        tipo = TipoEvento.CAPTURA,
        titulo = "Captura rápida · ${textoPids(resumen.porPid.size)} · ${textoDuracion(resumen.duracionMs)}",
        resumen = resumen.porPid.joinToString(" · ") { textoPid(it, definicion(it.pid)) }.ifEmpty { "Sin muestras" },
        payload = CapturaJson.resumen(resumen)
            .put("pids", pidsJson(resumen, definicion))
            .put("serie", SerieReducidaJson.porPid(muestras))
            .put("puntosMaxPorPid", ReductorSerie.MAX_PUNTOS),
        adjuntoOrigen = resumen.rutaCsv,
    )

    private fun textoPids(n: Int): String = if (n == 1) "1 PID" else "$n PIDs"

    private fun textoDuracion(ms: Long): String = "${FormatoTaller.numero(ms / 1_000.0, 1)} s"

    private fun textoPid(p: ResumenPid, def: PidDefinition?): String {
        val unidad = def?.unit.orEmpty()
        val rango = "${FormatoTaller.numero(p.min, 1)}–${FormatoTaller.numero(p.max, 1)} $unidad".trim()
        return "${def?.nameEs ?: p.pid} $rango (${FormatoTaller.numero(p.hz, 1)} Hz)"
    }

    private fun pidsJson(resumen: ResumenCaptura, definicion: (String) -> PidDefinition?): JSONObject =
        JSONObject().apply {
            resumen.porPid.forEach { p ->
                val def = definicion(p.pid)
                put(p.pid, JSONObject().put("nombre", def?.nameEs ?: JSONObject.NULL).put("unidad", def?.unit ?: JSONObject.NULL))
            }
        }
}
