package com.revscope.core.obd.taller.pruebas

import com.revscope.core.common.i18n.LocaleNumbers
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R
import com.revscope.core.obd.model.ObdReading
import com.revscope.core.obd.session.ObdSessionManager

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
    fun evaluar(ctx: ContextoPrueba, resolver: StringResolver): ResultadoPrecondicion

    data object AdaptadorConectado : Precondicion {
        override fun evaluar(ctx: ContextoPrueba, resolver: StringResolver) = if (ctx.conectado) {
            ResultadoPrecondicion(resolver.get(R.string.obd_precondicion_adaptador_conectado), cumple = true)
        } else {
            ResultadoPrecondicion(
                resolver.get(R.string.obd_precondicion_sin_adaptador),
                cumple = false,
                resolver.get(R.string.obd_precondicion_sin_adaptador_que_hacer),
            )
        }
    }

    data class MotorApagado(val queHacer: String) : Precondicion {
        override fun evaluar(ctx: ContextoPrueba, resolver: StringResolver): ResultadoPrecondicion {
            val rpm = ctx.reciente(ContextoPrueba.PID_RPM)?.value
                ?: return ResultadoPrecondicion(resolver.get(R.string.obd_precondicion_motor_apagado_sin_rpm), cumple = true)
            if (rpm < 1.0) return ResultadoPrecondicion(resolver.get(R.string.obd_precondicion_motor_apagado_rpm_cero), cumple = true)
            val texto = resolver.get(R.string.obd_precondicion_motor_encendido, entero(rpm, resolver))
            return ResultadoPrecondicion(texto, cumple = false, queHacer)
        }
    }

    data class MotorEncendido(val minRpm: Double = MIN_RPM_ENCENDIDO) : Precondicion {
        override fun evaluar(ctx: ContextoPrueba, resolver: StringResolver): ResultadoPrecondicion {
            val encender = resolver.get(R.string.obd_precondicion_encender)
            val rpm = ctx.reciente(ContextoPrueba.PID_RPM)?.value
                ?: return ResultadoPrecondicion(resolver.get(R.string.obd_precondicion_sin_rpm), cumple = false, encender)
            val cifra = entero(rpm, resolver)
            if (rpm > minRpm) return ResultadoPrecondicion(resolver.get(R.string.obd_precondicion_motor_encendido, cifra), cumple = true)
            return ResultadoPrecondicion(resolver.get(R.string.obd_precondicion_motor_apagandose, cifra), cumple = false, encender)
        }

        private companion object {
            const val MIN_RPM_ENCENDIDO = 400.0
        }
    }

    // La ECU no informa la marcha por OBD: lo confirma el técnico.
    data object EnNeutro : Precondicion {
        override fun evaluar(ctx: ContextoPrueba, resolver: StringResolver) = ResultadoPrecondicion(
            resolver.get(R.string.obd_precondicion_neutro),
            cumple = true,
            resolver.get(R.string.obd_precondicion_neutro_que_hacer),
            aviso = true,
        )
    }

    // Frío si motor y aire difieren menos de 5 °C (típico tras ≥ 6 h de reposo). Tibio no impide la prueba:
    // el resultado sale etiquetado como «arranque tibio».
    data object MotorFrio : Precondicion {
        const val DIFERENCIA_MAX_C = 5.0

        override fun evaluar(ctx: ContextoPrueba, resolver: StringResolver): ResultadoPrecondicion {
            val ect = ctx.reciente(PID_ECT)?.value
            val iat = ctx.reciente(PID_IAT)?.value
            if (ect == null || iat == null) return sinTemperaturas(resolver)
            val medidas = resolver.get(R.string.obd_precondicion_temperaturas, grados(ect, resolver), grados(iat, resolver))
            if (ect - iat < DIFERENCIA_MAX_C) {
                return ResultadoPrecondicion(resolver.get(R.string.obd_precondicion_motor_frio, medidas), cumple = true)
            }
            return ResultadoPrecondicion(
                resolver.get(R.string.obd_precondicion_motor_tibio, medidas),
                cumple = true,
                resolver.get(R.string.obd_precondicion_motor_tibio_que_hacer),
                aviso = true,
            )
        }

        private fun sinTemperaturas(resolver: StringResolver) = ResultadoPrecondicion(
            resolver.get(R.string.obd_precondicion_sin_temperaturas),
            cumple = true,
            resolver.get(R.string.obd_precondicion_sin_temperaturas_que_hacer),
            aviso = true,
        )

        private fun grados(x: Double, resolver: StringResolver) = entero(x, resolver) + ESPACIO_DURO + GRADOS_C

        private const val PID_ECT = "05"
        private const val PID_IAT = "0F"
        private const val GRADOS_C = "°C"
    }

    // No se puede leer si la farola está prendida: se avisa para que el voltaje en reposo se lea con esa carga.
    data object LucesConContacto : Precondicion {
        override fun evaluar(ctx: ContextoPrueba, resolver: StringResolver) = ResultadoPrecondicion(
            resolver.get(R.string.obd_precondicion_luces),
            cumple = true,
            resolver.get(R.string.obd_precondicion_luces_que_hacer),
            aviso = true,
        )
    }

    // Sin PID 33 la prueba sigue con el barómetro del teléfono o la altitud GPS, que es una estimación.
    data object BarometricaDisponible : Precondicion {
        override fun evaluar(ctx: ContextoPrueba, resolver: StringResolver) = if (ctx.soportado(PID_BARO)) {
            ResultadoPrecondicion(resolver.get(R.string.obd_precondicion_baro_disponible, PID_BARO), cumple = true)
        } else {
            ResultadoPrecondicion(
                resolver.get(R.string.obd_precondicion_baro_no_disponible, PID_BARO),
                cumple = true,
                resolver.get(R.string.obd_precondicion_baro_que_hacer),
                aviso = true,
            )
        }

        private const val PID_BARO = "33"
    }

    data object MotoDetenida : Precondicion {
        override fun evaluar(ctx: ContextoPrueba, resolver: StringResolver): ResultadoPrecondicion {
            val kmh = ctx.velocidadKmh()
                ?: return ResultadoPrecondicion(resolver.get(R.string.obd_precondicion_moto_detenida_sin_velocidad), cumple = true)
            if (kmh <= 0.0) return ResultadoPrecondicion(resolver.get(R.string.obd_precondicion_moto_detenida), cumple = true)
            return ResultadoPrecondicion(
                resolver.get(R.string.obd_precondicion_moto_en_movimiento, entero(kmh, resolver)),
                cumple = false,
                resolver.get(R.string.obd_precondicion_moto_en_movimiento_que_hacer),
            )
        }
    }

    data class PidDisponible(val pid: String, val nombre: String, val alternativa: String) : Precondicion {
        override fun evaluar(ctx: ContextoPrueba, resolver: StringResolver) = if (ctx.soportado(pid)) {
            ResultadoPrecondicion(resolver.get(R.string.obd_precondicion_pid_disponible, nombre, pid), cumple = true)
        } else {
            ResultadoPrecondicion(resolver.get(R.string.obd_precondicion_pid_no_disponible, nombre, pid), cumple = false, alternativa)
        }
    }
}

private const val ESPACIO_DURO = " "

private fun entero(x: Double, resolver: StringResolver) = LocaleNumbers.decimal(x, 0, resolver.locale)
