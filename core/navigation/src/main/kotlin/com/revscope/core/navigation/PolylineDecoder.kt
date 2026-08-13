package com.revscope.core.navigation

import kotlin.math.pow

data class LatLon(val lat: Double, val lon: Double)

/**
 * Decodificador del formato Encoded Polyline de Google. Puro, sin dependencias.
 *
 * La precisión es un parámetro **obligatorio** a propósito. OSRM devuelve precisión 5 con
 * `geometries=polyline` y 6 con `geometries=polyline6`; decodificar una como la otra no falla,
 * solo desplaza las coordenadas por un factor de 10 y la ruta aparece en otro continente.
 * Un valor por omisión aquí sería justamente la trampa.
 */
object PolylineDecoder {

    fun decode(encoded: String, precision: Int): List<LatLon> {
        val divisor = 10.0.pow(precision)
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
            points.add(LatLon(lat / divisor, lon / divisor))
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
}
