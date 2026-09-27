package com.revscope.core.obd.mcp

import com.revscope.core.obd.taller.multimetro.AsistenteMultimetro
import com.revscope.core.obd.taller.multimetro.CeldaRegistrada
import com.revscope.core.obd.taller.multimetro.FuncionCable
import com.revscope.core.obd.taller.multimetro.LecturaMultimetro
import com.revscope.core.obd.taller.multimetro.MedicionJson
import com.revscope.core.obd.taller.multimetro.SensorMultimetro
import com.revscope.core.obd.taller.multimetro.TextoCelda
import com.revscope.core.obd.taller.sesion.OrigenEvento
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

/** Anota una medición del multímetro en la sesión abierta y devuelve el veredicto de la celda y el acumulado. */
class RegistrarMedicionTool @Inject constructor(private val asistente: AsistenteMultimetro) : McpTool {

    override val name = "registrar_medicion"
    override val description =
        "Anota en la sesión de taller abierta una medición del multímetro en un cable de un sensor (plantilla TPS, " +
            "MAP, ECT, IAT, INYECTOR o BATERIA) y devuelve el veredicto de esa celda contra su banda (típica, del " +
            "modelo o editada, con su origen), el veredicto acumulado con todo lo medido de esa plantilla en la " +
            "sesión (p. ej. «Referencia y masa correctas; señal baja en todo el recorrido») y, si hay un barrido del " +
            "TPS en la sesión, la comparación con lo que recibió la ECU (±0,1 V típico en cerrado y a fondo). Usa los " +
            "colores del cableado del modelo si están guardados. Requiere una sesión abierta"
    override val inputSchema: JSONObject = McpSchemas.objeto(
        "plantilla" to McpSchemas.enumString(SensorMultimetro.entries.map { it.name }, "Sensor medido"),
        "funcion" to McpSchemas.enumString(
            FuncionCable.entries.map { it.name },
            "Cable: REF_5V, MASA, SENAL, ALIMENTACION_12V (bornes de la batería) u OTRO (bobina del inyector)",
        ),
        "condicion" to McpSchemas.texto(
            "Condición: CERRADO, MEDIO o FONDO (TPS); CONTACTO (MAP, ECT, IAT); DESCONECTADO (inyector); " +
                "CONTACTO, ARRANQUE o CARGA (batería)",
        ),
        "valor" to McpSchemas.numero("Lo que marcó el multímetro"),
        "unidad" to McpSchemas.texto("V (por defecto) u ohm para el inyector"),
        requeridos = listOf("plantilla", "funcion", "condicion", "valor"),
    )
    override val permiso = McpPermiso.CONTROL

    override suspend fun call(arguments: JSONObject): String {
        val sensor = SensorMultimetro.entries.firstOrNull { it.name == arguments.optString("plantilla").uppercase() }
            ?: return error("plantilla debe ser una de ${SensorMultimetro.entries.joinToString { it.name }}")
        val lectura = lectura(arguments, sensor) ?: return error("Faltan funcion, condicion o un valor numérico")
        return asistente.registrarCelda(sensor, lectura, OrigenEvento.MCP).fold(
            onSuccess = ::respuesta,
            onFailure = { error(it.message ?: "No se pudo registrar la medición") },
        )
    }

    private fun lectura(arguments: JSONObject, sensor: SensorMultimetro): LecturaMultimetro? {
        val funcion = FuncionCable.entries.firstOrNull { it.name == arguments.optString("funcion").uppercase() } ?: return null
        val condicion = arguments.optString("condicion").trim().uppercase().takeIf { it.isNotEmpty() } ?: return null
        val valor = arguments.optDouble("valor").takeUnless(Double::isNaN) ?: return null
        return LecturaMultimetro(funcion, condicion, valor, unidad(arguments.optString("unidad"), sensor))
    }

    private fun unidad(texto: String, sensor: SensorMultimetro): String = when (texto.trim().lowercase()) {
        "" -> if (sensor == SensorMultimetro.INYECTOR) OHM else "V"
        "ohm", "ohmios", "ω", "Ω" -> OHM
        "v", "voltios" -> "V"
        else -> texto.trim()
    }

    private fun respuesta(r: CeldaRegistrada): String = JSONObject()
        .put("sesionId", r.sesionId)
        .put("eventoId", r.eventoId)
        .put(
            "plantilla",
            JSONObject()
                .put("sensor", r.plantilla.sensor.name)
                .put("titulo", r.plantilla.titulo)
                .put("fuenteColores", r.plantilla.fuenteColores ?: "Genérica típica, sin colores")
                .put("dondeMedir", r.plantilla.dondeMedir),
        )
        .put("celda", MedicionJson.celda(r.plantilla, r.celda).put("texto", TextoCelda.completo(r.plantilla, r.celda)))
        .put("acumulado", MedicionJson.veredicto(r.acumulado))
        .put("pendientes", JSONArray(r.pendientes.map { (f, c) -> JSONObject().put("funcion", f.name).put("condicion", c) }))
        .toString()

    private fun error(mensaje: String): String = JSONObject().put("error", mensaje).toString()

    private companion object {
        const val OHM = "Ω"
    }
}
