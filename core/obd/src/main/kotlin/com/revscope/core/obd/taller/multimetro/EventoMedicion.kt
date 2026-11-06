package com.revscope.core.obd.taller.multimetro

import com.revscope.core.common.i18n.LocaleNumbers
import com.revscope.core.common.i18n.StringResolver
import com.revscope.core.obd.R
import com.revscope.core.obd.taller.FormatoTaller
import com.revscope.core.obd.taller.sesion.EventoTaller
import com.revscope.core.obd.taller.sesion.NuevoEvento
import com.revscope.core.obd.taller.sesion.OrigenEvento
import com.revscope.core.obd.taller.sesion.TipoEvento
import com.revscope.core.obd.taller.sesion.Veredicto
import org.json.JSONArray
import org.json.JSONObject

// El payload usa la forma {"lecturas":[{"funcion","condicion","valor","unidad"}]} que ya lee VrefSesion.
object EventoMedicion {

    fun tabla(
        plantilla: PlantillaCableado,
        veredicto: VeredictoCombinado,
        origen: OrigenEvento = OrigenEvento.APP,
        resolver: StringResolver,
    ) = NuevoEvento(
        tipo = TipoEvento.MEDICION_MULTIMETRO,
        titulo = titulo(plantilla, resolver),
        resumen = listOfNotNull(veredicto.titulo, veredicto.ecu?.texto).joinToString(". "),
        veredicto = veredicto.veredicto,
        payload = base(plantilla, veredicto.celdas).put("veredicto", MedicionJson.veredicto(veredicto)),
        origen = origen,
    )

    fun celda(
        plantilla: PlantillaCableado,
        celda: EvaluacionCelda,
        acumulado: VeredictoCombinado,
        origen: OrigenEvento,
        resolver: StringResolver,
    ) = NuevoEvento(
        tipo = TipoEvento.MEDICION_MULTIMETRO,
        titulo = titulo(plantilla, resolver),
        resumen = TextoCelda.completo(plantilla, celda, resolver),
        veredicto = veredictoDe(celda.estado),
        payload = base(plantilla, listOf(celda)).put("veredictoAcumulado", MedicionJson.veredicto(acumulado)),
        origen = origen,
    )

    fun veredictoDe(estado: EstadoCelda): Veredicto = when (estado) {
        EstadoCelda.DENTRO -> Veredicto.OK
        EstadoCelda.BAJO, EstadoCelda.ALTO -> Veredicto.FALLA
        EstadoCelda.SIN_REFERENCIA -> Veredicto.INFO
    }

    private fun titulo(plantilla: PlantillaCableado, resolver: StringResolver) =
        resolver.get(R.string.obd_multimeter_event_title, plantilla.titulo)

    private fun base(plantilla: PlantillaCableado, celdas: List<EvaluacionCelda>): JSONObject = JSONObject()
        .put("plantilla", plantilla.sensor.name)
        .put("titulo", plantilla.titulo)
        .put("unidad", plantilla.unidad)
        .put("fuenteColores", plantilla.fuenteColores ?: JSONObject.NULL)
        .put("lecturas", JSONArray(celdas.map { MedicionJson.celda(plantilla, it) }))
}

object TextoCelda {

    fun valor(valor: Double, unidad: String, resolver: StringResolver): String =
        "${LocaleNumbers.decimal(valor, if (unidad == "V") 2 else 1, resolver.locale)} $unidad" // i18n-ignore

    fun estado(estado: EstadoCelda, resolver: StringResolver): String = resolver.get(
        when (estado) {
            EstadoCelda.DENTRO -> R.string.obd_multimeter_state_in_range
            EstadoCelda.BAJO -> R.string.obd_multimeter_state_low
            EstadoCelda.ALTO -> R.string.obd_multimeter_state_high
            EstadoCelda.SIN_REFERENCIA -> R.string.obd_multimeter_state_no_reference
        },
    )

    fun cable(plantilla: PlantillaCableado, funcion: FuncionCable, resolver: StringResolver): String {
        val cable = plantilla.cable(funcion)
        val etiqueta = cable?.etiqueta ?: funcion.etiqueta(resolver)
        return cable?.color?.let { "$etiqueta ($it)" } ?: etiqueta
    }

    fun completo(plantilla: PlantillaCableado, c: EvaluacionCelda, resolver: StringResolver): String {
        val condicion = plantilla.condicion(c.condicion)?.etiqueta ?: c.condicion
        return "${cable(plantilla, c.funcion, resolver)} · $condicion: ${valor(c.valor, c.unidad, resolver)}, ${estado(c.estado, resolver)} (${c.referencia})"
    }
}

object MedicionJson {

    fun celda(plantilla: PlantillaCableado, c: EvaluacionCelda): JSONObject = JSONObject()
        .put("funcion", c.funcion.name)
        .put("color", plantilla.cable(c.funcion)?.color ?: JSONObject.NULL)
        .put("condicion", c.condicion)
        .put("valor", FormatoTaller.redondear(c.valor, 3))
        .put("unidad", c.unidad)
        .put("estado", c.estado.name)
        .put("referencia", c.referencia)

    fun veredicto(v: VeredictoCombinado): JSONObject = JSONObject()
        .put("veredicto", v.veredicto.name)
        .put("titulo", v.titulo)
        .put("interpretacion", v.interpretacion ?: JSONObject.NULL)
        .put("ecu", v.ecu?.let(::ecu) ?: JSONObject.NULL)

    private fun ecu(e: ComparacionEcu): JSONObject = JSONObject()
        .put("origen", e.origen)
        .put("resultado", e.resultado.name)
        .put("texto", e.texto)
        .put("toleranciaV", ComparadorEcu.TOLERANCIA_V)
        .put("filas", JSONArray(e.filas.map(::filaEcu)))

    private fun filaEcu(f: FilaComparacionEcu): JSONObject = JSONObject()
        .put("condicion", f.condicion)
        .put("multimetroV", FormatoTaller.redondear(f.multimetroV, 3))
        .put("ecuV", FormatoTaller.redondear(f.ecuV, 3))
        .put("diferenciaV", FormatoTaller.redondear(f.diferenciaV, 3))
        .put("coincide", f.coincide)
}

// Lo ya medido de una plantilla en la sesión: la medida más reciente de cada cable y condición gana.
object LecturasSesion {

    fun de(eventos: List<EventoTaller>, sensor: SensorMultimetro): List<LecturaMultimetro> = eventos
        .filter { it.tipo == TipoEvento.MEDICION_MULTIMETRO }
        .sortedWith(compareBy({ it.instante }, { it.id }))
        .flatMap { lecturasDe(it, sensor) }
        .associateBy { it.funcion to it.condicion }
        .values
        .toList()

    fun reemplazar(lecturas: List<LecturaMultimetro>, nueva: LecturaMultimetro): List<LecturaMultimetro> =
        lecturas.filterNot { it.funcion == nueva.funcion && it.condicion == nueva.condicion } + nueva

    private fun lecturasDe(evento: EventoTaller, sensor: SensorMultimetro): List<LecturaMultimetro> {
        val payload = runCatching { JSONObject(evento.payloadJson) }.getOrNull() ?: return emptyList()
        if (payload.optString("plantilla") != sensor.name) return emptyList()
        val lecturas = payload.optJSONArray("lecturas") ?: return emptyList()
        return (0 until lecturas.length()).mapNotNull { lecturas.optJSONObject(it)?.let(::lectura) }
    }

    private fun lectura(o: JSONObject): LecturaMultimetro? {
        val funcion = FuncionCable.entries.firstOrNull { it.name == o.optString("funcion") } ?: return null
        val valor = o.optDouble("valor").takeUnless(Double::isNaN) ?: return null
        val condicion = o.optString("condicion").takeIf(String::isNotBlank) ?: return null
        return LecturaMultimetro(funcion, condicion, valor, o.optString("unidad", "V"))
    }
}
