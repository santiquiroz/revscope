package com.revscope.core.obd.taller.pruebas

import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.session.ObdSessionManager
import com.revscope.core.obd.taller.FormatoTaller

data class ContextoPrueba(
    val conectado: Boolean,
    val lecturas: Map<String, ObdReading>,
    val soportado: (String) -> Boolean,
    val ahoraMs: Long,
) {
    // Una lectura vieja (el sondeo pausado, un viaje anterior) no dice cómo está la moto ahora.
    fun reciente(pid: String): ObdReading? = lecturas[pid]?.takeIf { ahoraMs - it.timestamp <= VIGENCIA_MS }

    fun velocidadKmh(): Double? = listOfNotNull(reciente(PID_VELOCIDAD), reciente(ObdSessionManager.GPS_SPEED_PID))
        .maxOfOrNull { it.value }

    companion object {
        const val VIGENCIA_MS = 10_000L
        const val PID_RPM = "0C"
        const val PID_VELOCIDAD = "0D"
    }
}

data class ResultadoPrecondicion(val texto: String, val cumple: Boolean, val queHacer: String? = null)

sealed interface Precondicion {
    fun evaluar(ctx: ContextoPrueba): ResultadoPrecondicion

    data object AdaptadorConectado : Precondicion {
        override fun evaluar(ctx: ContextoPrueba) = if (ctx.conectado) {
            ResultadoPrecondicion("Adaptador conectado", cumple = true)
        } else {
            ResultadoPrecondicion("Sin adaptador conectado", cumple = false, "Conecta el adaptador y pon el contacto")
        }
    }

    data object MotorApagado : Precondicion {
        override fun evaluar(ctx: ContextoPrueba): ResultadoPrecondicion {
            val rpm = ctx.reciente(ContextoPrueba.PID_RPM)?.value
                ?: return ResultadoPrecondicion("Motor apagado (sin lectura de RPM)", cumple = true)
            if (rpm < 1.0) return ResultadoPrecondicion("Motor apagado (RPM = 0)", cumple = true)
            return ResultadoPrecondicion(
                "Motor encendido (${FormatoTaller.numero(rpm, 0)} rpm)",
                cumple = false,
                "Apaga el motor y deja el contacto puesto: a fondo con el motor encendido no es un barrido seguro",
            )
        }
    }

    data object MotoDetenida : Precondicion {
        override fun evaluar(ctx: ContextoPrueba): ResultadoPrecondicion {
            val kmh = ctx.velocidadKmh()
                ?: return ResultadoPrecondicion("Moto detenida (sin dato de velocidad: confírmalo tú)", cumple = true)
            if (kmh <= 0.0) return ResultadoPrecondicion("Moto detenida (0 km/h)", cumple = true)
            return ResultadoPrecondicion(
                "Moto en movimiento (${FormatoTaller.numero(kmh, 0)} km/h)",
                cumple = false,
                "Detén la moto: la prueba se hace con la moto quieta",
            )
        }
    }

    data class PidDisponible(val pid: String, val nombre: String, val alternativa: String) : Precondicion {
        override fun evaluar(ctx: ContextoPrueba) = if (ctx.soportado(pid)) {
            ResultadoPrecondicion("$nombre disponible en esta ECU (PID $pid)", cumple = true)
        } else {
            ResultadoPrecondicion("$nombre no disponible en esta ECU (PID $pid)", cumple = false, alternativa)
        }
    }
}
