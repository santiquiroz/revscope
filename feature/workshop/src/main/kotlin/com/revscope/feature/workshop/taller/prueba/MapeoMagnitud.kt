package com.revscope.feature.workshop.taller.prueba

import com.revscope.core.designsystem.BandaGrafica
import com.revscope.core.designsystem.ModeloGrafica
import com.revscope.core.designsystem.PuntoGrafica
import com.revscope.core.designsystem.SerieGrafica
import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.grafica.Tendencia
import com.revscope.core.obd.taller.pruebas.AnalizadorBateria
import com.revscope.core.obd.taller.pruebas.AnalizadorMinimo
import com.revscope.core.obd.taller.pruebas.TextoBanda
import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.referencia.ClavesBanda
import com.revscope.core.obd.taller.referencia.OrigenBanda

// Cómo se nombra y se escribe cada magnitud que no es de posición (RPM, temperaturas, presiones y el voltaje
// de AT RV) en la prueba guiada. [spanMinimo]: la escala Y nunca es más estrecha que esto, para que 1 °C de
// resolución no parezca un salto. [fuente]: de dónde sale, si no es un PID (el voltaje lo responde el ELM).
internal data class Magnitud(
    val nombre: String,
    val unidad: String,
    val decimales: Int,
    val estableHasta: Double,
    val spanMinimo: Double,
    val fuente: String? = null,
) {
    fun texto(valor: Double): String = "${FormatoTaller.numero(valor, decimales)} $unidad".trim()
}

internal object MapeoMagnitud {

    private const val PID_RPM = "0C"
    private const val PID_VOLTAJE = AnalizadorBateria.PID_VOLTAJE

    // La banda que se sombrea mientras se graba cada paso, por PID: la clave del paso sola se repite entre pruebas.
    private val BANDA_POR_PASO = mapOf(
        (PID_RPM to AnalizadorMinimo.Pasos.MINIMO) to ClavesBanda.MINIMO_RPM,
        (PID_VOLTAJE to AnalizadorBateria.Pasos.ARRANQUE) to ClavesBanda.ARRANQUE_MIN_V,
        (PID_VOLTAJE to AnalizadorBateria.Pasos.MINIMO) to ClavesBanda.CARGA_V,
        (PID_VOLTAJE to AnalizadorBateria.Pasos.RPM_ALTAS) to ClavesBanda.CARGA_V,
    )

    private data class Nombre(val nombre: String, val tipico: String)

    private val NOMBRES = mapOf(
        ClavesBanda.MINIMO_RPM to Nombre("Mínimo", "Mínimo típico"),
        ClavesBanda.ARRANQUE_MIN_V to Nombre("Valle al arrancar", "Valle típico"),
        ClavesBanda.CARGA_V to Nombre("Carga", "Carga típica"),
    )

    fun de(pid: String): Magnitud = when (pid.uppercase()) {
        PID_RPM -> Magnitud("RPM", "rpm", 0, 50.0, 500.0)
        "05" -> Magnitud("Temperatura del motor", "°C", 0, 1.0, 10.0)
        "0F" -> Magnitud("Aire de admisión", "°C", 0, 1.0, 10.0)
        "46" -> Magnitud("Temperatura ambiente", "°C", 0, 1.0, 10.0)
        "0B" -> Magnitud("MAP", "kPa", 0, 1.0, 10.0)
        "33" -> Magnitud("Presión barométrica", "kPa", 0, 1.0, 10.0)
        PID_VOLTAJE -> Magnitud("Voltaje de la batería", "V", 1, 0.1, 2.0, fuente = "AT RV en el conector")
        else -> Magnitud("PID $pid", "", 1, 0.0, 0.0)
    }

    fun valorVivo(pid: String, valor: Double): ValorVivoUi {
        val m = de(pid)
        return ValorVivoUi(etiqueta = "${m.nombre} · ${m.fuente ?: "PID ${pid.uppercase()}"}", principal = m.texto(valor))
    }

    fun banda(pid: String, clavePaso: String, bandas: Map<String, BandaReferencia>): BandaReferencia? =
        BANDA_POR_PASO[pid.uppercase() to clavePaso]?.let { bandas[it] }

    fun etiquetaCorta(b: BandaReferencia): String {
        val n = NOMBRES[b.clave] ?: return b.clave
        return if (b.origen == OrigenBanda.TIPICO) n.tipico else n.nombre
    }

    fun leyenda(b: BandaReferencia): String =
        "${NOMBRES[b.clave]?.nombre ?: b.clave}: ${TextoBanda.rango(b, decimales(b))} · ${b.etiquetaOrigen}"

    // Solo se sombrea una banda con los dos bordes; la de un solo lado (≥ 9,6 V) va en la leyenda.
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

    private fun decimales(b: BandaReferencia): Int = if (b.unidad == "V") 1 else 0

    private fun descripcion(pid: String, m: Magnitud, valores: List<Double>, banda: BandaReferencia?): String {
        val objetivo = banda?.let { ". Banda: ${leyenda(it)}" }.orEmpty()
        val origen = m.fuente ?: "PID $pid"
        if (valores.isEmpty()) return "${m.nombre} ($origen) en los últimos 10 s: esperando muestras$objetivo"
        return "${m.nombre} ($origen) en los últimos 10 s: de ${m.texto(valores.min())} a ${m.texto(valores.max())}, " +
            "${Tendencia.de(valores, m.estableHasta)}$objetivo"
    }
}
