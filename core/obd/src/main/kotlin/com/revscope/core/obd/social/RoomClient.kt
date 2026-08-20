package com.revscope.core.obd.social

import com.revscope.core.obd.service.LiveRouteHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

private const val SEND_THROTTLE_MS = 1_000L
private const val HELLO_TIMEOUT_MS = 3_000L
private const val RACE_COUNTDOWN_MS = 5_000L

/**
 * Cliente de rodadas en grupo (WebSocket contra revscope-server, protocolo de sala v2).
 * Se alimenta solo del [LiveRouteHolder] mientras estés unido a una sala — no toca el
 * recorder GPS. OFFLINE-FIRST: caída del socket = estado Disconnected silencioso, nunca
 * un error. Feature-detect: manda `hello` al conectar; si no llega `room_state` en
 * [HELLO_TIMEOUT_MS], asume server v1 (`roomState.legacyServer`).
 */
@Singleton
class RoomClient @Inject constructor(
    private val serverClient: ServerClient,
    private val routeHolder: LiveRouteHolder,
) {

    companion object {
        /** Un peer sin `pos` en esta ventana se considera desconectado. Pública: quien
         * recalcule staleness fuera de RoomClient (p. ej. el tick periódico de F5 en el
         * ViewModel) debe usar el mismo umbral, no una copia. */
        const val PEER_STALE_MS = 30_000L
    }

    data class Peer(
        val rider: String,
        val lat: Double,
        val lon: Double,
        val speedKmh: Double?,
        val headingDeg: Double?,
        val seenAtMs: Long,
    )

    data class SharedDest(val rider: String, val lat: Double, val lon: Double, val name: String)

    data class RaceState(val startedBy: String, val startAtMs: Long)

    data class RoomState(
        val dest: SharedDest? = null,
        val race: RaceState? = null,
        val legacyServer: Boolean = false,
        // Nombre asignado por el server (con sufijo si hubo colisión con otro rider de la
        // sala) — null con un server viejo que todavía no manda `you` en room_state (F6).
        val you: String? = null,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ok = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    @Volatile private var socket: WebSocket? = null
    private var feedJob: Job? = null
    private var helloTimeoutJob: Job? = null
    @Volatile private var lastSentMs = 0L
    @Volatile private var receivedRoomState = false

    private val _roomCode = MutableStateFlow<String?>(null)
    val roomCode: StateFlow<String?> = _roomCode.asStateFlow()

    private val _peers = MutableStateFlow<Map<String, Peer>>(emptyMap())
    val peers: StateFlow<Map<String, Peer>> = _peers.asStateFlow()

    private val _roomState = MutableStateFlow(RoomState())
    val roomState: StateFlow<RoomState> = _roomState.asStateFlow()

    private val _ghost = MutableStateFlow(false)
    val ghost: StateFlow<Boolean> = _ghost.asStateFlow()

    /** Modo fantasma: mientras esté activo, mi posición no se retransmite a la sala. */
    fun setGhost(enabled: Boolean) {
        _ghost.value = enabled
    }

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
        helloTimeoutJob?.cancel()
        helloTimeoutJob = null
        runCatching { socket?.close(1000, "bye") }
        socket = null
        receivedRoomState = false
        _roomCode.value = null
        _peers.value = emptyMap()
        _roomState.value = RoomState()
    }

    /** Propone un destino compartido a toda la sala. No pasa por el throttle del feed. */
    fun shareDestination(lat: Double, lon: Double, name: String) {
        sendRaw(
            JSONObject()
                .put("type", "dest")
                .put("lat", lat)
                .put("lon", lon)
                .put("name", name)
        )
    }

    /** Larga una carrera: el server hace broadcast y todos cuentan regresivamente igual. */
    fun startRace() {
        sendRaw(
            JSONObject()
                .put("type", "race")
                .put("action", "start")
                .put("start_at_ms", System.currentTimeMillis() + RACE_COUNTDOWN_MS)
        )
    }

    fun stopRace() {
        sendRaw(
            JSONObject()
                .put("type", "race")
                .put("action", "stop")
        )
    }

    private fun sendRaw(payload: JSONObject) {
        val ws = socket ?: run {
            Timber.w("RoomClient: sendRaw sin sala activa, descartado")
            return
        }
        ws.send(payload.toString())
    }

    /** Retransmite mi posición (1 Hz máx) mientras el routeHolder reciba fixes. */
    private fun startFeeding() {
        feedJob?.cancel()
        feedJob = scope.launch {
            routeHolder.lastPoint.collect { point ->
                val ws = socket ?: return@collect
                if (point == null) return@collect
                if (_ghost.value) return@collect
                val now = System.currentTimeMillis()
                if (now - lastSentMs < SEND_THROTTLE_MS) return@collect
                lastSentMs = now
                val payload = JSONObject()
                    .put("type", "pos")
                    .put("lat", point.lat)
                    .put("lon", point.lon)
                    .put("speed_kmh", routeHolder.lastSpeedKmh.value.toDouble())
                    .put("heading_deg", routeHolder.lastHeadingDeg.value ?: JSONObject.NULL)
                ws.send(payload.toString())
            }
        }
    }

    private fun onPos(pos: RoomMessage.Pos) {
        val now = System.currentTimeMillis()
        val peer = Peer(
            rider = pos.rider,
            lat = pos.lat,
            lon = pos.lon,
            speedKmh = pos.speedKmh,
            headingDeg = pos.headingDeg,
            seenAtMs = now,
        )
        _peers.value = (_peers.value + (pos.rider to peer))
            .filterValues { now - it.seenAtMs < PEER_STALE_MS }
    }

    private fun onDest(dest: RoomMessage.Dest) {
        _roomState.update { it.copy(dest = dest.toSharedDest()) }
    }

    private fun onRace(race: RoomMessage.Race) {
        _roomState.update { it.copy(race = race.toRaceStateOrNull()) }
    }

    private fun onRoomState(state: RoomMessage.RoomStateMsg) {
        receivedRoomState = true
        _roomState.update {
            it.copy(
                dest = state.dest?.toSharedDest(),
                race = state.race?.toRaceStateOrNull(),
                legacyServer = false,
                you = state.you,
            )
        }
    }

    private fun RoomMessage.Dest.toSharedDest() = SharedDest(rider, lat, lon, name)

    private fun RoomMessage.Race.toRaceStateOrNull(): RaceState? {
        val startAtMs = startAtMs ?: return null
        return if (action == "start") RaceState(rider, startAtMs) else null
    }

    private fun sendHelloAndArmTimeout(webSocket: WebSocket) {
        receivedRoomState = false
        webSocket.send(JSONObject().put("type", "hello").put("v", 2).toString())
        helloTimeoutJob?.cancel()
        helloTimeoutJob = scope.launch {
            delay(HELLO_TIMEOUT_MS)
            if (!receivedRoomState) _roomState.update { it.copy(legacyServer = true) }
        }
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            // Usa el webSocket del callback, no el campo `socket` — sigue siendo correcto
            // aunque `socket` todavía no se haya asignado (la asignación es posterior al
            // return de newWebSocket). No necesita el guard de identidad de abajo porque
            // no toca estado compartido destructivo, solo arma el timeout de hello.
            sendHelloAndArmTimeout(webSocket)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (webSocket !== socket) return // mensaje de una conexión vieja (leave+rejoin rápido) — se ignora
            when (val message = RoomMessageParser.parse(text)) {
                is RoomMessage.Pos -> onPos(message)
                is RoomMessage.Dest -> onDest(message)
                is RoomMessage.Race -> onRace(message)
                is RoomMessage.RoomStateMsg -> onRoomState(message)
                null -> Unit // ya logueado por RoomMessageParser
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (webSocket !== socket) return // falla de una conexión vieja ya reemplazada — se ignora
            Timber.i("RoomClient: socket caído (${t.message}) — rodada terminada en silencio")
            scope.launch { leaveInternal() }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (webSocket !== socket) return // cierre de una conexión vieja ya reemplazada — se ignora
            scope.launch { leaveInternal() }
        }
    }
}
