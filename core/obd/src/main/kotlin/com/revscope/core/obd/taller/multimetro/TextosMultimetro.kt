package com.revscope.core.obd.taller.multimetro

import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R

internal object TextosMultimetro {

    fun tituloSensor(plantilla: PlantillaCableado, celdas: List<EvaluacionCelda>, resolver: StringResolver): String {
        val partes = listOfNotNull(
            alimentacion(estado(celdas, FuncionCable.REF_5V), estado(celdas, FuncionCable.MASA), resolver),
            senal(plantilla, celdas.filter { it.funcion == FuncionCable.SENAL }, resolver),
        )
        return partes.joinToString(if (resolver.locale.language == "es") "; " else "; ")
            .ifEmpty { resolver.get(R.string.obd_multimeter_readings_empty) }
            .capitalizar(resolver)
    }

    fun interpretacionSensor(celdas: List<EvaluacionCelda>, resolver: StringResolver): String? {
        val ref = estado(celdas, FuncionCable.REF_5V)
        val masa = estado(celdas, FuncionCable.MASA)
        val senal = estado(celdas, FuncionCable.SENAL)
        return when {
            ref == EstadoGrupo.BAJO -> resolver.get(R.string.obd_multimeter_low_reference_diagnosis)
            masa == EstadoGrupo.ALTO -> resolver.get(R.string.obd_multimeter_earth_drop_diagnosis)
            senal == EstadoGrupo.BAJO -> senalBaja(ref, masa, resolver)
            senal == EstadoGrupo.ALTO -> resolver.get(R.string.obd_multimeter_high_signal_diagnosis)
            todoCorrecto(ref, masa, senal) -> resolver.get(R.string.obd_multimeter_sensor_circuit_typical)
            else -> null
        }
    }

    private fun estado(celdas: List<EvaluacionCelda>, funcion: FuncionCable): EstadoGrupo =
        VeredictoMultimetro.grupo(celdas.filter { it.funcion == funcion })

    private fun alimentacion(ref: EstadoGrupo, masa: EstadoGrupo, resolver: StringResolver): String? {
        if (ref == EstadoGrupo.CORRECTO && masa == EstadoGrupo.CORRECTO) {
            return resolver.get(R.string.obd_multimeter_reference_and_earth_correct)
        }
        val partes = listOfNotNull(textoReferencia(ref, resolver), textoMasa(masa, resolver))
        return partes.takeIf { it.isNotEmpty() }?.joinToString(" ${resolver.get(R.string.obd_multimeter_and)} ")
    }

    private fun textoReferencia(e: EstadoGrupo, resolver: StringResolver): String? = when (e) {
        EstadoGrupo.CORRECTO -> resolver.get(R.string.obd_multimeter_reference_correct)
        EstadoGrupo.BAJO -> resolver.get(R.string.obd_multimeter_reference_low)
        EstadoGrupo.ALTO -> resolver.get(R.string.obd_multimeter_reference_high)
        EstadoGrupo.MIXTO -> resolver.get(R.string.obd_multimeter_reference_unstable)
        EstadoGrupo.SIN_MEDIR, EstadoGrupo.SIN_REFERENCIA -> null
    }

    private fun textoMasa(e: EstadoGrupo, resolver: StringResolver): String? = when (e) {
        EstadoGrupo.CORRECTO -> resolver.get(R.string.obd_multimeter_earth_correct)
        EstadoGrupo.ALTO -> resolver.get(R.string.obd_multimeter_earth_drop)
        EstadoGrupo.BAJO -> resolver.get(R.string.obd_multimeter_earth_below_zero)
        EstadoGrupo.MIXTO -> resolver.get(R.string.obd_multimeter_earth_unstable)
        EstadoGrupo.SIN_MEDIR, EstadoGrupo.SIN_REFERENCIA -> null
    }

    private fun senal(
        plantilla: PlantillaCableado,
        celdas: List<EvaluacionCelda>,
        resolver: StringResolver,
    ): String? = when (VeredictoMultimetro.grupo(celdas)) {
        EstadoGrupo.SIN_MEDIR -> null
        EstadoGrupo.SIN_REFERENCIA -> resolver.get(R.string.obd_multimeter_signal_no_expected)
        EstadoGrupo.CORRECTO -> resolver.get(R.string.obd_multimeter_signal_in_range)
        EstadoGrupo.BAJO -> resolver.get(R.string.obd_multimeter_signal_low, donde(plantilla, celdas, EstadoCelda.BAJO, resolver))
        EstadoGrupo.ALTO -> resolver.get(R.string.obd_multimeter_signal_high, donde(plantilla, celdas, EstadoCelda.ALTO, resolver))
        EstadoGrupo.MIXTO -> resolver.get(
            R.string.obd_multimeter_signal_mixed,
            donde(plantilla, celdas, EstadoCelda.BAJO, resolver),
            donde(plantilla, celdas, EstadoCelda.ALTO, resolver),
        )
    }

    private fun donde(
        plantilla: PlantillaCableado,
        celdas: List<EvaluacionCelda>,
        estado: EstadoCelda,
        resolver: StringResolver,
    ): String {
        val fuera = celdas.filter { it.estado == estado }
        val posiciones = plantilla.cable(FuncionCable.SENAL)?.esperadoPorCondicion?.keys.orEmpty()
        val todas = posiciones.size > 1 && fuera.map { it.condicion }.toSet() == posiciones
        if (todas) return resolver.get(R.string.obd_multimeter_throughout_sweep)
        return "(" + enumerar(fuera.map { etiquetaCondicion(plantilla, it.condicion, resolver) }, resolver) + ")"
    }

    private fun senalBaja(ref: EstadoGrupo, masa: EstadoGrupo, resolver: StringResolver): String =
        if (ref == EstadoGrupo.CORRECTO && masa == EstadoGrupo.CORRECTO) {
            resolver.get(R.string.obd_multimeter_sensor_or_connection)
        } else {
            resolver.get(
                R.string.obd_multimeter_also_check_reference,
                resolver.get(R.string.obd_multimeter_sensor_or_connection).replaceFirstChar { it.lowercase(resolver.locale) },
            )
        }

    private fun todoCorrecto(vararg grupos: EstadoGrupo): Boolean =
        grupos.any { it == EstadoGrupo.CORRECTO } && grupos.all { it == EstadoGrupo.CORRECTO || it == EstadoGrupo.SIN_MEDIR }

    fun tituloGeneral(plantilla: PlantillaCableado, celdas: List<EvaluacionCelda>, resolver: StringResolver): String {
        val fuera = celdas.filter { it.fueraDeBanda }
        return when {
            fuera.isNotEmpty() -> resolver.get(
                R.string.obd_multimeter_outside_range,
                enumerar(fuera.map { celdaCorta(plantilla, it, resolver) }, resolver),
            )
            celdas.any { it.estado == EstadoCelda.DENTRO } -> resolver.get(R.string.obd_multimeter_all_in_range)
            celdas.isEmpty() -> resolver.get(R.string.obd_multimeter_readings_empty)
            else -> resolver.get(R.string.obd_multimeter_no_expected_values)
        }
    }

    fun interpretacionGeneral(
        plantilla: PlantillaCableado,
        celdas: List<EvaluacionCelda>,
        resolver: StringResolver,
    ): String? = celdas.filter { it.fueraDeBanda }
        .mapNotNull { PISTAS[Triple(plantilla.sensor, it.condicion, it.estado)] }
        .map { resolver.get(it) }
        .distinct()
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" ")

    private fun celdaCorta(plantilla: PlantillaCableado, c: EvaluacionCelda, resolver: StringResolver): String {
        val cable = plantilla.cable(c.funcion)?.etiqueta ?: c.funcion.etiqueta(resolver)
        val lado = resolver.get(if (c.estado == EstadoCelda.BAJO) R.string.obd_multimeter_low else R.string.obd_multimeter_high)
        return resolver.get(
            R.string.obd_multimeter_cell_short,
            cable.lowercase(resolver.locale),
            etiquetaCondicion(plantilla, c.condicion, resolver),
            lado,
        )
    }

    fun etiquetaCondicion(plantilla: PlantillaCableado, clave: String, resolver: StringResolver): String =
        plantilla.condicion(clave)?.etiqueta?.lowercase(resolver.locale) ?: clave

    private fun enumerar(partes: List<String>, resolver: StringResolver): String = when (partes.size) {
        0 -> ""
        1 -> partes.single()
        else -> partes.dropLast(1).joinToString(", ") + " ${resolver.get(R.string.obd_multimeter_and)} " + partes.last()
    }

    private fun String.capitalizar(resolver: StringResolver): String = replaceFirstChar { it.titlecase(resolver.locale) }

    private val PISTAS = mapOf(
        Triple(SensorMultimetro.BATERIA, CondicionesMultimetro.CONTACTO, EstadoCelda.BAJO) to R.string.obd_multimeter_hint_battery_rest,
        Triple(SensorMultimetro.BATERIA, CondicionesMultimetro.ARRANQUE, EstadoCelda.BAJO) to R.string.obd_multimeter_hint_battery_start,
        Triple(SensorMultimetro.BATERIA, CondicionesMultimetro.CARGA, EstadoCelda.BAJO) to R.string.obd_multimeter_hint_battery_charge_low,
        Triple(SensorMultimetro.BATERIA, CondicionesMultimetro.CARGA, EstadoCelda.ALTO) to R.string.obd_multimeter_hint_battery_charge_high,
        Triple(SensorMultimetro.INYECTOR, CondicionesMultimetro.DESCONECTADO, EstadoCelda.BAJO) to R.string.obd_multimeter_hint_injector_low,
        Triple(SensorMultimetro.INYECTOR, CondicionesMultimetro.DESCONECTADO, EstadoCelda.ALTO) to R.string.obd_multimeter_hint_injector_high,
    )
}
