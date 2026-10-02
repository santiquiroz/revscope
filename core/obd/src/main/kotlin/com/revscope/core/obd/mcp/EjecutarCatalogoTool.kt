package com.revscope.core.obd.mcp

import com.revscope.core.obd.catalogo.CatalogoPropietario
import com.revscope.core.obd.diagnostics.uds.ClaseComando
import com.revscope.core.obd.diagnostics.uds.ComandoHex
import com.revscope.core.obd.diagnostics.uds.PasoUds
import com.revscope.core.obd.diagnostics.uds.PasoUdsJson
import com.revscope.core.obd.diagnostics.uds.SecuenciaUds
import com.revscope.core.obd.mcp.escritura.EjecutorEscritura
import com.revscope.core.obd.mcp.escritura.EscrituraJson
import com.revscope.core.obd.mcp.escritura.SolicitudEscritura
import org.json.JSONObject
import javax.inject.Inject

class EjecutarCatalogoTool @Inject constructor(
    private val catalogo: CatalogoPropietario,
    private val ejecutor: EjecutorEscritura,
) : McpTool {
    override val name = "ejecutar_catalogo"
    override val description =
        "Ejecuta una escritura o rutina del catálogo de fabricante (get_catalogo) por su id. Revisa antes " +
            "verificado y riesgo: casi todo el catálogo viene de fuentes públicas sin probar en este vehículo. " +
            "Pide confirmación en el teléfono salvo bypass; exige vehículo detenido y, si el riesgo no es bajo, " +
            "motor apagado. Las lecturas van por leer_catalogo."
    override val inputSchema: JSONObject = McpSchemas.objeto(
        "id" to McpSchemas.texto("Identificador de la entrada del catálogo"),
        requeridos = listOf("id"),
    )
    override val permiso = McpPermiso.ESCRITURA

    override suspend fun call(arguments: JSONObject): String {
        val id = arguments.optString("id")
        val entrada = catalogo.porId(id) ?: return error("no existe la entrada $id")
        if (entrada.esLectura) return error("la entrada $id es de tipo lectura: usa leer_catalogo")
        val comandos = validarComandos(entrada.pasos)
            ?: return error("la entrada $id contiene pasos inválidos o bloqueados")
        val solicitud = SolicitudEscritura(
            tool = name,
            resumen = resumen(entrada),
            header = entrada.header,
            pasos = comandos.map { it.texto },
            requiereMotorApagado = entrada.riesgo != "bajo",
        )
        val pasosEjecutados = mutableListOf<PasoUds>()
        val resultado = ejecutor.ejecutar(solicitud) { bt ->
            SecuenciaUds.correr(bt, entrada.header, comandos).also(pasosEjecutados::addAll)
                .map { it.crudo ?: "(sin respuesta: ${it.error})" }
        }
        return EscrituraJson.de(resultado)
            .put("id", entrada.id)
            .put("pasos", PasoUdsJson.pasos(pasosEjecutados))
            .put("completada", pasosEjecutados.isNotEmpty() && pasosEjecutados.all { it.exitoso })
            .put("verificado", entrada.verificado)
            .put("fuente", entrada.fuente ?: JSONObject.NULL)
            .toString()
    }

    private fun validarComandos(pasos: List<String>) =
        pasos.map { paso ->
            ComandoHex.validar(paso).getOrNull()?.takeIf { it.clase != ClaseComando.BLOQUEADO }
        }.takeIf { comandos -> comandos.isNotEmpty() && comandos.all { it != null } }?.filterNotNull()

    private fun resumen(entrada: com.revscope.core.obd.catalogo.EntradaCatalogo): String {
        val vehiculo = listOfNotNull(entrada.marca, entrada.modulo).joinToString(" ")
        val sinVerificar = if (entrada.verificado) "" else ", SIN VERIFICAR"
        val seguridad = if (entrada.requiereSecurityAccess) ", pide acceso de seguridad" else ""
        return "${entrada.descripcion} [$vehiculo, riesgo ${entrada.riesgo}$sinVerificar$seguridad]"
    }

    private fun error(mensaje: String): String = JSONObject().put("error", mensaje).toString()
}
