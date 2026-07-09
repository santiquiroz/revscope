package com.revscope.core.obd.workshop

import com.revscope.core.obd.protocol.ReadinessParser
import kotlin.math.abs

/** Deterministic, offline interpretation rules for workshop diagnostics. */
object DiagnosticRules {

    enum class Nivel { OK, ATENCION, FALLA }

    data class Diagnosis(
        val nivel: Nivel,
        val area: String,
        val titulo: String,
        val causaProbable: String,
    )

    fun evaluarFuelTrimLargo(ltft: Double): Diagnosis = when {
        abs(ltft) <= LTFT_OK -> Diagnosis(
            Nivel.OK, "Mezcla", "Fuel trim largo %.1f%%".format(ltft),
            "El ECU compensa dentro del rango normal",
        )
        ltft > LTFT_FALLA || ltft < -LTFT_FALLA -> Diagnosis(
            Nivel.FALLA, "Mezcla", "Fuel trim largo %.1f%% fuera de control".format(ltft),
            "El ECU no logra compensar la mezcla — revisar sistema de combustible completo",
        )
        ltft > 0 -> Diagnosis(
            Nivel.ATENCION, "Mezcla", "Mezcla pobre (LTFT +%.1f%%)".format(ltft),
            "Mezcla pobre: fugas de vacío, inyectores sucios o sensor MAF sucio",
        )
        else -> Diagnosis(
            Nivel.ATENCION, "Mezcla", "Mezcla rica (LTFT %.1f%%)".format(ltft),
            "Mezcla rica: inyector goteando, presión de combustible alta o MAF descalibrado",
        )
    }

    fun evaluarTrimCombinado(stft: Double, ltft: Double): Diagnosis {
        val total = stft + ltft
        return if (abs(total) > TRIM_TOTAL_MAX) Diagnosis(
            Nivel.ATENCION, "Mezcla", "Corrección total %.1f%% excesiva".format(total),
            "La suma de trims corto y largo supera ±$TRIM_TOTAL_MAX% — condición activa de mezcla",
        ) else Diagnosis(
            Nivel.OK, "Mezcla", "Corrección total %.1f%%".format(total),
            "Trims combinados dentro del rango",
        )
    }

    fun evaluarO2(voltajes: List<Double>): Diagnosis {
        if (voltajes.size < O2_MIN_MUESTRAS) return Diagnosis(
            Nivel.OK, "Sensor O2", "Muestras insuficientes",
            "Se necesitan más lecturas para diagnosticar el sensor",
        )
        val clavadoBajo = voltajes.all { it < O2_BAJO }
        val clavadoAlto = voltajes.all { it > O2_ALTO }
        return when {
            clavadoBajo -> Diagnosis(
                Nivel.ATENCION, "Sensor O2", "Sensor O2 clavado bajo (<%.1fV)".format(O2_BAJO),
                "Sensor perezoso/agotado o mezcla extremadamente pobre",
            )
            clavadoAlto -> Diagnosis(
                Nivel.ATENCION, "Sensor O2", "Sensor O2 clavado alto (>%.1fV)".format(O2_ALTO),
                "Sensor contaminado o mezcla extremadamente rica",
            )
            else -> Diagnosis(
                Nivel.OK, "Sensor O2", "Sensor O2 oscilando",
                "El sensor conmuta — comportamiento sano",
            )
        }
    }

    fun evaluarVoltaje(volts: Double, motorEncendido: Boolean): Diagnosis = when {
        motorEncendido && volts < VOLT_MIN_MARCHA -> Diagnosis(
            Nivel.ATENCION, "Eléctrico", "Voltaje %.1fV bajo en marcha".format(volts),
            "El alternador/estator no está cargando bien",
        )
        !motorEncendido && volts < VOLT_MIN_REPOSO -> Diagnosis(
            Nivel.ATENCION, "Eléctrico", "Batería baja (%.1fV)".format(volts),
            "Batería descargada o al final de su vida",
        )
        else -> Diagnosis(
            Nivel.OK, "Eléctrico", "Voltaje %.1fV".format(volts),
            "Sistema de carga dentro del rango",
        )
    }

    fun evaluarTemperatura(tempC: Double): Diagnosis = when {
        tempC > TEMP_MAX -> Diagnosis(
            Nivel.FALLA, "Refrigeración", "Sobrecalentamiento (%.0f°C)".format(tempC),
            "Detener el motor — revisar refrigerante, bomba, ventilador y termostato",
        )
        else -> Diagnosis(
            Nivel.OK, "Refrigeración", "Temperatura %.0f°C".format(tempC),
            "Dentro del rango de operación",
        )
    }

    fun evaluarReadiness(status: ReadinessParser.ReadinessStatus): List<Diagnosis> = buildList {
        if (status.milOn) add(Diagnosis(
            Nivel.FALLA, "Readiness", "Testigo de motor (MIL) encendido — ${status.dtcCount} códigos",
            "Hay fallas activas — revisar los códigos DTC antes de la tecnomecánica",
        )) else add(Diagnosis(
            Nivel.OK, "Readiness", "Sin testigo de motor",
            "No hay fallas activas reportadas",
        ))
        status.monitors.filter { it.soportado }.forEach { m ->
            add(
                if (m.completo) Diagnosis(Nivel.OK, "Readiness", "${m.nombre}: listo", "Monitor completado")
                else Diagnosis(
                    Nivel.ATENCION, "Readiness", "${m.nombre}: NO listo",
                    "Monitor incompleto — conducir 20-30 min variados antes de la tecnomecánica",
                )
            )
        }
    }

    private const val LTFT_OK = 10.0
    private const val LTFT_FALLA = 25.0
    private const val TRIM_TOTAL_MAX = 15.0
    private const val O2_MIN_MUESTRAS = 30
    private const val O2_BAJO = 0.2
    private const val O2_ALTO = 0.8
    private const val VOLT_MIN_MARCHA = 13.2
    private const val VOLT_MIN_REPOSO = 11.8
    private const val TEMP_MAX = 105.0
}
