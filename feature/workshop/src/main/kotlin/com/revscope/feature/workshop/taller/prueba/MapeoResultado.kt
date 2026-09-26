package com.revscope.feature.workshop.taller.prueba

import com.revscope.core.designsystem.BandaGrafica
import com.revscope.core.designsystem.ModeloGrafica
import com.revscope.core.designsystem.PuntoGrafica
import com.revscope.core.designsystem.SerieGrafica
import com.revscope.core.designsystem.TramoGrafica
import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.grafica.BandasPosicion
import com.revscope.core.obd.taller.grafica.FormatoPosicion
import com.revscope.core.obd.taller.grafica.UnidadPosicion
import com.revscope.core.obd.taller.pruebas.AnalisisArranqueFrio
import com.revscope.core.obd.taller.pruebas.AnalisisBarridoTps
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

internal object MapeoResultado {

    private val BANDA_POR_PASO = mapOf(
        Pasos.CERRADO_1 to ClavesBanda.TPS_CERRADO_V,
        Pasos.CERRADO_2 to ClavesBanda.TPS_CERRADO_V,
        Pasos.A_FONDO to ClavesBanda.TPS_FONDO_V,
    )

    fun de(t: EstadoPrueba.Terminada): ResultadoUi {
        val base = base(t)
        return when (val detalle = t.resultado.detalle) {
            is AnalisisBarridoTps -> conBarrido(base, detalle, t.datos)
            is AnalisisMinimo -> MapeoResultadoMotor.minimo(base, detalle, t.datos)
            is AnalisisArranqueFrio -> MapeoResultadoMotor.arranque(base, detalle, t.datos)
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

    private fun conBarrido(base: ResultadoUi, a: AnalisisBarridoTps, datos: DatosPrueba?) = base.copy(
        pesas = pesas(a),
        comprobaciones = a.comprobaciones.map(::comprobacion),
        graficas = listOfNotNull(
            datos?.let { GraficaResultadoUi("Toda la prueba, en voltios", serie(it, a), leyendaTramos(it) + leyendaBandas(a)) },
        ),
        referencia = referencia(a.vref),
        codigoGuia = TextosPrueba.codigoGuia(a.patron),
    )

    fun referencia(vref: ReferenciaVoltaje): String = "Referencia ${FormatoPosicion.voltios(vref.voltios)} · ${vref.origen}"

    // ── Pesas: lo medido en cada posición contra su banda ────────────────────

    private fun pesas(a: AnalisisBarridoTps): List<PesaUi> =
        Pasos.SOSTENIDOS.mapNotNull { clave -> a.paso(clave)?.let { pesa(it, a) } }

    private fun pesa(p: EstadisticaPaso, a: AnalisisBarridoTps): PesaUi {
        val banda = BANDA_POR_PASO[p.clave]?.let { a.comprobacion(it)?.banda }
        val titulo = TextosBarridoTps.TITULOS_PASO[p.clave] ?: p.clave
        val bandaUi = banda?.let(::bandaPesa)
        return PesaUi(
            titulo = titulo,
            valores = "${FormatoPosicion.porcentaje(p.mediaPct)} · ${FormatoPosicion.voltios(p.mediaV)} · " +
                "de ${FormatoPosicion.voltios(p.minV)} a ${FormatoPosicion.voltios(p.maxV)} · ${p.n} muestras a ${TextosPrueba.hz(p.hz)}",
            minV = p.minV,
            maxV = p.maxV,
            mediaV = p.mediaV,
            escalaV = a.vref.voltios,
            banda = bandaUi,
            nivel = nivel(p.mediaV, banda),
            descripcion = "$titulo: media ${FormatoPosicion.voltios(p.mediaV)}, " +
                (bandaUi?.let { "banda ${it.texto}" } ?: "sin banda fija: debe quedar entre cerrado y fondo"),
        )
    }

    private fun bandaPesa(b: BandaReferencia): BandaPesaUi? {
        val desde = b.min ?: return null
        val hasta = b.max ?: return null
        return BandaPesaUi(desde, hasta, TextosBarridoTps.banda(b))
    }

    private fun nivel(mediaV: Double, banda: BandaReferencia?): Veredicto = when {
        banda == null -> Veredicto.INFO
        banda.clasificar(mediaV) == PosicionEnBanda.DENTRO -> Veredicto.OK
        else -> Veredicto.FALLA
    }

    private fun comprobacion(c: Comprobacion): ComprobacionUi {
        val valor = c.valor?.let { v -> if (c.unidad.isEmpty()) FormatoTaller.numero(v, 2) else "${FormatoTaller.numero(v, 2)} ${c.unidad}" }
        val texto = c.banda?.let { "${c.etiqueta}: ${valor ?: "—"} · banda ${TextosBarridoTps.banda(it)}" } ?: c.etiqueta
        return ComprobacionUi(texto, c.cumple)
    }

    // ── Serie completa con los pasos sombreados ─────────────────────────────

    private fun serie(datos: DatosPrueba, a: AnalisisBarridoTps): ModeloGrafica {
        val puntos = datos.muestras.filter { it.pid == AnalizadorBarridoTps.PID_TPS }
            .sortedBy { it.tMicros }
            .map { PuntoGrafica(it.tMicros / 1_000_000.0, datos.vref.aVoltios(it.valor)) }
        if (puntos.size < 2) return sinSerie()
        val bandas = BandasPosicion.tps(bandasDe(a), UnidadPosicion.VOLTIOS, datos.vref)
        return ModeloGrafica(
            series = listOf(SerieGrafica("TPS", puntos)),
            descripcion = "TPS en toda la prueba: de ${FormatoPosicion.voltios(puntos.minOf { it.y })} a " +
                "${FormatoPosicion.voltios(puntos.maxOf { it.y })}, ${datos.segmentos.size} pasos sombreados. " +
                bandas.joinToString("; ") { it.descripcion },
            bandas = bandas.map { BandaGrafica(it.etiquetaCorta, it.min, it.max) },
            tramos = datos.segmentos.mapIndexed { i, s -> TramoGrafica("${i + 1}", s.inicioMs / 1_000.0, s.finMs / 1_000.0) },
            rangoY = 0.0..datos.vref.voltios,
        )
    }

    private fun sinSerie() = ModeloGrafica(emptyList(), "Sin muestras del TPS para dibujar")

    private fun bandasDe(a: AnalisisBarridoTps): Map<String, BandaReferencia> =
        a.comprobaciones.mapNotNull { it.banda }.associateBy { it.clave }

    private fun leyendaTramos(datos: DatosPrueba): List<String> = listOf(
        datos.segmentos.mapIndexed { i, s -> "${i + 1} ${TextosBarridoTps.TITULOS_PASO[s.clave] ?: s.clave}" }.joinToString(" · "),
    ).filter { it.isNotEmpty() }

    private fun leyendaBandas(a: AnalisisBarridoTps): List<String> =
        BandasPosicion.tps(bandasDe(a), UnidadPosicion.VOLTIOS, a.vref).map { it.descripcion }
}
