package com.revscope.feature.workshop.taller.prueba

import com.revscope.core.designsystem.BandaGrafica
import com.revscope.core.designsystem.ModeloGrafica
import com.revscope.core.designsystem.PuntoGrafica
import com.revscope.core.designsystem.SerieGrafica
import com.revscope.core.designsystem.TramoGrafica
import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.pruebas.AnalisisArranqueFrio
import com.revscope.core.obd.taller.pruebas.AnalisisMinimo
import com.revscope.core.obd.taller.pruebas.AnalizadorArranqueFrio
import com.revscope.core.obd.taller.pruebas.AnalizadorMinimo
import com.revscope.core.obd.taller.pruebas.Comprobacion
import com.revscope.core.obd.taller.pruebas.DatosPrueba
import com.revscope.core.obd.taller.pruebas.PatronArranque
import com.revscope.core.obd.taller.pruebas.TextoBanda
import com.revscope.core.obd.taller.pruebas.TextosArranqueFrio
import com.revscope.core.obd.taller.pruebas.TextosMinimo
import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.referencia.ClavesBanda

// Resultado del mínimo y del arranque en frío: medidas en líneas, comprobaciones con su banda y las series
// de RPM y temperatura de toda la prueba con los pasos sombreados.
internal object MapeoResultadoMotor {

    private const val CODIGO_ECT_INTERMITENTE = "P0119"

    fun minimo(base: ResultadoUi, a: AnalisisMinimo, datos: DatosPrueba?): ResultadoUi {
        val banda = a.comprobacion(ClavesBanda.MINIMO_RPM)?.banda
        return base.copy(
            comprobaciones = a.comprobaciones.map(::comprobacion),
            medidas = TextosMinimo.medidas(a),
            graficas = listOfNotNull(datos?.let { grafica(it, AnalizadorMinimo.PID_RPM, TextosMinimo.TITULOS_PASO, banda) }),
        )
    }

    fun arranque(base: ResultadoUi, a: AnalisisArranqueFrio, datos: DatosPrueba?): ResultadoUi = base.copy(
        comprobaciones = a.comprobaciones.map(::comprobacion),
        medidas = TextosArranqueFrio.medidas(a),
        graficas = datos?.let { d ->
            listOfNotNull(
                grafica(d, AnalizadorArranqueFrio.PID_RPM, TextosArranqueFrio.TITULOS_PASO, null),
                grafica(d, AnalizadorArranqueFrio.PID_ECT, TextosArranqueFrio.TITULOS_PASO, null),
            )
        }.orEmpty(),
        codigoGuia = CODIGO_ECT_INTERMITENTE.takeIf { a.patron == PatronArranque.SALTOS_ECT },
    )

    fun comprobacion(c: Comprobacion): ComprobacionUi {
        val banda = c.banda ?: return ComprobacionUi(c.etiqueta, c.cumple)
        val valor = c.valor?.let { "${FormatoTaller.numero(it, decimales(c.unidad))} ${TextoBanda.unidad(c.unidad)}".trim() } ?: "—"
        return ComprobacionUi("${c.etiqueta}: $valor · banda ${TextoBanda.citar(banda, decimalesBanda(banda.unidad))}", c.cumple)
    }

    private fun decimales(unidad: String): Int = when (unidad) {
        "V" -> 2
        "rpm/s", "kPa" -> 1
        else -> 0
    }

    private fun decimalesBanda(unidad: String): Int = if (unidad == "V") 1 else 0

    // ── Serie de toda la prueba ─────────────────────────────────────────────

    fun grafica(datos: DatosPrueba, pid: String, titulos: Map<String, String>, banda: BandaReferencia?): GraficaResultadoUi? =
        graficaCon(datos, pid, titulos, banda?.let(MapeoMagnitud::bandaGrafica), banda?.let(MapeoMagnitud::leyenda))

    // [leyendaBanda] describe la banda sombreada (o la de un solo lado, que no se sombrea) con su origen.
    fun graficaCon(
        datos: DatosPrueba,
        pid: String,
        titulos: Map<String, String>,
        bandaGrafica: BandaGrafica?,
        leyendaBanda: String?,
    ): GraficaResultadoUi? {
        val m = MapeoMagnitud.de(pid)
        val puntos = datos.muestras.filter { it.pid == pid }.sortedBy { it.tMicros }.map { PuntoGrafica(it.tMicros / 1_000_000.0, it.valor) }
        if (puntos.size < 2) return null
        val modelo = ModeloGrafica(
            series = listOf(SerieGrafica(m.nombre, puntos)),
            descripcion = "${m.nombre} en toda la prueba: de ${m.texto(puntos.minOf { it.y })} a ${m.texto(puntos.maxOf { it.y })}, " +
                "${pasosSombreados(datos.segmentos.size)}." + leyendaBanda?.let { " Banda $it" }.orEmpty(),
            bandas = listOfNotNull(bandaGrafica),
            tramos = datos.segmentos.mapIndexed { i, s -> TramoGrafica("${i + 1}", s.inicioMs / 1_000.0, s.finMs / 1_000.0) },
        )
        val leyenda = listOf(datos.segmentos.mapIndexed { i, s -> "${i + 1} ${titulos[s.clave] ?: s.clave}" }.joinToString(" · ")) +
            listOfNotNull(leyendaBanda)
        return GraficaResultadoUi(tituloSerie(m), modelo, leyenda)
    }

    private fun pasosSombreados(n: Int) = if (n == 1) "1 paso sombreado" else "$n pasos sombreados"

    // «RPM en toda la prueba, en rpm» repite la unidad: solo se agrega cuando dice algo más que el nombre.
    private fun tituloSerie(m: Magnitud): String {
        val titulo = "${m.nombre} en toda la prueba"
        return if (m.unidad.equals(m.nombre, ignoreCase = true)) titulo else "$titulo, en ${m.unidad}"
    }
}
