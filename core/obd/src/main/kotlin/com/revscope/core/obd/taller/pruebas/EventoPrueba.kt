package com.revscope.core.obd.taller.pruebas

import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R
import com.revscope.core.obd.mcp.PruebaGuiadaJson
import com.revscope.core.obd.taller.ReductorSerie
import com.revscope.core.obd.taller.SerieReducidaJson
import com.revscope.core.obd.taller.sesion.NuevoEvento
import com.revscope.core.obd.taller.sesion.OrigenEvento
import com.revscope.core.obd.taller.sesion.TipoEvento
import com.revscope.core.obd.taller.sesion.Veredicto
import org.json.JSONArray
import org.json.JSONObject

// El resultado va con los pasos y la serie reducida (para el informe); el CSV de la captura, como adjunto.
object EventoPrueba {

    fun terminada(
        resultado: ResultadoPrueba,
        datos: DatosPrueba,
        rutaCsv: String?,
        origen: OrigenEvento,
        resolver: StringResolver,
    ) = NuevoEvento(
        tipo = TipoEvento.PRUEBA_GUIADA,
        titulo = resultado.tipo.titulo(resolver),
        resumen = if (resultado.bajaConfianza) {
            resolver.get(R.string.obd_prueba_baja_confianza, resultado.titulo)
        } else {
            resultado.titulo
        },
        veredicto = resultado.veredicto,
        payload = PruebaGuiadaJson.resultado(resultado, resolver)
            .put("estado", "TERMINADA")
            .put("segmentos", segmentosJson(datos.segmentos))
            .put("serie", SerieReducidaJson.porPid(datos.muestras))
            .put("puntosMaxPorPid", ReductorSerie.MAX_PUNTOS),
        origen = origen,
        adjuntoOrigen = rutaCsv,
    )

    // Lo capturado hasta el corte también queda: sirve para ver en qué paso se cayó el enlace.
    fun sinTerminar(
        fallida: EstadoPrueba.Fallida,
        datos: DatosPrueba,
        total: Int,
        rutaCsv: String?,
        origen: OrigenEvento,
        resolver: StringResolver,
    ) = NuevoEvento(
            tipo = TipoEvento.PRUEBA_GUIADA,
            titulo = resolver.get(R.string.obd_prueba_sin_terminar, fallida.tipo.titulo(resolver)),
            resumen = resolver.plural(R.plurals.obd_prueba_pasos_completos, total, fallida.motivo, datos.segmentos.size, total),
            veredicto = Veredicto.ATENCION,
            payload = JSONObject()
                .put("estado", "FALLIDA")
                .put("prueba", fallida.tipo.name)
                .put("motivo", fallida.motivo)
                .put("reintentable", fallida.reintentable)
                .put("pasosCompletos", datos.segmentos.size)
                .put("pasosTotales", total)
                .put("segmentos", segmentosJson(datos.segmentos))
                .put("serie", SerieReducidaJson.porPid(datos.muestras))
                .put("puntosMaxPorPid", ReductorSerie.MAX_PUNTOS),
            origen = origen,
            adjuntoOrigen = rutaCsv,
        )

    private fun segmentosJson(segmentos: List<SegmentoPaso>) = JSONArray(
        segmentos.map { s ->
            JSONObject()
                .put("clave", s.clave)
                .put("inicio_ms", s.inicioMs)
                .put("fin_ms", s.finMs)
                .put("util_desde_ms", s.utilDesdeMs)
        },
    )
}
