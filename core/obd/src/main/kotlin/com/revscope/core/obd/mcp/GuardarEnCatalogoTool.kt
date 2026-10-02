package com.revscope.core.obd.mcp

import com.revscope.core.obd.catalogo.CatalogoPropietario
import com.revscope.core.obd.catalogo.EntradaCatalogo
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class GuardarEnCatalogoTool @Inject constructor(
    private val catalogo: CatalogoPropietario,
) : McpTool {
    override val name = "guardar_en_catalogo"
    override val description =
        "Guarda o actualiza una operación propietaria descubierta en este vehículo (por ejemplo, un DID hallado con leer_did). " +
            "Marca verificado=true solo si se confirmó en el vehículo."
    override val inputSchema: JSONObject = McpSchemas.objeto(
        "entrada" to JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject()
                    .put("id", McpSchemas.texto("Identificador"))
                    .put("marca", McpSchemas.texto("Fabricante"))
                    .put("modelos", JSONObject().put("type", "array").put("items", JSONObject().put("type", "string")))
                    .put("anios", McpSchemas.texto("Años aplicables"))
                    .put("modulo", McpSchemas.texto("Módulo ECU"))
                    .put("header", McpSchemas.texto("Header CAN"))
                    .put("tipo", McpSchemas.enumString(listOf("lectura", "escritura", "rutina"), "Tipo"))
                    .put("pasos", McpSchemas.arrayDeStrings("Comandos hexadecimales"))
                    .put("formula", McpSchemas.texto("Fórmula sobre bytes A-Z"))
                    .put("unidad", McpSchemas.texto("Unidad del valor"))
                    .put("descripcion", McpSchemas.texto("Descripción"))
                    .put("requiereSecurityAccess", McpSchemas.booleano("Requiere acceso de seguridad"))
                    .put("riesgo", McpSchemas.enumString(listOf("bajo", "medio", "alto"), "Riesgo"))
                    .put("fuente", McpSchemas.texto("Fuente"))
                    .put("licencia", McpSchemas.texto("Licencia"))
                    .put("notas", McpSchemas.texto("Notas")),
            )
            .put("required", JSONArray(listOf("id", "marca", "tipo", "pasos", "descripcion"))),
        "verificado" to McpSchemas.booleano("Confirmado en el vehículo"),
        requeridos = listOf("entrada"),
    )
    override val permiso = McpPermiso.CONTROL

    override suspend fun call(arguments: JSONObject): String {
        val json = arguments.optJSONObject("entrada") ?: return error("falta el objeto entrada")
        val entrada = entrada(json, arguments.optBoolean("verificado", false))
        val motivo = catalogo.guardarUsuario(entrada)
        if (motivo != null) return error(motivo)
        return JSONObject().put("guardada", true).put("id", entrada.id).toString()
    }

    private fun entrada(json: JSONObject, verificado: Boolean): EntradaCatalogo {
        val tipo = texto(json, "tipo").orEmpty()
        return EntradaCatalogo(
            id = texto(json, "id").orEmpty(),
            marca = texto(json, "marca").orEmpty(),
            modelos = strings(json.optJSONArray("modelos")),
            anios = texto(json, "anios"),
            modulo = texto(json, "modulo"),
            header = texto(json, "header"),
            tipo = tipo,
            pasos = strings(json.optJSONArray("pasos")),
            formula = texto(json, "formula"),
            unidad = texto(json, "unidad"),
            descripcion = texto(json, "descripcion").orEmpty(),
            requiereSecurityAccess = json.optBoolean("requiereSecurityAccess", false),
            riesgo = texto(json, "riesgo") ?: if (tipo == "lectura") "bajo" else "medio",
            fuente = texto(json, "fuente") ?: "descubierto con RevScope",
            licencia = texto(json, "licencia") ?: "propia",
            verificado = verificado,
            notas = texto(json, "notas"),
        )
    }

    private fun texto(json: JSONObject, clave: String): String? =
        if (!json.has(clave) || json.isNull(clave)) null else json.optString(clave).takeIf(String::isNotBlank)

    private fun strings(array: JSONArray?): List<String> =
        (0 until (array?.length() ?: 0)).mapNotNull { array?.optString(it)?.takeIf(String::isNotBlank) }

    private fun error(mensaje: String): String = JSONObject().put("error", mensaje).toString()
}
