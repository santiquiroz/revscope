package com.revscope.core.obd.taller.dtc

import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R
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

    fun leerGuia(o: JSONObject, resolver: StringResolver) = GuiaDtc(
        codigo = o.getString("codigo"),
        titulo = o.getString("titulo"),
        sistema = o.getString("sistema"),
        urgencia = UrgenciaDtc.valueOf(o.getString("urgencia")),
        causas = o.textos("causas"),
        verificaciones = o.getJSONArray("verificaciones").objetos().map { leerVerificacion(it, resolver) },
        notasMoto = o.textos("notasMoto"),
        relacionados = o.textos("relacionados"),
    )

    private fun leerVerificacion(o: JSONObject, resolver: StringResolver) = VerificacionDtc(
        paso = o.getString("paso"),
        detalle = o.optString("detalle"),
        accion = o.textoONulo("accion")?.let { leerAccion(it, resolver) },
    )

    private fun leerAccion(clave: String, resolver: StringResolver): AccionGuia =
        requireNotNull(AccionGuia.desde(clave)) { resolver.get(R.string.obd_dtc_validation_unknown_action, clave) }

    private fun JSONObject.textos(nombre: String): List<String> {
        val arreglo = optJSONArray(nombre) ?: return emptyList()
        return List(arreglo.length()) { arreglo.getString(it) }
    }

    private fun JSONArray.objetos(): List<JSONObject> = List(length()) { getJSONObject(it) }

    private fun JSONObject.textoONulo(nombre: String): String? =
        if (isNull(nombre)) null else getString(nombre).takeIf { it.isNotBlank() }
}
