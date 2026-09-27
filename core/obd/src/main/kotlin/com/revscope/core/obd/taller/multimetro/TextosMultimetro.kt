package com.revscope.core.obd.taller.multimetro

internal object TextosMultimetro {

    // ── Sensores con referencia, masa y señal ───────────────────────────────

    fun tituloSensor(plantilla: PlantillaCableado, celdas: List<EvaluacionCelda>): String {
        val partes = listOfNotNull(
            alimentacion(estado(celdas, FuncionCable.REF_5V), estado(celdas, FuncionCable.MASA)),
            senal(plantilla, celdas.filter { it.funcion == FuncionCable.SENAL }),
        )
        return partes.joinToString("; ").ifEmpty { "Sin medidas todavía" }.capitalizar()
    }

    fun interpretacionSensor(celdas: List<EvaluacionCelda>): String? {
        val ref = estado(celdas, FuncionCable.REF_5V)
        val masa = estado(celdas, FuncionCable.MASA)
        val senal = estado(celdas, FuncionCable.SENAL)
        return when {
            ref == EstadoGrupo.BAJO -> REFERENCIA_BAJA
            masa == EstadoGrupo.ALTO -> MASA_CON_CAIDA
            senal == EstadoGrupo.BAJO -> senalBaja(ref, masa)
            senal == EstadoGrupo.ALTO -> SENAL_ALTA
            todoCorrecto(ref, masa, senal) -> "El circuito del sensor responde dentro de las bandas típicas."
            else -> null
        }
    }

    private fun estado(celdas: List<EvaluacionCelda>, funcion: FuncionCable): EstadoGrupo =
        VeredictoMultimetro.grupo(celdas.filter { it.funcion == funcion })

    private fun alimentacion(ref: EstadoGrupo, masa: EstadoGrupo): String? {
        if (ref == EstadoGrupo.CORRECTO && masa == EstadoGrupo.CORRECTO) return "referencia y masa correctas"
        val partes = listOfNotNull(textoReferencia(ref), textoMasa(masa))
        return partes.takeIf { it.isNotEmpty() }?.joinToString(" y ")
    }

    private fun textoReferencia(e: EstadoGrupo): String? = when (e) {
        EstadoGrupo.CORRECTO -> "referencia correcta"
        EstadoGrupo.BAJO -> "referencia de 5 V baja"
        EstadoGrupo.ALTO -> "referencia de 5 V alta"
        EstadoGrupo.MIXTO -> "referencia inestable"
        EstadoGrupo.SIN_MEDIR, EstadoGrupo.SIN_REFERENCIA -> null
    }

    private fun textoMasa(e: EstadoGrupo): String? = when (e) {
        EstadoGrupo.CORRECTO -> "masa correcta"
        EstadoGrupo.ALTO -> "masa con caída de voltaje"
        EstadoGrupo.BAJO -> "masa por debajo de 0 V (revisa las puntas)"
        EstadoGrupo.MIXTO -> "masa inestable"
        EstadoGrupo.SIN_MEDIR, EstadoGrupo.SIN_REFERENCIA -> null
    }

    private fun senal(plantilla: PlantillaCableado, celdas: List<EvaluacionCelda>): String? = when (VeredictoMultimetro.grupo(celdas)) {
        EstadoGrupo.SIN_MEDIR -> null
        EstadoGrupo.SIN_REFERENCIA -> "señal sin valor esperado: compárala con el manual"
        EstadoGrupo.CORRECTO -> "señal dentro de las bandas"
        EstadoGrupo.BAJO -> "señal baja ${donde(plantilla, celdas, EstadoCelda.BAJO)}"
        EstadoGrupo.ALTO -> "señal alta ${donde(plantilla, celdas, EstadoCelda.ALTO)}"
        EstadoGrupo.MIXTO ->
            "señal baja ${donde(plantilla, celdas, EstadoCelda.BAJO)} y alta ${donde(plantilla, celdas, EstadoCelda.ALTO)}"
    }

    // «En todo el recorrido» solo si se midieron todas las posiciones y todas salieron del mismo lado.
    private fun donde(plantilla: PlantillaCableado, celdas: List<EvaluacionCelda>, estado: EstadoCelda): String {
        val fuera = celdas.filter { it.estado == estado }
        val posiciones = plantilla.cable(FuncionCable.SENAL)?.esperadoPorCondicion?.keys.orEmpty()
        val todas = posiciones.size > 1 && fuera.map { it.condicion }.toSet() == posiciones
        if (todas) return "en todo el recorrido"
        return "(" + enumerar(fuera.map { etiquetaCondicion(plantilla, it.condicion) }) + ")"
    }

    private fun senalBaja(ref: EstadoGrupo, masa: EstadoGrupo): String =
        if (ref == EstadoGrupo.CORRECTO && masa == EstadoGrupo.CORRECTO) SENSOR_O_CONTACTO
        else "Mide también la referencia y la masa: con ellas correctas, ${SENSOR_O_CONTACTO.replaceFirstChar(Char::lowercase)}"

    private fun todoCorrecto(vararg grupos: EstadoGrupo): Boolean =
        grupos.any { it == EstadoGrupo.CORRECTO } && grupos.all { it == EstadoGrupo.CORRECTO || it == EstadoGrupo.SIN_MEDIR }

    // ── Plantillas sin señal: inyector y batería ────────────────────────────

    fun tituloGeneral(plantilla: PlantillaCableado, celdas: List<EvaluacionCelda>): String {
        val fuera = celdas.filter { it.fueraDeBanda }
        return when {
            fuera.isNotEmpty() -> "Fuera de banda: " + enumerar(fuera.map { celdaCorta(plantilla, it) })
            celdas.any { it.estado == EstadoCelda.DENTRO } -> "Todo lo medido está dentro de las bandas"
            celdas.isEmpty() -> "Sin medidas todavía"
            else -> "Sin valores esperados para comparar"
        }
    }

    fun interpretacionGeneral(plantilla: PlantillaCableado, celdas: List<EvaluacionCelda>): String? =
        celdas.filter { it.fueraDeBanda }
            .mapNotNull { PISTAS[Triple(plantilla.sensor, it.condicion, it.estado)] }
            .distinct()
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" ")

    private fun celdaCorta(plantilla: PlantillaCableado, c: EvaluacionCelda): String {
        val cable = plantilla.cable(c.funcion)?.etiqueta ?: c.funcion.etiqueta
        val lado = if (c.estado == EstadoCelda.BAJO) "bajo" else "alto"
        return "${cable.lowercase()}, ${etiquetaCondicion(plantilla, c.condicion)} ($lado)"
    }

    // ── Comunes ─────────────────────────────────────────────────────────────

    fun etiquetaCondicion(plantilla: PlantillaCableado, clave: String): String =
        plantilla.condicion(clave)?.etiqueta?.lowercase() ?: clave

    fun enumerar(partes: List<String>): String = when (partes.size) {
        0 -> ""
        1 -> partes.single()
        else -> partes.dropLast(1).joinToString(", ") + " y " + partes.last()
    }

    private fun String.capitalizar(): String = replaceFirstChar(Char::uppercase)

    private const val SENSOR_O_CONTACTO =
        "Apunta al sensor (dañado o no equivalente al original) o al contacto entre el sensor y el conector."
    private const val REFERENCIA_BAJA =
        "Con la referencia baja, todo sensor que la comparte lee bajo (el MAP suele compartirla con el TPS): revisa " +
            "la alimentación de 5 V que sale de la ECU antes de cambiar el sensor."
    private const val MASA_CON_CAIDA =
        "Una masa con caída de voltaje se suma a la señal: revisa el terminal de masa del sensor y su camino hasta la ECU."
    private const val SENAL_ALTA =
        "Apunta al sensor o a un cruce de la señal con la referencia de 5 V."

    private val PISTAS = mapOf(
        Triple(SensorMultimetro.BATERIA, CondicionesMultimetro.CONTACTO, EstadoCelda.BAJO) to
            "Batería baja en reposo: cárgala y vuelve a medir antes de juzgar el arranque y la carga.",
        Triple(SensorMultimetro.BATERIA, CondicionesMultimetro.ARRANQUE, EstadoCelda.BAJO) to
            "Caída fuerte al arrancar: compatible con una batería débil o con bornes flojos o sulfatados.",
        Triple(SensorMultimetro.BATERIA, CondicionesMultimetro.CARGA, EstadoCelda.BAJO) to
            "No carga lo suficiente: compatible con un estator o un regulador/rectificador que no entrega.",
        Triple(SensorMultimetro.BATERIA, CondicionesMultimetro.CARGA, EstadoCelda.ALTO) to
            "Carga de más: compatible con un regulador/rectificador que no limita la carga.",
        Triple(SensorMultimetro.INYECTOR, CondicionesMultimetro.DESCONECTADO, EstadoCelda.BAJO) to
            "Resistencia baja: compatible con una bobina en corto o con un inyector de baja impedancia (compara con el manual).",
        Triple(SensorMultimetro.INYECTOR, CondicionesMultimetro.DESCONECTADO, EstadoCelda.ALTO) to
            "Resistencia alta: compatible con una bobina abierta o un terminal sulfatado.",
    )
}
