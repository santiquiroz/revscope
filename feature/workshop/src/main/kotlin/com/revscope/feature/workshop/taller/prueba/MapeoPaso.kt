package com.revscope.feature.workshop.taller.prueba

import com.revscope.core.designsystem.BandaGrafica
import com.revscope.core.designsystem.ModeloGrafica
import com.revscope.core.designsystem.PuntoGrafica
import com.revscope.core.designsystem.SerieGrafica
import com.revscope.core.designsystem.textoPaso
import com.revscope.core.obd.taller.grafica.BandaEnEscala
import com.revscope.core.obd.taller.grafica.BandasPosicion
import com.revscope.core.obd.taller.grafica.FormatoPosicion
import com.revscope.core.obd.taller.grafica.PidsPosicion
import com.revscope.core.obd.taller.grafica.Tendencia
import com.revscope.core.obd.taller.grafica.UnidadPosicion
import com.revscope.core.obd.taller.pruebas.AnalizadorBarridoTps.Pasos
import com.revscope.core.obd.taller.pruebas.EstadoPrueba
import com.revscope.core.obd.taller.pruebas.FasePaso
import com.revscope.core.obd.taller.pruebas.ReferenciaVoltaje
import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.referencia.ClavesBanda
import kotlin.math.ceil

// Serie en vivo de un PID: (ms de la captura, valor en %).
typealias SerieVivo = List<Pair<Long, Double>>

internal object MapeoPaso {

    const val VENTANA_MS = 10_000L
    const val SEGUNDOS_ANUNCIADOS = 3
    private const val ESTABLE_MAX_V = 0.06

    // Solo el paso cuya posición tiene banda la sombrea; el barrido lento recorre las dos.
    private val BANDAS_POR_PASO = mapOf(
        Pasos.CERRADO_1 to setOf(ClavesBanda.TPS_CERRADO_V),
        Pasos.CERRADO_2 to setOf(ClavesBanda.TPS_CERRADO_V),
        Pasos.A_FONDO to setOf(ClavesBanda.TPS_FONDO_V),
        Pasos.BARRIDO_LENTO to setOf(ClavesBanda.TPS_CERRADO_V, ClavesBanda.TPS_FONDO_V),
    )

    fun de(e: EstadoPrueba.EnPaso, pid: String, serie: SerieVivo, vref: ReferenciaVoltaje, bandas: Map<String, BandaReferencia>): PasoUi =
        if (PidsPosicion.es(pid)) dePosicion(e, pid, serie, vref, bandas) else deMagnitud(e, pid, serie, bandas)

    private fun dePosicion(e: EstadoPrueba.EnPaso, pid: String, serie: SerieVivo, vref: ReferenciaVoltaje, bandas: Map<String, BandaReferencia>): PasoUi {
        val objetivo = BandasPosicion.tps(bandas, UnidadPosicion.VOLTIOS, vref).filter { it.clave in BANDAS_POR_PASO[e.paso.clave].orEmpty() }
        return base(e).copy(
            vivo = serie.lastOrNull()?.let { valorVivo(pid, it.second, vref) },
            grafica = grafica(pid, serie, vref, objetivo),
            tituloGrafica = "Últimos 10 s, en voltios",
            leyendaGrafica = objetivo.map { it.descripcion },
        )
    }

    // RPM y temperaturas: el valor tal cual, en su unidad, y la banda del paso si la tiene (el mínimo).
    private fun deMagnitud(e: EstadoPrueba.EnPaso, pid: String, serie: SerieVivo, bandas: Map<String, BandaReferencia>): PasoUi {
        val banda = MapeoMagnitud.banda(e.paso.clave, bandas)
        return base(e).copy(
            vivo = serie.lastOrNull()?.let { MapeoMagnitud.valorVivo(pid, it.second) },
            grafica = MapeoMagnitud.grafica(pid, serie, VENTANA_MS, banda),
            tituloGrafica = "Últimos 10 s, en ${MapeoMagnitud.de(pid).unidad.ifEmpty { "unidades del PID" }}",
            leyendaGrafica = listOfNotNull(banda?.let(MapeoMagnitud::leyenda)),
        )
    }

    private fun base(e: EstadoPrueba.EnPaso): PasoUi {
        val restanteS = e.restanteMs?.let { ceil(it / 1_000.0).toInt() }
        return PasoUi(
            tipo = e.tipo,
            indice = e.indice + 1,
            total = e.total,
            titulo = e.paso.titulo,
            instruccion = e.paso.instruccion,
            subtexto = TextosPrueba.subtexto(e.fase, e.paso.modo),
            fase = e.fase,
            restanteS = restanteS,
            duracionS = ceil(e.paso.modo.limiteMs / 1_000.0).toInt(),
            fraccionRestante = e.restanteMs?.let { (it.toFloat() / e.paso.modo.limiteMs).coerceIn(0f, 1f) },
            vivo = null,
            grafica = ModeloGrafica(emptyList(), ""),
            tituloGrafica = "",
            leyendaGrafica = emptyList(),
            anuncio = anuncio(e, restanteS),
            accionPrincipal = TextosPrueba.accionPrincipal(e.fase, e.paso.modo),
            grabados = e.indice,
        )
    }

    // Lo que lee TalkBack solo cambia al cambiar de paso o de fase y en los últimos 3 s, no con cada décima.
    fun anuncio(e: EstadoPrueba.EnPaso, restanteS: Int?): String {
        if (e.fase != FasePaso.POSICIONANDO && restanteS != null && restanteS in 1..SEGUNDOS_ANUNCIADOS) return "$restanteS"
        return "${textoPaso(e.indice + 1, e.total, e.paso.titulo)}. ${TextosPrueba.estadoFase(e.fase)}"
    }

    private fun valorVivo(pid: String, porcentaje: Double, vref: ReferenciaVoltaje) = ValorVivoUi(
        etiqueta = "TPS · PID $pid · con ${FormatoPosicion.voltios(vref.voltios)} de referencia",
        principal = FormatoPosicion.porcentaje(porcentaje),
        secundario = FormatoPosicion.voltios(vref.aVoltios(porcentaje)),
    )

    private fun grafica(pid: String, serie: SerieVivo, vref: ReferenciaVoltaje, bandas: List<BandaEnEscala>): ModeloGrafica {
        val ultimo = serie.lastOrNull()?.first ?: 0L
        val puntos = serie.filter { ultimo - it.first <= VENTANA_MS }
            .map { (t, pct) -> PuntoGrafica((t - ultimo) / 1_000.0, vref.aVoltios(pct)) }
        return ModeloGrafica(
            series = listOf(SerieGrafica("TPS", puntos)),
            descripcion = descripcion(pid, puntos.map { it.y }, bandas),
            bandas = bandas.map { BandaGrafica(it.etiquetaCorta, it.min, it.max) },
            rangoX = -VENTANA_MS / 1_000.0..0.0,
            rangoY = 0.0..vref.voltios,
        )
    }

    private fun descripcion(pid: String, valoresV: List<Double>, bandas: List<BandaEnEscala>): String {
        val objetivo = bandas.joinToString("; ") { it.descripcion }.let { if (it.isEmpty()) "" else ". Banda: $it" }
        if (valoresV.isEmpty()) return "PID $pid en los últimos 10 s: esperando muestras$objetivo"
        val min = FormatoPosicion.voltios(valoresV.min())
        val max = FormatoPosicion.voltios(valoresV.max())
        return "PID $pid en los últimos 10 s: de $min a $max, ${Tendencia.de(valoresV, ESTABLE_MAX_V)}$objetivo"
    }
}
