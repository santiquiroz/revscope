package com.revscope.feature.workshop.taller.prueba

import com.revscope.core.designsystem.BandaGrafica
import com.revscope.core.designsystem.ModeloGrafica
import com.revscope.core.designsystem.PuntoGrafica
import com.revscope.core.designsystem.SerieGrafica
import com.revscope.core.designsystem.textoPaso
import com.revscope.core.common.i18n.StringResolver
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
import com.revscope.feature.workshop.R
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

    fun de(
        e: EstadoPrueba.EnPaso,
        pid: String,
        serie: SerieVivo,
        vref: ReferenciaVoltaje,
        bandas: Map<String, BandaReferencia>,
        resolver: StringResolver,
    ): PasoUi =
        if (PidsPosicion.es(pid)) dePosicion(e, pid, serie, vref, bandas, resolver) else deMagnitud(e, pid, serie, bandas, resolver)

    private fun dePosicion(
        e: EstadoPrueba.EnPaso,
        pid: String,
        serie: SerieVivo,
        vref: ReferenciaVoltaje,
        bandas: Map<String, BandaReferencia>,
        resolver: StringResolver,
    ): PasoUi {
        val objetivo = BandasPosicion.tps(bandas, UnidadPosicion.VOLTIOS, vref, resolver)
            .filter { it.clave in BANDAS_POR_PASO[e.paso.clave].orEmpty() }
        return base(e, resolver).copy(
            vivo = serie.lastOrNull()?.let { valorVivo(pid, it.second, vref, resolver) },
            grafica = grafica(pid, serie, vref, objetivo, resolver),
            tituloGrafica = resolver.get(R.string.workshop_prueba_titulo_grafica_voltios),
            leyendaGrafica = objetivo.map { it.descripcion },
        )
    }

    // RPM y temperaturas: el valor tal cual, en su unidad, y la banda del paso si la tiene (el mínimo).
    private fun deMagnitud(
        e: EstadoPrueba.EnPaso,
        pid: String,
        serie: SerieVivo,
        bandas: Map<String, BandaReferencia>,
        resolver: StringResolver,
    ): PasoUi {
        val banda = MapeoMagnitud.banda(pid, e.paso.clave, bandas)
        return base(e, resolver).copy(
            vivo = serie.lastOrNull()?.let { MapeoMagnitud.valorVivo(pid, it.second, resolver) },
            grafica = MapeoMagnitud.grafica(pid, serie, VENTANA_MS, banda, resolver),
            tituloGrafica = tituloMagnitud(pid, resolver),
            leyendaGrafica = listOfNotNull(banda?.let { MapeoMagnitud.leyenda(it, resolver) }),
        )
    }

    private fun tituloMagnitud(pid: String, resolver: StringResolver): String {
        val unidad = MapeoMagnitud.de(pid, resolver).unidad.ifEmpty { resolver.get(R.string.workshop_prueba_unidades_pid) }
        return resolver.get(R.string.workshop_prueba_titulo_grafica_unidad, unidad)
    }

    private fun base(e: EstadoPrueba.EnPaso, resolver: StringResolver): PasoUi {
        val restanteS = e.restanteMs?.let { ceil(it / 1_000.0).toInt() }
        return PasoUi(
            tipo = e.tipo,
            indice = e.indice + 1,
            total = e.total,
            titulo = e.paso.titulo,
            instruccion = e.paso.instruccion(resolver),
            subtexto = TextosPrueba.subtexto(e.fase, e.paso.modo, resolver),
            fase = e.fase,
            restanteS = restanteS,
            duracionS = ceil(e.paso.modo.limiteMs / 1_000.0).toInt(),
            fraccionRestante = e.restanteMs?.let { (it.toFloat() / e.paso.modo.limiteMs).coerceIn(0f, 1f) },
            vivo = null,
            grafica = ModeloGrafica(emptyList(), ""),
            tituloGrafica = "",
            leyendaGrafica = emptyList(),
            anuncio = anuncio(e, restanteS, resolver),
            accionPrincipal = TextosPrueba.accionPrincipal(e.fase, e.paso.modo, resolver),
            grabados = e.indice,
        )
    }

    // Lo que lee TalkBack solo cambia al cambiar de paso o de fase y en los últimos 3 s, no con cada décima.
    fun anuncio(e: EstadoPrueba.EnPaso, restanteS: Int?, resolver: StringResolver): String {
        if (e.fase != FasePaso.POSICIONANDO && restanteS != null && restanteS in 1..SEGUNDOS_ANUNCIADOS) return "$restanteS"
        return "${textoPaso(e.indice + 1, e.total, resolver, e.paso.titulo)}. ${TextosPrueba.estadoFase(e.fase, resolver)}"
    }

    private fun valorVivo(pid: String, porcentaje: Double, vref: ReferenciaVoltaje, resolver: StringResolver) = ValorVivoUi(
        etiqueta = resolver.get(R.string.workshop_prueba_vivo_tps, pid, FormatoPosicion.voltios(vref.voltios, resolver)),
        principal = FormatoPosicion.porcentaje(porcentaje, resolver),
        secundario = FormatoPosicion.voltios(vref.aVoltios(porcentaje), resolver),
    )

    private fun grafica(
        pid: String,
        serie: SerieVivo,
        vref: ReferenciaVoltaje,
        bandas: List<BandaEnEscala>,
        resolver: StringResolver,
    ): ModeloGrafica {
        val ultimo = serie.lastOrNull()?.first ?: 0L
        val puntos = serie.filter { ultimo - it.first <= VENTANA_MS }
            .map { (t, pct) -> PuntoGrafica((t - ultimo) / 1_000.0, vref.aVoltios(pct)) }
        return ModeloGrafica(
            series = listOf(SerieGrafica("TPS", puntos)),
            descripcion = descripcion(pid, puntos.map { it.y }, bandas, resolver),
            bandas = bandas.map { BandaGrafica(it.etiquetaCorta, it.min, it.max) },
            rangoX = -VENTANA_MS / 1_000.0..0.0,
            rangoY = 0.0..vref.voltios,
        )
    }

    private fun descripcion(pid: String, valoresV: List<Double>, bandas: List<BandaEnEscala>, resolver: StringResolver): String {
        val objetivo = bandas.joinToString("; ") { it.descripcion }
            .let { if (it.isEmpty()) "" else resolver.get(R.string.workshop_prueba_grafica_objetivo, it) }
        if (valoresV.isEmpty()) return resolver.get(R.string.workshop_prueba_tps_descripcion_vacia, pid, objetivo)
        val min = FormatoPosicion.voltios(valoresV.min(), resolver)
        val max = FormatoPosicion.voltios(valoresV.max(), resolver)
        val tendencia = Tendencia.de(valoresV, ESTABLE_MAX_V, resolver)
        return resolver.get(R.string.workshop_prueba_tps_descripcion, pid, min, max, tendencia, objetivo)
    }
}
