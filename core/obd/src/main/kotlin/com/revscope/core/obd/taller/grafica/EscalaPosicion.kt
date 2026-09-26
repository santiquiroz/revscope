package com.revscope.core.obd.taller.grafica

import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.pruebas.ReferenciaVoltaje
import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.referencia.ClavesBanda
import com.revscope.core.obd.taller.referencia.OrigenBanda

// Mariposa (11, 45, 47) y pedal (49, 4A, 4B, 4C, 5A): la ECU da % de la referencia, que se puede ver en voltios.
object PidsPosicion {
    private val TODOS = setOf("11", "45", "47", "49", "4A", "4B", "4C", "5A")

    fun es(pid: String): Boolean = pid.uppercase() in TODOS
}

enum class UnidadPosicion(val simbolo: String) {
    PORCENTAJE("%"),
    VOLTIOS("V");

    fun desdePorcentaje(porcentaje: Double, vref: ReferenciaVoltaje): Double = when (this) {
        PORCENTAJE -> porcentaje
        VOLTIOS -> vref.aVoltios(porcentaje)
    }

    fun desdeVoltios(voltios: Double, vref: ReferenciaVoltaje): Double = when (this) {
        PORCENTAJE -> vref.aPorcentaje(voltios)
        VOLTIOS -> voltios
    }
}

data class BandaEnEscala(
    val clave: String,
    val min: Double,
    val max: Double,
    val etiquetaCorta: String,
    val descripcion: String,
)

object BandasPosicion {

    private val NOMBRES_TPS = linkedMapOf(ClavesBanda.TPS_CERRADO_V to "Cerrado", ClavesBanda.TPS_FONDO_V to "A fondo")

    // Solo se sombrean las bandas con los dos límites: una abierta no tiene franja que pintar.
    fun tps(bandas: Map<String, BandaReferencia>, unidad: UnidadPosicion, vref: ReferenciaVoltaje): List<BandaEnEscala> =
        NOMBRES_TPS.mapNotNull { (clave, nombre) -> bandas[clave]?.let { enEscala(it, nombre, unidad, vref) } }

    private fun enEscala(banda: BandaReferencia, nombre: String, unidad: UnidadPosicion, vref: ReferenciaVoltaje): BandaEnEscala? {
        val min = banda.min ?: return null
        val max = banda.max ?: return null
        return BandaEnEscala(
            clave = banda.clave,
            min = unidad.desdeVoltios(min, vref),
            max = unidad.desdeVoltios(max, vref),
            etiquetaCorta = "$nombre ${origenCorto(banda.origen)}",
            descripcion = "$nombre: ${rangoV(min, max)} (≈${rangoPct(vref.aPorcentaje(min), vref.aPorcentaje(max))}) · " +
                banda.etiquetaOrigen,
        )
    }

    private fun origenCorto(origen: OrigenBanda): String = when (origen) {
        OrigenBanda.TIPICO -> "típico"
        OrigenBanda.FUENTE -> "según fuente"
        OrigenBanda.USUARIO -> "editado por ti"
    }

    // Espacio duro antes de la unidad: con letra grande el «%» o la «V» no quedan solos en otra línea.
    private fun rangoV(min: Double, max: Double) = "${FormatoTaller.numero(min, 1)}–${FormatoTaller.numero(max, 1)}${NBSP}V"

    private fun rangoPct(min: Double, max: Double) = "${FormatoTaller.compacto(min)}–${FormatoTaller.compacto(max)}${NBSP}%"

    private const val NBSP = ' '
}

object FormatoPosicion {
    fun porcentaje(valor: Double): String = "${FormatoTaller.numero(valor, 1)} %"

    fun voltios(valor: Double): String = "${FormatoTaller.numero(valor, 2)} V"

    fun enUnidad(valor: Double, unidad: UnidadPosicion): String = when (unidad) {
        UnidadPosicion.PORCENTAJE -> porcentaje(valor)
        UnidadPosicion.VOLTIOS -> voltios(valor)
    }

    fun ambos(porcentaje: Double, vref: ReferenciaVoltaje): String = "${porcentaje(porcentaje)} · ${voltios(vref.aVoltios(porcentaje))}"
}

// Cómo va una señal en la ventana visible, para el resumen accesible de las gráficas en vivo.
object Tendencia {
    fun de(valores: List<Double>, estableHasta: Double): String = when {
        valores.size < 2 || valores.max() - valores.min() <= estableHasta -> "estable"
        valores.last() > valores.first() -> "subiendo"
        else -> "bajando"
    }
}
