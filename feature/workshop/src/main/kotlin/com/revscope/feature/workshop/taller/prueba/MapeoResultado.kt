package com.revscope.feature.workshop.taller.prueba

import com.revscope.core.common.i18n.LocaleNumbers
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.designsystem.BandaGrafica
import com.revscope.core.designsystem.ModeloGrafica
import com.revscope.core.designsystem.PuntoGrafica
import com.revscope.core.designsystem.SerieGrafica
import com.revscope.core.designsystem.TramoGrafica
import com.revscope.core.obd.taller.grafica.BandasPosicion
import com.revscope.core.obd.taller.grafica.FormatoPosicion
import com.revscope.core.obd.taller.grafica.UnidadPosicion
import com.revscope.core.obd.taller.pruebas.AnalisisArranqueFrio
import com.revscope.core.obd.taller.pruebas.AnalisisBarridoTps
import com.revscope.core.obd.taller.pruebas.AnalisisBateria
import com.revscope.core.obd.taller.pruebas.AnalisisMapBaro
import com.revscope.core.obd.taller.pruebas.AnalisisMinimo
import com.revscope.core.obd.taller.pruebas.AnalizadorBarridoTps
import com.revscope.core.obd.taller.pruebas.AnalizadorBarridoTps.Pasos
import com.revscope.core.obd.taller.pruebas.Comprobacion
import com.revscope.core.obd.taller.pruebas.DatosPrueba
import com.revscope.core.obd.taller.pruebas.EstadisticaPaso
import com.revscope.core.obd.taller.pruebas.EstadoPrueba
import com.revscope.core.obd.taller.pruebas.ReferenciaVoltaje
import com.revscope.core.obd.taller.pruebas.TextosBarridoTps
import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.referencia.ClavesBanda
import com.revscope.core.obd.taller.referencia.PosicionEnBanda
import com.revscope.core.obd.taller.sesion.Veredicto
import com.revscope.feature.workshop.R

internal object MapeoResultado {

    private const val SERIE_TPS = "TPS"
    private const val SIN_VALOR = "—"

    private val BANDA_POR_PASO = mapOf(
        Pasos.CERRADO_1 to ClavesBanda.TPS_CERRADO_V,
        Pasos.CERRADO_2 to ClavesBanda.TPS_CERRADO_V,
        Pasos.A_FONDO to ClavesBanda.TPS_FONDO_V,
    )

    fun de(t: EstadoPrueba.Terminada, resolver: StringResolver): ResultadoUi {
        val base = base(t)
        return when (val detalle = t.resultado.detalle) {
            is AnalisisBarridoTps -> conBarrido(base, detalle, t.datos, resolver)
            is AnalisisMinimo -> MapeoResultadoMotor.minimo(base, detalle, t.datos, resolver)
            is AnalisisArranqueFrio -> MapeoResultadoMotor.arranque(base, detalle, t.datos, resolver)
            is AnalisisBateria -> MapeoResultadoElectrico.bateria(base, detalle, t.datos, resolver)
            is AnalisisMapBaro -> MapeoResultadoElectrico.mapBaro(base, detalle, t.datos, resolver)
            else -> base
        }
    }

    private fun base(t: EstadoPrueba.Terminada): ResultadoUi {
        val r = t.resultado
        return ResultadoUi(
            tipo = r.tipo,
            veredicto = r.veredicto,
            titulo = r.titulo,
            interpretacion = r.interpretacion,
            hallazgos = r.hallazgos,
            siguientePaso = r.siguientePaso,
            pesas = emptyList(),
            comprobaciones = emptyList(),
            medidas = emptyList(),
            graficas = emptyList(),
            referencia = "",
            codigoGuia = null,
            guardado = t.eventoId != null,
        )
    }

    private fun conBarrido(
        base: ResultadoUi,
        a: AnalisisBarridoTps,
        datos: DatosPrueba?,
        resolver: StringResolver,
    ) = base.copy(
        pesas = pesas(a, resolver),
        comprobaciones = a.comprobaciones.map { comprobacion(it, resolver) },
        graficas = listOfNotNull(datos?.let { graficaBarrido(it, a, resolver) }),
        referencia = referencia(a.vref, resolver),
        codigoGuia = TextosPrueba.codigoGuia(a.patron),
    )

    private fun graficaBarrido(datos: DatosPrueba, a: AnalisisBarridoTps, resolver: StringResolver) = GraficaResultadoUi(
        resolver.get(R.string.workshop_prueba_resultado_grafica_tps_titulo),
        serie(datos, a, resolver),
        leyendaTramos(datos, resolver) + leyendaBandas(a, resolver),
    )

    fun referencia(vref: ReferenciaVoltaje, resolver: StringResolver): String =
        resolver.get(R.string.workshop_prueba_resultado_referencia, FormatoPosicion.voltios(vref.voltios, resolver), vref.origen)

    // ── Pesas: lo medido en cada posición contra su banda ────────────────────

    private fun pesas(a: AnalisisBarridoTps, resolver: StringResolver): List<PesaUi> =
        Pasos.SOSTENIDOS.mapNotNull { clave -> a.paso(clave)?.let { pesa(it, a, resolver) } }

    private fun pesa(p: EstadisticaPaso, a: AnalisisBarridoTps, resolver: StringResolver): PesaUi {
        val banda = BANDA_POR_PASO[p.clave]?.let { a.comprobacion(it)?.banda }
        val titulo = TextosBarridoTps.titulosPaso(resolver)[p.clave] ?: p.clave
        val bandaUi = banda?.let { bandaPesa(it, resolver) }
        return PesaUi(
            titulo = titulo,
            valores = valoresPesa(p, resolver),
            minV = p.minV,
            maxV = p.maxV,
            mediaV = p.mediaV,
            escalaV = a.vref.voltios,
            banda = bandaUi,
            nivel = nivel(p.mediaV, banda),
            descripcion = descripcionPesa(titulo, p, bandaUi, resolver),
        )
    }

    private fun valoresPesa(p: EstadisticaPaso, resolver: StringResolver): String = resolver.plural(
        R.plurals.workshop_prueba_resultado_pesa_valores,
        p.n,
        FormatoPosicion.porcentaje(p.mediaPct, resolver),
        FormatoPosicion.voltios(p.mediaV, resolver),
        FormatoPosicion.voltios(p.minV, resolver),
        FormatoPosicion.voltios(p.maxV, resolver),
        p.n,
        TextosPrueba.hz(p.hz, resolver),
    )

    private fun descripcionPesa(titulo: String, p: EstadisticaPaso, bandaUi: BandaPesaUi?, resolver: StringResolver): String {
        val banda = bandaUi?.let { resolver.get(R.string.workshop_prueba_resultado_pesa_banda, it.texto) }
            ?: resolver.get(R.string.workshop_prueba_resultado_pesa_sin_banda)
        return resolver.get(R.string.workshop_prueba_resultado_pesa_descripcion, titulo, FormatoPosicion.voltios(p.mediaV, resolver), banda)
    }

    private fun bandaPesa(b: BandaReferencia, resolver: StringResolver): BandaPesaUi? {
        val desde = b.min ?: return null
        val hasta = b.max ?: return null
        return BandaPesaUi(desde, hasta, TextosBarridoTps.banda(b, resolver))
    }

    private fun nivel(mediaV: Double, banda: BandaReferencia?): Veredicto = when {
        banda == null -> Veredicto.INFO
        banda.clasificar(mediaV) == PosicionEnBanda.DENTRO -> Veredicto.OK
        else -> Veredicto.FALLA
    }

    private fun comprobacion(c: Comprobacion, resolver: StringResolver): ComprobacionUi {
        val valor = c.valor?.let { v ->
            val formatted = LocaleNumbers.decimal(v, 2, resolver.locale)
            if (c.unidad.isEmpty()) formatted else "$formatted ${c.unidad}"
        }
        val texto = c.banda?.let {
            resolver.get(R.string.workshop_prueba_resultado_comprobacion, c.etiqueta, valor ?: SIN_VALOR, TextosBarridoTps.banda(it, resolver))
        } ?: c.etiqueta
        return ComprobacionUi(texto, c.cumple)
    }

    // ── Serie completa con los pasos sombreados ─────────────────────────────

    private fun serie(datos: DatosPrueba, a: AnalisisBarridoTps, resolver: StringResolver): ModeloGrafica {
        val puntos = datos.muestras.filter { it.pid == AnalizadorBarridoTps.PID_TPS }
            .sortedBy { it.tMicros }
            .map { PuntoGrafica(it.tMicros / 1_000_000.0, datos.vref.aVoltios(it.valor)) }
        if (puntos.size < 2) return sinSerie(resolver)
        val bandas = BandasPosicion.tps(bandasDe(a), UnidadPosicion.VOLTIOS, datos.vref, resolver)
        val recorrido = resolver.get(
            R.string.workshop_prueba_resultado_serie_descripcion,
            SERIE_TPS,
            FormatoPosicion.voltios(puntos.minOf { it.y }, resolver),
            FormatoPosicion.voltios(puntos.maxOf { it.y }, resolver),
            pasosSombreados(datos.segmentos.size, resolver),
        )
        return ModeloGrafica(
            series = listOf(SerieGrafica(SERIE_TPS, puntos)),
            descripcion = "$recorrido " + bandas.joinToString("; ") { it.descripcion },
            bandas = bandas.map { BandaGrafica(it.etiquetaCorta, it.min, it.max) },
            tramos = datos.segmentos.mapIndexed { i, s -> TramoGrafica("${i + 1}", s.inicioMs / 1_000.0, s.finMs / 1_000.0) },
            rangoY = 0.0..datos.vref.voltios,
        )
    }

    fun pasosSombreados(n: Int, resolver: StringResolver): String =
        resolver.plural(R.plurals.workshop_prueba_resultado_pasos_sombreados, n, n)

    private fun sinSerie(resolver: StringResolver) =
        ModeloGrafica(emptyList(), resolver.get(R.string.workshop_prueba_resultado_sin_muestras_tps))

    private fun bandasDe(a: AnalisisBarridoTps): Map<String, BandaReferencia> =
        a.comprobaciones.mapNotNull { it.banda }.associateBy { it.clave }

    private fun leyendaTramos(datos: DatosPrueba, resolver: StringResolver): List<String> {
        val titulos = TextosBarridoTps.titulosPaso(resolver)
        return listOf(
            datos.segmentos.mapIndexed { i, s -> "${i + 1} ${titulos[s.clave] ?: s.clave}" }.joinToString(" · "),
        ).filter { it.isNotEmpty() }
    }

    private fun leyendaBandas(a: AnalisisBarridoTps, resolver: StringResolver): List<String> =
        BandasPosicion.tps(bandasDe(a), UnidadPosicion.VOLTIOS, a.vref, resolver).map { it.descripcion }
}
