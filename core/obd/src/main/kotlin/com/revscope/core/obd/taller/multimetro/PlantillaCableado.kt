package com.revscope.core.obd.taller.multimetro

data class Condicion(val clave: String, val etiqueta: String)

sealed interface Esperado {
    data class Banda(val clave: String) : Esperado

    // La posición media del acelerador no es exacta: no hay banda fija, solo debe quedar entre las otras dos.
    data class EntreCondiciones(val desde: String, val hasta: String) : Esperado

    data class SinValor(val motivo: String) : Esperado
}

data class Cable(
    val funcion: FuncionCable,
    val color: String?,
    val esperadoPorCondicion: Map<String, Esperado>,
    val etiqueta: String = funcion.etiqueta,
) {
    fun esperado(condicion: String): Esperado? = esperadoPorCondicion[condicion]
}

data class PlantillaCableado(
    val sensor: SensorMultimetro,
    val titulo: String,
    val cables: List<Cable>,
    val condiciones: List<Condicion>,
    val unidad: String,
    val dondeMedir: String,
    val fuenteColores: String? = null,
    val condicionesEcu: Set<String> = emptySet(),
) {
    init {
        require(cables.isNotEmpty()) { "La plantilla de $sensor no tiene cables" }
        require(cables.map { it.funcion }.toSet().size == cables.size) { "La plantilla de $sensor repite una función" }
        require(condiciones.isNotEmpty()) { "La plantilla de $sensor no tiene condiciones" }
    }

    val conColores: Boolean get() = cables.any { it.color != null }

    fun cable(funcion: FuncionCable): Cable? = cables.firstOrNull { it.funcion == funcion }

    fun condicion(clave: String): Condicion? = condiciones.firstOrNull { it.clave == clave }

    fun celdas(): List<Pair<FuncionCable, String>> =
        cables.flatMap { cable -> condiciones.filter { cable.esperado(it.clave) != null }.map { cable.funcion to it.clave } }
}

data class LecturaMultimetro(val funcion: FuncionCable, val condicion: String, val valor: Double, val unidad: String)
