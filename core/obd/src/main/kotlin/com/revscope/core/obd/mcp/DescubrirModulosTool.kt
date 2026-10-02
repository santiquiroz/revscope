package com.revscope.core.obd.mcp

import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.connection.Transport
import com.revscope.core.obd.diagnostics.ModuleDiscovery
import com.revscope.core.obd.protocol.ResponseParser
import com.revscope.core.obd.session.ObdSessionManager
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class DescubrirModulosTool @Inject constructor(
    private val sessionManager: ObdSessionManager,
) : McpTool {

    override val name = "descubrir_modulos"
    override val description = "Descubre módulos CAN de 11 bits conectados al vehículo"
    override val inputSchema = McpSchemas.noArguments()

    override suspend fun call(arguments: JSONObject): String {
        if (sessionManager.connectionState.value !is ConnectionState.Connected) return sinEnlace()
        val result = sessionManager.withDiagnosticLease(LEASE_OWNER) { bt -> descubrir(bt) }
        return result.fold(
            onSuccess = { it.toString() },
            onFailure = { JSONObject().put("conectado", true).put("error", it.message ?: "no se pudieron descubrir módulos").toString() },
        )
    }

    private suspend fun descubrir(bt: Transport): JSONObject {
        val protocolo = ResponseParser.cleanResponse(bt.exchange("AT DPN\r"))
        if (!ModuleDiscovery.isCan11Bit(protocolo)) {
            return JSONObject().put("conectado", true)
                .put("error", "el descubrimiento solo funciona en CAN de 11 bits (protocolo $protocolo)")
        }
        val candidatos = ModuleDiscovery.candidateHeaders().filterNot { it.header == FUNCTIONAL_HEADER }
        val resultados = candidatos.map { candidato ->
            val sondeo = try {
                bt.targetedExchange(candidato.header, PROBE_REQUEST, PROBE_TIMEOUT_MS)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            candidato to (sondeo?.let { ModuleDiscovery.interpretProbe(candidato.header, it) }
                ?: ModuleDiscovery.ProbeResult(candidato.header, null, false))
        }.sortedByDescending { (_, resultado) -> resultado.present }
        val modulos = JSONArray(resultados.map { (candidato, resultado) ->
            JSONObject()
                .put("header", candidato.header)
                .put("etiqueta", candidato.label)
                .put("presente", resultado.present)
                .put("headerRespuesta", resultado.replyHeader ?: JSONObject.NULL)
        })
        return JSONObject().put("conectado", true).put("protocolo", protocolo)
            .put("modulos", modulos).put("sondeados", candidatos.size)
    }

    private fun sinEnlace(): String =
        JSONObject().put("conectado", false).put("mensaje", "vehículo no conectado").toString()

    private companion object {
        const val LEASE_OWNER = "mcp:descubrir_modulos"
        const val FUNCTIONAL_HEADER = "7DF"
        const val PROBE_REQUEST = "22 F190"
        const val PROBE_TIMEOUT_MS = 400L
    }
}
