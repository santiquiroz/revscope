package com.revscope.feature.map.search

import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Búsqueda de direcciones contra Photon, el geocoder open source de Komoot sobre datos de
 * OpenStreetMap. Sin API key, pensado para autocompletar y tolerante a errores de tipeo.
 */
object PhotonGeocoder {

    private const val BASE_URL = "https://photon.komoot.io/api"
    private const val LIMIT = 8
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 10_000

    /** Mínimo de caracteres antes de molestar a un servicio público y gratuito. */
    const val MIN_QUERY_LENGTH = 3

    /**
     * @param biasLat y [biasLon] la última posición conocida. Sin sesgo, buscar "medellin"
     * devuelve Medellín de Filipinas primero: para alguien buscando una calle de su ciudad
     * eso no es un detalle de ranking, es que la función no sirve.
     */
    fun search(query: String, biasLat: Double?, biasLon: Double?): List<PlaceResult> {
        if (query.trim().length < MIN_QUERY_LENGTH) return emptyList()
        val url = buildString {
            append(BASE_URL)
            append("?q=").append(URLEncoder.encode(query.trim(), "UTF-8"))
            append("&limit=").append(LIMIT)
            if (biasLat != null && biasLon != null) {
                // Locale.US: en es-CO el separador decimal es coma y rompe la URL.
                append(String.format(Locale.US, "&lat=%f&lon=%f", biasLat, biasLon))
            }
        }
        return runCatching { PhotonParser.parse(httpGet(url)) }
            .onFailure { Timber.w(it, "PhotonGeocoder: búsqueda fallida") }
            .getOrDefault(emptyList())
    }

    private fun httpGet(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "RevScope/1.0 (github.com/santiquiroz/revscope)")
            connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }
}
