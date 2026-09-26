package com.revscope.feature.workshop.taller.prueba

import com.revscope.core.designsystem.BandaGrafica
import com.revscope.core.designsystem.ModeloGrafica
import com.revscope.core.designsystem.PuntoGrafica
import com.revscope.core.designsystem.SerieGrafica
import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.grafica.Tendencia
import com.revscope.core.obd.taller.pruebas.AnalizadorMinimo
import com.revscope.core.obd.taller.pruebas.TextoBanda
import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.referencia.ClavesBanda
import com.revscope.core.obd.taller.referencia.OrigenBanda

// Cómo se nombra y se escribe cada magnitud que no es de posición (RPM y temperaturas) en la prueba guiada.
// [spanMinimo]: la escala Y nunca es más estrecha que esto, para que 1 °C de resolución no parezca un salto.
internal data class Magnitud(
    val nombre: String,
    val unidad: String,
    val decimales: Int,
    val estableHasta: Double,
    val spanMinimo: Double,
) {
    fun texto(valor: Double): String = "${FormatoTaller.numero(valor, decimales)} $unidad".trim()
}

internal object MapeoMagnitud {

    private const val PID_RPM = "0C"

    // Solo el mínimo tiene una banda que sombrear mientras se graba.
    private val BANDA_POR_PASO = mapOf(AnalizadorMinimo.Pasos.MINIMO to ClavesBanda.MINIMO_RPM)

    fun de(pid: String): Magnitud = when (pid.uppercase()) {
        PID_RPM -> Magnitud("RPM", "rpm", 0, 50.0, 500.0)
        "05" -> Magnitud("Temperatura del motor", "°C", 0, 1.0, 10.0)
        "0F" -> Magnitud("Aire de admisión", "°C", 0, 1.0, 10.0)
        "46" -> Magnitud("Temperatura ambiente", "°C", 0, 1.0, 10.0)
        else -> Magnitud("PID $pid", "", 1, 0.0, 0.0)
    }

    fun valorVivo(pid: String, valor: Double): ValorVivoUi {
        val m = de(pid)
        return ValorVivoUi(etiqueta = "${m.nombre} · PID ${pid.uppercase()}", principal = m.texto(valor))
    }

    fun banda(clavePaso: String, bandas: Map<String, BandaReferencia>): BandaReferencia? =
        BANDA_POR_PASO[clavePaso]?.let { bandas[it] }

    fun etiquetaCorta(b: BandaReferencia): String = if (b.origen == OrigenBanda.TIPICO) "Mínimo típico" else "Mínimo"

    fun leyenda(b: BandaReferencia): String = "Mínimo: ${TextoBanda.rango(b, 0)} · ${b.etiquetaOrigen}"

    fun bandaGrafica(b: BandaReferencia): BandaGrafica? {
        val desde = b.min ?: return null
        val hasta = b.max ?: return null
        return BandaGrafica(etiquetaCorta(b), desde, hasta)
    }

    fun grafica(pid: String, serie: SerieVivo, ventanaMs: Long, banda: BandaReferencia?): ModeloGrafica {
        val m = de(pid)
        val ultimo = serie.lastOrNull()?.first ?: 0L
        val puntos = serie.filter { ultimo - it.first <= ventanaMs }.map { (t, v) -> PuntoGrafica((t - ultimo) / 1_000.0, v) }
        return ModeloGrafica(
            series = listOf(SerieGrafica(m.nombre, puntos)),
            descripcion = descripcion(pid, m, puntos.map { it.y }, banda),
            bandas = listOfNotNull(banda?.let(::bandaGrafica)),
            rangoX = -ventanaMs / 1_000.0..0.0,
            rangoY = rangoY(puntos.map { it.y } + listOfNotNull(banda?.min, banda?.max), m.spanMinimo),
        )
    }

    fun rangoY(valores: List<Double>, spanMinimo: Double): ClosedFloatingPointRange<Double>? {
        if (valores.isEmpty() || spanMinimo <= 0.0) return null
        val min = valores.min()
        val max = valores.max()
        val falta = (spanMinimo - (max - min)).coerceAtLeast(0.0) / 2
        return (min - falta)..(max + falta)
    }

    private fun descripcion(pid: String, m: Magnitud, valores: List<Double>, banda: BandaReferencia?): String {
        val objetivo = banda?.let { ". Banda: ${leyenda(it)}" }.orEmpty()
        if (valores.isEmpty()) return "${m.nombre} (PID $pid) en los últimos 10 s: esperando muestras$objetivo"
        return "${m.nombre} (PID $pid) en los últimos 10 s: de ${m.texto(valores.min())} a ${m.texto(valores.max())}, " +
            "${Tendencia.de(valores, m.estableHasta)}$objetivo"
    }
}
