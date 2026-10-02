package com.revscope.core.obd.diagnostics.uds

import com.revscope.core.obd.connection.Transport
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

data class PasoUds(
    val comando: String,
    val crudo: String?,
    val respuestas: List<RespuestaUds>,
    val error: String? = null,
) {
    val exitoso: Boolean get() = error == null && respuestas.any { it is RespuestaUds.Positiva }
}

/** Corre pasos en orden y se detiene en el primero sin respuesta positiva: sin sesión o sin clave, el resto no tiene sentido. */
object SecuenciaUds {

    suspend fun correr(bt: Transport, header: String?, comandos: List<ComandoValidado>): List<PasoUds> {
        val pasos = mutableListOf<PasoUds>()
        for (comando in comandos) {
            val paso = correrPaso(bt, header, comando)
            pasos += paso
            if (!paso.exitoso) break
        }
        return pasos
    }

    private suspend fun correrPaso(bt: Transport, header: String?, comando: ComandoValidado): PasoUds = try {
        val crudo = EnvioUds.enviar(bt, header, comando.texto)
        PasoUds(comando.texto, crudo, EnvioUds.interpretar(crudo, comando, header))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        PasoUds(comando.texto, crudo = null, respuestas = emptyList(), error = e.message ?: "sin respuesta del adaptador")
    }

    /** Datos útiles de una respuesta positiva: quita el eco del PID/DID para que A sea el primer byte de valor. */
    fun datosSinEco(servicio: Int, positiva: RespuestaUds.Positiva): List<Int> = positiva.datos.drop(ecoDe(servicio))

    private fun ecoDe(servicio: Int): Int = when (servicio) {
        0x22, 0x02 -> 2
        0x31 -> 3
        0x01, 0x09, 0x21, 0x1A -> 1
        else -> 0
    }
}

object PasoUdsJson {

    fun pasos(pasos: List<PasoUds>): JSONArray = JSONArray(pasos.map(::paso))

    fun paso(paso: PasoUds): JSONObject = JSONObject()
        .put("comando", paso.comando)
        .put("exitoso", paso.exitoso)
        .put("crudo", paso.crudo ?: JSONObject.NULL)
        .put("respuestas", JSONArray(paso.respuestas.map(::respuesta)))
        .put("error", paso.error ?: JSONObject.NULL)

    fun respuesta(r: RespuestaUds): JSONObject = when (r) {
        is RespuestaUds.Positiva -> JSONObject()
            .put("tipo", "positiva")
            .put("header", r.header ?: JSONObject.NULL)
            .put("datos", ParserUds.hex(r.datos))
        is RespuestaUds.Negativa -> JSONObject()
            .put("tipo", "negativa")
            .put("header", r.header ?: JSONObject.NULL)
            .put("nrc", "%02X".format(r.nrc))
            .put("significado", r.nombreNrc)
        is RespuestaUds.SinRespuesta -> JSONObject()
            .put("tipo", "sin_respuesta")
            .put("header", r.header ?: JSONObject.NULL)
            .put("motivo", r.motivo)
    }
}
