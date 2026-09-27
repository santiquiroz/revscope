package com.revscope.core.obd.taller.informe

import org.json.JSONArray
import org.json.JSONObject

object InformeTallerJson {

    fun render(informe: InformeTaller): String = json(informe).toString()

    fun json(i: InformeTaller): JSONObject = JSONObject()
        .put("generadoEn", i.generadoEn)
        .put("titulo", i.titulo)
        .put("vehiculo", i.vehiculo)
        .put("codigo", i.codigo)
        .put("sintomas", i.sintomas)
        .put("comoSeMidio", i.comoSeMidio)
        .put("conclusion", conclusion(i.conclusion))
        .put("mediciones", JSONArray(i.mediciones.map(::medicion)))
        .put("pruebas", JSONArray(i.pruebas.map(::prueba)))
        .put("lineaTiempo", JSONArray(i.lineaTiempo.map(::evento)))
        .put("comparacion", JSONArray(i.comparacion.map { fila ->
            JSONObject()
                .put("metrica", fila.metrica.etiqueta)
                .put("base", fila.base)
                .put("ahora", fila.ahora)
                .put("cambio", fila.cambio)
                .put("veredicto", fila.veredicto.name)
                .put("referencia", fila.referencia ?: JSONObject.NULL)
        }))
        .put("interpretacionAutomatica", JSONArray(i.interpretacionAutomatica))
        .put("interpretacionTecnico", i.interpretacionTecnico)
        .put("repuestos", JSONArray(i.repuestos.map(::repuesto)))
        .put("pasosGuia", JSONArray(i.pasosGuia.map(::pasoGuia)))
        .put("pendientes", JSONArray(i.pendientes))
        .put("fuentes", JSONArray(i.fuentes.map { JSONObject().put("nombre", it.nombre).put("url", it.url ?: JSONObject.NULL) }))
        .put("versionApp", i.versionApp)

    private fun conclusion(c: ConclusionInforme) = JSONObject()
        .put("veredicto", c.veredicto.name)
        .put("frase", c.frase)
        .put("cables", JSONArray(c.cables.map {
            JSONObject().put("etiqueta", it.etiqueta).put("valor", it.valor).put("veredicto", it.veredicto.name)
        }))

    private fun medicion(m: MedicionInforme) = JSONObject()
        .put("sensor", m.sensor)
        .put("cable", m.cable)
        .put("condicion", m.condicion)
        .put("valor", m.valor)
        .put("unidad", m.unidad)
        .put("estado", m.estado)
        .put("referencia", m.referencia)
        .put("bandaMin", m.bandaMin ?: JSONObject.NULL)
        .put("bandaMax", m.bandaMax ?: JSONObject.NULL)

    private fun prueba(p: PruebaInforme) = JSONObject()
        .put("titulo", p.titulo)
        .put("veredicto", p.veredicto.name)
        .put("resumen", p.resumen)
        .put("interpretacion", p.interpretacion)
        .put("hallazgos", JSONArray(p.hallazgos))
        .put("pasos", JSONArray(p.pasos.map {
            JSONObject().put("nombre", it.nombre)
                .put("porcentaje", it.porcentaje ?: JSONObject.NULL)
                .put("voltios", it.voltios ?: JSONObject.NULL)
        }))
        .put("series", JSONArray(p.series.map { serie ->
            JSONObject().put("nombre", serie.nombre).put("puntos", JSONArray(serie.puntos.map { punto ->
                JSONArray().put(punto.x).put(punto.y)
            }))
        }))
        .put("tramos", JSONArray(p.tramos.map {
            JSONObject().put("inicioX", it.inicioX).put("finX", it.finX).put("etiqueta", it.etiqueta)
        }))

    private fun evento(e: EventoInforme) = JSONObject()
        .put("instante", e.instante)
        .put("tipo", e.tipo)
        .put("titulo", e.titulo)
        .put("resumen", e.resumen)
        .put("veredicto", e.veredicto.name)

    private fun repuesto(r: RepuestoInforme) = JSONObject()
        .put("rol", r.rol)
        .put("referencia", r.referencia)
        .put("descripcion", r.descripcion)
        .put("tipo", r.tipo)
        .put("nota", r.nota)
        .put("fuente", r.fuente)

    private fun pasoGuia(p: PasoGuiaInforme) = JSONObject()
        .put("numero", p.numero)
        .put("titulo", p.titulo)
        .put("detalle", p.detalle)
}
