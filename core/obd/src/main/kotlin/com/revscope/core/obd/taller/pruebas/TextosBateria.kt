package com.revscope.core.obd.taller.pruebas

import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.pruebas.AnalizadorBateria.Pasos
import com.revscope.core.obd.taller.referencia.ClavesBanda

// Redacción de batería y carga: cada voltaje con su banda y origen, el desfase de AT RV siempre dicho y
// «compatible con…», nunca un dictamen. Las unidades van con espacio duro.
object TextosBateria {

    val TITULOS_PASO = mapOf(
        Pasos.CONTACTO to "Contacto",
        Pasos.ARRANQUE to "Arranque",
        Pasos.MINIMO to "Mínimo",
        Pasos.RPM_ALTAS to "Rpm altas",
    )

    const val AVISO_FAROLA = "En muchas motos la farola queda encendida con el contacto (AHO) y el voltaje baja por eso."
    const val NOTA_AT_RV = "AT RV mide en el conector OBD con ±0,1-0,2 V (típico): calibra el desfase del vehículo con " +
        "el multímetro en los bornes para afinarlo."

    fun resultado(a: AnalisisBateria) = ResultadoPrueba(
        tipo = TipoPrueba.BATERIA_CARGA,
        veredicto = a.patron.veredicto,
        titulo = titulo(a),
        interpretacion = interpretacion(a),
        siguientePaso = siguientePaso(a),
        hallazgos = hallazgos(a),
        bajaConfianza = a.bajaConfianza,
        detalle = a,
    )

    fun titulo(a: AnalisisBateria): String = when (a.patron) {
        PatronBateria.SIN_DATOS -> "Sin lecturas de voltaje del adaptador"
        PatronBateria.SOBRECARGA -> "Sobrecarga: ${v(a.cargaMaxV)} con el motor en marcha"
        PatronBateria.ARRANQUE_BAJO -> "El voltaje cae a ${v(a.arranque?.valleV)} al arrancar"
        PatronBateria.ADAPTADOR_REINICIADO -> "El adaptador se reinició al arrancar"
        PatronBateria.NO_CARGA -> "No carga: ${v(a.rpmAltas?.mediaV)} a rpm altas"
        PatronBateria.CARGA_ALTA -> "Carga alta: ${v(a.rpmAltas?.mediaV)} a rpm altas"
        PatronBateria.SIN_CARGA_EN_MINIMO -> "Carga a rpm altas, pero no en mínimo"
        PatronBateria.BATERIA_BAJA -> "Carga bien, pero la batería está baja en reposo"
        PatronBateria.NORMAL -> "Batería y carga dentro de las bandas"
    }

    // ── Interpretación ──────────────────────────────────────────────────────

    private fun interpretacion(a: AnalisisBateria): String {
        if (a.patron == PatronBateria.SIN_DATOS) return "El adaptador no devolvió lecturas de voltaje (AT RV): no hay nada que medir."
        return listOfNotNull(
            fraseContacto(a),
            fraseArranque(a),
            fraseCarga(a),
            fraseVeredicto(a),
            fraseDesfase(a.desfase),
        ).joinToString(" ")
    }

    private fun fraseContacto(a: AnalisisBateria): String? {
        val contacto = a.contacto ?: return null
        val banda = banda(a, AnalizadorBateria.claveContacto(a.conFarola))
        val condicion = if (a.conFarola) "con la farola encendida" else "sin cargas"
        val farola = if (a.conFarola) " $AVISO_FAROLA" else ""
        return "Con el contacto puesto y el motor apagado marcó ${v(contacto.mediaV)}; $condicion se espera $banda.$farola"
    }

    private fun fraseArranque(a: AnalisisBateria): String? {
        val r = a.arranque ?: return null
        if (r.adaptadorReiniciado) return fraseReinicio(r)
        if (!r.visto) return "No se vio la caída del arranque."
        return "Al arrancar cayó hasta ${v(r.valleV)} (${v(r.caidaV)} menos que en reposo); se espera ${banda(a, ClavesBanda.ARRANQUE_MIN_V)}."
    }

    private fun fraseReinicio(r: ArranqueVoltaje): String {
        val ultimo = r.valleV?.let { " Lo más bajo que alcanzó a medir fue ${v(it)}." }.orEmpty()
        val cuanto = if (r.enlacePerdido) {
            "se perdió el enlace con el adaptador"
        } else {
            "el adaptador no respondió durante ${seg(r.sinRespuestaMs / 1_000.0)} s"
        }
        return "Durante el arranque $cuanto: se reinició, lo que suele pasar cuando el voltaje cae mucho " +
            "(típico por debajo de ~${v(AnalizadorBateria.REINICIO_TIPICO_V, 0)}, depende del adaptador).$ultimo"
    }

    private fun fraseCarga(a: AnalisisBateria): String? {
        val altas = a.rpmAltas
        val minimo = a.minimo
        if (altas == null && minimo == null) return null
        val partes = listOfNotNull(minimo?.let { "en mínimo marcó ${v(it.mediaV)}" }, altas?.let { "a rpm altas ${v(it.mediaV)}" })
        return "Con el motor en marcha, ${partes.joinToString(" y ")}; la carga se espera en ${banda(a, ClavesBanda.CARGA_V)}."
    }

    private fun fraseVeredicto(a: AnalisisBateria): String? = when (a.patron) {
        PatronBateria.SOBRECARGA -> "Supera el máximo (${banda(a, ClavesBanda.SOBRECARGA_MAX_V)}): compatible con un " +
            "regulador/rectificador que no limita la carga; puede dañar la batería y las luces."
        PatronBateria.ARRANQUE_BAJO -> "Compatible con una batería débil o sulfatada, con bornes o masas flojos o sulfatados, " +
            "o con un motor de arranque que consume de más."
        PatronBateria.NO_CARGA -> "Compatible con un regulador/rectificador, un estator o su conector en falla, o con un fusible de carga abierto."
        PatronBateria.CARGA_ALTA -> "Algo por encima de la banda sin llegar a sobrecarga: compatible con un regulador que empieza a fallar."
        PatronBateria.SIN_CARGA_EN_MINIMO ->
            "En mínimo no sube sobre el reposo: en motos pequeñas puede ser normal, pero con luces encendidas la batería se descarga."
        PatronBateria.BATERIA_BAJA -> "Compatible con una batería descargada o con un consumo que la baja con el contacto apagado."
        else -> null
    }

    private fun fraseDesfase(d: DesfaseVoltaje): String = if (d.calibrado) {
        "Voltajes corregidos con el desfase del vehículo (${conSigno(d.voltios)}, ${d.origen})."
    } else {
        NOTA_AT_RV
    }

    // ── Hallazgos y siguiente paso ──────────────────────────────────────────

    private fun hallazgos(a: AnalisisBateria): List<String> = buildList {
        val r = a.arranque
        if (r?.adaptadorReiniciado == true) add("El adaptador se reinició durante el arranque: caída severa del voltaje (típico por debajo de ~8 V)")
        if (r != null && !r.adaptadorReiniciado && !r.visto && r.valleV != null) {
            add("No se vio la caída del arranque: puede que no se haya arrancado en ese paso")
        }
        if (a.bajaConfianza) add("Con ${hz(r?.tasaHz)} lecturas por segundo, el valle real puede ser más profundo que el medido")
        if (a.patron == PatronBateria.SOBRECARGA) add("Sobrecarga: ${v(a.cargaMaxV)} con el motor en marcha")
        if (a.patron == PatronBateria.ARRANQUE_BAJO) add("El voltaje cayó a ${v(r?.valleV)} al arrancar")
        if (a.patron == PatronBateria.NO_CARGA) add("A rpm altas marcó ${v(a.rpmAltas?.mediaV)}: no está cargando")
    }

    private fun siguientePaso(a: AnalisisBateria): String? = when (a.patron) {
        PatronBateria.SIN_DATOS -> "Revisa que el adaptador responda a AT RV y repite la prueba"
        PatronBateria.ARRANQUE_BAJO, PatronBateria.ADAPTADOR_REINICIADO ->
            "Mide la batería en los bornes durante el arranque con el multímetro (plantilla batería) y revisa bornes y masas"
        PatronBateria.SOBRECARGA, PatronBateria.NO_CARGA, PatronBateria.CARGA_ALTA, PatronBateria.SIN_CARGA_EN_MINIMO ->
            "Mide la carga en los bornes con el multímetro (plantilla batería) y revisa el regulador/rectificador, el estator y su conector"
        PatronBateria.BATERIA_BAJA -> "Carga la batería y repite; si vuelve a bajar en reposo, busca consumos con el contacto apagado"
        PatronBateria.NORMAL -> null
    }

    // ── Medidas: una línea por cifra, para la pantalla y el informe ──────────

    fun medidas(a: AnalisisBateria): List<String> = listOfNotNull(
        a.contacto?.let { "Contacto: ${tramo(it)}" },
        a.arranque?.let(::medidaArranque),
        a.minimo?.let { "Mínimo: ${tramo(it)}" },
        a.rpmAltas?.let { "Rpm altas: ${tramo(it)}" },
        "Desfase: " + if (a.desfase.calibrado) "${conSigno(a.desfase.voltios)} (${a.desfase.origen})" else "sin calibrar (AT RV ±0,1-0,2 V, típico)",
    )

    private fun medidaArranque(r: ArranqueVoltaje): String = when {
        r.adaptadorReiniciado -> "Arranque: el adaptador se reinició" + (r.valleV?.let { " (lo último, ${v(it)})" } ?: "")
        !r.visto -> "Arranque: sin caída visible (${hz(r.tasaHz)} lecturas/s)"
        else -> "Arranque: valle ${v(r.valleV)}, caída de ${v(r.caidaV)} (${hz(r.tasaHz)} lecturas/s)"
    }

    // ── Formato ─────────────────────────────────────────────────────────────

    private fun tramo(t: TramoVoltaje): String {
        val desde = FormatoTaller.numero(t.minV, 1)
        val hasta = FormatoTaller.numero(t.maxV, 1)
        return if (desde == hasta) v(t.mediaV) else "${v(t.mediaV)} (de $desde a ${v(t.maxV)})"
    }

    private fun banda(a: AnalisisBateria, clave: String): String {
        val c = a.comprobacion(clave)?.banda ?: return "la banda típica"
        return TextoBanda.citar(c, 1)
    }

    private fun v(x: Double?, decimales: Int = 1) = "${FormatoTaller.numero(x ?: 0.0, decimales)} V"

    private fun conSigno(x: Double) = "${FormatoTaller.conSigno(x, 2)} V"

    private fun hz(x: Double?) = FormatoTaller.numero(x ?: 0.0, 0)

    private fun seg(x: Double) = FormatoTaller.numero(x, 1)
}
