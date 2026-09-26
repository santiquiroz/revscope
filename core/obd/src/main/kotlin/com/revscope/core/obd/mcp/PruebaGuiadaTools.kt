package com.revscope.core.obd.mcp

import com.revscope.core.obd.taller.pruebas.CatalogoPruebas
import com.revscope.core.obd.taller.pruebas.ControladorPruebaGuiada
import com.revscope.core.obd.taller.pruebas.EnlacePrueba
import com.revscope.core.obd.taller.pruebas.EstadoPrueba
import com.revscope.core.obd.taller.pruebas.FasePaso
import com.revscope.core.obd.taller.pruebas.OpcionesPrueba
import com.revscope.core.obd.taller.pruebas.ReferenciaVoltaje
import com.revscope.core.obd.taller.pruebas.TipoPrueba
import com.revscope.core.obd.taller.sesion.OrigenEvento
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

private fun errorJson(mensaje: String): String = JSONObject().put("error", mensaje).toString()

private fun disponibles(): List<String> = CatalogoPruebas.disponibles.map { it.name }

// Estado de la prueba con el valor en vivo del PID principal mientras corre (la captura lo publica al flujo del enlace).
private fun estadoJson(controlador: ControladorPruebaGuiada, enlace: EnlacePrueba, estado: EstadoPrueba): JSONObject {
    val principal = (estado as? EstadoPrueba.EnPaso)?.let { CatalogoPruebas.definicion(it.tipo)?.pids?.firstOrNull() }
    return PruebaGuiadaJson.estado(estado, controlador.idPrueba, principal?.let { enlace.lecturas()[it] })
        .put("mensaje", mensaje(estado))
}

private fun mensaje(estado: EstadoPrueba): String = when (estado) {
    EstadoPrueba.Inactiva -> "No hay prueba guiada en curso: arráncala con iniciar_prueba_guiada"
    is EstadoPrueba.Verificando -> if (estado.listas) {
        "Precondiciones listas"
    } else {
        "No se cumplen las precondiciones: " +
            estado.precondiciones.filterNot { it.cumple }.joinToString("; ") { "${it.texto} → ${it.queHacer ?: "revísalo"}" }
    }
    is EstadoPrueba.EnPaso -> mensajePaso(estado)
    is EstadoPrueba.Analizando -> "Analizando la prueba"
    is EstadoPrueba.Terminada -> "${estado.resultado.titulo}. " +
        if (estado.eventoId != null) "Quedó en la sesión de taller abierta" else "No hay sesión abierta: no quedó guardada"
    is EstadoPrueba.Cancelada -> "Prueba cancelada: ${estado.motivo}"
    is EstadoPrueba.Fallida -> "La prueba no terminó: ${estado.motivo}" +
        if (estado.reintentable) ". Se puede repetir con iniciar_prueba_guiada" else ""
}

private fun mensajePaso(e: EstadoPrueba.EnPaso): String {
    val cabeza = "Paso ${e.indice + 1} de ${e.total} (${e.paso.titulo}): ${e.paso.instruccion}"
    return when (e.fase) {
        FasePaso.POSICIONANDO -> "$cabeza. Cuando esté en posición, llama avanzar_prueba_guiada (equivale a «Listo»)"
        FasePaso.SOSTENIENDO -> "$cabeza. Sosteniendo; faltan ${(e.restanteMs ?: 0) / 1_000} s"
        FasePaso.GRABANDO -> "$cabeza. Grabando; faltan ${(e.restanteMs ?: 0) / 1_000} s"
    }
}

/** Arranca una prueba guiada del Taller; el teléfono guía por voz y el resultado queda en la sesión abierta. */
class IniciarPruebaGuiadaTool @Inject constructor(
    private val controlador: ControladorPruebaGuiada,
    private val enlace: EnlacePrueba,
) : McpTool {

    override val name = "iniciar_prueba_guiada"
    override val description =
        "Arranca una prueba guiada del Taller sobre la captura rápida. TPS_BARRIDO: barrido del acelerador con el " +
            "motor apagado y el contacto puesto (cerrado, medio, a fondo, cerrado otra vez y un barrido lento); da el " +
            "patrón de la señal (p. ej. «señal baja en todo el recorrido», compatible con P0122) contra bandas típicas " +
            "editables. Primero verifica adaptador, motor apagado, moto detenida y que la ECU reporte el PID: si algo " +
            "falla devuelve qué y qué hacer. El teléfono guía cada paso por voz; los pasos sostenidos esperan a " +
            "avanzar_prueba_guiada («Listo»). Sigue el avance con get_prueba_guiada. Una prueba a la vez y no con una " +
            "captura rápida manual activa. El resultado queda en la sesión de taller abierta"
    override val inputSchema: JSONObject = McpSchemas.objeto(
        "tipo" to McpSchemas.enumString(disponibles(), "Prueba a correr"),
        "voz" to McpSchemas.booleano("Guiar cada paso por voz en el teléfono (por defecto true)"),
        "vref_v" to McpSchemas.numero(
            "Referencia de 5 V medida con el multímetro, para convertir el % del TPS a voltios " +
                "(${ReferenciaVoltaje.MIN_V}-${ReferenciaVoltaje.MAX_V}; por defecto 5,0 V típico)",
        ),
        requeridos = listOf("tipo"),
    )
    override val permiso = McpPermiso.CONTROL

    override suspend fun call(arguments: JSONObject): String {
        val tipo = TipoPrueba.entries.firstOrNull { it.name == arguments.optString("tipo") }
            ?.takeIf { it.name in disponibles() }
            ?: return errorJson("tipo inválido; usa ${disponibles()}")
        val vref = referencia(arguments) ?: return errorJson(
            "vref_v debe estar entre ${ReferenciaVoltaje.MIN_V} y ${ReferenciaVoltaje.MAX_V} V",
        )
        val opciones = OpcionesPrueba(voz = arguments.optBoolean("voz", true), origen = OrigenEvento.MCP, vref = vref)
        return controlador.iniciar(tipo, opciones).fold(
            onSuccess = { estadoJson(controlador, enlace, it).put("iniciada", it is EstadoPrueba.EnPaso).toString() },
            onFailure = { errorJson(it.message ?: "no se pudo iniciar la prueba") },
        )
    }

    private fun referencia(arguments: JSONObject): ReferenciaVoltaje? {
        if (!arguments.has("vref_v")) return ReferenciaVoltaje.TIPICA
        val voltios = arguments.optDouble("vref_v", Double.NaN)
        if (voltios.isNaN() || voltios !in ReferenciaVoltaje.MIN_V..ReferenciaVoltaje.MAX_V) return null
        return ReferenciaVoltaje.editada(voltios)
    }
}

/** «Listo» remoto: arranca la cuenta del paso sostenido o termina el paso que graba hasta que se diga. */
class AvanzarPruebaGuiadaTool @Inject constructor(
    private val controlador: ControladorPruebaGuiada,
    private val enlace: EnlacePrueba,
) : McpTool {

    override val name = "avanzar_prueba_guiada"
    override val description =
        "Equivale a tocar «Listo» en el paso actual de la prueba guiada: úsalo cuando el acelerador ya esté en la " +
            "posición que pide el paso; arranca su cuenta regresiva. Los pasos que graban solos no lo necesitan"
    override val inputSchema: JSONObject = McpSchemas.noArguments()
    override val permiso = McpPermiso.CONTROL

    override suspend fun call(arguments: JSONObject): String = controlador.avanzar().fold(
        onSuccess = { estadoJson(controlador, enlace, it).toString() },
        onFailure = { errorJson(it.message ?: "no se pudo avanzar") },
    )
}

/** Cancela la prueba guiada en curso y vuelve al muestreo normal; nada queda en la sesión. */
class CancelarPruebaGuiadaTool @Inject constructor(
    private val controlador: ControladorPruebaGuiada,
    private val enlace: EnlacePrueba,
) : McpTool {

    override val name = "cancelar_prueba_guiada"
    override val description =
        "Cancela la prueba guiada en curso: detiene su captura rápida, vuelve al muestreo normal y no anota nada " +
            "en la sesión"
    override val inputSchema: JSONObject = McpSchemas.noArguments()
    override val permiso = McpPermiso.CONTROL

    override suspend fun call(arguments: JSONObject): String {
        if (!controlador.enCurso()) return errorJson("No hay una prueba guiada en curso")
        return estadoJson(controlador, enlace, controlador.cancelar(MOTIVO_MCP)).toString()
    }

    private companion object {
        const val MOTIVO_MCP = "Cancelada desde el MCP"
    }
}

/** Paso, fase y tiempo restante de la prueba guiada; al terminar, el veredicto con su interpretación y detalle. */
class GetPruebaGuiadaTool @Inject constructor(
    private val controlador: ControladorPruebaGuiada,
    private val enlace: EnlacePrueba,
) : McpTool {

    override val name = "get_prueba_guiada"
    override val description =
        "Estado de la prueba guiada del Taller: precondiciones, paso actual (instrucción, fase y ms restantes) con " +
            "el valor en vivo del sensor, y al terminar el veredicto (patrón, interpretación «compatible con…», " +
            "siguiente paso, tabla por paso en % y V, comprobaciones con su banda y origen, irregularidades y tasa)"
    override val inputSchema: JSONObject = McpSchemas.noArguments()

    override suspend fun call(arguments: JSONObject): String =
        estadoJson(controlador, enlace, controlador.estado.value).put("disponibles", JSONArray(disponibles())).toString()
}
