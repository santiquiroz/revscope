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

// [aviso]: se puede seguir, pero hay algo que el técnico debe saber o confirmar (p. ej. un arranque tibio).
data class ResultadoPrecondicion(val texto: String, val cumple: Boolean, val queHacer: String? = null, val aviso: Boolean = false)

sealed interface Precondicion {
    fun evaluar(ctx: ContextoPrueba): ResultadoPrecondicion

    data object AdaptadorConectado : Precondicion {
        override fun evaluar(ctx: ContextoPrueba) = if (ctx.conectado) {
            ResultadoPrecondicion("Adaptador conectado", cumple = true)
        } else {
            ResultadoPrecondicion("Sin adaptador conectado", cumple = false, "Conecta el adaptador y pon el contacto")
        }
    }

    data class MotorApagado(val queHacer: String) : Precondicion {
        override fun evaluar(ctx: ContextoPrueba): ResultadoPrecondicion {
            val rpm = ctx.reciente(ContextoPrueba.PID_RPM)?.value
                ?: return ResultadoPrecondicion("Motor apagado (sin lectura de RPM)", cumple = true)
            if (rpm < 1.0) return ResultadoPrecondicion("Motor apagado (RPM = 0)", cumple = true)
            return ResultadoPrecondicion("Motor encendido (${FormatoTaller.numero(rpm, 0)} rpm)", cumple = false, queHacer)
        }
    }

    data class MotorEncendido(val minRpm: Double = MIN_RPM_ENCENDIDO) : Precondicion {
        override fun evaluar(ctx: ContextoPrueba): ResultadoPrecondicion {
            val rpm = ctx.reciente(ContextoPrueba.PID_RPM)?.value
                ?: return ResultadoPrecondicion("Sin lectura de RPM", cumple = false, ENCENDER)
            if (rpm > minRpm) return ResultadoPrecondicion("Motor encendido (${FormatoTaller.numero(rpm, 0)} rpm)", cumple = true)
            return ResultadoPrecondicion("Motor apagado o a punto de apagarse (${FormatoTaller.numero(rpm, 0)} rpm)", cumple = false, ENCENDER)
        }

        private companion object {
            const val MIN_RPM_ENCENDIDO = 400.0
            const val ENCENDER = "Enciende el motor y déjalo en mínimo sin tocar el acelerador"
        }
    }

    // La ECU no informa la marcha por OBD: lo confirma el técnico.
    data object EnNeutro : Precondicion {
        override fun evaluar(ctx: ContextoPrueba) = ResultadoPrecondicion(
            "Caja en neutro: confírmalo tú (la ECU no lo informa)",
            cumple = true,
            "Vas a acelerar hasta unas 3 000 rpm: pon la caja en neutro antes de empezar",
            aviso = true,
        )
    }

    // Frío si motor y aire difieren menos de 5 °C (típico tras ≥ 6 h de reposo). Tibio no impide la prueba:
    // el resultado sale etiquetado como «arranque tibio».
    data object MotorFrio : Precondicion {
        const val DIFERENCIA_MAX_C = 5.0

        override fun evaluar(ctx: ContextoPrueba): ResultadoPrecondicion {
            val ect = ctx.reciente(PID_ECT)?.value
            val iat = ctx.reciente(PID_IAT)?.value
            if (ect == null || iat == null) return sinTemperaturas()
            val medidas = "motor ${grados(ect)}, aire ${grados(iat)}"
            if (ect - iat < DIFERENCIA_MAX_C) return ResultadoPrecondicion("Motor frío ($medidas)", cumple = true)
            return ResultadoPrecondicion(
                "Motor tibio ($medidas): la prueba seguirá como «arranque tibio»",
                cumple = true,
                "Para un arranque en frío de verdad, repítela tras el reposo de la noche (≥ 6 h, típico)",
                aviso = true,
            )
        }

        private fun sinTemperaturas() = ResultadoPrecondicion(
            "Sin lectura de la temperatura del motor o del aire",
            cumple = true,
            "Se decidirá con el paso de contacto; si la ECU no las reporta, el resultado no dirá si estaba frío",
            aviso = true,
        )

        private fun grados(x: Double) = "${FormatoTaller.numero(x, 0)} °C"

        private const val PID_ECT = "05"
        private const val PID_IAT = "0F"
    }

    // No se puede leer si la farola está prendida: se avisa para que el voltaje en reposo se lea con esa carga.
    data object LucesConContacto : Precondicion {
        override fun evaluar(ctx: ContextoPrueba) = ResultadoPrecondicion(
            "Farola y luces: en muchas motos la farola queda encendida con el contacto (AHO)",
            cumple = true,
            "El voltaje en reposo baja por eso y la banda lo tiene en cuenta; en un carro, apaga las luces antes de empezar",
            aviso = true,
        )
    }

    // Sin PID 33 la prueba sigue con el barómetro del teléfono o la altitud GPS, que es una estimación.
    data object BarometricaDisponible : Precondicion {
        override fun evaluar(ctx: ContextoPrueba) = if (ctx.soportado(PID_BARO)) {
            ResultadoPrecondicion("Presión barométrica de la ECU disponible (PID $PID_BARO)", cumple = true)
        } else {
            ResultadoPrecondicion(
                "La ECU no informa la presión barométrica (PID $PID_BARO)",
                cumple = true,
                "Se usará el barómetro del teléfono o, si no tiene, la altitud GPS (estimada, ±3 kPa): activa la ubicación",
                aviso = true,
            )
        }

        private const val PID_BARO = "33"
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
