package com.revscope.core.obd.cameras

import com.revscope.core.data.db.dao.SpeedCameraDao
import com.revscope.core.data.db.entities.SpeedCameraEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection
import javax.inject.Inject
import javax.inject.Singleton

private const val OVERPASS_URL = "https://overpass-api.de/api/interpreter"
private const val RADIUS_M = 50_000
private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 60_000

/**
 * Downloads fixed speed cameras (OSM `highway=speed_camera` nodes) within 50 km
 * of a location via the Overpass API and stores them locally — alerts then work
 * fully offline. Coverage depends on what the OSM community has mapped.
 */
@Singleton
class SpeedCameraUpdater @Inject constructor(
    private val dao: SpeedCameraDao,
    private val alerter: SpeedCameraAlerter,
) {

    /** Returns the number of cameras stored, or throws on network/parse failure. */
    suspend fun downloadAround(latitude: Double, longitude: Double): Int =
        withContext(Dispatchers.IO) {
            val query =
                "[out:json][timeout:50];node[\"highway\"=\"speed_camera\"]" +
                    "(around:$RADIUS_M,$latitude,$longitude);out;"
            val body = "data=" + URLEncoder.encode(query, "UTF-8")

            val connection = (URL(OVERPASS_URL).openConnection() as HttpsURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
            }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val response = connection.inputStream.bufferedReader().readText()

            val elements = JSONObject(response).getJSONArray("elements")
            val cameras = buildList {
                for (i in 0 until elements.length()) {
                    val node = elements.getJSONObject(i)
                    add(
                        SpeedCameraEntity(
                            osmId = node.getLong("id"),
                            latitude = node.getDouble("lat"),
                            longitude = node.getDouble("lon"),
                            maxSpeedKmh = node.optJSONObject("tags")
                                ?.optString("maxspeed")
                                ?.filter { it.isDigit() }
                                ?.toIntOrNull(),
                        )
                    )
                }
            }
            dao.insertAll(cameras)
            alerter.invalidateCache()
            Timber.i("SpeedCameraUpdater: stored ${cameras.size} cameras")
            cameras.size
        }
}
