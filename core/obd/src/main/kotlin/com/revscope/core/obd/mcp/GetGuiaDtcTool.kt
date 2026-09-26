package com.revscope.core.obd.mcp

import com.revscope.core.obd.taller.dtc.BaseConocimientoDtc
import com.revscope.core.obd.taller.dtc.DecodificadorDtc
import com.revscope.core.obd.taller.dtc.EstructuraDtc
import com.revscope.core.obd.taller.dtc.GuiaDtc
import com.revscope.core.obd.taller.dtc.VerificacionDtc
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class GetGuiaDtcTool @Inject constructor(
    private val base: BaseConocimientoDtc,
) : McpTool {

    override val name = "get_guia_dtc"
    override val description =
        "Guía local de un código de falla: título SAE, causas probables en orden, verificaciones numeradas con la " +
            "prueba guiada o el multímetro que corresponde y notas de moto. Si el código no está en la guía, " +
            "explica su estructura SAE J2012 (sistema, genérico o del fabricante, subsistema) sin inventar la " +
            "descripción. No necesita adaptador"
    override val inputSchema: JSONObject = McpSchemas.objeto(
        "codigo" to McpSchemas.texto("Código DTC, p. ej. P0122"),
        requeridos = listOf("codigo"),
    )

    override suspend fun call(arguments: JSONObject): String {
        val texto = arguments.optString("codigo")
        val estructura = DecodificadorDtc.decodificar(texto) ?: return codigoInvalido(texto)
        val respuesta = JSONObject()
            .put("codigo", estructura.codigo)
            .put("estructura", estructuraJson(estructura))
        val guia = base.guia(estructura.codigo)
        return if (guia == null) sinGuia(respuesta, estructura) else conGuia(respuesta, guia)
    }

    private fun codigoInvalido(texto: String): String = JSONObject()
        .put("codigo", texto)
        .put("error", "Código DTC no válido: se espera ${DecodificadorDtc.EJEMPLO_FORMATO}")
        .toString()

    private fun sinGuia(respuesta: JSONObject, estructura: EstructuraDtc): String = respuesta
        .put("en_guia_local", false)
        .put("mensaje", DecodificadorDtc.mensajeSinGuia(estructura))
        .toString()

    private fun conGuia(respuesta: JSONObject, guia: GuiaDtc): String = respuesta
        .put("en_guia_local", true)
        .put("aviso", base.catalogo.aviso)
        .put("fuente_titulo", base.catalogo.fuenteDescripciones)
        .put("guia", guiaJson(guia))
        .toString()

    private fun estructuraJson(e: EstructuraDtc): JSONObject = JSONObject()
        .put("sistema", e.sistema.nombre)
        .put("ambito", e.ambito.name.lowercase())
        .put("ambito_texto", e.ambito.etiqueta)
        .putOpt("subsistema", e.subsistema)
        .put("explicacion", e.explicacion)

    private fun guiaJson(guia: GuiaDtc): JSONObject = JSONObject()
        .put("titulo", guia.titulo)
        .put("sistema", guia.sistema)
        .put("urgencia", guia.urgencia.name)
        .put("urgencia_texto", guia.urgencia.etiqueta)
        .put("causas", JSONArray(guia.causas))
        .put("verificaciones", JSONArray(guia.verificaciones.mapIndexed(::verificacionJson)))
        .put("notas_moto", JSONArray(guia.notasMoto))
        .put("relacionados", JSONArray(guia.relacionados))

    private fun verificacionJson(indice: Int, v: VerificacionDtc): JSONObject = JSONObject()
        .put("n", indice + 1)
        .put("paso", v.paso)
        .put("detalle", v.detalle)
        .putOpt("accion", v.accion?.clave)
        .putOpt("accion_texto", v.accion?.etiqueta)
}
