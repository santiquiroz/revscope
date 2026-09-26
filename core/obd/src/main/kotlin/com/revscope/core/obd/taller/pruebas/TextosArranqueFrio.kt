package com.revscope.core.obd.taller.pruebas

import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.pruebas.AnalizadorArranqueFrio.Pasos
import kotlin.math.abs

// Redacción del arranque en frío: la condición (frío o tibio) va en el título, las cifras con su banda y
// origen, y la nota de los motores refrigerados por aire siempre, porque la ECU no dice cómo se enfría.
object TextosArranqueFrio {

    val TITULOS_PASO = mapOf(
        Pasos.CONTACTO to "Contacto",
        Pasos.ARRANQUE to "Arranque",
        Pasos.CALENTAMIENTO to "Calentamiento",
    )

    const val NOTA_AIRE = "En motos refrigeradas por aire, el sensor de «motor» suele medir la culata o el aceite: " +
        "sube distinto que un refrigerante."

    fun resultado(a: AnalisisArranqueFrio) = ResultadoPrueba(
        tipo = TipoPrueba.ARRANQUE_FRIO,
        veredicto = a.patron.veredicto,
        titulo = titulo(a),
        interpretacion = interpretacion(a),
        siguientePaso = siguientePaso(a),
        hallazgos = hallazgos(a),
        bajaConfianza = false,
        detalle = a,
    )

    fun titulo(a: AnalisisArranqueFrio): String {
        val cuerpo = cuerpoTitulo(a)
        return when (a.plausibilidad.condicion) {
            CondicionMotor.FRIO -> "Arranque en frío: $cuerpo"
            CondicionMotor.TIBIO -> "Arranque tibio: $cuerpo"
            CondicionMotor.SIN_DATO -> cuerpo.replaceFirstChar(Char::uppercase)
        }
    }

    private fun cuerpoTitulo(a: AnalisisArranqueFrio): String = when (a.patron) {
        PatronArranque.SIN_DATOS -> "sin datos suficientes de RPM"
        PatronArranque.NO_ARRANCO -> "no arrancó"
        PatronArranque.SE_APAGA -> "se apagó ${veces(a.apagones)} después de arrancar"
        PatronArranque.SALTOS_ECT -> "saltos en la temperatura del motor"
        PatronArranque.VARIOS_INTENTOS -> "arrancó al ${a.arranque.intentos}.º intento${enTiempo(a)}"
        PatronArranque.IRREGULAR -> "arrancó, con observaciones"
        PatronArranque.NORMAL -> "arrancó al primer intento${enTiempo(a)}"
    }

    private fun enTiempo(a: AnalisisArranqueFrio): String = a.arranque.tiempoS?.let { " en ${seg(it)} s" }.orEmpty()

    // ── Interpretación ──────────────────────────────────────────────────────

    private fun interpretacion(a: AnalisisArranqueFrio): String = listOfNotNull(
        fraseCondicion(a.plausibilidad),
        fraseArranque(a),
        fraseCalentamiento(a),
        frasePerfil(a.perfil),
        fraseSaltos(a),
        NOTA_AIRE,
    ).joinToString(" ")

    private fun fraseCondicion(p: PlausibilidadFrio): String {
        val ect = p.ectC
        val iat = p.iatC
        if (ect == null || iat == null) {
            return "Sin lectura de la temperatura del ${if (ect == null) "motor" else "aire"} en contacto: no se pudo " +
                "confirmar que el motor estuviera frío."
        }
        val medidas = "En contacto, el motor marcaba ${grados(ect)} y el aire ${grados(iat)} " +
            "(${grados(ect - iat)} de diferencia; frío es menos de ${grados(p.limiteC)}, ${origenLimite(p)})."
        return when (p.condicion) {
            CondicionMotor.TIBIO -> "$medidas Es un arranque tibio: no equivale a un arranque tras el reposo de la noche."
            else -> medidas
        }
    }

    private fun origenLimite(p: PlausibilidadFrio): String = p.banda?.etiquetaOrigen ?: "Típico (editable)"

    private fun fraseArranque(a: AnalisisArranqueFrio): String? {
        val r = a.arranque
        if (!r.arranco) return if (r.intentos == 0) "El motor no giró durante el paso de arranque." else "No quedó encendido en ${intentos(r.intentos)}."
        val intento = if (r.intentos == 1) "al primer intento" else "al ${r.intentos}.º intento"
        val apagones = if (a.apagones > 0) " Después se apagó ${veces(a.apagones)}." else ""
        return "Arrancó $intento, ${seg(r.tiempoS ?: 0.0)} s después de pedirlo.$apagones"
    }

    private fun fraseCalentamiento(a: AnalisisArranqueFrio): String? {
        a.tiempoHasta60S?.let { return "Llegó a ${grados(AnalizadorArranqueFrio.CALENTADO_C)} en ${duracion(it)}." }
        val maximo = a.ectMaxC ?: return null
        return if (a.arranque.arranco) "Llegó hasta ${grados(maximo)} sin alcanzar ${grados(AnalizadorArranqueFrio.CALENTADO_C)}." else null
    }

    private fun frasePerfil(p: PerfilMinimoRapido?): String? {
        if (p?.baja == null) return null
        val verbo = if (p.rpmFin <= p.rpmInicio) "bajó" else "subió"
        val cierre = if (p.baja) "como se espera" else "y debería bajar a medida que calienta"
        return "El mínimo rápido $verbo de ${rpm(p.rpmInicio)} a ${rpm(p.rpmFin)} rpm mientras el motor pasaba de " +
            "${grados(p.ectInicioC)} a ${grados(p.ectFinC)}, $cierre."
    }

    private fun fraseSaltos(a: AnalisisArranqueFrio): String? {
        val mayor = a.saltosEct.maxByOrNull { abs(it.delta) } ?: return null
        return "La temperatura del motor saltó ${grados(abs(mayor.delta))} en ${seg(mayor.duracionMs / 1_000.0)} s: " +
            "compatible con P0119 (señal intermitente del sensor de temperatura del motor) o con un falso contacto en su conector."
    }

    // ── Hallazgos y siguiente paso ──────────────────────────────────────────

    private fun hallazgos(a: AnalisisArranqueFrio): List<String> = buildList {
        val p = a.plausibilidad
        if (p.motorBajoAire) add("El motor marca ${grados(-(p.diferenciaC ?: 0.0))} menos que el aire tras el reposo: revisa los sensores ECT e IAT")
        if (p.aireLejosDelAmbiente) {
            add("El aire de admisión (${grados(p.iatC ?: 0.0)}) difiere del ambiente (${grados(p.ambienteC ?: 0.0)}) con el motor frío: revisa el IAT")
        }
        if (a.arranque.arranco && a.arranque.intentos > 1) add("Arrancó al ${a.arranque.intentos}.º intento")
        if (a.apagones > 0) add("Se apagó ${veces(a.apagones)} después de arrancar")
        a.saltosEct.forEach { add("La temperatura del motor saltó ${grados(abs(it.delta))} en ${seg(it.duracionMs / 1_000.0)} s") }
        if (a.ectBajaMientrasCalienta) add("La temperatura del motor bajó ${grados(a.caidaEctMaxC)} mientras calentaba")
        if (a.perfil?.baja == false) {
            add("El mínimo rápido subió de ${rpm(a.perfil.rpmInicio)} a ${rpm(a.perfil.rpmFin)} rpm mientras calentaba")
        }
    }

    private fun siguientePaso(a: AnalisisArranqueFrio): String? = when (a.patron) {
        PatronArranque.SIN_DATOS -> "Revisa que el adaptador siga conectado y repite la prueba"
        PatronArranque.NO_ARRANCO -> "Mide la batería con el multímetro durante el arranque (plantilla batería) y revisa combustible y chispa"
        PatronArranque.SE_APAGA -> "Corre la prueba de mínimo y retorno con el motor frío y revisa el control del mínimo"
        PatronArranque.SALTOS_ECT ->
            "Revisa el conector y el cableado del sensor de temperatura del motor y mídelo con el multímetro (plantilla ECT)"
        PatronArranque.VARIOS_INTENTOS ->
            "Repite la prueba tras el reposo de la noche; si sigue costando, mide la batería durante el arranque (plantilla batería)"
        PatronArranque.IRREGULAR -> siguienteIrregular(a)
        PatronArranque.NORMAL -> if (a.plausibilidad.condicion == CondicionMotor.TIBIO) REPETIR_EN_FRIO else null
    }

    private fun siguienteIrregular(a: AnalisisArranqueFrio): String = when {
        !a.plausibilidad.coherente -> "Mide los sensores de temperatura con el multímetro (plantillas ECT e IAT)"
        a.ectBajaMientrasCalienta -> "Mide el sensor de temperatura del motor con el multímetro (plantilla ECT)"
        else -> "Revisa el control del mínimo: el mínimo rápido debería bajar a medida que el motor calienta"
    }

    private const val REPETIR_EN_FRIO = "Para confirmarlo en frío, repite la prueba tras el reposo de la noche (≥ 6 h, típico)"

    // ── Medidas: una línea por cifra, para la pantalla y el informe ──────────

    fun medidas(a: AnalisisArranqueFrio): List<String> = listOfNotNull(
        medidaContacto(a.plausibilidad),
        medidaArranque(a.arranque),
        "Apagones después de arrancar: ${a.apagones}",
        a.perfil?.let { p ->
            "Mínimo rápido: ${rpm(p.rpmInicio)} rpm con ${grados(p.ectInicioC)} → ${rpm(p.rpmFin)} rpm con ${grados(p.ectFinC)}"
        },
        medidaCalentamiento(a),
        "Saltos de la temperatura del motor: " + if (a.saltosEct.isEmpty()) "ninguno" else a.saltosEct.size.toString(),
    )

    private fun medidaContacto(p: PlausibilidadFrio): String {
        val partes = listOfNotNull(
            p.ectC?.let { "motor ${grados(it)}" },
            p.iatC?.let { "aire ${grados(it)}" },
            p.ambienteC?.let { "ambiente ${grados(it)}" },
        )
        val condicion = when (p.condicion) {
            CondicionMotor.FRIO -> "frío"
            CondicionMotor.TIBIO -> "tibio"
            CondicionMotor.SIN_DATO -> "sin dato"
        }
        return "En contacto: ${partes.ifEmpty { listOf("sin temperaturas") }.joinToString(", ")} · $condicion"
    }

    private fun medidaArranque(r: ArranqueMedido): String = if (r.arranco) {
        "Arranque: ${intentos(r.intentos)}, ${seg(r.tiempoS ?: 0.0)} s hasta prender"
    } else {
        "Arranque: no prendió (${intentos(r.intentos)})"
    }

    private fun medidaCalentamiento(a: AnalisisArranqueFrio): String? {
        a.tiempoHasta60S?.let { return "Hasta ${grados(AnalizadorArranqueFrio.CALENTADO_C)}: ${duracion(it)}" }
        return a.ectMaxC?.let { "Hasta ${grados(AnalizadorArranqueFrio.CALENTADO_C)}: no llegó (máximo ${grados(it)})" }
    }

    // ── Formato ─────────────────────────────────────────────────────────────

    private fun duracion(s: Double): String =
        if (s < 120) "${seg(s)} s" else "${FormatoTaller.numero(s / 60, 1)} min"

    private fun veces(n: Int) = if (n == 1) "1 vez" else "$n veces"

    private fun intentos(n: Int) = if (n == 1) "1 intento" else "$n intentos"

    private fun grados(x: Double) = "${FormatoTaller.numero(x, 0)} °C"

    private fun rpm(x: Double) = FormatoTaller.numero(x, 0)

    private fun seg(x: Double) = FormatoTaller.compacto(FormatoTaller.redondear(x, 1))
}
