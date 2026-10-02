package com.revscope.core.obd.mcp.escritura

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class EntradaRegistro(
    val ts: Long,
    val tool: String,
    val resumen: String,
    val header: String?,
    val pasos: List<String>,
    val autorizacion: String,
    val respuestas: List<String> = emptyList(),
    val error: String? = null,
)

/** Auditoría de escrituras del MCP en JSONL; guarda las últimas [MAX_ENTRADAS]. */
@Singleton
class RegistroEscrituras internal constructor(private val archivo: File) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(File(context.filesDir, ARCHIVO))

    @Synchronized
    fun registrar(entrada: EntradaRegistro) {
        runCatching {
            val lineas = leerLineas() + CodecRegistro.aJson(entrada).toString()
            archivo.writeText(lineas.takeLast(MAX_ENTRADAS).joinToString("\n", postfix = "\n"))
        }.onFailure { Timber.w(it, "RegistroEscrituras: no se pudo escribir") }
    }

    @Synchronized
    fun ultimas(n: Int): List<EntradaRegistro> =
        leerLineas().mapNotNull(CodecRegistro::desdeJson).takeLast(n.coerceAtLeast(0)).reversed()

    private fun leerLineas(): List<String> =
        if (archivo.exists()) archivo.readLines().filter { it.isNotBlank() } else emptyList()

    companion object {
        const val ARCHIVO = "mcp_escrituras.jsonl"
        const val MAX_ENTRADAS = 500
    }
}

internal object CodecRegistro {

    fun aJson(e: EntradaRegistro): JSONObject = JSONObject()
        .put("ts", e.ts)
        .put("tool", e.tool)
        .put("resumen", e.resumen)
        .put("header", e.header ?: JSONObject.NULL)
        .put("pasos", JSONArray(e.pasos))
        .put("autorizacion", e.autorizacion)
        .put("respuestas", JSONArray(e.respuestas))
        .put("error", e.error ?: JSONObject.NULL)

    fun desdeJson(linea: String): EntradaRegistro? = runCatching {
        val o = JSONObject(linea)
        EntradaRegistro(
            ts = o.getLong("ts"),
            tool = o.getString("tool"),
            resumen = o.optString("resumen"),
            header = o.optString("header").takeUnless { o.isNull("header") },
            pasos = strings(o.optJSONArray("pasos")),
            autorizacion = o.optString("autorizacion"),
            respuestas = strings(o.optJSONArray("respuestas")),
            error = o.optString("error").takeUnless { o.isNull("error") },
        )
    }.getOrNull()

    private fun strings(array: JSONArray?): List<String> = (0 until (array?.length() ?: 0)).map { array!!.getString(it) }
}
