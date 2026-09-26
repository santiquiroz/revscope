package com.revscope.core.obd.taller.pruebas

import com.revscope.core.obd.taller.FormatoTaller
import kotlin.math.abs
import kotlin.math.roundToLong

// Redacción del MAP contra la barométrica: la fuente de la referencia y su incertidumbre siempre a la vista,
// «compatible con…» y nunca un dictamen. Las unidades van con espacio duro.
object TextosMapBaro {

    val TITULOS_PASO = mapOf(AnalizadorMapBaro.Pasos.CONTACTO to "Contacto")

    const val NOTA_ECU = "Muchas ECU calculan la barométrica con el mismo sensor MAP al poner el contacto: " +
        "que coincidan no descarta un MAP descalibrado."

    fun resultado(a: AnalisisMapBaro) = ResultadoPrueba(
        tipo = TipoPrueba.MAP_BARO,
        veredicto = a.patron.veredicto,
        titulo = titulo(a),
        interpretacion = interpretacion(a),
        siguientePaso = siguientePaso(a),
        hallazgos = hallazgos(a),
        bajaConfianza = false,
        detalle = a,
    )

    fun titulo(a: AnalisisMapBaro): String = when (a.patron) {
        PatronMapBaro.SIN_DATOS -> "Sin lecturas del MAP"
        PatronMapBaro.SIN_REFERENCIA -> "MAP en ${kpa(a.map?.mediaKpa)}, sin presión barométrica para comparar"
        PatronMapBaro.MAP_BAJO -> "El MAP marca ${kpa(abs(a.deltaKpa ?: 0.0))} por debajo de la barométrica"
        PatronMapBaro.MAP_ALTO -> "El MAP marca ${kpa(abs(a.deltaKpa ?: 0.0))} por encima de la barométrica"
        PatronMapBaro.NO_CONCLUYENTE -> "MAP cerca de la barométrica, sin poder concluir"
        PatronMapBaro.NORMAL -> "El MAP coincide con la barométrica"
    }

    // ── Interpretación ──────────────────────────────────────────────────────

    private fun interpretacion(a: AnalisisMapBaro): String {
        val map = a.map ?: return "No llegaron lecturas del MAP (PID 0B) con el contacto puesto: sin ellas no hay nada que comparar."
        return listOfNotNull(
            "Con el motor apagado, el MAP marcó ${kpa(map.mediaKpa)} (${rango(map)}).",
            fraseReferencia(a),
            fraseEquivalencia(a),
            fraseVeredicto(a),
            NOTA_ECU.takeIf { a.referencia?.origen == OrigenBarometrica.ECU },
        ).joinToString(" ")
    }

    private fun fraseReferencia(a: AnalisisMapBaro): String? {
        val ref = a.referencia ?: return "No hubo presión barométrica de referencia: la ECU no reportó el PID 33, " +
            "el teléfono no tiene barómetro y no hubo altitud GPS."
        val banda = a.banda?.let { TextoBanda.citar(it, 0) } ?: "≤ ${kpa(AnalizadorMapBaro.BANDA_TIPICA_KPA)}, Típico (editable)"
        return "La referencia fue ${ref.enFrase}: ${kpa(ref.kPa)}. La diferencia es de ${kpaConSigno(a.deltaKpa ?: 0.0)}; " +
            "sin vacío se espera $banda, más ±${kpa(ref.incertidumbreKpa)} de la fuente."
    }

    private fun fraseEquivalencia(a: AnalisisMapBaro): String? {
        if (a.patron == PatronMapBaro.NORMAL || a.patron == PatronMapBaro.NO_CONCLUYENTE) return null
        val altitud = a.altitudEquivalenteM ?: return null
        return "${kpa(a.map?.mediaKpa)} equivalen a la presión atmosférica a unos ${metros(altitud)} de altitud."
    }

    private fun fraseVeredicto(a: AnalisisMapBaro): String? = when (a.patron) {
        PatronMapBaro.MAP_BAJO -> "Marca de menos: compatible con un sensor MAP descalibrado (P0106) o con una referencia de 5 V " +
            "baja. El MAP comparte esa referencia con el TPS: si el TPS también marca bajo, revisa la referencia común."
        PatronMapBaro.MAP_ALTO ->
            "Marca de más: compatible con un sensor MAP descalibrado (P0106), con la señal alta (P0108) o con la masa del sensor abierta."
        PatronMapBaro.NO_CONCLUYENTE -> fraseNoConcluyente(a)
        PatronMapBaro.NORMAL -> "Coincide dentro de la banda: el MAP lee bien la presión con el motor apagado."
        else -> null
    }

    private fun fraseNoConcluyente(a: AnalisisMapBaro): String {
        val ref = a.referencia
        val alt = a.alternativa
        if (ref != null && alt != null && a.contrasteDiscrepa) {
            return "Coincide con ${ref.origen.enFrase} pero no con ${alt.origen.enFrase}: con referencias que no concuerdan no se puede concluir."
        }
        return "La diferencia supera la banda típica pero cabe en la incertidumbre de la fuente: con esta referencia no se puede concluir."
    }

    // ── Hallazgos y siguiente paso ──────────────────────────────────────────

    private fun hallazgos(a: AnalisisMapBaro): List<String> = buildList {
        val delta = a.deltaKpa
        if (delta != null && (a.patron == PatronMapBaro.MAP_BAJO || a.patron == PatronMapBaro.MAP_ALTO)) {
            add("El MAP marca ${kpa(abs(delta))} ${lado(delta)} de la barométrica (tolerancia ±${kpa(a.toleranciaKpa ?: 0.0)})")
        }
        contraste(a)?.let(::add)
        val map = a.map
        if (map != null && map.variacionKpa > AnalizadorMapBaro.VARIACION_MAX_KPA) {
            add("El MAP varió ${kpa(map.variacionKpa)} con el motor apagado: señal inestable, compatible con un falso contacto")
        }
    }

    private fun contraste(a: AnalisisMapBaro): String? {
        val alt = a.alternativa ?: return null
        val delta = a.deltaAlternativaKpa ?: return null
        if (!a.contrasteDiscrepa) return null
        return "Contra ${alt.origen.enFrase} (${kpa(alt.kPa)}), el MAP marca ${kpa(abs(delta))} ${lado(delta)}: " +
            "fuera de ±${kpa(a.toleranciaAlternativaKpa ?: 0.0)}"
    }

    private fun siguientePaso(a: AnalisisMapBaro): String? = when (a.patron) {
        PatronMapBaro.SIN_DATOS -> "Revisa que el adaptador siga conectado y que la ECU reporte el MAP (PID 0B)"
        PatronMapBaro.SIN_REFERENCIA ->
            "Activa la ubicación del teléfono (para la altitud GPS) o usa un teléfono con barómetro y repite la prueba"
        PatronMapBaro.MAP_BAJO, PatronMapBaro.MAP_ALTO -> MEDIR_MAP
        PatronMapBaro.NO_CONCLUYENTE -> if (a.contrasteDiscrepa) MEDIR_MAP else REPETIR_CON_BAROMETRO
        PatronMapBaro.NORMAL -> if (a.referencia?.origen == OrigenBarometrica.ECU && a.alternativa == null) CONTRASTAR else null
    }

    private const val MEDIR_MAP = "Mide la referencia de 5 V, la masa y la señal del MAP con el multímetro (plantilla MAP)"
    private const val REPETIR_CON_BAROMETRO =
        "Repite con el barómetro del teléfono o con la barométrica de la ECU (PID 33): son referencias más exactas que la altitud"
    private const val CONTRASTAR = "Para descartar un MAP descalibrado, repite la prueba con un teléfono que tenga barómetro"

    // ── Medidas: una línea por cifra, para la pantalla y el informe ──────────

    fun medidas(a: AnalisisMapBaro): List<String> {
        val map = a.map ?: return emptyList()
        return listOfNotNull(
            "MAP con el motor apagado: ${kpa(map.mediaKpa)} (${rango(map)})",
            a.referencia?.let { "Barométrica: ${kpa(it.kPa)} · ${it.etiqueta}" } ?: "Barométrica: sin fuente",
            a.deltaKpa?.let { "Diferencia: ${kpaConSigno(it)} (tolerancia ±${kpa(a.toleranciaKpa ?: 0.0)} con la fuente)" },
            a.altitudEquivalenteM?.let { "El MAP equivale a unos ${metros(it)} de altitud" },
            a.alternativa?.let { alt ->
                "Contraste: ${alt.origen.nombre} ${kpa(alt.kPa)} (diferencia ${kpaConSigno(a.deltaAlternativaKpa ?: 0.0)})"
            },
        )
    }

    // ── Formato ─────────────────────────────────────────────────────────────

    private fun rango(m: MapMedido): String {
        val desde = FormatoTaller.compacto(r1(m.minKpa))
        val hasta = FormatoTaller.compacto(r1(m.maxKpa))
        return if (desde == hasta) "estable en ${m.n} lecturas" else "de $desde a ${kpa(m.maxKpa)}, ${m.n} lecturas"
    }

    private fun lado(delta: Double) = if (delta < 0) "por debajo" else "por encima"

    private fun kpa(x: Double?) = "${FormatoTaller.compacto(r1(x ?: 0.0))} kPa"

    private fun kpaConSigno(x: Double) = "${FormatoTaller.conSigno(x, 1)} kPa"

    // Redondeada a la centena y con separador de miles: es una equivalencia, no una medida.
    private fun metros(m: Double): String {
        val centenas = (m / 100).roundToLong() * 100
        val texto = if (abs(centenas) >= 1_000) "${centenas / 1_000} ${"%03d".format(abs(centenas) % 1_000)}" else centenas.toString()
        return "$texto m"
    }

    private fun r1(x: Double) = FormatoTaller.redondear(x, 1)
}
