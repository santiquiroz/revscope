package com.revscope.core.obd.workshop

import com.revscope.core.common.i18n.LocaleNumbers
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R
import com.revscope.core.obd.protocol.ReadinessParser
import kotlin.math.abs

/** Deterministic, offline interpretation rules for workshop diagnostics. */
object DiagnosticRules {

    enum class Nivel { OK, ATENCION, FALLA }

    enum class Refrigeracion {
        LIQUIDA, AIRE_ACEITE;

        companion object {
            fun desde(valor: String?): Refrigeracion = entries.firstOrNull { it.name == valor } ?: LIQUIDA
        }
    }

    data class Diagnosis(
        val nivel: Nivel,
        val area: String,
        val titulo: String,
        val causaProbable: String,
    )

    fun evaluarFuelTrimLargo(ltft: Double, resolver: StringResolver): Diagnosis {
        val area = resolver.get(R.string.obd_diag_area_mixture)
        val valor = decimal(ltft, 1, resolver)
        return when {
            abs(ltft) <= LTFT_OK -> Diagnosis(
                Nivel.OK, area, resolver.get(R.string.obd_diag_ltft_ok, valor),
                resolver.get(R.string.obd_diag_ltft_ok_cause),
            )
            ltft > LTFT_FALLA || ltft < -LTFT_FALLA -> Diagnosis(
                Nivel.FALLA, area, resolver.get(R.string.obd_diag_ltft_fail, valor),
                resolver.get(R.string.obd_diag_ltft_fail_cause),
            )
            ltft > 0 -> Diagnosis(
                Nivel.ATENCION, area, resolver.get(R.string.obd_diag_ltft_lean, valor),
                resolver.get(R.string.obd_diag_ltft_lean_cause),
            )
            else -> Diagnosis(
                Nivel.ATENCION, area, resolver.get(R.string.obd_diag_ltft_rich, valor),
                resolver.get(R.string.obd_diag_ltft_rich_cause),
            )
        }
    }

    fun evaluarTrimCombinado(stft: Double, ltft: Double, resolver: StringResolver): Diagnosis {
        val total = stft + ltft
        val area = resolver.get(R.string.obd_diag_area_mixture)
        val valor = decimal(total, 1, resolver)
        return if (abs(total) > TRIM_TOTAL_MAX) Diagnosis(
            Nivel.ATENCION, area, resolver.get(R.string.obd_diag_total_trim_high, valor),
            resolver.get(R.string.obd_diag_total_trim_high_cause, TRIM_TOTAL_MAX.toString()),
        ) else Diagnosis(
            Nivel.OK, area, resolver.get(R.string.obd_diag_total_trim_ok, valor),
            resolver.get(R.string.obd_diag_total_trim_ok_cause),
        )
    }

    fun evaluarO2(voltajes: List<Double>, resolver: StringResolver): Diagnosis {
        val area = resolver.get(R.string.obd_diag_area_o2)
        if (voltajes.size < O2_MIN_MUESTRAS) return Diagnosis(
            Nivel.OK, area, resolver.get(R.string.obd_diag_o2_few_samples),
            resolver.get(R.string.obd_diag_o2_few_samples_cause),
        )
        val clavadoBajo = voltajes.all { it < O2_BAJO }
        val clavadoAlto = voltajes.all { it > O2_ALTO }
        return when {
            clavadoBajo -> Diagnosis(
                Nivel.ATENCION, area, resolver.get(R.string.obd_diag_o2_stuck_low, decimal(O2_BAJO, 1, resolver)),
                resolver.get(R.string.obd_diag_o2_stuck_low_cause),
            )
            clavadoAlto -> Diagnosis(
                Nivel.ATENCION, area, resolver.get(R.string.obd_diag_o2_stuck_high, decimal(O2_ALTO, 1, resolver)),
                resolver.get(R.string.obd_diag_o2_stuck_high_cause),
            )
            else -> Diagnosis(
                Nivel.OK, area, resolver.get(R.string.obd_diag_o2_switching),
                resolver.get(R.string.obd_diag_o2_switching_cause),
            )
        }
    }

    fun evaluarVoltaje(volts: Double, motorEncendido: Boolean, resolver: StringResolver): Diagnosis {
        val area = resolver.get(R.string.obd_diag_area_electrical)
        val valor = decimal(volts, 1, resolver)
        return when {
            motorEncendido && volts < VOLT_MIN_MARCHA -> Diagnosis(
                Nivel.ATENCION, area, resolver.get(R.string.obd_diag_voltage_low_running, valor),
                resolver.get(R.string.obd_diag_voltage_low_running_cause),
            )
            !motorEncendido && volts < VOLT_MIN_REPOSO -> Diagnosis(
                Nivel.ATENCION, area, resolver.get(R.string.obd_diag_battery_low, valor),
                resolver.get(R.string.obd_diag_battery_low_cause),
            )
            else -> Diagnosis(
                Nivel.OK, area, resolver.get(R.string.obd_diag_voltage_ok, valor),
                resolver.get(R.string.obd_diag_voltage_ok_cause),
            )
        }
    }

    fun evaluarTemperatura(
        tempC: Double,
        refrigeracion: Refrigeracion,
        motorEncendido: Boolean?,
        resolver: StringResolver,
    ): Diagnosis {
        val area = resolver.get(R.string.obd_diag_area_cooling)
        val valor = decimal(tempC, 0, resolver)
        return when {
            tempC <= limiteTemperatura(refrigeracion) -> Diagnosis(
                Nivel.OK, area, resolver.get(R.string.obd_diag_temperature_ok, valor),
                resolver.get(R.string.obd_diag_temperature_ok_cause),
            )
            motorEncendido == false -> Diagnosis(
                Nivel.ATENCION, area, resolver.get(R.string.obd_diag_temp_high_engine_off, valor),
                resolver.get(R.string.obd_diag_temp_high_engine_off_cause),
            )
            else -> Diagnosis(
                Nivel.FALLA, area, resolver.get(R.string.obd_diag_overheating, valor),
                resolver.get(causaSobrecalentamiento(refrigeracion)),
            )
        }
    }

    private fun limiteTemperatura(refrigeracion: Refrigeracion): Double = when (refrigeracion) {
        Refrigeracion.LIQUIDA -> TEMP_MAX_LIQUIDA
        Refrigeracion.AIRE_ACEITE -> TEMP_MAX_AIRE_ACEITE
    }

    private fun causaSobrecalentamiento(refrigeracion: Refrigeracion): Int = when (refrigeracion) {
        Refrigeracion.LIQUIDA -> R.string.obd_diag_overheating_cause
        Refrigeracion.AIRE_ACEITE -> R.string.obd_diag_overheating_cause_air_oil
    }

    fun evaluarReadiness(status: ReadinessParser.ReadinessStatus, resolver: StringResolver): List<Diagnosis> = buildList {
        val area = resolver.get(R.string.obd_diag_area_readiness)
        if (status.milOn) add(Diagnosis(
            Nivel.FALLA, area, resolver.plural(R.plurals.obd_diag_mil_on, status.dtcCount, status.dtcCount),
            resolver.get(R.string.obd_diag_mil_on_cause),
        )) else add(Diagnosis(
            Nivel.OK, area, resolver.get(R.string.obd_diag_mil_off),
            resolver.get(R.string.obd_diag_mil_off_cause),
        ))
        status.monitors.filter { it.soportado }.forEach { m ->
            add(
                if (m.completo) Diagnosis(
                    Nivel.OK, area, resolver.get(R.string.obd_diag_monitor_ready, resolver.get(m.nombre)),
                    resolver.get(R.string.obd_diag_monitor_ready_cause),
                )
                else Diagnosis(
                    Nivel.ATENCION, area, resolver.get(R.string.obd_diag_monitor_not_ready, resolver.get(m.nombre)),
                    resolver.get(R.string.obd_diag_monitor_not_ready_cause),
                )
            )
        }
    }

    private fun decimal(value: Double, decimals: Int, resolver: StringResolver): String =
        LocaleNumbers.decimal(value, decimals, resolver.locale)

    private const val LTFT_OK = 10.0
    private const val LTFT_FALLA = 25.0
    private const val TRIM_TOTAL_MAX = 15.0
    private const val O2_MIN_MUESTRAS = 30
    private const val O2_BAJO = 0.2
    private const val O2_ALTO = 0.8
    private const val VOLT_MIN_MARCHA = 13.2
    private const val VOLT_MIN_REPOSO = 11.8
    private const val TEMP_MAX_LIQUIDA = 105.0
    private const val TEMP_MAX_AIRE_ACEITE = 130.0
}
