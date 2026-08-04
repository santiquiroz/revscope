package com.revscope.core.obd.social

import com.revscope.core.obd.service.LiveRouteHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val PEER_STALE_MS = 30_000L
private const val SEND_THROTTLE_MS = 1_000L

/**
 * Cliente de rodadas en grupo (WebSocket contra revscope-server). Se alimenta solo
 * del [LiveRouteHolder] mientras estés unido a una sala — no toca el recorder GPS.
 * OFFLINE-FIRST: caída del socket = estado Disconnected silencioso, nunca un error.
 */
@Singleton
class RoomClient @Inject constructor(
    private val serverClient: ServerClient,
    private val routeHolder: LiveRouteHolder,
) {

    data class Peer(val rider: String, val lat: Double, val lon: Double, val speedKmh: Double?, val seenAtMs: Long)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ok = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var socket: WebSocket? = null
    private var feedJob: Job? = null
    @Volatile private var lastSentMs = 0L

    private val _roomCode = MutableStateFlow<String?>(null)
    val roomCode: StateFlow<String?> = _roomCode.asStateFlow()

    private val _peers = MutableStateFlow<Map<String, Peer>>(emptyMap())
    val peers: StateFlow<Map<String, Peer>> = _peers.asStateFlow()

    /** Crea una sala en el server y se une. Null si no hay server o falló (silencioso). */
    suspend fun createAndJoin(): String? {
        val code = serverClient.postJson("/v1/rooms", JSONObject())
            .getOrNull()?.optString("code")?.takeIf { it.isNotBlank() } ?: return null
        join(code)
        return code
    }

    fun join(code: String) {
        scope.launch {
            leaveInternal()
            val config = serverClient.config() ?: return@launch
            val wsBase = config.baseUrl.replaceFirst("http", "ws")
            val request = Request.Builder()
                .url("$wsBase/v1/rooms/${code.uppercase()}/ws?rider=${config.riderName}")
                .apply { if (config.token.isNotBlank()) header("Authorization", "Bearer ${config.token}") }
                .build()
            socket = ok.newWebSocket(request, listener)
            _roomCode.value = code.uppercase()
            startFeeding()
        }
    }

    fun leave() {
        scope.launch { leaveInternal() }
    }

    private fun leaveInternal() {
        feedJob?.cancel()
        feedJob = null
        runCatching { socket?.close(1000, "bye") }
        socket = null
        _roomCode.value = null
        _peers.value = emptyMap()
    }

    /** Retransmite mi posición (1 Hz máx) mientras el routeHolder reciba fixes. */
    private fun startFeeding() {
        feedJob?.cancel()
        feedJob = scope.launch {
            routeHolder.lastPoint.collect { point ->
                val ws = socket ?: return@collect
                if (point == null) return@collect
                val now = System.currentTimeMillis()
                if (now - lastSentMs < SEND_THROTTLE_MS) return@collect
                lastSentMs = now
                val payload = JSONObject()
                    .put("lat", point.lat)
                    .put("lon", point.lon)
                    .put("speed_kmh", routeHolder.lastSpeedKmh.value.toDouble())
                ws.send(payload.toString())
            }
        }
    }

    private val listener = object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            runCatching {
                val o = JSONObject(text)
                val rider = o.getString("rider")
                val peer = Peer(
                    rider = rider,
                    lat = o.getDouble("lat"),
                    lon = o.getDouble("lon"),
                    speedKmh = if (o.has("speed_kmh")) o.optDouble("speed_kmh") else null,
                    seenAtMs = System.currentTimeMillis(),
                )
                val now = System.currentTimeMillis()
                _peers.value = (_peers.value + (rider to peer))
                    .filterValues { now - it.seenAtMs < PEER_STALE_MS }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Timber.i("RoomClient: socket caído (${t.message}) — rodada terminada en silencio")
            scope.launch { leaveInternal() }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            scope.launch { leaveInternal() }
        }
    }
}
