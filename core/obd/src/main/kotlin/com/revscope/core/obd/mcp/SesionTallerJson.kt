package com.revscope.core.obd.mcp

import com.revscope.core.obd.taller.sesion.AnalisisSesion
import com.revscope.core.obd.taller.sesion.EventoTaller
import com.revscope.core.obd.taller.sesion.FilaComparacion
import com.revscope.core.obd.taller.sesion.PruebaSugerida
import com.revscope.core.obd.taller.sesion.SesionTaller
import com.revscope.core.obd.taller.sesion.Sintoma
import org.json.JSONArray
import org.json.JSONObject

/** JSON de las tools de sesión del Taller (get_sesion_taller, iniciar_sesion_taller, agregar_nota_taller). */
internal object SesionTallerJson {

    fun sesion(s: SesionTaller, totalEventos: Int? = null): JSONObject = JSONObject()
        .put("id", s.id)
        .put("titulo", s.titulo)
        .put("vehiculoId", s.vehiculoId)
        .put("claveModelo", s.claveModelo ?: JSONObject.NULL)
        .put("inicio", s.inicio)
        .put("cierre", s.cierre ?: JSONObject.NULL)
        .put("abierta", s.abierta)
        .put("sintomas", JSONArray(s.sintomas.sortedBy { it.ordinal }.map(::sintoma)))
        .put("sintomasTexto", s.sintomasTexto)
        .put("notas", s.notas)
        .put("odometroKm", s.odometroKm ?: JSONObject.NULL)
        .put("chequeoBaseId", s.chequeoBaseId ?: JSONObject.NULL)
        .put("interpretacion", s.interpretacion)
        .putOpt("totalEventos", totalEventos)

    fun evento(e: EventoTaller, incluirPayload: Boolean): JSONObject {
        val json = JSONObject()
            .put("id", e.id)
            .put("instante", e.instante)
            .put("tipo", e.tipo.name)
            .put("origen", e.origen.name)
            .put("titulo", e.titulo)
            .put("resumen", e.resumen)
            .put("veredicto", e.veredicto.name)
            .put("adjunto", e.adjunto ?: JSONObject.NULL)
        if (incluirPayload) json.put("payload", runCatching { JSONObject(e.payloadJson) }.getOrElse { e.payloadJson })
        return json
    }

    fun analisis(a: AnalisisSesion): JSONObject = JSONObject()
        .put("codigosActuales", JSONArray(a.codigos))
        .put("chequeoBase", a.chequeoBase?.let { JSONObject().put("id", it.id).put("fecha", it.instante) } ?: JSONObject.NULL)
        .put("comparacion", comparacion(a))
        .put("pruebasSugeridas", JSONArray(a.sugeridas.map(::sugerida)))

    fun sugerida(p: PruebaSugerida): JSONObject = JSONObject()
        .put("accion", p.accion.clave)
        .put("texto", p.accion.etiqueta)
        .put("motivo", p.motivo)

    fun sintoma(s: Sintoma): JSONObject = JSONObject().put("clave", s.name).put("etiqueta", s.etiqueta)

    private fun comparacion(a: AnalisisSesion): Any = when {
        a.comparacion.isNotEmpty() -> JSONArray(a.comparacion.map(::fila))
        a.chequeoBase == null -> "Sin chequeo base: el vehículo no tenía chequeos de salud antes de abrir la sesión"
        a.chequeoBase.metricas == null -> "El chequeo base es del formato 1 (sin métricas): solo sirve de referencia textual"
        else -> "Corre un chequeo de salud en la sesión para compararlo con el chequeo base"
    }

    private fun fila(f: FilaComparacion): JSONObject = JSONObject()
        .put("metrica", f.metrica.name)
        .put("etiqueta", f.metrica.etiqueta)
        .put("base", f.base)
        .put("ahora", f.ahora)
        .put("cambio", f.cambio)
        .put("veredicto", f.veredicto.name)
        .put("referencia", f.referencia ?: JSONObject.NULL)
}
