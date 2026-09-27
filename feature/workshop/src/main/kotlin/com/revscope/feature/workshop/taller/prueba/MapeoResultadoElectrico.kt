package com.revscope.feature.workshop.taller.prueba

import com.revscope.core.designsystem.BandaGrafica
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

    fun bateria(base: ResultadoUi, a: AnalisisBateria, datos: DatosPrueba?): ResultadoUi {
        val carga = a.comprobacion(ClavesBanda.CARGA_V)?.banda
        return base.copy(
            comprobaciones = a.comprobaciones.map(MapeoResultadoMotor::comprobacion),
            medidas = TextosBateria.medidas(a),
            graficas = listOfNotNull(datos?.let { MapeoResultadoMotor.grafica(it, AnalizadorBateria.PID_VOLTAJE, TextosBateria.TITULOS_PASO, carga) }),
            codigoGuia = codigoBateria(a.patron),
        )
    }

    fun mapBaro(base: ResultadoUi, a: AnalisisMapBaro, datos: DatosPrueba?): ResultadoUi = base.copy(
        comprobaciones = a.comprobaciones.map(MapeoResultadoMotor::comprobacion),
        medidas = TextosMapBaro.medidas(a),
        graficas = listOfNotNull(
            datos?.let { MapeoResultadoMotor.graficaCon(it, AnalizadorMapBaro.PID_MAP, TextosMapBaro.TITULOS_PASO, bandaBaro(a), leyendaBaro(a)) },
        ),
        codigoGuia = CODIGO_MAP.takeIf { a.patron == PatronMapBaro.MAP_BAJO || a.patron == PatronMapBaro.MAP_ALTO },
    )

    // La barométrica más y menos la tolerancia (la banda típica y lo que puede errar la fuente).
    fun bandaBaro(a: AnalisisMapBaro): BandaGrafica? {
        val ref = a.referencia ?: return null
        val tolerancia = a.toleranciaKpa ?: return null
        return BandaGrafica("Barométrica ±${kpa(tolerancia)}", ref.kPa - tolerancia, ref.kPa + tolerancia)
    }

    private fun leyendaBaro(a: AnalisisMapBaro): String? {
        val ref = a.referencia ?: return null
        return "Barométrica ${kpa(ref.kPa)} ±${kpa(a.toleranciaKpa ?: 0.0)} · ${ref.etiqueta}"
    }

    private fun codigoBateria(p: PatronBateria): String? = when (p) {
        PatronBateria.SOBRECARGA -> "P0563"
        PatronBateria.NO_CARGA, PatronBateria.ARRANQUE_BAJO -> "P0562"
        else -> null
    }

    private fun kpa(x: Double) = "${FormatoTaller.compacto(FormatoTaller.redondear(x, 1))} kPa"

    private const val CODIGO_MAP = "P0106"
}
