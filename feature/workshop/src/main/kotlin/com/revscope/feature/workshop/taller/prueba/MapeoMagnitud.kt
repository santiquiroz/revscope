package com.revscope.feature.workshop.taller.prueba

import androidx.annotation.StringRes
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.common.i18n.LocaleNumbers
import com.revscope.core.designsystem.BandaGrafica
import com.revscope.core.designsystem.ModeloGrafica
import com.revscope.core.designsystem.PuntoGrafica
import com.revscope.core.designsystem.SerieGrafica
import com.revscope.core.obd.taller.grafica.Tendencia
import com.revscope.core.obd.taller.pruebas.AnalizadorBateria
import com.revscope.core.obd.taller.pruebas.AnalizadorMinimo
import com.revscope.core.obd.taller.pruebas.TextoBanda
import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.referencia.ClavesBanda
import com.revscope.core.obd.taller.referencia.OrigenBanda
import com.revscope.feature.workshop.R

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
    fun texto(valor: Double, resolver: StringResolver): String =
        "${LocaleNumbers.decimal(valor, decimales, resolver.locale)} $unidad".trim()

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

    private data class Nombre(@StringRes val nombre: Int, @StringRes val tipico: Int)

    private val NOMBRES = mapOf(
        ClavesBanda.MINIMO_RPM to Nombre(R.string.workshop_prueba_banda_minimo, R.string.workshop_prueba_banda_minimo_tipico),
        ClavesBanda.ARRANQUE_MIN_V to Nombre(R.string.workshop_prueba_banda_valle, R.string.workshop_prueba_banda_valle_tipico),
        ClavesBanda.CARGA_V to Nombre(R.string.workshop_prueba_banda_carga, R.string.workshop_prueba_banda_carga_tipica),
    )

    fun de(pid: String, resolver: StringResolver): Magnitud = when (pid.uppercase()) {
        PID_RPM -> Magnitud("RPM", "rpm", 0, 50.0, 500.0)
        "05" -> Magnitud(resolver.get(R.string.workshop_prueba_magnitud_temp_motor), "°C", 0, 1.0, 10.0)
        "0F" -> Magnitud(resolver.get(R.string.workshop_prueba_magnitud_aire_admision), "°C", 0, 1.0, 10.0)
        "46" -> Magnitud(resolver.get(R.string.workshop_prueba_magnitud_temp_ambiente), "°C", 0, 1.0, 10.0)
        "0B" -> Magnitud("MAP", "kPa", 0, 1.0, 10.0)
        "33" -> Magnitud(resolver.get(R.string.workshop_prueba_magnitud_presion_baro), "kPa", 0, 1.0, 10.0)
        PID_VOLTAJE -> voltaje(resolver)
        else -> Magnitud(nombrePid(pid, resolver), "", 1, 0.0, 0.0)
    }

    private fun voltaje(resolver: StringResolver) = Magnitud(
        resolver.get(R.string.workshop_prueba_magnitud_voltaje_bateria),
        "V",
        1,
        0.1,
        2.0,
        fuente = resolver.get(R.string.workshop_prueba_magnitud_fuente_at_rv),
    )

    private fun nombrePid(pid: String, resolver: StringResolver) = resolver.get(R.string.workshop_prueba_pid, pid)

    fun valorVivo(pid: String, valor: Double, resolver: StringResolver): ValorVivoUi {
        val m = de(pid, resolver)
        val origen = m.fuente ?: nombrePid(pid.uppercase(), resolver)
        return ValorVivoUi(etiqueta = "${m.nombre} · $origen", principal = m.texto(valor, resolver))
    }

    fun banda(pid: String, clavePaso: String, bandas: Map<String, BandaReferencia>): BandaReferencia? =
        BANDA_POR_PASO[pid.uppercase() to clavePaso]?.let { bandas[it] }

    fun etiquetaCorta(b: BandaReferencia, resolver: StringResolver): String {
        val n = NOMBRES[b.clave] ?: return b.clave
        return resolver.get(if (b.origen == OrigenBanda.TIPICO) n.tipico else n.nombre)
    }

    fun leyenda(b: BandaReferencia, resolver: StringResolver): String {
        val nombre = NOMBRES[b.clave]?.let { resolver.get(it.nombre) } ?: b.clave
        return "$nombre: ${TextoBanda.rango(b, decimales(b), resolver)} · ${b.etiquetaOrigen(resolver)}"
    }

    // Solo se sombrea una banda con los dos bordes; la de un solo lado (≥ 9,6 V) va en la leyenda.
    fun bandaGrafica(b: BandaReferencia, resolver: StringResolver): BandaGrafica? {
        val desde = b.min ?: return null
        val hasta = b.max ?: return null
        return BandaGrafica(etiquetaCorta(b, resolver), desde, hasta)
    }

    fun grafica(pid: String, serie: SerieVivo, ventanaMs: Long, banda: BandaReferencia?, resolver: StringResolver): ModeloGrafica {
        val m = de(pid, resolver)
        val ultimo = serie.lastOrNull()?.first ?: 0L
        val puntos = serie.filter { ultimo - it.first <= ventanaMs }.map { (t, v) -> PuntoGrafica((t - ultimo) / 1_000.0, v) }
        return ModeloGrafica(
            series = listOf(SerieGrafica(m.nombre, puntos)),
            descripcion = descripcion(pid, m, puntos.map { it.y }, banda, resolver),
            bandas = listOfNotNull(banda?.let { bandaGrafica(it, resolver) }),
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

    private fun descripcion(pid: String, m: Magnitud, valores: List<Double>, banda: BandaReferencia?, resolver: StringResolver): String {
        val objetivo = banda?.let { resolver.get(R.string.workshop_prueba_grafica_objetivo, leyenda(it, resolver)) }.orEmpty()
        val origen = m.fuente ?: nombrePid(pid, resolver)
        if (valores.isEmpty()) return resolver.get(R.string.workshop_prueba_magnitud_descripcion_vacia, m.nombre, origen, objetivo)
        return resolver.get(
            R.string.workshop_prueba_magnitud_descripcion,
            m.nombre,
            origen,
            m.texto(valores.min(), resolver),
            m.texto(valores.max(), resolver),
            Tendencia.de(valores, m.estableHasta, resolver),
            objetivo,
        )
    }
}
