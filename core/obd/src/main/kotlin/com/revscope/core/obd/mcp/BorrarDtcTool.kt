package com.revscope.core.obd.mcp

import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.diagnostics.BorradoDtc
import com.revscope.core.obd.diagnostics.RechazoBorradoDtc
import com.revscope.core.obd.diagnostics.ReglasBorradoDtc
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.pid.PidRegistry
import com.revscope.core.obd.session.ObdSessionManager
import org.json.JSONObject
import javax.inject.Inject

/**
 * Modo 04 desde el MCP: exige el permiso BORRADO, `confirmar = "BORRAR"` y el vehículo detenido
 * según [ReglasBorradoDtc]; relee los activos antes y después.
 */
class BorrarDtcTool @Inject constructor(
    private val sessionManager: ObdSessionManager,
    private val registry: PidRegistry,
    private val notifier: McpActionNotifier,
) : McpTool {

    internal constructor(
        sessionManager: ObdSessionManager,
        registry: PidRegistry,
        notifier: McpActionNotifier,
        nowMs: () -> Long,
    ) : this(sessionManager, registry, notifier) {
        this.nowMs = nowMs
    }

    override val name = "borrar_dtc"
    override val description =
        "Borra los códigos de falla (modo 04) con el vehículo detenido y relee los activos. Reinicia los " +
            "monitores de preparación (readiness): la revisión técnico-mecánica puede rechazar el vehículo " +
            "hasta completar ciclos de manejo. Exige confirmar=\"BORRAR\""
    override val inputSchema: JSONObject = McpSchemas.objeto(
        "confirmar" to McpSchemas.enumString(listOf(CONFIRMACION), "Escribe BORRAR para confirmar"),
        requeridos = listOf("confirmar"),
    )
    override val permiso = McpPermiso.BORRADO

    private var nowMs: () -> Long = { System.currentTimeMillis() }

    override suspend fun call(arguments: JSONObject): String {
        if (sessionManager.connectionState.value !is ConnectionState.Connected) return sinEnlaceJson()
        motivoRechazo(arguments.optString("confirmar"), sessionManager.readings.value[ReglasBorradoDtc.PID_VELOCIDAD])
            ?.let { return JSONObject().put("error", it).toString() }
        return sessionManager.borrarDtcConRelectura(LEASE_OWNER).fold(
            onSuccess = { borrado -> exito(borrado) },
            onFailure = { e -> JSONObject().put("error", e.message ?: "no se pudieron borrar los DTC").toString() },
        )
    }

    internal fun motivoRechazo(confirmar: String, velocidad: ObdReading?): String? =
        ReglasBorradoDtc.evaluar(confirmar == CONFIRMACION, velocidad, nowMs())?.let(::textoRechazo)

    private fun textoRechazo(rechazo: RechazoBorradoDtc): String = when (rechazo) {
        RechazoBorradoDtc.SinConfirmar -> "falta confirmar=\"BORRAR\""
        RechazoBorradoDtc.SinVelocidadReciente ->
            "sin lectura reciente de velocidad (PID 0D): no se puede comprobar que el vehículo esté detenido"
        is RechazoBorradoDtc.EnMovimiento -> "el vehículo está en movimiento (${rechazo.kmh} km/h)"
    }

    private fun exito(borrado: BorradoDtc): String {
        notifier.avisar("El MCP borró los códigos de falla", "Antes: ${codigos(borrado.antes.activos.map { it.code })}")
        return JSONObject()
            .put("borrado", !borrado.rechazadoPorCondiciones)
            .put("rechazadoPorCondiciones", borrado.rechazadoPorCondiciones)
            .put("respuesta", borrado.respuestaCruda ?: JSONObject.NULL)
            .put("antes", DtcScanJson.scan(borrado.antes, incluirCrudo = false, nombrePid = ::nombrePid))
            .put("despues", DtcScanJson.scan(borrado.despues, incluirCrudo = false, nombrePid = ::nombrePid))
            .put("mensaje", mensaje(borrado))
            .toString()
    }

    private fun mensaje(borrado: BorradoDtc): String =
        if (borrado.rechazadoPorCondiciones) "el ECU rechazó el borrado (7F 04 22: condiciones no correctas; apaga el motor y deja el contacto)"
        else "códigos borrados; los monitores de readiness quedan incompletos hasta completar ciclos de manejo"

    private fun codigos(codes: List<String>): String = codes.joinToString().ifEmpty { "sin códigos activos" }

    private fun nombrePid(pid: String): String? = registry.getDefinition(pid)?.nameEs

    private companion object {
        const val LEASE_OWNER = "mcp:borrar_dtc"
        const val CONFIRMACION = "BORRAR"
    }
}
