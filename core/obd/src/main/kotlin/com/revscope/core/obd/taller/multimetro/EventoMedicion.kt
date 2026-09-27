package com.revscope.core.obd.taller.multimetro

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

    fun tabla(plantilla: PlantillaCableado, veredicto: VeredictoCombinado, origen: OrigenEvento = OrigenEvento.APP) = NuevoEvento(
        tipo = TipoEvento.MEDICION_MULTIMETRO,
        titulo = titulo(plantilla),
        resumen = listOfNotNull(veredicto.titulo, veredicto.ecu?.texto).joinToString(". "),
        veredicto = veredicto.veredicto,
        payload = base(plantilla, veredicto.celdas).put("veredicto", MedicionJson.veredicto(veredicto)),
        origen = origen,
    )

    fun celda(plantilla: PlantillaCableado, celda: EvaluacionCelda, acumulado: VeredictoCombinado, origen: OrigenEvento) = NuevoEvento(
        tipo = TipoEvento.MEDICION_MULTIMETRO,
        titulo = titulo(plantilla),
        resumen = TextoCelda.completo(plantilla, celda),
        veredicto = veredictoDe(celda.estado),
        payload = base(plantilla, listOf(celda)).put("veredictoAcumulado", MedicionJson.veredicto(acumulado)),
        origen = origen,
    )

    fun veredictoDe(estado: EstadoCelda): Veredicto = when (estado) {
        EstadoCelda.DENTRO -> Veredicto.OK
        EstadoCelda.BAJO, EstadoCelda.ALTO -> Veredicto.FALLA
        EstadoCelda.SIN_REFERENCIA -> Veredicto.INFO
    }

    private fun titulo(plantilla: PlantillaCableado) = "Multímetro · ${plantilla.titulo}"

    private fun base(plantilla: PlantillaCableado, celdas: List<EvaluacionCelda>): JSONObject = JSONObject()
        .put("plantilla", plantilla.sensor.name)
        .put("titulo", plantilla.titulo)
        .put("unidad", plantilla.unidad)
        .put("fuenteColores", plantilla.fuenteColores ?: JSONObject.NULL)
        .put("lecturas", JSONArray(celdas.map { MedicionJson.celda(plantilla, it) }))
}

object TextoCelda {

    fun valor(valor: Double, unidad: String): String =
        "${FormatoTaller.numero(valor, if (unidad == "V") 2 else 1)} $unidad"

    fun estado(estado: EstadoCelda): String = when (estado) {
        EstadoCelda.DENTRO -> "dentro"
        EstadoCelda.BAJO -> "bajo"
        EstadoCelda.ALTO -> "alto"
        EstadoCelda.SIN_REFERENCIA -> "sin referencia"
    }

    fun cable(plantilla: PlantillaCableado, funcion: FuncionCable): String {
        val cable = plantilla.cable(funcion)
        val etiqueta = cable?.etiqueta ?: funcion.etiqueta
        return cable?.color?.let { "$etiqueta ($it)" } ?: etiqueta
    }

    fun completo(plantilla: PlantillaCableado, c: EvaluacionCelda): String {
        val condicion = plantilla.condicion(c.condicion)?.etiqueta ?: c.condicion
        return "${cable(plantilla, c.funcion)} · $condicion: ${valor(c.valor, c.unidad)}, ${estado(c.estado)} (${c.referencia})"
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
