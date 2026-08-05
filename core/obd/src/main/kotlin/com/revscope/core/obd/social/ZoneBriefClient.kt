package com.revscope.core.obd.social

import org.json.JSONObject
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Briefs de zona contra revscope-server. Server-first: la app pregunta aquí antes de
 * gastar IA; si el server no tiene, la app genera con IA y CONTRIBUYE de vuelta con
 * [contribute] para que el siguiente viajero lo reciba gratis. OFFLINE-FIRST: sin
 * server/red, [get] retorna null silencioso y el flujo cae a la IA (o a nada).
 */
@Singleton
class ZoneBriefClient @Inject constructor(
    private val client: ServerClient,
) {

    /** Brief comunitario fresco para [place]/[country], o null si el server no tiene / no configurado. */
    suspend fun get(place: String, country: String?): String? {
        if (client.config() == null) return null
        val q = StringBuilder("/v1/zone-brief?place=").append(enc(place))
        if (!country.isNullOrBlank()) q.append("&country=").append(enc(country))
        val obj = client.getJson(q.toString()).getOrNull() ?: return null
        // El endpoint devuelve null (cuerpo "null") cuando no hay brief fresco.
        return obj.optString("body").takeIf { it.isNotBlank() }
    }

    /** Sube un brief generado por IA para que otros lo reciban sin gastar IA. */
    suspend fun contribute(place: String, country: String?, body: String) {
        if (client.config() == null) return
        val payload = JSONObject().put("place", place).put("body", body)
        if (!country.isNullOrBlank()) payload.put("country", country)
        client.postJson("/v1/zone-brief", payload)
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
