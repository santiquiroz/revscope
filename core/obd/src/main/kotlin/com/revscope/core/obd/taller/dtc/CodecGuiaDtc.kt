package com.revscope.core.obd.taller.dtc

import org.json.JSONArray
import org.json.JSONObject

data class CabeceraGuiaDtc(val version: Int, val fuenteDescripciones: String, val aviso: String)

object CodecGuiaDtc {

    fun leerCabecera(raiz: JSONObject) = CabeceraGuiaDtc(
        version = raiz.getInt("version"),
        fuenteDescripciones = raiz.getString("fuenteDescripciones"),
        aviso = raiz.getString("aviso"),
    )

    fun entradas(raiz: JSONObject): List<JSONObject> = raiz.getJSONArray("codigos").objetos()

    fun leerGuia(o: JSONObject) = GuiaDtc(
        codigo = o.getString("codigo"),
        titulo = o.getString("titulo"),
        sistema = o.getString("sistema"),
        urgencia = UrgenciaDtc.valueOf(o.getString("urgencia")),
        causas = o.textos("causas"),
        verificaciones = o.getJSONArray("verificaciones").objetos().map(::leerVerificacion),
        notasMoto = o.textos("notasMoto"),
        relacionados = o.textos("relacionados"),
    )

    private fun leerVerificacion(o: JSONObject) = VerificacionDtc(
        paso = o.getString("paso"),
        detalle = o.optString("detalle"),
        accion = o.textoONulo("accion")?.let(::leerAccion),
    )

    private fun leerAccion(clave: String): AccionGuia =
        requireNotNull(AccionGuia.desde(clave)) { "Acción desconocida «$clave»" }

    private fun JSONObject.textos(nombre: String): List<String> {
        val arreglo = optJSONArray(nombre) ?: return emptyList()
        return List(arreglo.length()) { arreglo.getString(it) }
    }

    private fun JSONArray.objetos(): List<JSONObject> = List(length()) { getJSONObject(it) }

    private fun JSONObject.textoONulo(nombre: String): String? =
        if (isNull(nombre)) null else getString(nombre).takeIf { it.isNotBlank() }
}
