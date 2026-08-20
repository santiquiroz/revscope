package com.revscope.core.obd.social

import org.json.JSONObject
import timber.log.Timber

/** Mensajes del protocolo de sala v2 (envelope `type`, compat total con pos sin type). */
sealed interface RoomMessage {

    data class Pos(
        val rider: String,
        val lat: Double,
        val lon: Double,
        val speedKmh: Double?,
        val headingDeg: Double?,
    ) : RoomMessage

    data class Dest(
        val rider: String,
        val lat: Double,
        val lon: Double,
        val name: String,
    ) : RoomMessage

    data class Race(
        val rider: String,
        val action: String,
        val startAtMs: Long?,
    ) : RoomMessage

    data class RoomStateMsg(
        val dest: Dest?,
        val race: Race?,
    ) : RoomMessage
}

/** Parser puro de mensajes de sala. JSON malformado o desconocido -> null (con log). */
object RoomMessageParser {

    fun parse(json: String): RoomMessage? = runCatching {
        val o = JSONObject(json)
        when (o.optString("type", "pos")) {
            "pos" -> parsePos(o)
            "dest" -> parseDest(o)
            "race" -> parseRace(o)
            "room_state" -> parseRoomState(o)
            else -> null
        }
    }.getOrElse { e ->
        Timber.w(e, "RoomMessageParser: mensaje malformado")
        null
    }

    private fun parsePos(o: JSONObject) = RoomMessage.Pos(
        rider = o.getString("rider"),
        lat = o.getDouble("lat"),
        lon = o.getDouble("lon"),
        speedKmh = o.doubleOrNull("speed_kmh"),
        headingDeg = o.doubleOrNull("heading_deg"),
    )

    private fun parseDest(o: JSONObject) = RoomMessage.Dest(
        rider = o.getString("rider"),
        lat = o.getDouble("lat"),
        lon = o.getDouble("lon"),
        name = o.getString("name"),
    )

    private fun parseRace(o: JSONObject) = RoomMessage.Race(
        rider = o.getString("rider"),
        action = o.getString("action"),
        startAtMs = o.longOrNull("start_at_ms"),
    )

    private fun parseRoomState(o: JSONObject) = RoomMessage.RoomStateMsg(
        dest = o.optJSONObject("dest")?.let(::parseDest),
        race = o.optJSONObject("race")?.let(::parseRace),
    )

    private fun JSONObject.doubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key) else null

    private fun JSONObject.longOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) optLong(key) else null
}
