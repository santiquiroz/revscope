package com.revscope.core.obd.taller.pruebas

import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.pruebas.AnalizadorBarridoTps.Pasos
import com.revscope.core.obd.taller.referencia.BandaReferencia
import com.revscope.core.obd.taller.referencia.ClavesBanda
import com.revscope.core.obd.taller.referencia.PosicionEnBanda
import com.revscope.core.obd.taller.sesion.Veredicto

// Redacción de «compatible con…», citando cada banda con su origen (típica, con fuente o editada).
object TextosBarridoTps {

    val TITULOS_PASO = mapOf(
        Pasos.CERRADO_1 to "Cerrado",
        Pasos.MEDIO to "Medio",
        Pasos.A_FONDO to "A fondo",
        Pasos.CERRADO_2 to "Cerrado otra vez",
        Pasos.BARRIDO_LENTO to "Barrido lento",
    )

    fun resultado(a: AnalisisBarridoTps) = ResultadoPrueba(
        tipo = TipoPrueba.TPS_BARRIDO,
        veredicto = a.patron.veredicto,
        titulo = titulo(a.patron),
        interpretacion = interpretacion(a) + notaConfianza(a),
        siguientePaso = siguientePaso(a.patron),
        hallazgos = hallazgos(a),
        bajaConfianza = a.bajaConfianza,
        detalle = a,
    )

    fun sinDatos(faltan: List<String>) = ResultadoPrueba(
        tipo = TipoPrueba.TPS_BARRIDO,
        veredicto = Veredicto.ATENCION,
        titulo = "Sin datos suficientes del TPS",
        interpretacion = "No llegaron muestras del TPS en: ${faltan.joinToString { TITULOS_PASO[it] ?: it }}. " +
            "Sin ellas no se puede comparar con las bandas.",
        siguientePaso = "Revisa que el adaptador siga conectado y repite la prueba",
        hallazgos = emptyList(),
        bajaConfianza = false,
        detalle = SinDatosTps(faltan),
    )

    fun titulo(patron: PatronTps): String = when (patron) {
        PatronTps.CORTES_O_SALTOS -> "Cortes o saltos en la señal"
        PatronTps.SENAL_BAJA_TODO_EL_RECORRIDO -> "Señal baja en todo el recorrido, pareja y estable"
        PatronTps.SENAL_ALTA -> "Señal alta con el acelerador cerrado"
        PatronTps.RANGO_DESEMPENO -> "Señal fuera de rango o irregular"
        PatronTps.NORMAL -> "Señal del TPS dentro de lo típico"
    }

    fun siguientePaso(patron: PatronTps): String? = when (patron) {
        PatronTps.CORTES_O_SALTOS ->
            "Revisa el conector y el cableado del TPS y mide la señal con el multímetro moviendo el acelerador despacio"
        PatronTps.SENAL_BAJA_TODO_EL_RECORRIDO ->
            "Mide con el multímetro la referencia de 5 V, la masa y la señal del TPS (plantilla TPS), confirma que el " +
                "repuesto sea el original y revisa el conector"
        PatronTps.SENAL_ALTA -> "Mide con el multímetro la masa y la señal del TPS (plantilla TPS) con el acelerador cerrado"
        PatronTps.RANGO_DESEMPENO ->
            "Revisa el tope mecánico del acelerador y repite la prueba; si se repite, mide la señal con el multímetro"
        PatronTps.NORMAL -> null
    }

    private fun interpretacion(a: AnalisisBarridoTps): String = when (a.patron) {
        PatronTps.CORTES_O_SALTOS ->
            "La señal tuvo ${cuantos(a.cuenta(TipoIrregularidad.CORTE), "corte", "cortes")} y " +
                "${cuantos(a.cuenta(TipoIrregularidad.SALTO), "salto", "saltos")} durante la prueba: compatible con " +
                "P0124 (señal intermitente del TPS) o con una pista gastada del sensor."
        PatronTps.SENAL_BAJA_TODO_EL_RECORRIDO -> senalBaja(a)
        PatronTps.SENAL_ALTA ->
            "Cerrado ${v(a.cerradoV)}, por encima de la banda (${bandaDe(a, ClavesBanda.TPS_CERRADO_V)}): compatible " +
                "con P0123 (señal alta del TPS): referencia en corto con la señal, señal a 5 V o masa abierta."
        PatronTps.RANGO_DESEMPENO ->
            "Recorre ${v(a.recorridoV)}, pero ${a.comprobaciones.filterNot { it.cumple }.joinToString("; ") { falla(it, a) }}: " +
                "compatible con P0121 (rango o desempeño del TPS) o con un ajuste mecánico del tope."
        PatronTps.NORMAL ->
            "Cerrado ${v(a.cerradoV)} (${bandaDe(a, ClavesBanda.TPS_CERRADO_V)}), a fondo ${v(a.fondoV)} " +
                "(${bandaDe(a, ClavesBanda.TPS_FONDO_V)}) y recorrido ${v(a.recorridoV)}: estable, en orden y sin " +
                "cortes, saltos ni zonas muertas."
    }

    private fun senalBaja(a: AnalisisBarridoTps): String {
        val tipico = a.recorridoTipicoV?.let { " (≈${v(it)})" }.orEmpty()
        val fraccion = a.fraccionRecorrido?.let { ", el ${FormatoTaller.numero(it * 100, 0)} % del típico$tipico" }.orEmpty()
        return "Cerrado ${v(a.cerradoV)} (${bandaDe(a, ClavesBanda.TPS_CERRADO_V)}) y a fondo ${v(a.fondoV)} " +
            "(${bandaDe(a, ClavesBanda.TPS_FONDO_V)}): la señal queda baja en todo el recorrido. Recorre " +
            "${v(a.recorridoV)}$fraccion. Sube en orden, pareja, y repite el cerrado (${v(a.repetibilidadV)}): " +
            "compatible con P0122 (señal baja del TPS)."
    }

    private fun notaConfianza(a: AnalisisBarridoTps): String {
        val depende = a.patron == PatronTps.CORTES_O_SALTOS || a.patron == PatronTps.RANGO_DESEMPENO
        if (!a.bajaConfianza || !depende) return ""
        return " Detección con baja confianza por la tasa de ${hz(a.tasaHz)}."
    }

    private fun hallazgos(a: AnalisisBarridoTps): List<String> = buildList {
        a.comprobaciones.filterNot { it.cumple }.forEach { add(falla(it, a).replaceFirstChar(Char::uppercase)) }
        irregularidades(a)?.let(::add)
        if (a.bajaConfianza) {
            add("Tasa de ${hz(a.tasaHz)}, por debajo de 8 Hz: la detección de saltos y zonas muertas es de baja confianza")
        }
    }

    private fun irregularidades(a: AnalisisBarridoTps): String? {
        val partes = listOf(
            a.cuenta(TipoIrregularidad.CORTE) to ("corte" to "cortes"),
            a.cuenta(TipoIrregularidad.SALTO) to ("salto" to "saltos"),
            a.cuenta(TipoIrregularidad.ZONA_MUERTA) to ("zona muerta" to "zonas muertas"),
        ).filter { it.first > 0 }.map { (n, nombres) -> cuantos(n, nombres.first, nombres.second) }
        return partes.takeIf { it.isNotEmpty() }?.joinToString(", ")?.replaceFirstChar(Char::uppercase)
    }

    private fun falla(c: Comprobacion, a: AnalisisBarridoTps): String = when {
        c.clave == AnalizadorBarridoTps.CLAVE_ORDEN -> "las posiciones no quedan en orden (cerrado < medio < fondo)"
        c.clave == AnalizadorBarridoTps.CLAVE_SIN_ZONAS_MUERTAS ->
            "${cuantos(a.cuenta(TipoIrregularidad.ZONA_MUERTA), "zona muerta", "zonas muertas")} en plena subida o bajada"
        c.valor == null -> "${c.etiqueta.lowercase()} no se pudo calcular"
        else -> "${c.etiqueta.lowercase()} ${valor(c)} ${lado(c.posicion)} de la banda (${c.banda?.let(::banda)})"
    }

    private fun lado(posicion: PosicionEnBanda?): String =
        if (posicion == PosicionEnBanda.BAJO) "por debajo" else "por encima"

    private fun valor(c: Comprobacion): String {
        val x = c.valor ?: return "—"
        return if (c.unidad.isEmpty()) FormatoTaller.numero(x, 2) else "${FormatoTaller.numero(x, 2)} ${c.unidad}"
    }

    private fun bandaDe(a: AnalisisBarridoTps, clave: String): String =
        a.comprobacion(clave)?.banda?.let(::banda) ?: "sin banda de referencia"

    fun banda(b: BandaReferencia): String = "${rango(b)}, ${b.etiquetaOrigen}"

    private fun rango(b: BandaReferencia): String {
        val unidad = if (b.unidad == "V") " V" else ""
        val min = b.min
        val max = b.max
        return when {
            min != null && max != null -> "${numeroBanda(min)}–${numeroBanda(max)}$unidad"
            min != null -> "≥ ${numeroBanda(min)}$unidad"
            else -> "≤ ${numeroBanda(max ?: 0.0)}$unidad"
        }
    }

    private fun numeroBanda(x: Double): String =
        FormatoTaller.numero(x, if (FormatoTaller.redondear(x, 1) == x) 1 else 2)

    private fun cuantos(n: Int, uno: String, varios: String): String = if (n == 1) "1 $uno" else "$n $varios"

    private fun v(x: Double): String = "${FormatoTaller.numero(x, 2)} V"

    private fun hz(x: Double): String = "${FormatoTaller.numero(x, 1)} Hz"
}
