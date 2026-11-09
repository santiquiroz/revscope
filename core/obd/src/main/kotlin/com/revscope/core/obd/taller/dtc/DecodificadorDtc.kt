package com.revscope.core.obd.taller.dtc

import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R

enum class SistemaDtc(val letra: Char) {
    TREN_MOTRIZ('P'),
    CHASIS('C'),
    CARROCERIA('B'),
    RED('U'),
    ;

    fun nombre(resolver: StringResolver): String = resolver.get(
        when (this) {
            TREN_MOTRIZ -> R.string.obd_dtc_system_powertrain
            CHASIS -> R.string.obd_dtc_system_chassis
            CARROCERIA -> R.string.obd_dtc_system_body
            RED -> R.string.obd_dtc_system_network
        },
    )

    val nombre: String get() = when (this) { // i18n-ignore
        TREN_MOTRIZ -> "Tren motriz (motor y transmisión)" // i18n-ignore
        CHASIS -> "Chasis" // i18n-ignore
        CARROCERIA -> "Carrocería" // i18n-ignore
        RED -> "Red de comunicación entre módulos" // i18n-ignore
    }
}

enum class AmbitoDtc {
    GENERICO,
    FABRICANTE,

    ;

    val etiqueta: String get() = when (this) { // i18n-ignore
        GENERICO -> "Genérico, definido por SAE" // i18n-ignore
        FABRICANTE -> "Del fabricante" // i18n-ignore
    }
}

data class EstructuraDtc(
    val codigo: String,
    val sistema: SistemaDtc,
    val ambito: AmbitoDtc,
    private val subsistemaRes: Int?,
) {
    val subsistema: String? get() = when (subsistemaRes) { // i18n-ignore
        R.string.obd_dtc_subsystem_air_fuel_emissions -> "Medición de aire y combustible y control auxiliar de emisiones" // i18n-ignore
        R.string.obd_dtc_subsystem_air_fuel -> "Medición de aire y combustible" // i18n-ignore
        R.string.obd_dtc_subsystem_fuel_injector_circuit -> "Medición de aire y combustible (circuito de inyectores)" // i18n-ignore
        R.string.obd_dtc_subsystem_ignition_misfire -> "Sistema de encendido o fallas de encendido" // i18n-ignore
        R.string.obd_dtc_subsystem_aux_emissions_controls -> "Controles auxiliares de emisiones" // i18n-ignore
        R.string.obd_dtc_subsystem_speed_idle_aux_inputs -> "Velocidad del vehículo, control del ralentí y entradas auxiliares" // i18n-ignore
        R.string.obd_dtc_subsystem_ecu_outputs -> "Computadora (ECU) y circuitos de salida" // i18n-ignore
        R.string.obd_dtc_subsystem_transmission -> "Transmisión" // i18n-ignore
        R.string.obd_dtc_subsystem_hybrid_propulsion -> "Propulsión híbrida" // i18n-ignore
        R.string.obd_dtc_subsystem_aux_inputs -> "Entradas auxiliares" // i18n-ignore
        R.string.obd_dtc_subsystem_ecu_aux_outputs -> "Computadora (ECU) y salidas auxiliares" // i18n-ignore
        else -> null
    }

    val explicacion: String get() = when (ambito) { // i18n-ignore
        AmbitoDtc.FABRICANTE -> "Código del fabricante: consulta el manual del modelo" // i18n-ignore
        AmbitoDtc.GENERICO -> listOfNotNull(
            "Código genérico definido por SAE", // i18n-ignore
            sistema.nombre.replaceFirstChar { it.lowercaseChar() },
            subsistema?.let { "subsistema: ${it.replaceFirstChar { first -> first.lowercaseChar() }}" }, // i18n-ignore
        ).joinToString(" · ")
    }

    fun explicacion(resolver: StringResolver): String = when (ambito) {
        AmbitoDtc.FABRICANTE -> resolver.get(R.string.obd_dtc_manufacturer_code)
        AmbitoDtc.GENERICO -> listOfNotNull(
            resolver.get(R.string.obd_dtc_generic_code),
            sistema.nombre(resolver).replaceFirstChar { it.lowercase(resolver.locale) },
            subsistemaRes?.let {
                resolver.get(
                    R.string.obd_dtc_subsystem,
                    resolver.get(it).replaceFirstChar { first -> first.lowercase(resolver.locale) },
                )
            },
        ).joinToString(" · ")
    }
}

// Estructura de SAE J2012: letra = sistema, 2.º carácter = genérico o del fabricante, 3.º = subsistema (P genéricos).
object DecodificadorDtc {

    const val MENSAJE_FABRICANTE = "Código del fabricante: consulta el manual del modelo" // i18n-ignore
    const val EJEMPLO_FORMATO = "una letra P, C, B o U seguida de cuatro caracteres, p. ej. P0122" // i18n-ignore

    private val FORMATO = Regex("^[PCBU][0-3][0-9A-F]{3}$")

    private val SUBSISTEMAS_P0 = mapOf(
        '0' to R.string.obd_dtc_subsystem_air_fuel_emissions,
        '1' to R.string.obd_dtc_subsystem_air_fuel,
        '2' to R.string.obd_dtc_subsystem_fuel_injector_circuit,
        '3' to R.string.obd_dtc_subsystem_ignition_misfire,
        '4' to R.string.obd_dtc_subsystem_aux_emissions_controls,
        '5' to R.string.obd_dtc_subsystem_speed_idle_aux_inputs,
        '6' to R.string.obd_dtc_subsystem_ecu_outputs,
        '7' to R.string.obd_dtc_subsystem_transmission,
        '8' to R.string.obd_dtc_subsystem_transmission,
        '9' to R.string.obd_dtc_subsystem_transmission,
        'A' to R.string.obd_dtc_subsystem_hybrid_propulsion,
        'B' to R.string.obd_dtc_subsystem_hybrid_propulsion,
        'C' to R.string.obd_dtc_subsystem_hybrid_propulsion,
    )

    private val SUBSISTEMAS_P2 = mapOf(
        '0' to R.string.obd_dtc_subsystem_air_fuel_emissions,
        '1' to R.string.obd_dtc_subsystem_air_fuel,
        '2' to R.string.obd_dtc_subsystem_air_fuel,
        '3' to R.string.obd_dtc_subsystem_ignition_misfire,
        '4' to R.string.obd_dtc_subsystem_aux_emissions_controls,
        '5' to R.string.obd_dtc_subsystem_aux_inputs,
        '6' to R.string.obd_dtc_subsystem_ecu_aux_outputs,
        '7' to R.string.obd_dtc_subsystem_transmission,
    )

    fun normalizar(texto: String): String? = texto.trim().uppercase().takeIf(FORMATO::matches)

    fun decodificar(texto: String): EstructuraDtc? {
        val codigo = normalizar(texto) ?: return null
        val sistema = SistemaDtc.entries.first { it.letra == codigo[0] }
        val ambito = ambitoDe(codigo)
        return EstructuraDtc(codigo, sistema, ambito, subsistemaDe(codigo, ambito))
    }

    fun mensajeSinGuia(estructura: EstructuraDtc, resolver: StringResolver): String = when (estructura.ambito) {
        AmbitoDtc.FABRICANTE -> resolver.get(R.string.obd_dtc_no_guide_manufacturer, resolver.get(R.string.obd_dtc_manufacturer_code))
        AmbitoDtc.GENERICO -> resolver.get(R.string.obd_dtc_no_guide_generic)
    }

    fun mensajeSinGuia(estructura: EstructuraDtc): String = when (estructura.ambito) { // i18n-ignore
        AmbitoDtc.FABRICANTE -> "$MENSAJE_FABRICANTE. RevScope no inventa su descripción." // i18n-ignore
        AmbitoDtc.GENERICO ->
            "No está en la guía local de RevScope: busca la descripción exacta en SAE J2012 o en el manual de servicio del modelo." // i18n-ignore
    }

    private fun ambitoDe(codigo: String): AmbitoDtc =
        if (codigo[0] == 'P') ambitoTrenMotriz(codigo) else ambitoOtrosSistemas(codigo[1])

    // P0, P2 y P34-P39 son de SAE; P1 y P30-P33 los define cada fabricante.
    private fun ambitoTrenMotriz(codigo: String): AmbitoDtc = when (codigo[1]) {
        '1' -> AmbitoDtc.FABRICANTE
        '3' -> if (codigo[2] in '0'..'3') AmbitoDtc.FABRICANTE else AmbitoDtc.GENERICO
        else -> AmbitoDtc.GENERICO
    }

    // En C, B y U: 0 es de SAE, 1 y 2 del fabricante y 3 está reservado por SAE.
    private fun ambitoOtrosSistemas(segundo: Char): AmbitoDtc =
        if (segundo == '1' || segundo == '2') AmbitoDtc.FABRICANTE else AmbitoDtc.GENERICO

    private fun subsistemaDe(codigo: String, ambito: AmbitoDtc): Int? {
        if (codigo[0] != 'P' || ambito != AmbitoDtc.GENERICO) return null
        return when (codigo[1]) {
            '0' -> SUBSISTEMAS_P0[codigo[2]]
            '2' -> SUBSISTEMAS_P2[codigo[2]]
            else -> null
        }
    }
}
