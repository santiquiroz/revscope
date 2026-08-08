package com.revscope.feature.map.routing

/**
 * Decodificador del formato Encoded Polyline de Google (precisión 1e-5) — el mismo
 * que devuelve OSRM en `geometries=polyline`. Puro, sin dependencias.
 */
object PolylineDecoder {

    data class LatLon(val lat: Double, val lon: Double)

    fun decode(encoded: String): List<LatLon> {
        val points = mutableListOf<LatLon>()
        var index = 0
        var lat = 0
        var lon = 0
        while (index < encoded.length) {
            val deltaLat = decodeChunk(encoded, index)
            index = deltaLat.second
            lat += deltaLat.first
            val deltaLon = decodeChunk(encoded, index)
            index = deltaLon.second
            lon += deltaLon.first
            points.add(LatLon(lat / PRECISION, lon / PRECISION))
        }
        return points
    }

    private fun decodeChunk(encoded: String, startIndex: Int): Pair<Int, Int> {
        var index = startIndex
        var result = 0
        var shift = 0
        var byte: Int
        do {
            byte = encoded[index++].code - 63
            result = result or ((byte and 0x1F) shl shift)
            shift += 5
        } while (byte >= 0x20)
        val delta = if (result and 1 != 0) (result shr 1).inv() else result shr 1
        return delta to index
    }

    private const val PRECISION = 1e5
}
