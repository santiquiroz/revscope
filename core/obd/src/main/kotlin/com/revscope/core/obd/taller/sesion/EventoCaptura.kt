package com.revscope.core.obd.taller.sesion

import com.revscope.core.obd.mcp.CapturaJson
import com.revscope.core.common.i18n.LocaleNumbers
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R
import com.revscope.core.obd.pid.PidDefinition
import com.revscope.core.obd.taller.ReductorSerie
import com.revscope.core.obd.taller.SerieReducidaJson
import com.revscope.core.obd.telemetry.captura.MuestraCaptura
import com.revscope.core.obd.telemetry.captura.ResumenCaptura
import com.revscope.core.obd.telemetry.captura.ResumenPid
import org.json.JSONObject

// El CSV completo va como adjunto; el payload guarda estadísticas y una serie reducida para el informe.
object EventoCaptura {

    fun de(
        resumen: ResumenCaptura,
        muestras: List<MuestraCaptura>,
        definicion: (String) -> PidDefinition?,
        resolver: StringResolver,
    ) = NuevoEvento(
        tipo = TipoEvento.CAPTURA,
        titulo = resolver.get(
            R.string.obd_session_event_quick_capture,
            textoPids(resumen.porPid.size, resolver),
            textoDuracion(resumen.duracionMs, resolver),
        ),
        resumen = resumen.porPid.joinToString(" · ") { textoPid(it, definicion(it.pid), resolver) }
            .ifEmpty { resolver.get(R.string.obd_session_capture_no_samples) },
        payload = CapturaJson.resumen(resumen)
            .put("pids", pidsJson(resumen, definicion))
            .put("serie", SerieReducidaJson.porPid(muestras))
            .put("puntosMaxPorPid", ReductorSerie.MAX_PUNTOS),
        adjuntoOrigen = resumen.rutaCsv,
    )

    private fun textoPids(n: Int, resolver: StringResolver): String = resolver.plural(R.plurals.obd_session_pid_count, n, n)

    private fun textoDuracion(ms: Long, resolver: StringResolver): String =
        resolver.get(R.string.obd_session_decimal_seconds, LocaleNumbers.decimal(ms / 1_000.0, 1, resolver.locale))

    private fun textoPid(p: ResumenPid, def: PidDefinition?, resolver: StringResolver): String {
        val unidad = def?.unit.orEmpty()
        val rango = resolver.get(
            R.string.obd_session_capture_range,
            LocaleNumbers.decimal(p.min, 1, resolver.locale),
            LocaleNumbers.decimal(p.max, 1, resolver.locale),
            unidad,
        ).trim()
        return resolver.get(
            R.string.obd_session_capture_pid_rate,
            def?.nameEs ?: p.pid,
            rango,
            LocaleNumbers.decimal(p.hz, 1, resolver.locale),
        )
    }

    private fun pidsJson(resumen: ResumenCaptura, definicion: (String) -> PidDefinition?): JSONObject =
        JSONObject().apply {
            resumen.porPid.forEach { p ->
                val def = definicion(p.pid)
                put(p.pid, JSONObject().put("nombre", def?.nameEs ?: JSONObject.NULL).put("unidad", def?.unit ?: JSONObject.NULL))
            }
        }
}
