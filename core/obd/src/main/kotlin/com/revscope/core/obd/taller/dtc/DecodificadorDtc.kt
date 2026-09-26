package com.revscope.core.obd.taller.dtc

enum class SistemaDtc(val letra: Char, val nombre: String) {
    TREN_MOTRIZ('P', "Tren motriz (motor y transmisión)"),
    CHASIS('C', "Chasis"),
    CARROCERIA('B', "Carrocería"),
    RED('U', "Red de comunicación entre módulos"),
}

enum class AmbitoDtc(val etiqueta: String) {
    GENERICO("Genérico, definido por SAE"),
    FABRICANTE("Del fabricante"),
}

data class EstructuraDtc(
    val codigo: String,
    val sistema: SistemaDtc,
    val ambito: AmbitoDtc,
    val subsistema: String?,
) {
    val explicacion: String
        get() = when (ambito) {
            AmbitoDtc.FABRICANTE -> DecodificadorDtc.MENSAJE_FABRICANTE
            AmbitoDtc.GENERICO -> listOfNotNull(
                "Código genérico definido por SAE",
                sistema.nombre.enMinuscula(),
                subsistema?.let { "subsistema: ${it.enMinuscula()}" },
            ).joinToString(" · ")
        }

    private fun String.enMinuscula(): String = replaceFirstChar { it.lowercaseChar() }
}

// Estructura de SAE J2012: letra = sistema, 2.º carácter = genérico o del fabricante, 3.º = subsistema (P genéricos).
object DecodificadorDtc {

    const val MENSAJE_FABRICANTE = "Código del fabricante: consulta el manual del modelo"
    const val EJEMPLO_FORMATO = "una letra P, C, B o U seguida de cuatro caracteres, p. ej. P0122"

    private val FORMATO = Regex("^[PCBU][0-3][0-9A-F]{3}$")

    private val SUBSISTEMAS_P0 = mapOf(
        '0' to "Medición de aire y combustible y control auxiliar de emisiones",
        '1' to "Medición de aire y combustible",
        '2' to "Medición de aire y combustible (circuito de inyectores)",
        '3' to "Sistema de encendido o fallas de encendido",
        '4' to "Controles auxiliares de emisiones",
        '5' to "Velocidad del vehículo, control del ralentí y entradas auxiliares",
        '6' to "Computadora (ECU) y circuitos de salida",
        '7' to "Transmisión",
        '8' to "Transmisión",
        '9' to "Transmisión",
        'A' to "Propulsión híbrida",
        'B' to "Propulsión híbrida",
        'C' to "Propulsión híbrida",
    )

    private val SUBSISTEMAS_P2 = mapOf(
        '0' to "Medición de aire y combustible y control auxiliar de emisiones",
        '1' to "Medición de aire y combustible",
        '2' to "Medición de aire y combustible",
        '3' to "Sistema de encendido o fallas de encendido",
        '4' to "Controles auxiliares de emisiones",
        '5' to "Entradas auxiliares",
        '6' to "Computadora (ECU) y salidas auxiliares",
        '7' to "Transmisión",
    )

    fun normalizar(texto: String): String? = texto.trim().uppercase().takeIf(FORMATO::matches)

    fun decodificar(texto: String): EstructuraDtc? {
        val codigo = normalizar(texto) ?: return null
        val sistema = SistemaDtc.entries.first { it.letra == codigo[0] }
        val ambito = ambitoDe(codigo)
        return EstructuraDtc(codigo, sistema, ambito, subsistemaDe(codigo, ambito))
    }

    fun mensajeSinGuia(estructura: EstructuraDtc): String = when (estructura.ambito) {
        AmbitoDtc.FABRICANTE -> "$MENSAJE_FABRICANTE. RevScope no inventa su descripción."
        AmbitoDtc.GENERICO ->
            "No está en la guía local de RevScope: busca la descripción exacta en SAE J2012 " +
                "o en el manual de servicio del modelo."
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

    private fun subsistemaDe(codigo: String, ambito: AmbitoDtc): String? {
        if (codigo[0] != 'P' || ambito != AmbitoDtc.GENERICO) return null
        return when (codigo[1]) {
            '0' -> SUBSISTEMAS_P0[codigo[2]]
            '2' -> SUBSISTEMAS_P2[codigo[2]]
            else -> null
        }
    }
}
