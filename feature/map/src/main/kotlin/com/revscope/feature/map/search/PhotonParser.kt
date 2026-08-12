package com.revscope.feature.map.search

import org.json.JSONArray
import org.json.JSONObject

/** Un lugar encontrado. [subtitle] es el contexto que lo desambigua; puede quedar vacío. */
data class PlaceResult(
    val name: String,
    val subtitle: String,
    val lat: Double,
    val lon: Double,
)

/**
 * Parser puro de la respuesta de Photon. Ningún campo de `properties` está garantizado, así
 * que todos se tratan como opcionales y las features inservibles se descartan en vez de
 * romper la búsqueda entera.
 */
object PhotonParser {

    fun parse(json: String): List<PlaceResult> = runCatching {
        val features = JSONObject(json).optJSONArray("features") ?: return emptyList()
        (0 until features.length()).mapNotNull { placeAt(features, it) }
    }.getOrDefault(emptyList())

    private fun placeAt(features: JSONArray, index: Int): PlaceResult? {
        val feature = features.optJSONObject(index) ?: return null
        val props = feature.optJSONObject("properties") ?: return null
        val name = props.optString("name").takeIf { it.isNotBlank() } ?: return null
        val coords = feature.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return null
        if (coords.length() < 2) return null
        // GeoJSON es [lon, lat], no al revés.
        return PlaceResult(
            name = name,
            subtitle = subtitleOf(props, name),
            lat = coords.optDouble(1),
            lon = coords.optDouble(0),
        )
    }

    /** Calle con número, ciudad, departamento y país — sin vacíos y sin repetir el nombre. */
    private fun subtitleOf(props: JSONObject, name: String): String {
        val street = props.optString("street").takeIf { it.isNotBlank() }
        val houseNumber = props.optString("housenumber").takeIf { it.isNotBlank() }
        val streetLine = when {
            street != null && houseNumber != null -> "$street $houseNumber"
            else -> street
        }
        return listOfNotNull(
            streetLine,
            props.optString("city").takeIf { it.isNotBlank() },
            props.optString("state").takeIf { it.isNotBlank() },
            props.optString("country").takeIf { it.isNotBlank() },
        )
            .filter { it != name }
            .distinct()
            .joinToString(", ")
    }
}
