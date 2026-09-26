package com.revscope.core.obd.diagnostics

import com.revscope.core.obd.model.ObdReading

sealed interface RechazoBorradoDtc {
    data object SinConfirmar : RechazoBorradoDtc
    data object SinVelocidadReciente : RechazoBorradoDtc
    data class EnMovimiento(val kmh: Int) : RechazoBorradoDtc
}

/**
 * Cuándo se puede mandar el modo 04: confirmado y con el vehículo detenido según una lectura de
 * velocidad (0D) reciente. La comparten la pantalla de DTC y la tool `borrar_dtc` del MCP.
 */
object ReglasBorradoDtc {
    const val PID_VELOCIDAD = "0D"
    const val MAX_EDAD_VELOCIDAD_MS = 2_000L

    fun evaluar(confirmado: Boolean, velocidad: ObdReading?, ahoraMs: Long): RechazoBorradoDtc? = when {
        !confirmado -> RechazoBorradoDtc.SinConfirmar
        velocidad == null || ahoraMs - velocidad.timestamp > MAX_EDAD_VELOCIDAD_MS -> RechazoBorradoDtc.SinVelocidadReciente
        velocidad.value > 0.0 -> RechazoBorradoDtc.EnMovimiento(velocidad.value.toInt())
        else -> null
    }
}
