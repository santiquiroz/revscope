package com.revscope.core.obd.catalogo

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

object CodecCatalogo {

    fun leer(json: String): Pair<String?, List<EntradaCatalogo>> {
        val raiz = runCatching { JSONObject(json) }.getOrNull() ?: return null to emptyList()
        val entradas = raiz.optJSONArray("entradas") ?: JSONArray()
        return raiz.textoOpcional("aviso") to (0 until entradas.length()).mapNotNull { indice ->
            runCatching { entradas.getJSONObject(indice).aEntrada() }.getOrNull()
        }
    }

    fun aJson(entrada: EntradaCatalogo): JSONObject = JSONObject()
        .put("id", entrada.id)
        .put("marca", entrada.marca)
        .put("modelos", JSONArray(entrada.modelos))
        .put("anios", entrada.anios ?: JSONObject.NULL)
        .put("modulo", entrada.modulo ?: JSONObject.NULL)
        .put("header", entrada.header ?: JSONObject.NULL)
        .put("tipo", entrada.tipo)
        .put("pasos", JSONArray(entrada.pasos))
        .put("formula", entrada.formula ?: JSONObject.NULL)
        .put("unidad", entrada.unidad ?: JSONObject.NULL)
        .put("descripcion", entrada.descripcion)
        .put("requiereSecurityAccess", entrada.requiereSecurityAccess)
        .put("riesgo", entrada.riesgo)
        .put("fuente", entrada.fuente ?: JSONObject.NULL)
        .put("licencia", entrada.licencia ?: JSONObject.NULL)
        .put("verificado", entrada.verificado)
        .put("notas", entrada.notas ?: JSONObject.NULL)

    fun escribir(aviso: String?, entradas: List<EntradaCatalogo>): String =
        JSONObject()
            .put("version", 1)
            .put("generado", LocalDate.now().toString())
            .put("aviso", aviso ?: JSONObject.NULL)
            .put("entradas", JSONArray(entradas.map(::aJson)))
            .toString()

    private fun JSONObject.aEntrada(): EntradaCatalogo = EntradaCatalogo(
        id = getString("id"),
        marca = getString("marca"),
        modelos = getJSONArray("modelos").strings(),
        anios = textoOpcional("anios"),
        modulo = textoOpcional("modulo"),
        header = textoOpcional("header"),
        tipo = getString("tipo"),
        pasos = getJSONArray("pasos").strings(),
        formula = textoOpcional("formula"),
        unidad = textoOpcional("unidad"),
        descripcion = getString("descripcion"),
        requiereSecurityAccess = optBoolean("requiereSecurityAccess", false),
        riesgo = optString("riesgo", "bajo"),
        fuente = textoOpcional("fuente"),
        licencia = textoOpcional("licencia"),
        verificado = optBoolean("verificado", false),
        notas = textoOpcional("notas"),
    )

    private fun JSONObject.textoOpcional(clave: String): String? =
        if (isNull(clave) || !has(clave)) null else optString(clave).takeIf(String::isNotBlank)

    private fun JSONArray.strings(): List<String> =
        (0 until length()).mapNotNull { indice ->
            optString(indice).takeIf { it.isNotBlank() && it != "null" }
        }
}
