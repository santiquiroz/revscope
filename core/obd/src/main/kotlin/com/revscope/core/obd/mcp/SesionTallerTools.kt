package com.revscope.core.obd.mcp

import com.revscope.core.data.db.entities.VehicleType
import com.revscope.core.obd.taller.sesion.AnalizadorSesion
import com.revscope.core.obd.taller.sesion.OrigenEvento
import com.revscope.core.obd.taller.sesion.RegistroTaller
import com.revscope.core.obd.taller.sesion.SesionTaller
import com.revscope.core.obd.taller.sesion.Sintoma
import com.revscope.core.obd.taller.sesion.SolicitudSesion
import com.revscope.core.obd.taller.sesion.TallerRepository
import com.revscope.core.obd.taller.sesion.VehiculoActivo
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

private const val MAX_TEXTO = 2_000

private fun errorJson(mensaje: String): String = JSONObject().put("error", mensaje).toString()

/** Sesión de diagnóstico del Taller: resumen, comparación con el chequeo base, pruebas sugeridas y eventos paginados. */
class GetSesionTallerTool @Inject constructor(
    private val repositorio: TallerRepository,
    private val vehiculo: VehiculoActivo,
    private val analizador: AnalizadorSesion,
) : McpTool {

    override val name = "get_sesion_taller"
    override val description =
        "Sesión de diagnóstico del Taller: la abierta del vehículo activo o la de sesion_id. Devuelve síntomas, " +
            "notas, los códigos actuales, la comparación con el chequeo de salud anterior a la sesión, las pruebas " +
            "sugeridas y la línea de tiempo (lecturas de códigos, borrados, chequeos, capturas y notas) paginada. " +
            "No necesita adaptador"
    override val inputSchema: JSONObject = McpSchemas.objeto(
        "sesion_id" to McpSchemas.entero("Id de la sesión (por defecto, la abierta del vehículo activo)"),
        "desde_evento" to McpSchemas.entero("Posición del primer evento a devolver (0 = el primero)"),
        "max_eventos" to McpSchemas.entero("Cuántos eventos devolver (por defecto $POR_PAGINA, máximo $MAX_POR_PAGINA)"),
        "incluir_payload" to McpSchemas.booleano(
            "Incluir los datos completos de cada evento: crudo del ECU, métricas, series (máximo $MAX_CON_PAYLOAD por página)",
        ),
    )

    override suspend fun call(arguments: JSONObject): String {
        val actual = vehiculo.actual()
        val sesion = if (arguments.has("sesion_id")) {
            val id = arguments.optLong("sesion_id")
            repositorio.sesion(id) ?: return errorJson("No existe la sesión $id")
        } else {
            actual ?: return errorJson("Sin vehículo activo: elige uno en la app")
            repositorio.sesionAbierta(actual.id) ?: return sinSesion(actual.nombre)
        }
        val eventos = repositorio.eventos(sesion.id)
        val analisis = analizador.analizar(sesion, eventos, actual?.tipo ?: VehicleType.MOTORCYCLE)
        val pagina = Pagina.de(arguments, eventos.size)
        return JSONObject()
            .put("sesion", SesionTallerJson.sesion(sesion, eventos.size))
            .put("analisis", SesionTallerJson.analisis(analisis))
            .put(
                "eventos",
                JSONArray(eventos.drop(pagina.desde).take(pagina.maximo).map { SesionTallerJson.evento(it, pagina.conPayload) }),
            )
            .put("desdeEvento", pagina.desde)
            .put("siguienteDesde", pagina.siguiente ?: JSONObject.NULL)
            .toString()
    }

    private fun sinSesion(nombreVehiculo: String): String = JSONObject()
        .put("abierta", false)
        .put("vehiculo", nombreVehiculo)
        .put("mensaje", "No hay sesión de taller abierta para $nombreVehiculo: ábrela con iniciar_sesion_taller")
        .toString()

    private data class Pagina(val desde: Int, val maximo: Int, val conPayload: Boolean, val siguiente: Int?) {
        companion object {
            fun de(arguments: JSONObject, total: Int): Pagina {
                val conPayload = arguments.optBoolean("incluir_payload", false)
                val tope = if (conPayload) MAX_CON_PAYLOAD else MAX_POR_PAGINA
                val desde = arguments.optInt("desde_evento", 0).coerceIn(0, total)
                val maximo = arguments.optInt("max_eventos", POR_PAGINA).coerceIn(1, tope)
                return Pagina(desde, maximo, conPayload, (desde + maximo).takeIf { it < total })
            }
        }
    }

    private companion object {
        const val POR_PAGINA = 20
        const val MAX_POR_PAGINA = 50
        const val MAX_CON_PAYLOAD = 5
    }
}

/** Abre una sesión de diagnóstico para el vehículo activo; desde ahí las lecturas quedan en su línea de tiempo. */
class IniciarSesionTallerTool @Inject constructor(
    private val registro: RegistroTaller,
    private val repositorio: TallerRepository,
    private val vehiculo: VehiculoActivo,
    private val analizador: AnalizadorSesion,
) : McpTool {

    override val name = "iniciar_sesion_taller"
    override val description =
        "Abre una sesión de diagnóstico del Taller para el vehículo activo con sus síntomas y notas. Desde ese " +
            "momento cada lectura de códigos (app o get_dtc), borrado, chequeo de salud y captura rápida queda en su " +
            "línea de tiempo. Toma como base el último chequeo de salud anterior. Si ya hay una abierta, exige " +
            "cerrar_anterior=true"
    override val inputSchema: JSONObject = McpSchemas.objeto(
        "sintomas" to McpSchemas.arrayDeEnum(Sintoma.entries.map { it.name }, sintomasDescripcion()),
        "sintomas_texto" to McpSchemas.texto("Síntomas en palabras del dueño (máximo $MAX_TEXTO caracteres)"),
        "notas" to McpSchemas.texto("Notas iniciales, p. ej. repuestos cambiados (máximo $MAX_TEXTO caracteres)"),
        "titulo" to McpSchemas.texto("Título corto (por defecto, el primer síntoma)"),
        "cerrar_anterior" to McpSchemas.booleano("Cerrar la sesión que siga abierta en este vehículo y abrir esta"),
    )
    override val permiso = McpPermiso.CONTROL

    override suspend fun call(arguments: JSONObject): String {
        val actual = vehiculo.actual() ?: return errorJson("Sin vehículo activo: elige uno en la app")
        val abierta = repositorio.sesionAbierta(actual.id)
        if (abierta != null && !arguments.optBoolean("cerrar_anterior", false)) return yaAbierta(abierta)
        textoDemasiadoLargo(arguments)?.let { return errorJson(it) }
        val pedidos = McpSchemas.strings(arguments.optJSONArray("sintomas"))
        val sintomas = Sintoma.entries.filter { it.name in pedidos }.toSet()
        val solicitud = SolicitudSesion(
            sintomas = sintomas,
            sintomasTexto = arguments.optString("sintomas_texto"),
            notas = arguments.optString("notas"),
            titulo = arguments.optString("titulo").takeIf { it.isNotBlank() },
            origen = OrigenEvento.MCP,
        )
        val sesion = registro.abrirSesion(solicitud).getOrElse { return errorJson(it.message ?: "no se pudo abrir la sesión") }
        val analisis = analizador.analizar(sesion, repositorio.eventos(sesion.id), actual.tipo)
        return JSONObject()
            .put("sesion", SesionTallerJson.sesion(sesion))
            .put("sesionCerrada", abierta?.id ?: JSONObject.NULL)
            .put("sintomasIgnorados", JSONArray(pedidos.filter { p -> sintomas.none { it.name == p } }))
            .put("analisis", SesionTallerJson.analisis(analisis))
            .put("mensaje", "Sesión abierta: las lecturas de códigos, chequeos y capturas quedan en su línea de tiempo")
            .toString()
    }

    private fun yaAbierta(abierta: SesionTaller): String = JSONObject()
        .put(
            "error",
            "Ya hay una sesión abierta (#${abierta.id} «${abierta.titulo}»). Pasa cerrar_anterior=true para cerrarla " +
                "y abrir otra, o sigue en ella con agregar_nota_taller",
        )
        .put("sesionAbierta", SesionTallerJson.sesion(abierta))
        .toString()

    private fun textoDemasiadoLargo(arguments: JSONObject): String? =
        listOf("sintomas_texto", "notas", "titulo").firstOrNull { arguments.optString(it).length > MAX_TEXTO }
            ?.let { "$it supera $MAX_TEXTO caracteres" }

    private fun sintomasDescripcion(): String =
        "Síntomas: " + Sintoma.entries.joinToString("; ") { "${it.name} = ${it.etiqueta}" }
}

/** Agrega una nota con hora a la sesión abierta del vehículo activo. */
class AgregarNotaTallerTool @Inject constructor(
    private val registro: RegistroTaller,
) : McpTool {

    override val name = "agregar_nota_taller"
    override val description =
        "Agrega una nota a la línea de tiempo de la sesión de taller abierta (p. ej. una medición a mano, lo que " +
            "se vio al revisar el conector o la interpretación de una prueba). Requiere una sesión abierta"
    override val inputSchema: JSONObject = McpSchemas.objeto(
        "texto" to McpSchemas.texto("Texto de la nota (máximo $MAX_TEXTO caracteres)"),
        requeridos = listOf("texto"),
    )
    override val permiso = McpPermiso.CONTROL

    override suspend fun call(arguments: JSONObject): String {
        val texto = arguments.optString("texto").trim()
        if (texto.isEmpty()) return errorJson("La nota está vacía")
        if (texto.length > MAX_TEXTO) return errorJson("La nota supera $MAX_TEXTO caracteres")
        val sesion = registro.sesionAbierta()
            ?: return errorJson("No hay sesión de taller abierta: ábrela con iniciar_sesion_taller")
        val eventoId = registro.anotarNota(texto, OrigenEvento.MCP) ?: return errorJson("No se pudo guardar la nota")
        return JSONObject().put("sesionId", sesion.id).put("eventoId", eventoId).put("texto", texto).toString()
    }
}
