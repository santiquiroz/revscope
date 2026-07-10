package com.revscope.core.obd.cameras

import com.revscope.core.data.db.entities.SpeedCameraEntity
import org.json.JSONObject

private const val WAY_TYPE_TAG = 1L
private const val RELATION_TYPE_TAG = 2L
private const val TYPE_TAG_BITS = 2

/**
 * Pure parsing of an Overpass `[out:json]` response into [SpeedCameraEntity]
 * rows — no I/O, fully unit-testable.
 */
object OverpassCameraParser {

    fun parse(responseJson: String): List<SpeedCameraEntity> {
        val elements = JSONObject(responseJson).getJSONArray("elements")
        return buildList {
            for (i in 0 until elements.length()) {
                parseElement(elements.getJSONObject(i))?.let(::add)
            }
        }
    }

    private fun parseElement(element: JSONObject): SpeedCameraEntity? {
        val (lat, lon) = elementCoordinates(element) ?: return null
        return SpeedCameraEntity(
            osmId = encodeId(element.optString("type"), element.getLong("id")),
            latitude = lat,
            longitude = lon,
            maxSpeedKmh = element.optJSONObject("tags")?.let(::parseMaxSpeed),
        )
    }

    /** Nodes carry lat/lon directly; ways/relations only have a "center" (needs `out center;`). */
    private fun elementCoordinates(element: JSONObject): Pair<Double, Double>? {
        if (element.has("lat") && element.has("lon")) {
            return element.getDouble("lat") to element.getDouble("lon")
        }
        val center = element.optJSONObject("center") ?: return null
        if (!center.has("lat") || !center.has("lon")) return null
        return center.getDouble("lat") to center.getDouble("lon")
    }

    private fun parseMaxSpeed(tags: JSONObject): Int? =
        tags.optString("maxspeed").filter { it.isDigit() }.toIntOrNull()

    /**
     * OSM node/way/relation ids share no uniqueness guarantee across types, so a
     * union query can produce two different elements with the same numeric id.
     * Tag the low 2 bits with the element type before using it as a Room key.
     */
    private fun encodeId(type: String, rawId: Long): Long {
        val typeTag = when (type) {
            "way" -> WAY_TYPE_TAG
            "relation" -> RELATION_TYPE_TAG
            else -> 0L
        }
        return (rawId shl TYPE_TAG_BITS) or typeTag
    }
}
