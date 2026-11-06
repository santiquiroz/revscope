package com.revscope.core.obd.taller.grafica

import com.revscope.core.common.i18n.LocaleNumbers
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R
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

    private val NOMBRES_TPS = linkedMapOf(ClavesBanda.TPS_CERRADO_V to R.string.obd_graph_throttle_closed, ClavesBanda.TPS_FONDO_V to R.string.obd_graph_full_throttle)

    // Solo se sombrean las bandas con los dos límites: una abierta no tiene franja que pintar.
    fun tps(
        bandas: Map<String, BandaReferencia>,
        unidad: UnidadPosicion,
        vref: ReferenciaVoltaje,
        resolver: StringResolver,
    ): List<BandaEnEscala> =
        NOMBRES_TPS.mapNotNull { (clave, nombre) ->
            bandas[clave]?.let { enEscala(it, resolver.get(nombre), unidad, vref, resolver) }
        }

    private fun enEscala(
        banda: BandaReferencia,
        nombre: String,
        unidad: UnidadPosicion,
        vref: ReferenciaVoltaje,
        resolver: StringResolver,
    ): BandaEnEscala? {
        val min = banda.min ?: return null
        val max = banda.max ?: return null
        return BandaEnEscala(
            clave = banda.clave,
            min = unidad.desdeVoltios(min, vref),
            max = unidad.desdeVoltios(max, vref),
            etiquetaCorta = "$nombre ${origenCorto(banda.origen, resolver)}",
            descripcion = resolver.get(
                R.string.obd_graph_band_description,
                nombre,
                rangoV(min, max, resolver),
                rangoPct(vref.aPorcentaje(min), vref.aPorcentaje(max), resolver),
                banda.etiquetaOrigen(resolver),
            ),
        )
    }

    private fun origenCorto(origen: OrigenBanda, resolver: StringResolver): String = resolver.get(
        when (origen) {
            OrigenBanda.TIPICO -> R.string.obd_band_origin_short_typical
            OrigenBanda.FUENTE -> R.string.obd_band_origin_short_source
            OrigenBanda.USUARIO -> R.string.obd_band_origin_short_user
        },
    )

    private fun rangoV(min: Double, max: Double, resolver: StringResolver) =
        "${LocaleNumbers.decimal(min, 1, resolver.locale)}–${LocaleNumbers.decimal(max, 1, resolver.locale)}${NBSP}V"

    private fun rangoPct(min: Double, max: Double, resolver: StringResolver) =
        "${compacto(min, resolver)}–${compacto(max, resolver)}${NBSP}%"

    private fun compacto(valor: Double, resolver: StringResolver): String {
        val entero = valor.toLong()
        return if (kotlin.math.abs(valor - entero) < 1e-9) entero.toString()
        else LocaleNumbers.decimal(valor, 1, resolver.locale)
    }

    private const val NBSP = ' '
}

object FormatoPosicion {
    fun porcentaje(valor: Double, resolver: StringResolver): String = "${LocaleNumbers.decimal(valor, 1, resolver.locale)} %"

    fun voltios(valor: Double, resolver: StringResolver): String = "${LocaleNumbers.decimal(valor, 2, resolver.locale)} V"

    fun enUnidad(valor: Double, unidad: UnidadPosicion, resolver: StringResolver): String = when (unidad) {
        UnidadPosicion.PORCENTAJE -> porcentaje(valor, resolver)
        UnidadPosicion.VOLTIOS -> voltios(valor, resolver)
    }

    fun ambos(porcentaje: Double, vref: ReferenciaVoltaje, resolver: StringResolver): String =
        "${porcentaje(porcentaje, resolver)} · ${voltios(vref.aVoltios(porcentaje), resolver)}"
}

// Cómo va una señal en la ventana visible, para el resumen accesible de las gráficas en vivo.
object Tendencia {
    fun de(valores: List<Double>, estableHasta: Double, resolver: StringResolver): String = resolver.get(
        when {
            valores.size < 2 || valores.max() - valores.min() <= estableHasta -> R.string.obd_graph_stable
            valores.last() > valores.first() -> R.string.obd_graph_rising
            else -> R.string.obd_graph_falling
        },
    )
}
