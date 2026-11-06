package com.revscope.core.obd.taller.pruebas

import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R
import kotlin.math.abs

// AT RV mide en el pin 16 del conector OBD con ±0,1-0,2 V (típico). El técnico lo calibra por vehículo contra
// el multímetro en los bornes y la app suma ese desfase; no se usa AT CV, que escribe en el adaptador.
// [origen] es una clave estable (se guarda en DataStore y en el JSON del evento); se muestra con [origenTexto].
data class DesfaseVoltaje(val voltios: Double, val origen: String) {
    init {
        require(abs(voltios) <= MAX_V) { "El desfase debe estar entre -$MAX_V y $MAX_V V" } // i18n-ignore
    }

    val calibrado: Boolean get() = this != SIN_CALIBRAR

    fun corregir(medidoV: Double): Double = medidoV + voltios

    fun origenTexto(resolver: StringResolver): String = when (origen) {
        ORIGEN_MULTIMETRO -> resolver.get(R.string.obd_origen_multimetro)
        ORIGEN_EDITADO -> resolver.get(R.string.obd_origen_editado)
        ORIGEN_SIN_CALIBRAR -> resolver.get(R.string.obd_origen_sin_calibrar)
        else -> origen
    }

    companion object {
        const val MAX_V = 1.0
        const val ORIGEN_MULTIMETRO = "Calibrado con el multímetro" // i18n-ignore
        const val ORIGEN_EDITADO = "Editado por ti" // i18n-ignore
        const val ORIGEN_SIN_CALIBRAR = "Sin calibrar" // i18n-ignore
        val SIN_CALIBRAR = DesfaseVoltaje(0.0, ORIGEN_SIN_CALIBRAR)

        fun medido(multimetroV: Double, adaptadorV: Double) = DesfaseVoltaje(multimetroV - adaptadorV, ORIGEN_MULTIMETRO)

        fun editado(voltios: Double) = DesfaseVoltaje(voltios, ORIGEN_EDITADO)
    }
}
