package com.revscope.core.obd.taller.pruebas

import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.pruebas.AnalizadorMinimo.Pasos
import com.revscope.core.obd.taller.referencia.ClavesBanda
import com.revscope.core.obd.taller.referencia.PosicionEnBanda

// Redacción del mínimo y retorno: cifras medidas, la banda con su origen y «compatible con…», nunca un dictamen.
object TextosMinimo {

    val TITULOS_PASO = mapOf(
        Pasos.MINIMO to "Mínimo",
        Pasos.RETORNO_1 to "Retorno 1",
        Pasos.RETORNO_2 to "Retorno 2",
        Pasos.RETORNO_3 to "Retorno 3",
    )

    fun resultado(a: AnalisisMinimo) = ResultadoPrueba(
        tipo = TipoPrueba.MINIMO_RETORNO,
        veredicto = a.patron.veredicto,
        titulo = titulo(a),
        interpretacion = interpretacion(a),
        siguientePaso = siguientePaso(a),
        hallazgos = hallazgos(a),
        bajaConfianza = false,
        detalle = a,
    )

    fun titulo(a: AnalisisMinimo): String = when (a.patron) {
        PatronMinimo.SE_APAGA -> tituloApagones(a)
        PatronMinimo.INESTABLE -> "Mínimo inestable"
        PatronMinimo.RETORNO_BAJO -> "Al soltar, las RPM caen demasiado"
        PatronMinimo.FUERA_DE_BANDA -> "Mínimo estable, pero fuera de la banda"
        PatronMinimo.TPS_RALENTI_FUERA -> "Mínimo estable, con el TPS de ralentí fuera de la banda de cerrado"
        PatronMinimo.SIN_RETORNOS -> "Mínimo estable; no se detectaron las aceleradas"
        PatronMinimo.SIN_DATOS -> "Sin datos suficientes de RPM"
        PatronMinimo.NORMAL -> "Mínimo estable y buen retorno"
    }

    private fun tituloApagones(a: AnalisisMinimo): String {
        val enMinimo = a.minimo?.apagones ?: 0
        val alSoltar = "${a.apagonesAlSoltar} de ${a.retornosValidos.size} veces al soltar el acelerador"
        return when {
            enMinimo == 0 -> "Se apagó $alSoltar"
            a.apagonesAlSoltar == 0 -> "Se apagó en el mínimo sin tocar el acelerador"
            else -> "Se apagó en el mínimo y $alSoltar"
        }
    }

    // ── Interpretación ──────────────────────────────────────────────────────

    private fun interpretacion(a: AnalisisMinimo): String {
        val m = a.minimo ?: return "No llegaron lecturas de RPM durante el mínimo: sin ellas no hay nada que medir."
        return listOfNotNull(
            frasesMinimo(a, m),
            fraseBanda(a),
            fraseRetornos(a),
            a.ectMediaC?.let { "Motor a ${FormatoTaller.numero(it, 0)} °C durante el mínimo." },
            compatibilidad(a.patron),
        ).joinToString(" ")
    }

    private fun frasesMinimo(a: AnalisisMinimo, m: EstadisticaMinimo): String {
        val deriva = a.comprobacion(ClavesBanda.MINIMO_DERIVA_MAX)
        val base = if (deriva?.cumple == false) {
            "El mínimo ${if (m.derivaRpmS < 0) "cayó" else "subió"} de ${rpm(m.inicioRpm)} a ${rpm(m.finRpm)} rpm en " +
                "${FormatoTaller.compacto(FormatoTaller.redondear(m.duracionS, 1))} s (${FormatoTaller.numero(m.derivaRpmS, 0)} rpm/s)"
        } else {
            "El mínimo quedó en ${rpm(m.mediaRpm)} rpm de media, con desviación de ${rpm(m.desviacionRpm)} rpm"
        }
        val oscila = m.oscilacion?.let { ", y oscila ±${rpm(it.amplitudRpm)} rpm${it.periodoS?.let { p -> " cada ${seg(p)} s" }.orEmpty()}" }
        return base + oscila.orEmpty() + if (m.inestable) ": inestable${bandasEstabilidad(a)}." else "."
    }

    private fun bandasEstabilidad(a: AnalisisMinimo): String {
        val citas = listOf(ClavesBanda.MINIMO_DERIVA_MAX, ClavesBanda.MINIMO_DESVIACION_MAX)
            .mapNotNull { a.comprobacion(it)?.banda }
            .map { TextoBanda.citar(it, 0) }
        return if (citas.isEmpty()) "" else " (${citas.joinToString("; ")})"
    }

    // Con el mínimo inestable, la media no dice dónde queda el ralentí: la banda se calla.
    private fun fraseBanda(a: AnalisisMinimo): String? {
        val c = mediaFueraDeBanda(a)?.takeIf { it.banda != null } ?: return null
        return "La media, ${rpm(c.valor ?: 0.0)} rpm, queda ${TextoBanda.lado(c.posicion)} de la banda " +
            "(${TextoBanda.citar(checkNotNull(c.banda), 0)})."
    }

    private fun fraseRetornos(a: AnalisisMinimo): String? {
        val validos = a.retornosValidos
        if (validos.isEmpty()) return null
        if (a.apagonesAlSoltar > 0) return "Al soltar el acelerador se apagó ${a.apagonesAlSoltar} de ${validos.size} veces."
        val peor = validos.minBy { it.valleFraccion ?: 1.0 }
        val tiempos = validos.mapNotNull { it.tiempoS }
        val vuelta = if (tiempos.isEmpty()) "" else " y volvió al mínimo en ${seg(tiempos.max())} s como mucho"
        return "Al soltar, el valle más bajo fue de ${rpm(peor.valleRpm ?: 0.0)} rpm " +
            "(${pct(peor.valleFraccion)} del mínimo)$vuelta."
    }

    private fun compatibilidad(patron: PatronMinimo): String? = when (patron) {
        PatronMinimo.SE_APAGA, PatronMinimo.INESTABLE, PatronMinimo.RETORNO_BAJO ->
            "Compatible con un mínimo mal controlado: válvula o motor paso a paso del mínimo, cuerpo de aceleración " +
                "sucio, aire falso o una señal del TPS que la ECU no reconoce como ralentí."
        else -> null
    }

    // ── Hallazgos y siguiente paso ──────────────────────────────────────────

    private fun hallazgos(a: AnalisisMinimo): List<String> = buildList {
        val m = a.minimo ?: return@buildList
        if (m.inestable) add(hallazgoInestable(m))
        if (m.apagones > 0) add("Se apagó ${veces(m.apagones)} en el mínimo sin tocar el acelerador")
        a.retornos.filter { it.seApago }.takeIf { it.isNotEmpty() }?.let { add("Se apagó al soltar en ${nombres(it)}") }
        addAll(a.retornos.mapNotNull(::hallazgoRetorno))
        hallazgoValle(a)?.let(::add)
        hallazgoBanda(a)?.let(::add)
        hallazgoTps(a)?.let(::add)
    }

    private fun hallazgoInestable(m: EstadisticaMinimo): String {
        val osc = m.oscilacion?.let { ", oscila ±${rpm(it.amplitudRpm)} rpm" }.orEmpty()
        return "Mínimo inestable: deriva de ${FormatoTaller.numero(m.derivaRpmS, 1)} rpm/s y desviación de " +
            "${rpm(m.desviacionRpm)} rpm$osc"
    }

    private fun hallazgoRetorno(r: RetornoMedido): String? {
        val nombre = TITULOS_PASO[r.clave] ?: r.clave
        return when {
            !r.acelerada -> "$nombre: no se detectó la acelerada (llegó a ${rpm(r.picoRpm)} rpm); no cuenta"
            !r.seApago && r.tiempoS == null -> "$nombre: no volvió a ±10 % del mínimo antes de terminar el paso"
            else -> null
        }
    }

    private fun hallazgoValle(a: AnalisisMinimo): String? {
        val c = a.comprobacion(ClavesBanda.RETORNO_VALLE_MIN)?.takeIf { !it.cumple && a.apagonesAlSoltar == 0 } ?: return null
        val banda = c.banda?.let { " (${TextoBanda.citar(it, 0)})" }.orEmpty()
        return "Valle al soltar de ${FormatoTaller.numero(c.valor ?: 0.0, 0)} % del mínimo, por debajo de la banda$banda"
    }

    private fun hallazgoBanda(a: AnalisisMinimo): String? {
        val c = mediaFueraDeBanda(a) ?: return null
        val banda = c.banda?.let { " (${TextoBanda.citar(it, 0)})" }.orEmpty()
        return "Mínimo de ${rpm(c.valor ?: 0.0)} rpm, ${TextoBanda.lado(c.posicion)} de la banda$banda"
    }

    private fun mediaFueraDeBanda(a: AnalisisMinimo): Comprobacion? {
        if (a.minimo?.inestable != false) return null
        return a.comprobacion(ClavesBanda.MINIMO_RPM)?.takeIf { !it.cumple }
    }

    private fun hallazgoTps(a: AnalisisMinimo): String? {
        val t = a.tpsRalenti?.takeIf { it.posicion != null && it.posicion != PosicionEnBanda.DENTRO } ?: return null
        val banda = t.banda?.let { " (${TextoBanda.citar(it, 1)})" }.orEmpty()
        val consecuencia = if (t.posicion == PosicionEnBanda.BAJO) {
            "la ECU puede no reconocer la posición de ralentí"
        } else {
            "el acelerador puede no cerrar del todo o el tope estar desajustado"
        }
        return "TPS en ralentí ${FormatoTaller.numero(t.minV, 2)}-${FormatoTaller.numero(t.maxV, 2)} V, " +
            "${TextoBanda.lado(t.posicion)} de la banda de cerrado$banda: $consecuencia"
    }

    private fun siguientePaso(a: AnalisisMinimo): String? {
        val tpsFuera = a.tpsRalenti?.posicion.let { it != null && it != PosicionEnBanda.DENTRO }
        if (tpsFuera && a.patron != PatronMinimo.SIN_DATOS) {
            return "Corre el barrido del TPS con el motor apagado y mide su señal con el multímetro (plantilla TPS)"
        }
        return when (a.patron) {
            PatronMinimo.SE_APAGA, PatronMinimo.INESTABLE, PatronMinimo.RETORNO_BAJO ->
                "Revisa el cuerpo de aceleración y el control del mínimo, busca entradas de aire falso y repite la " +
                    "prueba en frío y en caliente para comparar"
            PatronMinimo.FUERA_DE_BANDA ->
                "Confirma el mínimo que indica el fabricante para este modelo; si difiere de la banda típica, edítala"
            PatronMinimo.SIN_RETORNOS -> "Repite la prueba acelerando hasta unas 3 000 rpm en cada retorno"
            PatronMinimo.SIN_DATOS -> "Revisa que el adaptador siga conectado y repite la prueba"
            PatronMinimo.TPS_RALENTI_FUERA, PatronMinimo.NORMAL -> null
        }
    }

    // ── Medidas: una línea por cifra, para la pantalla y el informe ──────────

    fun medidas(a: AnalisisMinimo): List<String> {
        val m = a.minimo ?: return emptyList()
        return listOfNotNull(
            "Mínimo: media ${rpm(m.mediaRpm)} rpm, desviación ${rpm(m.desviacionRpm)} rpm, de ${rpm(m.minRpm)} a " +
                "${rpm(m.maxRpm)} rpm (${m.n} muestras en ${seg(m.duracionS)} s)",
            "Deriva: ${FormatoTaller.numero(m.derivaRpmS, 1)} rpm/s (de ${rpm(m.inicioRpm)} a ${rpm(m.finRpm)} rpm)",
            "Oscilación: " + (m.oscilacion?.let { o -> "±${rpm(o.amplitudRpm)} rpm${o.periodoS?.let { " cada ${seg(it)} s" }.orEmpty()}" } ?: "no"),
            a.tpsRalenti?.let { t ->
                "TPS en ralentí: ${FormatoTaller.numero(t.minV, 2)}-${FormatoTaller.numero(t.maxV, 2)} V " +
                    "(media ${FormatoTaller.numero(t.mediaV, 2)} V)"
            },
            a.ectMediaC?.let { "Motor a ${FormatoTaller.numero(it, 0)} °C durante el mínimo" },
        ) + a.retornos.map(::medidaRetorno)
    }

    private fun medidaRetorno(r: RetornoMedido): String {
        val nombre = TITULOS_PASO[r.clave] ?: r.clave
        return when {
            !r.acelerada -> "$nombre: sin acelerada (máximo ${rpm(r.picoRpm)} rpm)"
            r.seApago -> "$nombre: pico ${rpm(r.picoRpm)} rpm y se apagó al soltar"
            else -> "$nombre: pico ${rpm(r.picoRpm)} rpm, valle ${rpm(r.valleRpm ?: 0.0)} rpm (${pct(r.valleFraccion)} del mínimo), " +
                (r.tiempoS?.let { "volvió en ${seg(it)} s" } ?: "no volvió a ±10 % del mínimo")
        }
    }

    // ── Formato ─────────────────────────────────────────────────────────────

    private fun nombres(retornos: List<RetornoMedido>): String {
        val lista = retornos.map { (TITULOS_PASO[it.clave] ?: it.clave).lowercase() }
        return if (lista.size == 1) "el ${lista.first()}" else "el ${lista.dropLast(1).joinToString(", el ")} y el ${lista.last()}"
    }

    private fun veces(n: Int) = if (n == 1) "1 vez" else "$n veces"

    private fun rpm(x: Double) = FormatoTaller.numero(x, 0)

    private fun seg(x: Double) = FormatoTaller.numero(x, 1)

    private fun pct(fraccion: Double?) = "${FormatoTaller.numero((fraccion ?: 0.0) * 100, 0)} %"
}
