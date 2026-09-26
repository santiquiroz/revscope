package com.revscope.core.obd.mcp

import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.pruebas.EstadoPrueba
import com.revscope.core.obd.taller.pruebas.PasoPrueba
import com.revscope.core.obd.taller.pruebas.ResultadoPrecondicion
import com.revscope.core.obd.taller.pruebas.ResultadoPrueba
import org.json.JSONArray
import org.json.JSONObject

object PruebaGuiadaJson {

    fun resultado(r: ResultadoPrueba): JSONObject = JSONObject()
        .put("prueba", r.tipo.name)
        .put("tituloPrueba", r.tipo.titulo)
        .put("veredicto", r.veredicto.name)
        .put("titulo", r.titulo)
        .put("interpretacion", r.interpretacion)
        .put("siguientePaso", r.siguientePaso ?: JSONObject.NULL)
        .put("hallazgos", JSONArray(r.hallazgos))
        .put("bajaConfianza", r.bajaConfianza)
        .put("detalle", r.detalle.json())

    fun estado(estado: EstadoPrueba, idPrueba: String?, valorActual: ObdReading?): JSONObject = when (estado) {
        EstadoPrueba.Inactiva -> JSONObject().put("estado", "INACTIVA")
        is EstadoPrueba.Verificando -> verificando(estado)
        is EstadoPrueba.EnPaso -> enPaso(estado, valorActual)
        is EstadoPrueba.Analizando -> base("ANALIZANDO", estado.tipo.name)
        is EstadoPrueba.Terminada -> terminada(estado)
        is EstadoPrueba.Cancelada -> base("CANCELADA", estado.tipo.name).put("motivo", estado.motivo)
        is EstadoPrueba.Fallida ->
            base("FALLIDA", estado.tipo.name).put("motivo", estado.motivo).put("reintentable", estado.reintentable)
    }.put("pruebaId", idPrueba ?: JSONObject.NULL)

    private fun base(nombre: String, prueba: String): JSONObject = JSONObject().put("estado", nombre).put("prueba", prueba)

    private fun verificando(e: EstadoPrueba.Verificando): JSONObject = base("VERIFICANDO", e.tipo.name)
        .put("listas", e.listas)
        .put("precondiciones", JSONArray(e.precondiciones.map(::precondicion)))

    private fun precondicion(p: ResultadoPrecondicion): JSONObject = JSONObject()
        .put("texto", p.texto)
        .put("cumple", p.cumple)
        .put("queHacer", p.queHacer ?: JSONObject.NULL)

    private fun enPaso(e: EstadoPrueba.EnPaso, valorActual: ObdReading?): JSONObject = base("EN_PASO", e.tipo.name)
        .put("paso", e.indice + 1)
        .put("total", e.total)
        .put("fase", e.fase.name)
        .put("restanteMs", e.restanteMs ?: JSONObject.NULL)
        .put("pasoActual", paso(e.paso))
        .put("valorActual", valorActual?.let(::lectura) ?: JSONObject.NULL)

    private fun paso(p: PasoPrueba): JSONObject = JSONObject()
        .put("clave", p.clave)
        .put("titulo", p.titulo)
        .put("instruccion", p.instruccion)
        .put("modo", p.modo::class.simpleName)
        .put("duracionMs", p.modo.limiteMs)

    private fun lectura(r: ObdReading): JSONObject = JSONObject()
        .put("pid", r.pid)
        .put("valor", FormatoTaller.redondear(r.value, 2))
        .put("unidad", r.unit)

    private fun terminada(e: EstadoPrueba.Terminada): JSONObject = base("TERMINADA", e.resultado.tipo.name)
        .put("eventoId", e.eventoId ?: JSONObject.NULL)
        .put("guardadaEnSesion", e.eventoId != null)
        .put("resultado", resultado(e.resultado))
}
