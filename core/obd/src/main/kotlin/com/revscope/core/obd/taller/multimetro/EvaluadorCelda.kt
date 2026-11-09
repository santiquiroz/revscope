package com.revscope.core.obd.taller.multimetro

import com.revscope.core.common.i18n.LocaleNumbers
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R
import com.revscope.core.obd.taller.pruebas.TextoBanda
import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.referencia.PosicionEnBanda

enum class EstadoCelda { DENTRO, BAJO, ALTO, SIN_REFERENCIA }

data class EvaluacionCelda(
    val funcion: FuncionCable,
    val condicion: String,
    val valor: Double,
    val unidad: String,
    val estado: EstadoCelda,
    val referencia: String,
) {
    val fueraDeBanda: Boolean get() = estado == EstadoCelda.BAJO || estado == EstadoCelda.ALTO
}

object EvaluadorCelda {

    // [fila]: lo medido en el mismo cable en cada condición, para la posición media que va entre las otras dos.
    fun evaluar(
        plantilla: PlantillaCableado,
        lectura: LecturaMultimetro,
        fila: Map<String, Double>,
        bandas: Map<String, BandaReferencia>,
        resolver: StringResolver,
    ): EvaluacionCelda {
        val cable = plantilla.cable(lectura.funcion)
        val resultado = when (val esperado = cable?.esperado(lectura.condicion)) {
            is Esperado.Banda -> contraBanda(lectura, bandas[esperado.clave], resolver)
            is Esperado.EntreCondiciones -> entre(
                lectura.valor,
                limitesEntre(cable, esperado, fila, bandas),
                plantilla,
                esperado,
                resolver,
            )
            is Esperado.SinValor -> EstadoCelda.SIN_REFERENCIA to esperado.motivo
            null -> EstadoCelda.SIN_REFERENCIA to resolver.get(R.string.obd_multimeter_condition_no_expected)
        }
        return EvaluacionCelda(lectura.funcion, lectura.condicion, lectura.valor, lectura.unidad, resultado.first, resultado.second)
    }

    fun evaluarTodas(
        plantilla: PlantillaCableado,
        lecturas: List<LecturaMultimetro>,
        bandas: Map<String, BandaReferencia>,
        resolver: StringResolver,
    ): List<EvaluacionCelda> =
        lecturas.map { evaluar(plantilla, it, filaDe(lecturas, it.funcion), bandas, resolver) }

    fun filaDe(lecturas: List<LecturaMultimetro>, funcion: FuncionCable): Map<String, Double> =
        lecturas.filter { it.funcion == funcion }.associate { it.condicion to it.valor }

    private fun contraBanda(
        lectura: LecturaMultimetro,
        banda: BandaReferencia?,
        resolver: StringResolver,
    ): Pair<EstadoCelda, String> {
        if (banda == null) return EstadoCelda.SIN_REFERENCIA to resolver.get(R.string.obd_multimeter_no_reference_band)
        if (banda.unidad != lectura.unidad) {
            return EstadoCelda.SIN_REFERENCIA to resolver.get(R.string.obd_multimeter_band_unit, banda.unidad)
        }
        return estadoDe(banda.clasificar(lectura.valor)) to
            "${TextoBanda.rango(banda, decimales(banda.unidad), resolver)} · ${banda.etiquetaOrigen(resolver)}"
    }

    private data class Limites(val inferior: Double?, val superior: Double?)

    private fun limitesEntre(cable: Cable, e: Esperado.EntreCondiciones, fila: Map<String, Double>, bandas: Map<String, BandaReferencia>) =
        Limites(
            inferior = listOfNotNull(bandaDe(cable, e.desde, bandas)?.max, fila[e.desde]).maxOrNull(),
            superior = listOfNotNull(bandaDe(cable, e.hasta, bandas)?.min, fila[e.hasta]).minOrNull(),
        )

    private fun entre(
        valor: Double,
        l: Limites,
        plantilla: PlantillaCableado,
        e: Esperado.EntreCondiciones,
        resolver: StringResolver,
    ): Pair<EstadoCelda, String> {
        val texto = resolver.get(
            R.string.obd_multimeter_no_fixed_band,
            etiqueta(plantilla, e.desde, resolver),
            etiqueta(plantilla, e.hasta, resolver),
        )
        val estado = when {
            l.inferior == null && l.superior == null -> EstadoCelda.SIN_REFERENCIA
            l.inferior != null && valor < l.inferior -> EstadoCelda.BAJO
            l.superior != null && valor > l.superior -> EstadoCelda.ALTO
            else -> EstadoCelda.DENTRO
        }
        return estado to texto
    }

    private fun bandaDe(cable: Cable, condicion: String, bandas: Map<String, BandaReferencia>): BandaReferencia? =
        (cable.esperado(condicion) as? Esperado.Banda)?.let { bandas[it.clave] }

    private fun etiqueta(plantilla: PlantillaCableado, condicion: String, resolver: StringResolver): String =
        plantilla.condicion(condicion)?.etiqueta?.lowercase(resolver.locale) ?: condicion

    private fun estadoDe(p: PosicionEnBanda): EstadoCelda = when (p) {
        PosicionEnBanda.DENTRO -> EstadoCelda.DENTRO
        PosicionEnBanda.BAJO -> EstadoCelda.BAJO
        PosicionEnBanda.ALTO -> EstadoCelda.ALTO
    }

    private fun decimales(unidad: String): Int = if (unidad == "V") 1 else 0
}
