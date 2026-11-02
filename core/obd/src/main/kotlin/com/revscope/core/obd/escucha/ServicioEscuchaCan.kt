package com.revscope.core.obd.escucha

import com.revscope.core.obd.session.EstadoViaje
import com.revscope.core.obd.session.ObdSessionManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServicioEscuchaCan @Inject constructor(
    private val sessionManager: ObdSessionManager,
    private val escucha: EscuchaCan,
    private val repositorio: CapturasCanRepositorio,
) {

    suspend fun escuchar(opciones: OpcionesEscucha): Result<CapturaCan> =
        capturar(opciones).onSuccess { repositorio.guardar(it) }

    suspend fun capturar(opciones: OpcionesEscucha): Result<CapturaCan> {
        motivoRechazo()?.let { return Result.failure(IllegalStateException(it)) }
        return sessionManager.withDiagnosticLease("escucha_can", opciones.duracionMs + 15_000) { bt ->
            escucha.capturar(bt, opciones)
        }
    }

    fun motivoRechazo(): String? =
        if (sessionManager.estadoViaje.value is EstadoViaje.Grabando) VIAJE_GRABANDO else null

    companion object {
        const val VIAJE_GRABANDO = "hay un viaje grabando; el modo escucha detiene el sondeo"
    }
}
