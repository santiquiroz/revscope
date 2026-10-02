package com.revscope.core.obd.mcp

import com.revscope.core.obd.mcp.escritura.RegistroEscrituras
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class GetRegistroEscriturasTool @Inject constructor(
    private val registro: RegistroEscrituras,
) : McpTool {

    override val name = "get_registro_escrituras"
    override val description = "Auditoría de cada escritura que intentó el MCP: autorizada, rechazada o fallida"
    override val inputSchema = McpSchemas.optionalInt("n", "Cantidad de entradas (por defecto 20, entre 1 y 100)")

    override suspend fun call(arguments: JSONObject): String {
        val cantidad = if (arguments.has("n")) arguments.optInt("n", 20).coerceIn(1, 100) else 20
        val fecha = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.systemDefault())
        val escrituras = JSONArray(registro.ultimas(cantidad).map { entrada ->
            JSONObject()
                .put("ts", entrada.ts)
                .put("fecha", fecha.format(Instant.ofEpochMilli(entrada.ts).atZone(ZoneId.systemDefault())))
                .put("tool", entrada.tool)
                .put("resumen", entrada.resumen)
                .put("header", entrada.header ?: JSONObject.NULL)
                .put("pasos", JSONArray(entrada.pasos))
                .put("autorizacion", entrada.autorizacion)
                .put("respuestas", JSONArray(entrada.respuestas))
                .put("error", entrada.error ?: JSONObject.NULL)
        })
        return JSONObject().put("escrituras", escrituras).toString()
    }
}
