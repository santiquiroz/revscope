package com.revscope.core.obd.escucha

import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R
import com.revscope.core.obd.session.EstadoViaje
import com.revscope.core.obd.session.ObdSessionManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServicioEscuchaCan @Inject constructor(
    private val sessionManager: ObdSessionManager,
    private val escucha: EscuchaCan,
    private val repositorio: CapturasCanRepositorio,
    private val resolver: StringResolver,
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
        if (sessionManager.estadoViaje.value is EstadoViaje.Grabando) {
            resolver.get(R.string.obd_listen_trip_recording)
        } else {
            null
        }
}
