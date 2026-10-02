package com.revscope.core.obd.mcp.escritura

import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.connection.Transport
import com.revscope.core.obd.diagnostics.uds.ClaseComando
import com.revscope.core.obd.diagnostics.uds.ComandoHex
import com.revscope.core.obd.session.ObdSessionManager
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

sealed interface PermisoEscritura {
    data class Concedido(val origen: OrigenAutorizacion) : PermisoEscritura
    data class Denegado(val motivo: String) : PermisoEscritura
}

/**
 * Único camino de escritura del MCP: guardas físicas → bloqueo de flasheo → toque o bypass →
 * concesión de diagnóstico → registro de auditoría.
 */
@Singleton
class EjecutorEscritura @Inject constructor(
    private val sessionManager: ObdSessionManager,
    private val autorizador: AutorizadorEscritura,
    private val registro: RegistroEscrituras,
) {

    internal constructor(
        sessionManager: ObdSessionManager,
        autorizador: AutorizadorEscritura,
        registro: RegistroEscrituras,
        nowMs: () -> Long,
    ) : this(sessionManager, autorizador, registro) {
        this.nowMs = nowMs
    }

    private var nowMs: () -> Long = { System.currentTimeMillis() }

    suspend fun ejecutar(
        solicitud: SolicitudEscritura,
        accion: suspend (Transport) -> List<String>,
    ): ResultadoEscritura = when (val permiso = autorizar(solicitud)) {
        is PermisoEscritura.Denegado -> ResultadoEscritura.NoAutorizada(permiso.motivo)
        is PermisoEscritura.Concedido -> correr(solicitud, permiso.origen, accion)
    }

    suspend fun autorizar(solicitud: SolicitudEscritura): PermisoEscritura {
        val denegacion = motivoGuarda(solicitud) ?: motivoBloqueo(solicitud.pasos)
        if (denegacion != null) return denegar(solicitud, "GUARDA", denegacion)
        return when (val a = autorizador.autorizar(solicitud)) {
            is Autorizacion.Concedida -> revalidar(solicitud, a.origen)
            Autorizacion.Rechazada -> denegar(solicitud, "RECHAZADA", "el dueño rechazó la escritura en el teléfono")
            Autorizacion.SinRespuesta -> denegar(solicitud, "SIN_RESPUESTA", "sin respuesta del dueño en ${AutorizadorEscritura.ESPERA_MS / 1000} s: escritura cancelada")
            Autorizacion.NotificacionesBloqueadas -> denegar(solicitud, "NOTIFICACIONES_BLOQUEADAS", TEXTO_NOTIFICACIONES)
        }
    }

    // La espera del toque dura hasta 45 s: el vehículo pudo arrancar mientras tanto.
    private fun revalidar(solicitud: SolicitudEscritura, origen: OrigenAutorizacion): PermisoEscritura =
        motivoGuarda(solicitud)?.let { denegar(solicitud, "GUARDA", it) } ?: PermisoEscritura.Concedido(origen)

    fun registrarResultado(solicitud: SolicitudEscritura, origen: OrigenAutorizacion, respuestas: List<String>, error: String?) {
        registro.registrar(entrada(solicitud, origen.name, respuestas, error))
    }

    private suspend fun correr(
        solicitud: SolicitudEscritura,
        origen: OrigenAutorizacion,
        accion: suspend (Transport) -> List<String>,
    ): ResultadoEscritura =
        sessionManager.withDiagnosticLease("mcp:${solicitud.tool}") { bt -> accion(bt) }.fold(
            onSuccess = { respuestas ->
                registrarResultado(solicitud, origen, respuestas, null)
                ResultadoEscritura.Ejecutada(origen, respuestas)
            },
            onFailure = { e ->
                val error = e.message ?: e::class.java.simpleName
                registrarResultado(solicitud, origen, emptyList(), error)
                ResultadoEscritura.Fallida(origen, error)
            },
        )

    private fun motivoGuarda(solicitud: SolicitudEscritura): String? = GuardasEscritura.evaluar(
        conectado = sessionManager.connectionState.value is ConnectionState.Connected,
        lecturas = sessionManager.readings.value,
        requiereMotorApagado = solicitud.requiereMotorApagado || incluyeReinicio(solicitud.pasos),
        ahoraMs = nowMs(),
    )?.let(GuardasEscritura::texto)

    // Un reinicio de ECU con el motor en marcha lo apaga: se exige motor apagado lo diga o no la tool.
    private fun incluyeReinicio(pasos: List<String>): Boolean =
        pasos.any { ComandoHex.validar(it).getOrNull()?.bytes?.firstOrNull() == SERVICIO_REINICIO }

    private fun motivoBloqueo(pasos: List<String>): String? {
        val bloqueado = pasos.firstOrNull { paso ->
            ComandoHex.validar(paso).getOrNull()?.clase.let { it == null || it == ClaseComando.BLOQUEADO }
        } ?: return null
        return "comando bloqueado o inválido: «$bloqueado» (la programación/flasheo no se permite por Bluetooth)"
    }

    private fun denegar(solicitud: SolicitudEscritura, autorizacion: String, motivo: String): PermisoEscritura.Denegado {
        registro.registrar(entrada(solicitud, autorizacion, emptyList(), motivo))
        return PermisoEscritura.Denegado(motivo)
    }

    private fun entrada(solicitud: SolicitudEscritura, autorizacion: String, respuestas: List<String>, error: String?) =
        EntradaRegistro(
            ts = nowMs(),
            tool = solicitud.tool,
            resumen = solicitud.resumen,
            header = solicitud.header,
            pasos = solicitud.pasos,
            autorizacion = autorizacion,
            respuestas = respuestas,
            error = error,
        )

    companion object {
        private const val SERVICIO_REINICIO = 0x11
        const val TEXTO_NOTIFICACIONES =
            "las notificaciones de RevScope están desactivadas: no se puede pedir confirmación " +
                "(actívalas o usa el bypass en Ajustes → Servidor MCP)"
    }
}

/** JSON común de una escritura para las tools; cada tool agrega su interpretación de las respuestas. */
object EscrituraJson {

    fun de(resultado: ResultadoEscritura): JSONObject = when (resultado) {
        is ResultadoEscritura.NoAutorizada -> JSONObject().put("ejecutada", false).put("motivo", resultado.motivo)
        is ResultadoEscritura.Fallida -> JSONObject()
            .put("ejecutada", false)
            .put("autorizadoPor", origen(resultado.origen))
            .put("error", resultado.error)
        is ResultadoEscritura.Ejecutada -> JSONObject()
            .put("ejecutada", true)
            .put("autorizadoPor", origen(resultado.origen))
            .put("respuestasCrudas", JSONArray(resultado.respuestas))
    }

    private fun origen(origen: OrigenAutorizacion): String = when (origen) {
        OrigenAutorizacion.TOQUE -> "toque en el teléfono"
        OrigenAutorizacion.BYPASS -> "bypass activo"
    }
}
