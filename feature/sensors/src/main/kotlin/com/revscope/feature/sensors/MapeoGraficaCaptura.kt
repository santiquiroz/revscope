package com.revscope.feature.sensors

import com.revscope.core.designsystem.BandaGrafica
import com.revscope.core.designsystem.EstiloLinea
import com.revscope.core.designsystem.ModeloGrafica
import com.revscope.core.designsystem.PuntoGrafica
import com.revscope.core.designsystem.SerieGrafica
import com.revscope.core.designsystem.estiloDeSerie
import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.grafica.BandaEnEscala
import com.revscope.core.obd.taller.grafica.BandasPosicion
import com.revscope.core.obd.taller.grafica.EstadoVref
import com.revscope.core.obd.taller.grafica.FormatoPosicion
import com.revscope.core.obd.taller.grafica.PidsPosicion
import com.revscope.core.obd.taller.grafica.Tendencia
import com.revscope.core.obd.taller.grafica.UnidadPosicion
import com.revscope.core.obd.taller.pruebas.ReferenciaVoltaje
import com.revscope.core.obd.taller.referencia.BandaReferencia
import kotlin.math.abs

typealias SerieCaptura = List<Pair<Long, Double>>

data class EntradaGraficaCaptura(
    val series: Map<String, SerieCaptura>,
    val nombre: (String) -> String,
    val unidadPid: (String) -> String,
    val hz: Map<String, Double>,
    val unidad: UnidadPosicion,
    val referencia: EstadoVref,
    val ventanaMs: Long,
    val pausada: Boolean,
    val bandas: Map<String, BandaReferencia>,
)

data class ValorPidUi(val pid: String, val nombre: String, val valor: String, val secundario: String?, val hz: String?)

// Un panel por magnitud: el TPS en % no se aplasta contra las rpm en el mismo eje.
data class PanelGrafica(val titulo: String, val modelo: ModeloGrafica, val leyenda: List<String>)

data class GraficaCapturaUi(
    val hayPosicion: Boolean,
    val unidad: UnidadPosicion,
    val referencia: EstadoVref,
    val ventanaS: Int,
    val pausada: Boolean,
    val valores: List<ValorPidUi>,
    val paneles: List<PanelGrafica>,
) {
    val tieneDatos: Boolean get() = paneles.any { it.modelo.tieneDatos }
}

// De las series del anillo de la captura a la gráfica en vivo: %/V en los PIDs de posición, bandas del TPS,
// ventana de 10 o 30 s, trazos distintos por serie y un resumen que TalkBack puede leer.
object MapeoGraficaCaptura {

    private const val PID_TPS = "11"
    private const val TPS_ESTABLE_V = 0.06
    private const val ESTABLE_RELATIVO = 0.02
    private const val GRUPO_POSICION = "posición"

    fun de(e: EntradaGraficaCaptura): GraficaCapturaUi {
        val recortadas = recortar(e.series, e.ventanaMs)
        return GraficaCapturaUi(
            hayPosicion = recortadas.keys.any(PidsPosicion::es),
            unidad = e.unidad,
            referencia = e.referencia,
            ventanaS = (e.ventanaMs / 1_000).toInt(),
            pausada = e.pausada,
            valores = recortadas.mapNotNull { (pid, serie) -> valor(pid, serie, e) },
            paneles = recortadas.entries
                .groupBy { grupo(it.key, e) }
                .map { (grupo, series) -> panel(grupo, series.associate { it.toPair() }, e) },
        )
    }

    private fun grupo(pid: String, e: EntradaGraficaCaptura): String =
        if (PidsPosicion.es(pid)) GRUPO_POSICION else e.unidadPid(pid).ifEmpty { pid }

    private fun panel(grupo: String, series: Map<String, SerieCaptura>, e: EntradaGraficaCaptura): PanelGrafica {
        val vref = e.referencia.usada
        val posicion = grupo == GRUPO_POSICION
        val bandas = if (PID_TPS in series) BandasPosicion.tps(e.bandas, e.unidad, vref) else emptyList()
        val lineas = series.entries.mapIndexed { i, (pid, serie) -> serieGrafica(pid, serie, i, e.unidad, vref) }
        return PanelGrafica(
            titulo = if (posicion) tituloPosicion(e.unidad) else tituloPanel(series.keys, grupo, e),
            modelo = ModeloGrafica(
                series = lineas,
                descripcion = resumen(series, e, bandas),
                bandas = bandas.map { BandaGrafica(it.etiquetaCorta, it.min, it.max) },
                rangoX = -e.ventanaMs / 1_000.0..0.0,
                rangoY = if (posicion) rangoPosicion(e.unidad, vref) else null,
            ),
            leyenda = leyendaSeries(lineas, e) + bandas.map { "Banda ${it.descripcion}" },
        )
    }

    private fun tituloPosicion(unidad: UnidadPosicion): String =
        if (unidad == UnidadPosicion.VOLTIOS) "Posición, en voltios" else "Posición, en %"

    private fun tituloPanel(pids: Set<String>, unidad: String, e: EntradaGraficaCaptura): String =
        pids.joinToString(" · ") { e.nombre(it) } + if (unidadRedundante(pids, unidad, e)) "" else ", en $unidad"

    private fun unidadRedundante(pids: Set<String>, unidad: String, e: EntradaGraficaCaptura): Boolean =
        unidad in pids || pids.all { e.nombre(it).equals(unidad, ignoreCase = true) }

    // Con una sola línea el título del panel ya la nombra; con varias, cada una dice su trazo.
    private fun leyendaSeries(lineas: List<SerieGrafica>, e: EntradaGraficaCaptura): List<String> =
        if (lineas.size < 2) emptyList() else lineas.map { "${it.etiqueta} ${e.nombre(it.etiqueta)}: ${trazo(it.estilo)}" }

    // Todas contra el último instante visto, así las series quedan alineadas a la derecha en 0 s.
    private fun recortar(series: Map<String, SerieCaptura>, ventanaMs: Long): Map<String, SerieCaptura> {
        val ultimo = series.values.mapNotNull { it.lastOrNull()?.first }.maxOrNull() ?: return emptyMap()
        return series.mapValues { (_, s) -> s.filter { ultimo - it.first <= ventanaMs }.map { (t, v) -> (t - ultimo) to v } }
            .filterValues { it.isNotEmpty() }
    }

    private fun serieGrafica(pid: String, serie: SerieCaptura, i: Int, unidad: UnidadPosicion, vref: ReferenciaVoltaje) =
        SerieGrafica(pid, serie.map { (t, v) -> PuntoGrafica(t / 1_000.0, enEscala(pid, v, unidad, vref)) }, estiloDeSerie(i))

    private fun enEscala(pid: String, valor: Double, unidad: UnidadPosicion, vref: ReferenciaVoltaje): Double =
        if (PidsPosicion.es(pid)) unidad.desdePorcentaje(valor, vref) else valor

    // La escala completa (0-100 % o 0-Vref) deja ver cuánto del recorrido se usa.
    private fun rangoPosicion(unidad: UnidadPosicion, vref: ReferenciaVoltaje): ClosedFloatingPointRange<Double> =
        if (unidad == UnidadPosicion.VOLTIOS) 0.0..vref.voltios else 0.0..100.0

    private fun valor(pid: String, serie: SerieCaptura, e: EntradaGraficaCaptura): ValorPidUi? {
        val ultimo = serie.lastOrNull()?.second ?: return null
        val (principal, secundario) = textos(pid, ultimo, e)
        return ValorPidUi(pid, e.nombre(pid), principal, secundario, e.hz[pid]?.let { "${FormatoTaller.numero(it, 1)} Hz" })
    }

    private fun textos(pid: String, valor: Double, e: EntradaGraficaCaptura): Pair<String, String?> {
        if (!PidsPosicion.es(pid)) return "${numero(valor)} ${e.unidadPid(pid)}".trim() to null
        val vref = e.referencia.usada
        val pct = FormatoPosicion.porcentaje(valor)
        val v = FormatoPosicion.voltios(vref.aVoltios(valor))
        return if (e.unidad == UnidadPosicion.VOLTIOS) v to pct else pct to v
    }

    private fun resumen(series: Map<String, SerieCaptura>, e: EntradaGraficaCaptura, bandas: List<BandaEnEscala>): String {
        val pausa = if (e.pausada) "Gráfica en pausa, la captura sigue. " else ""
        val porPid = series.map { (pid, s) -> resumenPid(pid, s, e) }
        val conBandas = bandas.takeIf { it.isNotEmpty() }?.joinToString("; ", prefix = " Bandas: ") { it.descripcion }.orEmpty()
        return "${pausa}Últimos ${e.ventanaMs / 1_000} s. ${porPid.joinToString(". ")}.$conBandas"
    }

    private fun resumenPid(pid: String, serie: SerieCaptura, e: EntradaGraficaCaptura): String {
        val (principal, secundario) = textos(pid, serie.last().second, e)
        val valor = secundario?.let { "$principal ($it)" } ?: principal
        return "$pid ${e.nombre(pid)}: $valor, ${Tendencia.de(serie.map { it.second }, umbralEstable(pid, serie, e.referencia.usada))}"
    }

    // Un TPS quieto varía un par de escalones (≈0,06 V); para lo demás, ±2 % de su valor.
    private fun umbralEstable(pid: String, serie: SerieCaptura, vref: ReferenciaVoltaje): Double =
        if (PidsPosicion.es(pid)) vref.aPorcentaje(TPS_ESTABLE_V) else maxOf(abs(serie.map { it.second }.average()), 1.0) * ESTABLE_RELATIVO

    private fun numero(valor: Double): String = FormatoTaller.numero(valor, if (abs(valor) >= 100) 0 else 1)

    private fun trazo(estilo: EstiloLinea): String = when (estilo) {
        EstiloLinea.CONTINUA -> "trazo continuo"
        EstiloLinea.DISCONTINUA -> "trazo discontinuo"
        EstiloLinea.PUNTEADA -> "trazo punteado"
    }
}
