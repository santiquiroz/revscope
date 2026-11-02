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

    suspend fun escuchar(opciones: OpcionesEscucha): Result<CapturaCan> {
        if (sessionManager.estadoViaje.value is EstadoViaje.Grabando) {
            return Result.failure(IllegalStateException("hay un viaje grabando; el modo escucha detiene el sondeo"))
        }
        return sessionManager.withDiagnosticLease("escucha_can", opciones.duracionMs + 15_000) { bt ->
            escucha.capturar(bt, opciones)
        }.onSuccess { repositorio.guardar(it) }
    }
}
