package com.revscope.core.obd.mcp.escritura

import com.revscope.core.obd.diagnostics.RechazoBorradoDtc
import com.revscope.core.obd.diagnostics.ReglasBorradoDtc
import com.revscope.core.obd.model.ObdReading

sealed interface RechazoGuarda {
    data object SinEnlace : RechazoGuarda
    data object SinVelocidadReciente : RechazoGuarda
    data class EnMovimiento(val kmh: Int) : RechazoGuarda
    data object SinRpmReciente : RechazoGuarda
    data class MotorEncendido(val rpm: Int) : RechazoGuarda
}

/** Guardas físicas de toda escritura: ni el toque ni el bypass las saltan. */
object GuardasEscritura {
    const val PID_RPM = "0C"

    fun evaluar(
        conectado: Boolean,
        lecturas: Map<String, ObdReading>,
        requiereMotorApagado: Boolean,
        ahoraMs: Long,
    ): RechazoGuarda? {
        if (!conectado) return RechazoGuarda.SinEnlace
        rechazoPorVelocidad(lecturas[ReglasBorradoDtc.PID_VELOCIDAD], ahoraMs)?.let { return it }
        return if (requiereMotorApagado) rechazoPorRpm(lecturas[PID_RPM], ahoraMs) else null
    }

    fun texto(rechazo: RechazoGuarda): String = when (rechazo) {
        RechazoGuarda.SinEnlace -> "vehículo no conectado"
        RechazoGuarda.SinVelocidadReciente ->
            "sin lectura reciente de velocidad (PID 0D): no se puede comprobar que el vehículo esté detenido"
        is RechazoGuarda.EnMovimiento -> "el vehículo está en movimiento (${rechazo.kmh} km/h)"
        RechazoGuarda.SinRpmReciente ->
            "sin lectura reciente de RPM (PID 0C): no se puede comprobar que el motor esté apagado"
        is RechazoGuarda.MotorEncendido -> "el motor está encendido (${rechazo.rpm} rpm): apágalo y deja el contacto"
    }

    private fun rechazoPorVelocidad(velocidad: ObdReading?, ahoraMs: Long): RechazoGuarda? =
        when (val r = ReglasBorradoDtc.evaluar(confirmado = true, velocidad = velocidad, ahoraMs = ahoraMs)) {
            null, RechazoBorradoDtc.SinConfirmar -> null
            RechazoBorradoDtc.SinVelocidadReciente -> RechazoGuarda.SinVelocidadReciente
            is RechazoBorradoDtc.EnMovimiento -> RechazoGuarda.EnMovimiento(r.kmh)
        }

    private fun rechazoPorRpm(rpm: ObdReading?, ahoraMs: Long): RechazoGuarda? = when {
        rpm == null || ahoraMs - rpm.timestamp > ReglasBorradoDtc.MAX_EDAD_VELOCIDAD_MS -> RechazoGuarda.SinRpmReciente
        rpm.value > 0.0 -> RechazoGuarda.MotorEncendido(rpm.value.toInt())
        else -> null
    }
}
