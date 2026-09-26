package com.revscope.core.obd.taller.pruebas

import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.referencia.ClavesBanda
import com.revscope.core.obd.taller.referencia.PosicionEnBanda
import kotlin.math.abs

// MAP con el motor apagado contra la presión barométrica (§2.4.6 del diseño del Taller): sin vacío, el MAP
// debe marcar lo mismo que la atmósfera. La tolerancia es la banda típica más la incertidumbre de la fuente.
object AnalizadorMapBaro {

    const val PID_MAP = "0B"
    const val PID_BARO = "33"
    const val BANDA_TIPICA_KPA = 3.0
    const val VARIACION_MAX_KPA = 3.0

    object Pasos {
        const val CONTACTO = "CONTACTO"
    }

    fun analizar(datos: DatosPrueba, bandas: Map<String, BandaReferencia>): ResultadoPrueba {
        val map = medir(datos.serie(PID_MAP, Pasos.CONTACTO))
        val fuentes = FuenteBarometrica.disponibles(mediaEcu(datos), datos.contexto.ambiente)
        val base = AnalisisMapBaro(
            map = map,
            referencia = fuentes.firstOrNull(),
            alternativa = fuentes.getOrNull(1),
            banda = bandas[ClavesBanda.MAP_KOEO_VS_BARO_KPA],
            comprobaciones = emptyList(),
            patron = PatronMapBaro.NORMAL,
        )
        val comprobado = base.copy(comprobaciones = listOfNotNull(comprobacion(base)))
        return TextosMapBaro.resultado(comprobado.copy(patron = patron(comprobado)))
    }

    // Fuera de la tolerancia (banda + incertidumbre): el lado dice si el MAP marca de menos o de más.
    fun posicion(delta: Double, bandaKpa: Double, toleranciaKpa: Double): PosicionEnBanda? = when {
        delta < -toleranciaKpa -> PosicionEnBanda.BAJO
        delta > toleranciaKpa -> PosicionEnBanda.ALTO
        abs(delta) <= bandaKpa -> PosicionEnBanda.DENTRO
        else -> null
    }

    private fun medir(serie: List<Punto>): MapMedido? {
        val valores = serie.map { it.valor }.takeIf { it.isNotEmpty() } ?: return null
        return MapMedido(valores.average(), valores.min(), valores.max(), valores.size)
    }

    private fun mediaEcu(datos: DatosPrueba): Double? =
        datos.serie(PID_BARO, Pasos.CONTACTO).map { it.valor }.takeIf { it.isNotEmpty() }?.average()

    // La comprobación compara |Δ| con la banda publicada; que cumpla o no lo decide la tolerancia con la fuente.
    private fun comprobacion(a: AnalisisMapBaro): Comprobacion? {
        val delta = a.deltaKpa ?: return null
        val tolerancia = a.toleranciaKpa ?: return null
        val enTolerancia = abs(delta) <= tolerancia
        return Comprobacion(
            clave = ClavesBanda.MAP_KOEO_VS_BARO_KPA,
            etiqueta = "Diferencia entre el MAP y la barométrica",
            valor = abs(delta),
            unidad = "kPa",
            banda = a.banda,
            posicion = if (enTolerancia) PosicionEnBanda.DENTRO else PosicionEnBanda.ALTO,
        )
    }

    private fun patron(a: AnalisisMapBaro): PatronMapBaro {
        if (a.map == null) return PatronMapBaro.SIN_DATOS
        val delta = a.deltaKpa ?: return PatronMapBaro.SIN_REFERENCIA
        val tolerancia = a.toleranciaKpa ?: return PatronMapBaro.SIN_REFERENCIA
        return when (posicion(delta, a.banda?.max ?: BANDA_TIPICA_KPA, tolerancia)) {
            PosicionEnBanda.BAJO -> PatronMapBaro.MAP_BAJO
            PosicionEnBanda.ALTO -> PatronMapBaro.MAP_ALTO
            PosicionEnBanda.DENTRO -> if (a.contrasteDiscrepa) PatronMapBaro.NO_CONCLUYENTE else PatronMapBaro.NORMAL
            null -> PatronMapBaro.NO_CONCLUYENTE
        }
    }
}
