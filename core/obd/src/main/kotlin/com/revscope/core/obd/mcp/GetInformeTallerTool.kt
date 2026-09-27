package com.revscope.core.obd.mcp

import com.revscope.core.obd.taller.informe.GeneradorInformeTaller
import com.revscope.core.obd.taller.informe.InformeTallerHtml
import com.revscope.core.obd.taller.informe.InformeTallerJson
import org.json.JSONObject
import javax.inject.Inject

private const val MAX_HTML_BYTES = 200 * 1024
private const val AVISO_HTML_TRUNCADO =
    "HTML truncado a 200 KiB para transportarlo por MCP. Exporta el informe desde la app para obtenerlo completo."

class GetInformeTallerTool internal constructor(
    private val generarJson: suspend (Long) -> String?,
    private val generarHtml: suspend (Long) -> String?,
) : McpTool {

    @Inject
    constructor(generador: GeneradorInformeTaller) : this(
        generarJson = { sesionId -> generador.generar(sesionId)?.let(InformeTallerJson::render) },
        generarHtml = { sesionId -> generador.generar(sesionId)?.let(InformeTallerHtml::render) },
    )

    override val name = "get_informe_taller"
    override val description =
        "Informe completo de una sesión de Taller en JSON o HTML autocontenido. El HTML se limita a 200 KiB y " +
            "avisa si fue truncado. No necesita adaptador"
    override val inputSchema: JSONObject = McpSchemas.objeto(
        "sesion_id" to McpSchemas.entero("Id de la sesión de Taller"),
        "formato" to McpSchemas.enumString(listOf("json", "html"), "Formato de salida"),
        requeridos = listOf("sesion_id", "formato"),
    )
    override val permiso = McpPermiso.LECTURA

    override suspend fun call(arguments: JSONObject): String {
        val sesionId = validarSesionId(arguments) ?: return errorSesionId(arguments)
        return when (arguments.opt("formato")) {
            "json" -> generarJson(sesionId) ?: sesionInexistente(sesionId)
            "html" -> generarHtml(sesionId)?.let(::respuestaHtml) ?: sesionInexistente(sesionId)
            else -> errorFormato(arguments)
        }
    }

    private fun validarSesionId(arguments: JSONObject): Long? {
        val numero = arguments.opt("sesion_id") as? Number ?: return null
        val id = numero.toLong()
        return id.takeIf { it > 0 && it.toDouble() == numero.toDouble() }
    }

    private fun errorSesionId(arguments: JSONObject): String {
        val mensaje = if (arguments.has("sesion_id")) {
            "sesion_id debe ser un entero positivo"
        } else {
            "Falta sesion_id"
        }
        return error(mensaje)
    }

    private fun errorFormato(arguments: JSONObject): String = if (arguments.has("formato")) {
        error("Formato no válido: usa json o html")
    } else {
        error("Falta formato: usa json o html")
    }

    private fun sesionInexistente(sesionId: Long): String = error("No existe la sesión $sesionId")

    private fun error(mensaje: String): String = JSONObject().put("error", mensaje).toString()

    private fun respuestaHtml(html: String): String {
        val limitado = limitarUtf8(html)
        return JSONObject()
            .put("html", limitado.texto)
            .put("truncado", limitado.truncado)
            .put("aviso", if (limitado.truncado) AVISO_HTML_TRUNCADO else JSONObject.NULL)
            .toString()
    }

    private fun limitarUtf8(texto: String): HtmlLimitado {
        val bytes = texto.toByteArray(Charsets.UTF_8)
        if (bytes.size <= MAX_HTML_BYTES) return HtmlLimitado(texto, truncado = false)
        var fin = MAX_HTML_BYTES
        while (fin > 0 && (bytes[fin].toInt() and 0xC0) == 0x80) fin--
        return HtmlLimitado(bytes.copyOf(fin).toString(Charsets.UTF_8), truncado = true)
    }

    private data class HtmlLimitado(val texto: String, val truncado: Boolean)
}
