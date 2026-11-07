package com.revscope.core.obd.taller.pruebas

import com.revscope.core.common.i18n.LocaleNumbers
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R
import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.referencia.PosicionEnBanda
import kotlin.math.abs
import kotlin.math.roundToLong

// Redacción del MAP contra la barométrica: la fuente de la referencia y su incertidumbre siempre a la vista,
// «compatible con…» y nunca un dictamen. Las unidades van con espacio duro.
object TextosMapBaro {

    val TITULOS_PASO: Map<String, Int> = mapOf(AnalizadorMapBaro.Pasos.CONTACTO to R.string.obd_mapbaro_titulo_paso_contacto)

    fun titulosPaso(resolver: StringResolver): Map<String, String> = TITULOS_PASO.mapValues { resolver.get(it.value) }

    fun notaEcu(resolver: StringResolver): String = resolver.get(R.string.obd_mapbaro_nota_ecu)

    fun resultado(a: AnalisisMapBaro, resolver: StringResolver): ResultadoPrueba = Redactor(a, resolver).resultado()

    fun titulo(a: AnalisisMapBaro, resolver: StringResolver): String = Redactor(a, resolver).titulo()

    fun medidas(a: AnalisisMapBaro, resolver: StringResolver): List<String> = Redactor(a, resolver).medidas()

    private fun kpaTexto(x: Double, resolver: StringResolver): String =
        LocaleNumbers.compact(FormatoTaller.redondear(x, 1), resolver.locale) + ESPACIO_DURO + "kPa"

    private class Redactor(private val a: AnalisisMapBaro, private val res: StringResolver) {

        fun resultado() = ResultadoPrueba(
            tipo = TipoPrueba.MAP_BARO,
            veredicto = a.patron.veredicto,
            titulo = titulo(),
            interpretacion = interpretacion(),
            siguientePaso = siguientePaso(),
            hallazgos = hallazgos(),
            bajaConfianza = false,
            detalle = a,
        )

        fun titulo(): String = when (a.patron) {
            PatronMapBaro.SIN_DATOS -> res.get(R.string.obd_mapbaro_titulo_sin_datos)
            PatronMapBaro.SIN_REFERENCIA -> res.get(R.string.obd_mapbaro_titulo_sin_referencia, kpa(a.map?.mediaKpa))
            PatronMapBaro.MAP_BAJO -> res.get(R.string.obd_mapbaro_titulo_map_bajo, kpa(abs(a.deltaKpa ?: 0.0)))
            PatronMapBaro.MAP_ALTO -> res.get(R.string.obd_mapbaro_titulo_map_alto, kpa(abs(a.deltaKpa ?: 0.0)))
            PatronMapBaro.NO_CONCLUYENTE -> res.get(R.string.obd_mapbaro_titulo_no_concluyente)
            PatronMapBaro.NORMAL -> res.get(R.string.obd_mapbaro_titulo_normal)
        }

        // ── Interpretación ──────────────────────────────────────────────────

        private fun interpretacion(): String {
            val map = a.map ?: return res.get(R.string.obd_mapbaro_interp_sin_datos)
            return listOfNotNull(
                res.get(R.string.obd_mapbaro_interp_map, kpa(map.mediaKpa), rango(map)),
                fraseReferencia(),
                fraseEquivalencia(),
                fraseVeredicto(),
                notaEcu(res).takeIf { a.referencia?.origen == OrigenBarometrica.ECU },
            ).joinToString(" ")
        }

        private fun fraseReferencia(): String {
            val ref = a.referencia ?: return res.get(R.string.obd_mapbaro_sin_referencia)
            val banda = a.banda?.let { TextoBanda.citar(it, 0, res) }
                ?: res.get(R.string.obd_mapbaro_banda_tipica, kpa(AnalizadorMapBaro.BANDA_TIPICA_KPA), res.get(R.string.obd_band_origin_typical))
            return res.get(
                R.string.obd_mapbaro_frase_referencia,
                ref.enFrase(res),
                kpa(ref.kPa),
                kpaConSigno(a.deltaKpa ?: 0.0),
                banda,
                kpa(ref.incertidumbreKpa),
            )
        }

        private fun fraseEquivalencia(): String? {
            if (a.patron == PatronMapBaro.NORMAL || a.patron == PatronMapBaro.NO_CONCLUYENTE) return null
            val altitud = a.altitudEquivalenteM ?: return null
            return res.get(R.string.obd_mapbaro_frase_equivalencia, kpa(a.map?.mediaKpa), metros(altitud))
        }

        private fun fraseVeredicto(): String? = when (a.patron) {
            PatronMapBaro.MAP_BAJO -> res.get(R.string.obd_mapbaro_veredicto_bajo)
            PatronMapBaro.MAP_ALTO -> res.get(R.string.obd_mapbaro_veredicto_alto)
            PatronMapBaro.NO_CONCLUYENTE -> fraseNoConcluyente()
            PatronMapBaro.NORMAL -> res.get(R.string.obd_mapbaro_veredicto_normal)
            else -> null
        }

        private fun fraseNoConcluyente(): String {
            val ref = a.referencia
            val alt = a.alternativa
            if (ref != null && alt != null && a.contrasteDiscrepa) {
                return res.get(R.string.obd_mapbaro_no_concluyente_contraste, ref.origen.enFrase(res), alt.origen.enFrase(res))
            }
            return res.get(R.string.obd_mapbaro_no_concluyente_incertidumbre)
        }

        // ── Hallazgos y siguiente paso ──────────────────────────────────────

        private fun hallazgos(): List<String> = buildList {
            val delta = a.deltaKpa
            if (delta != null && (a.patron == PatronMapBaro.MAP_BAJO || a.patron == PatronMapBaro.MAP_ALTO)) {
                add(res.get(R.string.obd_mapbaro_hallazgo_diferencia, kpa(abs(delta)), lado(delta), kpa(a.toleranciaKpa ?: 0.0)))
            }
            contraste()?.let(::add)
            val map = a.map
            if (map != null && map.variacionKpa > AnalizadorMapBaro.VARIACION_MAX_KPA) {
                add(res.get(R.string.obd_mapbaro_hallazgo_variacion, kpa(map.variacionKpa)))
            }
        }

        private fun contraste(): String? {
            val alt = a.alternativa ?: return null
            val delta = a.deltaAlternativaKpa ?: return null
            if (!a.contrasteDiscrepa) return null
            return res.get(
                R.string.obd_mapbaro_hallazgo_contraste,
                alt.origen.enFrase(res),
                kpa(alt.kPa),
                kpa(abs(delta)),
                lado(delta),
                kpa(a.toleranciaAlternativaKpa ?: 0.0),
            )
        }

        private fun siguientePaso(): String? = when (a.patron) {
            PatronMapBaro.SIN_DATOS -> res.get(R.string.obd_mapbaro_siguiente_sin_datos)
            PatronMapBaro.SIN_REFERENCIA -> res.get(R.string.obd_mapbaro_siguiente_sin_referencia)
            PatronMapBaro.MAP_BAJO, PatronMapBaro.MAP_ALTO -> res.get(R.string.obd_mapbaro_siguiente_medir_map)
            PatronMapBaro.NO_CONCLUYENTE -> res.get(
                if (a.contrasteDiscrepa) R.string.obd_mapbaro_siguiente_medir_map else R.string.obd_mapbaro_siguiente_repetir_barometro,
            )
            PatronMapBaro.NORMAL -> res.get(R.string.obd_mapbaro_siguiente_contrastar).takeIf {
                a.referencia?.origen == OrigenBarometrica.ECU && a.alternativa == null
            }
        }

        // ── Medidas: una línea por cifra, para la pantalla y el informe ──────

        fun medidas(): List<String> {
            val map = a.map ?: return emptyList()
            return listOfNotNull(
                res.get(R.string.obd_mapbaro_medida_map, kpa(map.mediaKpa), rango(map)),
                a.referencia?.let { res.get(R.string.obd_mapbaro_medida_baro, kpa(it.kPa), it.etiqueta(res)) }
                    ?: res.get(R.string.obd_mapbaro_medida_baro_sin_fuente),
                a.deltaKpa?.let { res.get(R.string.obd_mapbaro_medida_diferencia, kpaConSigno(it), kpa(a.toleranciaKpa ?: 0.0)) },
                a.altitudEquivalenteM?.let { res.get(R.string.obd_mapbaro_medida_altitud, metros(it)) },
                a.alternativa?.let { alt ->
                    res.get(
                        R.string.obd_mapbaro_medida_contraste,
                        alt.origen.nombre(res),
                        kpa(alt.kPa),
                        kpaConSigno(a.deltaAlternativaKpa ?: 0.0),
                    )
                },
            )
        }

        // ── Formato ─────────────────────────────────────────────────────────

        private fun rango(m: MapMedido): String {
            val desde = compacto(m.minKpa)
            val hasta = compacto(m.maxKpa)
            return if (desde == hasta) {
                res.plural(R.plurals.obd_mapbaro_rango_estable, m.n, m.n)
            } else {
                res.plural(R.plurals.obd_mapbaro_rango, m.n, desde, kpa(m.maxKpa), m.n)
            }
        }

        private fun lado(delta: Double) = TextoBanda.lado(if (delta < 0) PosicionEnBanda.BAJO else PosicionEnBanda.ALTO, res)

        private fun compacto(x: Double) = LocaleNumbers.compact(FormatoTaller.redondear(x, 1), res.locale)

        private fun kpa(x: Double?) = kpaTexto(x ?: 0.0, res)

        private fun kpaConSigno(x: Double) = LocaleNumbers.signed(x, 1, res.locale) + ESPACIO_DURO + "kPa"

        // Redondeada a la centena y con separador de miles: es una equivalencia, no una medida.
        private fun metros(m: Double): String {
            val centenas = (m / 100).roundToLong() * 100
            val texto = if (abs(centenas) >= 1_000) {
                res.get(R.string.obd_mapbaro_miles, centenas / 1_000, abs(centenas) % 1_000)
            } else {
                centenas.toString()
            }
            return texto + ESPACIO_DURO + "m"
        }
    }

    private const val ESPACIO_DURO = " "
}
