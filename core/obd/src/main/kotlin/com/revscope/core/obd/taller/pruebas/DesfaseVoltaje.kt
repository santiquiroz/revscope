package com.revscope.core.obd.taller.pruebas

import kotlin.math.abs

// AT RV mide en el pin 16 del conector OBD con ±0,1-0,2 V (típico). El técnico lo calibra por vehículo contra
// el multímetro en los bornes y la app suma ese desfase; no se usa AT CV, que escribe en el adaptador.
data class DesfaseVoltaje(val voltios: Double, val origen: String) {
    init {
        require(abs(voltios) <= MAX_V) { "El desfase debe estar entre -$MAX_V y $MAX_V V" }
    }

    val calibrado: Boolean get() = this != SIN_CALIBRAR

    fun corregir(medidoV: Double): Double = medidoV + voltios

    companion object {
        const val MAX_V = 1.0
        const val ORIGEN_MULTIMETRO = "Calibrado con el multímetro"
        const val ORIGEN_EDITADO = "Editado por ti"
        val SIN_CALIBRAR = DesfaseVoltaje(0.0, "Sin calibrar")

        fun medido(multimetroV: Double, adaptadorV: Double) = DesfaseVoltaje(multimetroV - adaptadorV, ORIGEN_MULTIMETRO)

        fun editado(voltios: Double) = DesfaseVoltaje(voltios, ORIGEN_EDITADO)
    }
}
