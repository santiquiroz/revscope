package com.revscope.core.obd.taller.pruebas

import com.revscope.core.common.i18n.LocaleNumbers
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R
import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.pruebas.AnalizadorMinimo.Pasos
import com.revscope.core.obd.taller.referencia.ClavesBanda
import com.revscope.core.obd.taller.referencia.PosicionEnBanda
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

// Redacción del mínimo y retorno: cifras medidas, la banda con su origen y «compatible con…», nunca un dictamen.
object TextosMinimo {

    private val RPM_S = TextoBanda.unidad("rpm/s")
    private val TOLERANCIA_PCT = (AnalizadorMinimo.RETORNO_TOLERANCIA * 100).roundToInt()

    val TITULOS_PASO: Map<String, Int> = mapOf(
        Pasos.MINIMO to R.string.obd_minimo_titulo_paso_minimo,
        Pasos.RETORNO_1 to R.string.obd_minimo_titulo_paso_retorno_1,
        Pasos.RETORNO_2 to R.string.obd_minimo_titulo_paso_retorno_2,
        Pasos.RETORNO_3 to R.string.obd_minimo_titulo_paso_retorno_3,
    )

    fun titulosPaso(resolver: StringResolver): Map<String, String> = TITULOS_PASO.mapValues { resolver.get(it.value) }

    fun resultado(a: AnalisisMinimo, resolver: StringResolver): ResultadoPrueba = Redactor(a, resolver).resultado()

    fun titulo(a: AnalisisMinimo, resolver: StringResolver): String = Redactor(a, resolver).titulo()

    fun medidas(a: AnalisisMinimo, resolver: StringResolver): List<String> = Redactor(a, resolver).medidas()

    private class Redactor(private val a: AnalisisMinimo, private val res: StringResolver) {

        fun resultado() = ResultadoPrueba(
            tipo = TipoPrueba.MINIMO_RETORNO,
            veredicto = a.patron.veredicto,
            titulo = titulo(),
            interpretacion = interpretacion(),
            siguientePaso = siguientePaso(),
            hallazgos = hallazgos(),
            bajaConfianza = false,
            detalle = a,
        )

        fun titulo(): String = when (a.patron) {
            PatronMinimo.SE_APAGA -> tituloApagones()
            PatronMinimo.INESTABLE -> res.get(R.string.obd_minimo_titulo_inestable)
            PatronMinimo.RETORNO_BAJO -> res.get(R.string.obd_minimo_titulo_retorno_bajo)
            PatronMinimo.FUERA_DE_BANDA -> res.get(R.string.obd_minimo_titulo_fuera_de_banda)
            PatronMinimo.TPS_RALENTI_FUERA -> res.get(R.string.obd_minimo_titulo_tps_ralenti_fuera)
            PatronMinimo.SIN_RETORNOS -> res.get(R.string.obd_minimo_titulo_sin_retornos)
            PatronMinimo.SIN_DATOS -> res.get(R.string.obd_minimo_titulo_sin_datos)
            PatronMinimo.NORMAL -> res.get(R.string.obd_minimo_titulo_normal)
        }

        private fun tituloApagones(): String {
            val enMinimo = a.minimo?.apagones ?: 0
            val total = a.retornosValidos.size
            val alSoltar = res.plural(R.plurals.obd_minimo_apagones_al_soltar, total, a.apagonesAlSoltar, total)
            return when {
                enMinimo == 0 -> res.get(R.string.obd_minimo_titulo_se_apago_al_soltar, alSoltar)
                a.apagonesAlSoltar == 0 -> res.get(R.string.obd_minimo_titulo_se_apago_en_minimo)
                else -> res.get(R.string.obd_minimo_titulo_se_apago_ambos, alSoltar)
            }
        }

        // ── Interpretación ──────────────────────────────────────────────────

        private fun interpretacion(): String {
            val m = a.minimo ?: return res.get(R.string.obd_minimo_interp_sin_datos)
            return listOfNotNull(
                frasesMinimo(m),
                fraseBanda(),
                fraseRetornos(),
                a.ectMediaC?.let { res.get(R.string.obd_minimo_ect, n(it, 0)) },
                compatibilidad(a.patron),
            ).joinToString(" ")
        }

        private fun frasesMinimo(m: EstadisticaMinimo): String {
            val deriva = a.comprobacion(ClavesBanda.MINIMO_DERIVA_MAX)
            val base = if (deriva?.cumple == false) {
                res.get(
                    if (m.derivaRpmS < 0) R.string.obd_minimo_deriva_cayo else R.string.obd_minimo_deriva_subio,
                    rpm(m.inicioRpm),
                    rpm(m.finRpm),
                    compacto(FormatoTaller.redondear(m.duracionS, 1)),
                    n(m.derivaRpmS, 0),
                    RPM_S,
                )
            } else {
                res.get(R.string.obd_minimo_media, rpm(m.mediaRpm), rpm(m.desviacionRpm))
            }
            val conOscilacion = m.oscilacion?.let {
                res.get(R.string.obd_minimo_con_oscilacion, base, rpm(it.amplitudRpm), cada(it.periodoS))
            } ?: base
            return if (m.inestable) res.get(R.string.obd_minimo_inestable_sufijo, conOscilacion, bandasEstabilidad()) else "$conOscilacion."
        }

        private fun bandasEstabilidad(): String {
            val citas = listOf(ClavesBanda.MINIMO_DERIVA_MAX, ClavesBanda.MINIMO_DESVIACION_MAX)
                .mapNotNull { a.comprobacion(it)?.banda }
                .map { TextoBanda.citar(it, 0, res) }
            return if (citas.isEmpty()) "" else entreParentesis(citas.joinToString("; "))
        }

        // Con el mínimo inestable, la media no dice dónde queda el ralentí: la banda se calla.
        private fun fraseBanda(): String? {
            val c = mediaFueraDeBanda()?.takeIf { it.banda != null } ?: return null
            return res.get(
                R.string.obd_minimo_frase_banda,
                rpm(c.valor ?: 0.0),
                TextoBanda.lado(c.posicion, res),
                TextoBanda.citar(checkNotNull(c.banda), 0, res),
            )
        }

        private fun fraseRetornos(): String? {
            val validos = a.retornosValidos
            if (validos.isEmpty()) return null
            if (a.apagonesAlSoltar > 0) {
                return res.plural(R.plurals.obd_minimo_apagones_retorno, validos.size, a.apagonesAlSoltar, validos.size)
            }
            val peor = validos.minBy { it.valleFraccion ?: 1.0 }
            val tiempos = validos.mapNotNull { it.tiempoS }
            val vuelta = if (tiempos.isEmpty()) "" else " " + res.get(R.string.obd_minimo_vuelta, seg(tiempos.max()))
            return res.get(R.string.obd_minimo_frase_valle, rpm(peor.valleRpm ?: 0.0), pct(peor.valleFraccion), vuelta)
        }

        private fun compatibilidad(patron: PatronMinimo): String? = when (patron) {
            PatronMinimo.SE_APAGA, PatronMinimo.INESTABLE, PatronMinimo.RETORNO_BAJO -> res.get(R.string.obd_minimo_compatibilidad)
            else -> null
        }

        // ── Hallazgos y siguiente paso ──────────────────────────────────────

        private fun hallazgos(): List<String> = buildList {
            val m = a.minimo ?: return@buildList
            if (m.inestable) add(hallazgoInestable(m))
            if (m.apagones > 0) add(res.plural(R.plurals.obd_minimo_hallazgo_apagones, m.apagones, m.apagones))
            a.retornos.filter { it.seApago }.takeIf { it.isNotEmpty() }?.let {
                add(res.get(R.string.obd_minimo_hallazgo_se_apago_al_soltar, nombres(it)))
            }
            addAll(a.retornos.mapNotNull(::hallazgoRetorno))
            hallazgoValle()?.let(::add)
            hallazgoBanda()?.let(::add)
            hallazgoTps()?.let(::add)
        }

        private fun hallazgoInestable(m: EstadisticaMinimo): String {
            val osc = m.oscilacion?.let { res.get(R.string.obd_minimo_hallazgo_oscila, rpm(it.amplitudRpm)) }.orEmpty()
            return res.get(R.string.obd_minimo_hallazgo_inestable, n(m.derivaRpmS, 1), RPM_S, rpm(m.desviacionRpm), osc)
        }

        private fun hallazgoRetorno(r: RetornoMedido): String? {
            val nombre = nombre(r.clave)
            return when {
                !r.acelerada -> res.get(R.string.obd_minimo_hallazgo_sin_acelerada, nombre, rpm(r.picoRpm))
                !r.seApago && r.tiempoS == null -> res.get(R.string.obd_minimo_hallazgo_no_volvio, nombre, TOLERANCIA_PCT)
                else -> null
            }
        }

        private fun hallazgoValle(): String? {
            val c = a.comprobacion(ClavesBanda.RETORNO_VALLE_MIN)?.takeIf { !it.cumple && a.apagonesAlSoltar == 0 } ?: return null
            return res.get(R.string.obd_minimo_hallazgo_valle, n(c.valor ?: 0.0, 0), citaBanda(c, 0))
        }

        private fun hallazgoBanda(): String? {
            val c = mediaFueraDeBanda() ?: return null
            return res.get(R.string.obd_minimo_hallazgo_banda, rpm(c.valor ?: 0.0), TextoBanda.lado(c.posicion, res), citaBanda(c, 0))
        }

        private fun citaBanda(c: Comprobacion, decimales: Int): String =
            c.banda?.let { entreParentesis(TextoBanda.citar(it, decimales, res)) }.orEmpty()

        private fun mediaFueraDeBanda(): Comprobacion? {
            if (a.minimo?.inestable != false) return null
            return a.comprobacion(ClavesBanda.MINIMO_RPM)?.takeIf { !it.cumple }
        }

        private fun hallazgoTps(): String? {
            val t = a.tpsRalenti?.takeIf { it.posicion != null && it.posicion != PosicionEnBanda.DENTRO } ?: return null
            val banda = t.banda?.let { entreParentesis(TextoBanda.citar(it, 1, res)) }.orEmpty()
            val consecuencia = res.get(if (t.posicion == PosicionEnBanda.BAJO) R.string.obd_minimo_tps_bajo else R.string.obd_minimo_tps_alto)
            return res.get(R.string.obd_minimo_hallazgo_tps, n(t.minV, 2), n(t.maxV, 2), TextoBanda.lado(t.posicion, res), banda, consecuencia)
        }

        private fun siguientePaso(): String? {
            val tpsFuera = a.tpsRalenti?.posicion.let { it != null && it != PosicionEnBanda.DENTRO }
            if (tpsFuera && a.patron != PatronMinimo.SIN_DATOS) return res.get(R.string.obd_minimo_siguiente_tps)
            return when (a.patron) {
                PatronMinimo.SE_APAGA, PatronMinimo.INESTABLE, PatronMinimo.RETORNO_BAJO -> res.get(R.string.obd_minimo_siguiente_inestable)
                PatronMinimo.FUERA_DE_BANDA -> res.get(R.string.obd_minimo_siguiente_fuera_de_banda)
                PatronMinimo.SIN_RETORNOS -> res.get(R.string.obd_minimo_siguiente_sin_retornos)
                PatronMinimo.SIN_DATOS -> res.get(R.string.obd_minimo_siguiente_sin_datos)
                PatronMinimo.TPS_RALENTI_FUERA, PatronMinimo.NORMAL -> null
            }
        }

        // ── Medidas: una línea por cifra, para la pantalla y el informe ──────

        fun medidas(): List<String> {
            val m = a.minimo ?: return emptyList()
            return listOfNotNull(
                res.get(
                    R.string.obd_minimo_medida_minimo,
                    rpm(m.mediaRpm),
                    rpm(m.desviacionRpm),
                    rpm(m.minRpm),
                    rpm(m.maxRpm),
                    res.plural(R.plurals.obd_minimo_muestras, m.n, m.n, seg(m.duracionS)),
                ),
                res.get(R.string.obd_minimo_medida_deriva, n(m.derivaRpmS, 1), RPM_S, rpm(m.inicioRpm), rpm(m.finRpm)),
                res.get(R.string.obd_minimo_medida_oscilacion, oscilacion(m)),
                a.tpsRalenti?.let { t -> res.get(R.string.obd_minimo_medida_tps, n(t.minV, 2), n(t.maxV, 2), n(t.mediaV, 2)) },
                a.ectMediaC?.let { res.get(R.string.obd_minimo_medida_ect, n(it, 0)) },
            ) + a.retornos.map(::medidaRetorno)
        }

        private fun oscilacion(m: EstadisticaMinimo): String = m.oscilacion?.let { o ->
            res.get(R.string.obd_minimo_oscilacion_valor, rpm(o.amplitudRpm)) + cada(o.periodoS)
        } ?: res.get(R.string.obd_minimo_oscilacion_no)

        private fun medidaRetorno(r: RetornoMedido): String {
            val nombre = nombre(r.clave)
            return when {
                !r.acelerada -> res.get(R.string.obd_minimo_retorno_sin_acelerada, nombre, rpm(r.picoRpm))
                r.seApago -> res.get(R.string.obd_minimo_retorno_se_apago, nombre, rpm(r.picoRpm))
                else -> res.get(R.string.obd_minimo_retorno, nombre, rpm(r.picoRpm), rpm(r.valleRpm ?: 0.0), pct(r.valleFraccion), vuelta(r))
            }
        }

        private fun vuelta(r: RetornoMedido): String =
            r.tiempoS?.let { res.get(R.string.obd_minimo_retorno_volvio, seg(it)) }
                ?: res.get(R.string.obd_minimo_retorno_no_volvio, TOLERANCIA_PCT)

        // ── Formato ─────────────────────────────────────────────────────────

        private fun nombre(clave: String): String = TITULOS_PASO[clave]?.let { res.get(it) } ?: clave

        private fun nombres(retornos: List<RetornoMedido>): String {
            val lista = retornos.map { res.get(R.string.obd_minimo_nombre_articulo, nombre(it.clave).lowercase()) }
            return lista.singleOrNull() ?: res.get(R.string.obd_minimo_nombres_varios, lista.dropLast(1).joinToString(", "), lista.last())
        }

        private fun cada(periodoS: Double?): String = periodoS?.let { " " + res.get(R.string.obd_minimo_cada, seg(it)) }.orEmpty()

        private fun compacto(x: Double): String {
            val entero = x.roundToLong()
            return if (abs(x - entero) < 1e-9) entero.toString() else n(x, 1)
        }

        private fun entreParentesis(texto: String) = " ($texto)"

        private fun rpm(x: Double) = n(x, 0)

        private fun seg(x: Double) = n(x, 1)

        private fun pct(fraccion: Double?) = n((fraccion ?: 0.0) * 100, 0) + ESPACIO_DURO + "%"

        private fun n(x: Double, decimales: Int) = LocaleNumbers.decimal(x, decimales, res.locale)
    }

    private const val ESPACIO_DURO = " "
}
