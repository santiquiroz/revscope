package com.revscope.core.obd.mcp

import com.revscope.core.obd.telemetry.captura.EstadisticasCaptura
import com.revscope.core.obd.telemetry.captura.InfoAdaptador
import com.revscope.core.obd.telemetry.captura.InicioCaptura
import com.revscope.core.obd.telemetry.captura.MuestraCaptura
import com.revscope.core.obd.telemetry.captura.ResumenCaptura
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToLong

/** JSON compacto de la captura rápida para las tools del MCP (cifras redondeadas, series columnares). */
internal object CapturaJson {

    fun inicio(i: InicioCaptura): JSONObject = JSONObject()
        .put("capturaId", i.id)
        .put("pidsAceptados", JSONArray(i.pidsAceptados))
        .put("pidsNoSoportados", JSONArray(i.pidsNoSoportados))
        .put("lotes", JSONArray(i.lotes.map { JSONArray(it) }))
        .put("tecnicas", JSONArray(i.tecnicas.map { it.clave }))
        .put("duracionMaxS", i.duracionMaxMs / 1_000)

    fun estadisticas(stats: EstadisticasCaptura?): JSONObject {
        if (stats == null) return JSONObject()
        return JSONObject()
            .put("tasaHz", JSONObject(stats.hzPorPid.mapValues { redondear(it.value, 1) }))
            .put("peticionesPorS", redondear(stats.peticionesPorS, 1))
            .put("latenciaP50Ms", stats.latenciaP50Ms?.let { redondear(it, 1) } ?: JSONObject.NULL)
            .put("latenciaP95Ms", stats.latenciaP95Ms?.let { redondear(it, 1) } ?: JSONObject.NULL)
            .put("ratioErrores", redondear(stats.ratioErrores, 3))
            .put("limitadoPor", stats.limitadoPor ?: JSONObject.NULL)
    }

    fun adaptador(info: InfoAdaptador): JSONObject = JSONObject()
        .put("nombre", info.nombre ?: JSONObject.NULL)
        .put("elm", info.elm ?: JSONObject.NULL)
        .put("protocolo", info.protocoloDpn ?: JSONObject.NULL)
        .put("can", info.esCan ?: JSONObject.NULL)

    fun resumen(r: ResumenCaptura): JSONObject = JSONObject()
        .put("capturaId", r.id)
        .put("duracionS", redondear(r.duracionMs / 1_000.0, 1))
        .put("motivoFin", r.motivoFin)
        .put("latenciaP50Ms", r.latenciaP50Ms?.let { redondear(it, 1) } ?: JSONObject.NULL)
        .put("latenciaP95Ms", r.latenciaP95Ms?.let { redondear(it, 1) } ?: JSONObject.NULL)
        .put("csvEnTelefono", r.rutaCsv ?: JSONObject.NULL)
        .put(
            "porPid",
            JSONObject().apply {
                r.porPid.forEach { p ->
                    put(
                        p.pid,
                        JSONObject()
                            .put("n", p.n)
                            .put("hz", redondear(p.hz, 1))
                            .put("min", redondear(p.min, 3))
                            .put("max", redondear(p.max, 3))
                            .put("media", redondear(p.media, 3)),
                    )
                }
            },
        )

    /** `{"49": {"t_ms": [...], "v": [...]}}`: unos 25 KB por página de 2 000 muestras. */
    fun series(muestras: List<MuestraCaptura>): JSONObject = JSONObject().apply {
        muestras.groupBy { it.pid }.forEach { (pid, lista) ->
            put(
                pid,
                JSONObject()
                    .put("t_ms", JSONArray(lista.map { it.tMicros / 1_000 }))
                    .put("v", JSONArray(lista.map { redondear(it.valor, 3) })),
            )
        }
    }

    private fun redondear(valor: Double, decimales: Int): Double {
        var factor = 1.0
        repeat(decimales) { factor *= 10 }
        return (valor * factor).roundToLong() / factor
    }
}
