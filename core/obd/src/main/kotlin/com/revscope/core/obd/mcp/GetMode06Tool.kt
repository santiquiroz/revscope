package com.revscope.core.obd.mcp

import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.connection.Transport
import com.revscope.core.obd.protocol.Mode06Parser
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.obd.workshop.Mode06MidNames
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class GetMode06Tool @Inject constructor(
    private val sessionManager: ObdSessionManager,
) : McpTool {

    override val name = "get_mode06"
    override val description =
        "Resultados de pruebas de monitoreo a bordo (modo 06): contadores de fallos de encendido, catalizador y sensores O2 con límites"
    override val inputSchema = McpSchemas.noArguments()

    override suspend fun call(arguments: JSONObject): String {
        if (sessionManager.connectionState.value !is ConnectionState.Connected) return sinEnlace()
        val lectura = sessionManager.withDiagnosticLease(LEASE_OWNER) { bt -> leerPruebas(bt) }
        val resultado = lectura.getOrElse { e ->
            return JSONObject().put("conectado", true).put("error", e.message ?: "no se pudo leer el modo 06").toString()
        }
        if (!resultado.soportado) {
            return JSONObject().put("conectado", true).put("soportado", false)
                .put("mensaje", "la ECU no soporta el modo 06").put("pruebas", JSONArray()).toString()
        }
        return JSONObject().put("conectado", true).put("soportado", true)
            .put("pruebas", resultado.pruebas).toString()
    }

    private suspend fun leerPruebas(bt: Transport): LecturaMode06 {
        val soportados = linkedSetOf<String>()
        var base = 0
        var rawPrimero: String? = null
        while (base <= 0xE0) {
            val raw = bt.exchange("06 ${base.hexByte()}\r")
            if (rawPrimero == null) rawPrimero = raw
            val bloque = Mode06Parser.parseSupportedMids(raw)
            soportados += bloque.filterNot { it.toInt(16) == base + 0x20 }
            if ((base + 0x20).toString(16).uppercase().padStart(2, '0') !in bloque) break
            base += 0x20
        }
        if (rawPrimero != null && rawPrimero.contains("NO DATA", ignoreCase = true)) {
            return LecturaMode06(false, JSONArray())
        }
        val pruebas = JSONArray()
        for (mid in soportados.sortedBy { it.toInt(16) }) {
            val raw = bt.exchange("06 $mid\r")
            Mode06Parser.parseTestResults(raw).forEach { pruebas.put(prueba(it)) }
        }
        return LecturaMode06(true, pruebas)
    }

    private fun prueba(test: Mode06Parser.TestResult): JSONObject = JSONObject()
        .put("mid", test.mid)
        .put("nombre", Mode06MidNames.nameFor(test.mid))
        .put("tid", test.tid)
        .put("valor", test.value)
        .put("minimo", test.min)
        .put("maximo", test.max)
        .put("unidad", test.unit)
        .put("pasa", test.pass)

    private fun Int.hexByte(): String = toString(16).uppercase().padStart(2, '0')

    private fun sinEnlace(): String =
        JSONObject().put("conectado", false).put("mensaje", "vehículo no conectado").toString()

    private data class LecturaMode06(val soportado: Boolean, val pruebas: JSONArray)

    private companion object {
        const val LEASE_OWNER = "mcp:get_mode06"
    }
}
