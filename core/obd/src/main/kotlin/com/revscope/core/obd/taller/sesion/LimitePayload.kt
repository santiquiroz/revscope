package com.revscope.core.obd.taller.sesion

import org.json.JSONObject

// ~60 KB por evento (diseño §2.9): primero se sacrifica la serie reducida, que el CSV adjunto conserva.
object LimitePayload {

    const val MAX_CARACTERES = 60_000

    fun ajustar(payload: JSONObject, maximo: Int = MAX_CARACTERES): String {
        val completo = payload.toString()
        if (completo.length <= maximo) return completo
        val sinSerie = JSONObject(completo).apply {
            remove("serie")
            put("serieOmitida", true)
        }.toString()
        if (sinSerie.length <= maximo) return sinSerie
        return JSONObject().put("recortado", true).put("caracteresOriginales", completo.length).toString()
    }
}
