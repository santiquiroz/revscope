package com.revscope.core.obd.taller.pruebas

import com.revscope.core.common.i18n.LocaleNumbers
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R
import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.pruebas.AnalizadorBarridoTps.Pasos
import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.referencia.ClavesBanda
import com.revscope.core.obd.taller.sesion.Veredicto

// Redacción de «compatible con…», citando cada banda con su origen (típica, con fuente o editada).
object TextosBarridoTps {

    val TITULOS_PASO: Map<String, Int> = mapOf(
        Pasos.CERRADO_1 to R.string.obd_barrido_paso_cerrado_1,
        Pasos.MEDIO to R.string.obd_barrido_paso_medio,
        Pasos.A_FONDO to R.string.obd_barrido_paso_a_fondo,
        Pasos.CERRADO_2 to R.string.obd_barrido_paso_cerrado_2,
        Pasos.BARRIDO_LENTO to R.string.obd_barrido_paso_barrido_lento,
    )

    fun titulosPaso(resolver: StringResolver): Map<String, String> = TITULOS_PASO.mapValues { resolver.get(it.value) }

    fun resultado(a: AnalisisBarridoTps, resolver: StringResolver): ResultadoPrueba = Redactor(a, resolver).resultado()

    fun sinDatos(faltan: List<String>, resolver: StringResolver) = ResultadoPrueba(
        tipo = TipoPrueba.TPS_BARRIDO,
        veredicto = Veredicto.ATENCION,
        titulo = resolver.get(R.string.obd_barrido_sin_datos_titulo),
        interpretacion = resolver.get(
            R.string.obd_barrido_sin_datos_interp,
            faltan.joinToString { clave -> TITULOS_PASO[clave]?.let { resolver.get(it) } ?: clave },
        ),
        siguientePaso = resolver.get(R.string.obd_barrido_sin_datos_siguiente),
        hallazgos = emptyList(),
        bajaConfianza = false,
        detalle = SinDatosTps(faltan),
    )

    fun titulo(patron: PatronTps, resolver: StringResolver): String = resolver.get(
        when (patron) {
            PatronTps.CORTES_O_SALTOS -> R.string.obd_barrido_titulo_cortes_saltos
            PatronTps.SENAL_BAJA_TODO_EL_RECORRIDO -> R.string.obd_barrido_titulo_senal_baja
            PatronTps.SENAL_ALTA -> R.string.obd_barrido_titulo_senal_alta
            PatronTps.RANGO_DESEMPENO -> R.string.obd_barrido_titulo_rango
            PatronTps.SIN_BARRIDO -> R.string.obd_barrido_titulo_sin_barrido
            PatronTps.NORMAL -> R.string.obd_barrido_titulo_normal
        },
    )

    fun siguientePaso(patron: PatronTps, resolver: StringResolver): String? = when (patron) {
        PatronTps.CORTES_O_SALTOS -> R.string.obd_barrido_siguiente_cortes_saltos
        PatronTps.SENAL_BAJA_TODO_EL_RECORRIDO -> R.string.obd_barrido_siguiente_senal_baja
        PatronTps.SENAL_ALTA -> R.string.obd_barrido_siguiente_senal_alta
        PatronTps.RANGO_DESEMPENO -> R.string.obd_barrido_siguiente_rango
        PatronTps.SIN_BARRIDO -> R.string.obd_barrido_siguiente_sin_barrido
        PatronTps.NORMAL -> null
    }?.let { resolver.get(it) }

    fun banda(b: BandaReferencia, resolver: StringResolver): String =
        "${rango(b, resolver)}, ${b.etiquetaOrigen(resolver)}"

    private fun rango(b: BandaReferencia, resolver: StringResolver): String {
        val unidad = if (b.unidad == "V") ESPACIO + "V" else ""
        val min = b.min
        val max = b.max
        return when {
            min != null && max != null -> "${numeroBanda(min, resolver)}–${numeroBanda(max, resolver)}$unidad"
            min != null -> "≥ ${numeroBanda(min, resolver)}$unidad"
            else -> "≤ ${numeroBanda(max ?: 0.0, resolver)}$unidad"
        }
    }

    private fun numeroBanda(x: Double, resolver: StringResolver): String =
        LocaleNumbers.decimal(x, if (FormatoTaller.redondear(x, 1) == x) 1 else 2, resolver.locale)

    private class Redactor(private val a: AnalisisBarridoTps, private val res: StringResolver) {

        fun resultado() = ResultadoPrueba(
            tipo = TipoPrueba.TPS_BARRIDO,
            veredicto = a.patron.veredicto,
            titulo = titulo(a.patron, res),
            interpretacion = interpretacion() + notaConfianza(),
            siguientePaso = siguientePaso(a.patron, res),
            hallazgos = hallazgos(),
            bajaConfianza = a.bajaConfianza,
            detalle = a,
        )

        // ── Interpretación ──────────────────────────────────────────────────

        private fun interpretacion(): String = when (a.patron) {
            PatronTps.CORTES_O_SALTOS -> res.get(
                R.string.obd_barrido_interp_cortes_saltos,
                cuantos(R.plurals.obd_barrido_cortes, a.cuenta(TipoIrregularidad.CORTE)),
                cuantos(R.plurals.obd_barrido_saltos, a.cuenta(TipoIrregularidad.SALTO)),
            )
            PatronTps.SENAL_BAJA_TODO_EL_RECORRIDO -> senalBaja()
            PatronTps.SENAL_ALTA -> res.get(R.string.obd_barrido_interp_senal_alta, v(a.cerradoV), bandaDe(ClavesBanda.TPS_CERRADO_V))
            PatronTps.RANGO_DESEMPENO -> res.get(
                R.string.obd_barrido_interp_rango,
                v(a.recorridoV),
                a.comprobaciones.filterNot { it.cumple }.joinToString("; ") { falla(it) },
            )
            PatronTps.SIN_BARRIDO -> extremos(R.string.obd_barrido_interp_sin_barrido, sinBarridoEvaluado())
            PatronTps.NORMAL -> extremos(R.string.obd_barrido_interp_normal)
        }

        private fun extremos(id: Int, vararg extra: Any): String = res.get(
            id,
            v(a.cerradoV),
            bandaDe(ClavesBanda.TPS_CERRADO_V),
            v(a.fondoV),
            bandaDe(ClavesBanda.TPS_FONDO_V),
            v(a.recorridoV),
            *extra,
        )

        private fun senalBaja(): String {
            val tipico = a.recorridoTipicoV?.let { ESPACIO + res.get(R.string.obd_barrido_tipico, v(it)) }.orEmpty()
            val fraccion = a.fraccionRecorrido?.let { res.get(R.string.obd_barrido_fraccion, n(it * 100, 0), tipico) }.orEmpty()
            return res.get(
                R.string.obd_barrido_interp_senal_baja,
                v(a.cerradoV),
                bandaDe(ClavesBanda.TPS_CERRADO_V),
                v(a.fondoV),
                bandaDe(ClavesBanda.TPS_FONDO_V),
                v(a.recorridoV),
                fraccion,
                v(a.repetibilidadV),
            )
        }

        private fun notaConfianza(): String {
            val depende = a.patron == PatronTps.CORTES_O_SALTOS || a.patron == PatronTps.RANGO_DESEMPENO
            if (!a.bajaConfianza || !depende) return ""
            return ESPACIO + res.get(R.string.obd_barrido_nota_confianza, hz(a.tasaHz))
        }

        // ── Hallazgos ───────────────────────────────────────────────────────

        private fun hallazgos(): List<String> = buildList {
            a.comprobaciones.filterNot { it.cumple }.forEach { add(falla(it).replaceFirstChar(Char::uppercase)) }
            irregularidades()?.let(::add)
            if (!a.barridoEvaluado && a.patron != PatronTps.SIN_BARRIDO) add(sinBarridoEvaluado().replaceFirstChar(Char::uppercase))
            if (a.bajaConfianza) add(res.get(R.string.obd_barrido_hallazgo_baja_confianza, hz(a.tasaHz)))
        }

        private fun irregularidades(): String? {
            val partes = listOf(
                a.cuenta(TipoIrregularidad.CORTE) to R.plurals.obd_barrido_cortes,
                a.cuenta(TipoIrregularidad.SALTO) to R.plurals.obd_barrido_saltos,
                a.cuenta(TipoIrregularidad.ZONA_MUERTA) to R.plurals.obd_barrido_zonas_muertas,
            ).filter { it.first > 0 }.map { (cuenta, id) -> cuantos(id, cuenta) }
            return partes.takeIf { it.isNotEmpty() }?.joinToString(", ")?.replaceFirstChar(Char::uppercase)
        }

        private fun falla(c: Comprobacion): String = when {
            c.clave == AnalizadorBarridoTps.CLAVE_ORDEN -> res.get(R.string.obd_barrido_falla_orden)
            c.clave == AnalizadorBarridoTps.CLAVE_SIN_ZONAS_MUERTAS -> res.get(
                R.string.obd_barrido_falla_zonas_muertas,
                cuantos(R.plurals.obd_barrido_zonas_muertas, a.cuenta(TipoIrregularidad.ZONA_MUERTA)),
            )
            c.valor == null -> res.get(R.string.obd_barrido_falla_sin_valor, c.etiqueta.lowercase())
            else -> res.get(
                R.string.obd_barrido_falla_fuera,
                c.etiqueta.lowercase(),
                valor(c),
                TextoBanda.lado(c.posicion, res),
                c.banda?.let { banda(it, res) }.toString(),
            )
        }

        // ── Formato ─────────────────────────────────────────────────────────

        private fun sinBarridoEvaluado(): String = res.get(R.string.obd_barrido_sin_barrido_evaluado)

        private fun valor(c: Comprobacion): String {
            val x = c.valor ?: return "—"
            return if (c.unidad.isEmpty()) n(x, 2) else n(x, 2) + ESPACIO + c.unidad
        }

        private fun bandaDe(clave: String): String =
            a.comprobacion(clave)?.banda?.let { banda(it, res) } ?: res.get(R.string.obd_barrido_sin_banda)

        private fun cuantos(id: Int, cuenta: Int): String = res.plural(id, cuenta, cuenta)

        private fun v(x: Double): String = n(x, 2) + ESPACIO + "V"

        private fun hz(x: Double): String = n(x, 1) + ESPACIO + "Hz"

        private fun n(x: Double, decimales: Int): String = LocaleNumbers.decimal(x, decimales, res.locale)
    }

    private const val ESPACIO = " "
}
