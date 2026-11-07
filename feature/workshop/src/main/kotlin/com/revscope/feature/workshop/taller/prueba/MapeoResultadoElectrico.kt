package com.revscope.feature.workshop.taller.prueba

import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.designsystem.BandaGrafica
import com.revscope.core.common.i18n.LocaleNumbers
import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.pruebas.AnalisisBateria
import com.revscope.core.obd.taller.pruebas.AnalisisMapBaro
import com.revscope.core.obd.taller.pruebas.AnalizadorBateria
import com.revscope.core.obd.taller.pruebas.AnalizadorMapBaro
import com.revscope.core.obd.taller.pruebas.DatosPrueba
import com.revscope.core.obd.taller.pruebas.PatronBateria
import com.revscope.core.obd.taller.pruebas.PatronMapBaro
import com.revscope.core.obd.taller.pruebas.TextosBateria
import com.revscope.core.obd.taller.pruebas.TextosMapBaro
import com.revscope.core.obd.taller.referencia.ClavesBanda

// Resultado de batería y carga y del MAP contra la barométrica: medidas en líneas, comprobaciones con su banda
// y la serie de toda la prueba (el voltaje con la banda de carga; el MAP con la barométrica ± la tolerancia).
internal object MapeoResultadoElectrico {

    fun bateria(base: ResultadoUi, a: AnalisisBateria, datos: DatosPrueba?, resolver: StringResolver): ResultadoUi {
        val carga = a.comprobacion(ClavesBanda.CARGA_V)?.banda
        return base.copy(
            comprobaciones = a.comprobaciones.map { MapeoResultadoMotor.comprobacion(it, resolver) },
            medidas = TextosBateria.medidas(a, resolver),
            graficas = listOfNotNull(
                datos?.let { MapeoResultadoMotor.grafica(it, AnalizadorBateria.PID_VOLTAJE, TextosBateria.titulosPaso(resolver), carga, resolver) },
            ),
            codigoGuia = codigoBateria(a.patron),
        )
    }

    fun mapBaro(base: ResultadoUi, a: AnalisisMapBaro, datos: DatosPrueba?, resolver: StringResolver): ResultadoUi = base.copy(
        comprobaciones = a.comprobaciones.map { MapeoResultadoMotor.comprobacion(it, resolver) },
        medidas = TextosMapBaro.medidas(a, resolver),
        graficas = listOfNotNull(
            datos?.let {
                MapeoResultadoMotor.graficaCon(
                    it,
                    AnalizadorMapBaro.PID_MAP,
                    TextosMapBaro.titulosPaso(resolver),
                    bandaBaro(a, resolver),
                    leyendaBaro(a, resolver),
                    resolver,
                )
            },
        ),
        codigoGuia = CODIGO_MAP.takeIf { a.patron == PatronMapBaro.MAP_BAJO || a.patron == PatronMapBaro.MAP_ALTO },
    )

    // La barométrica más y menos la tolerancia (la banda típica y lo que puede errar la fuente).
    fun bandaBaro(a: AnalisisMapBaro, resolver: StringResolver): BandaGrafica? {
        val ref = a.referencia ?: return null
        val tolerancia = a.toleranciaKpa ?: return null
        return BandaGrafica("Barométrica ±${kpa(tolerancia, resolver)}", ref.kPa - tolerancia, ref.kPa + tolerancia)
    }

    private fun leyendaBaro(a: AnalisisMapBaro, resolver: StringResolver): String? {
        val ref = a.referencia ?: return null
        return "Barométrica ${kpa(ref.kPa, resolver)} ±${kpa(a.toleranciaKpa ?: 0.0, resolver)} · ${ref.etiqueta(resolver)}"
    }

    private fun codigoBateria(p: PatronBateria): String? = when (p) {
        PatronBateria.SOBRECARGA -> "P0563"
        PatronBateria.NO_CARGA, PatronBateria.ARRANQUE_BAJO -> "P0562"
        else -> null
    }

    private fun kpa(x: Double, resolver: StringResolver) = "${LocaleNumbers.compact(x, resolver.locale)} kPa"

    private const val CODIGO_MAP = "P0106"
}
