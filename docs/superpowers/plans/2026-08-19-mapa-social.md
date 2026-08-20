# Mapa Social estilo Wheelz — Implementation Plan (sub-proyecto F)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Salas de rodada con destino compartido, ETAs/ranking en vivo, carreras con posiciones, pins con nombre+velocidad+rumbo, modo fantasma y rutas alternativas/panorámica.

**Architecture:** Protocolo v2 sobre el relay WS existente: envelope `type` con compat total hacia atrás (mensaje sin type = posición). El server (revscope-server, repo aparte) gana estado de sala + replay `room_state` + dispatch por tipo + auth en el WS. El cliente gana `sendRaw`, dispatch en el listener (extraído a un parser puro testeable), `roomState` observable y feature-detect (hello → room_state, timeout = modo legacy). Ranking y llegadas se computan en el cliente (restante haversine al destino compartido) — sin árbitro server v1.

**Tech Stack:** App: Kotlin/Compose/OkHttp WS. Server: FastAPI/websockets/pytest.

**Spec:** docs/superpowers/specs/2026-08-19-mapa-social-design.md

## Global Constraints

- App en `C:\personal\OBD2` (branch feature/backlog-e-f-g); server en `C:\personal\revscope-server` (branch master, commits propios). NO gradlew (GRADLE=... conocido). Server: `python -m pytest` desde su raíz (crear venv si hace falta: `python -m venv .venv && .venv/Scripts/pip install -r requirements.txt pytest httpx` — mirar README).
- Compat OBLIGATORIA: cliente viejo + server nuevo funcionan (pos sin type); cliente nuevo + server viejo degrada a modo legacy detectado (sin crash, hint de actualización).
- Mensajes (formas EXACTAS, cliente y server):
  - pos (c→s): `{"type":"pos","lat":D,"lon":D,"speed_kmh":D?,"heading_deg":D?}` — server acepta también SIN type (legacy).
  - pos (s→c): igual + `"rider":S`.
  - dest (c→s): `{"type":"dest","lat":D,"lon":D,"name":S}` — server agrega `rider` y guarda en estado.
  - race (c→s): `{"type":"race","action":"start","start_at_ms":L}` | `{"type":"race","action":"stop"}` — server agrega `rider`, guarda.
  - hello (c→s): `{"type":"hello","v":2}`.
  - room_state (s→c): `{"type":"room_state","dest":{...}|null,"race":{...}|null}` — al conectar y como respuesta a hello.
- Llegada de carrera: `restanteM < 40.0`. ETA = restante / max(speed, 5 km/h). Countdown de largada: `start_at_ms = now + 5_000`.
- Sinuosidad panorámica: `distanciaRuta / haversine(origen,destino) > 1.15` y no ser la más corta en duración.
- Glyphs para labels: `"glyphs": "https://protomaps.github.io/basemaps-assets/fonts/{fontstack}/{range}.pbf"` en MapStyleProvider (ambos estilos); font `"Noto Sans Regular"`.
- Commits español sin Co-Authored-By (ambos repos). Tests existentes verdes. SIN release en este plan.

---

### Task 1 (SERVER): protocolo v2 en rooms.py + tests

**Files (repo C:\personal\revscope-server):**
- Modify: `app/routers/rooms.py`
- Create: `tests/test_rooms.py`

**Interfaces:**
- Produces: el contrato de mensajes de Global Constraints, `_rooms[code] = {"members": dict[str, WebSocket], "state": {"dest": dict|None, "race": dict|None}}`, replay de `room_state` al conectar, dispatch: pos→broadcast proyectado (lat/lon/speed_kmh/heading_deg + rider), dest/race→estado+broadcast (con rider), hello→room_state al emisor. Nombre duplicado gana sufijo `-2`, `-3`... Auth: el WS valida identidad con `get_identity`-equivalente leyendo el header Authorization del handshake en modos token/oidc (en `none` sigue self-declared); fallo → close 4401.

- [ ] **Step 1: Write the failing tests** — `tests/test_rooms.py` con `fastapi.testclient.TestClient` (soporta websockets síncronos):

```python
from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)

def _create_room():
    r = client.post("/v1/rooms", headers={"X-Rider-Name": "ana"})
    assert r.status_code == 200
    return r.json()["code"]

def test_pos_legacy_sin_type_se_relayea():
    code = _create_room()
    with client.websocket_connect(f"/v1/rooms/{code}/ws?rider=ana") as a:
        a.receive_json()  # room_state inicial
        with client.websocket_connect(f"/v1/rooms/{code}/ws?rider=beto") as b:
            b.receive_json()  # room_state inicial
            a.send_json({"lat": 6.2, "lon": -75.5, "speed_kmh": 30.0})
            msg = b.receive_json()
            assert msg["rider"] == "ana" and msg["lat"] == 6.2

def test_dest_se_guarda_y_se_replayea_a_late_joiner():
    code = _create_room()
    with client.websocket_connect(f"/v1/rooms/{code}/ws?rider=ana") as a:
        a.receive_json()
        a.send_json({"type": "dest", "lat": 6.3, "lon": -75.6, "name": "Chilis"})
        with client.websocket_connect(f"/v1/rooms/{code}/ws?rider=beto") as b:
            state = b.receive_json()
            assert state["type"] == "room_state"
            assert state["dest"]["name"] == "Chilis" and state["dest"]["rider"] == "ana"

def test_race_start_broadcast_y_estado():
    code = _create_room()
    with client.websocket_connect(f"/v1/rooms/{code}/ws?rider=ana") as a:
        a.receive_json()
        with client.websocket_connect(f"/v1/rooms/{code}/ws?rider=beto") as b:
            b.receive_json()
            a.send_json({"type": "race", "action": "start", "start_at_ms": 123})
            msg = b.receive_json()
            assert msg["type"] == "race" and msg["action"] == "start" and msg["rider"] == "ana"

def test_hello_devuelve_room_state():
    code = _create_room()
    with client.websocket_connect(f"/v1/rooms/{code}/ws?rider=ana") as a:
        a.receive_json()
        a.send_json({"type": "hello", "v": 2})
        msg = a.receive_json()
        assert msg["type"] == "room_state"

def test_nombre_duplicado_gana_sufijo():
    code = _create_room()
    with client.websocket_connect(f"/v1/rooms/{code}/ws?rider=ana") as a:
        a.receive_json()
        with client.websocket_connect(f"/v1/rooms/{code}/ws?rider=ana") as a2:
            a2.receive_json()
            a2.send_json({"lat": 1.0, "lon": 2.0, "speed_kmh": None})
            msg = a.receive_json()
            assert msg["rider"] == "ana-2"
```

- [ ] **Step 2: RED** — `python -m pytest tests/test_rooms.py -q` (setup del venv si falta, documentado). Esperado: FAIL (no hay room_state inicial, dest se proyecta a null, etc.).
- [ ] **Step 3: Implementación** — reescribir el handler WS de `rooms.py`:

```python
_rooms: dict[str, dict] = {}  # {code: {"members": {rider: ws}, "state": {"dest": None, "race": None}}}

# En el connect (tras validar sala y capacidad):
#   rider único: base = rider; n = 2; while rider in members: rider = f"{base}-{n}"; n += 1
#   await websocket.send_json({"type": "room_state", **room["state"]})
# Loop:
#   payload = await websocket.receive_json()
#   mtype = payload.get("type", "pos")
#   if mtype == "hello": await websocket.send_json({"type": "room_state", **room["state"]}); continue
#   if mtype == "pos" or "type" not in payload:
#       message = {"type": "pos", "rider": rider, **{k: payload.get(k) for k in ("lat","lon","speed_kmh","heading_deg")}}
#   elif mtype == "dest":
#       message = {"type": "dest", "rider": rider, **{k: payload.get(k) for k in ("lat","lon","name")}}
#       room["state"]["dest"] = message
#   elif mtype == "race":
#       message = {"type": "race", "rider": rider, **{k: payload.get(k) for k in ("action","start_at_ms")}}
#       room["state"]["race"] = message if payload.get("action") == "start" else None
#   else: continue
#   broadcast a los demás (mecánica actual)
```

Compat legacy s→c: los mensajes pos AHORA llevan `"type":"pos"` — el cliente viejo los parsea igual (ignora la clave extra: `getString("rider")`/`getDouble("lat")` no fallan por claves extra) ✓. Auth WS: leer `websocket.headers.get("authorization")` y validar con la misma lógica de `auth.py` en modos token/oidc (extraer helper si get_identity es HTTP-only); fallo → `await websocket.close(code=4401)`. Room_state inicial también para el PRIMER socket (estado vacío).

- [ ] **Step 4: GREEN** — pytest verde completo (incluye test_smoke existente).
- [ ] **Step 5: Commit + push (repo server)**

```bash
git add app/routers/rooms.py tests/test_rooms.py
git commit -m "feat: protocolo de sala v2 — estado con replay, destino compartido, carreras y auth en el WebSocket"
git push origin master   # (env -u GITHUB_TOKEN)
```

---

### Task 2 (APP): RoomClient v2 + parser puro

**Files:**
- Create: `core/obd/src/main/kotlin/com/revscope/core/obd/social/RoomMessage.kt` (parser puro + modelos)
- Create: `core/obd/src/test/kotlin/com/revscope/core/obd/social/RoomMessageTest.kt`
- Modify: `core/obd/src/main/kotlin/com/revscope/core/obd/social/RoomClient.kt`

**Interfaces:**
- Produces: `sealed interface RoomMessage { data class Pos(rider, lat, lon, speedKmh: Double?, headingDeg: Double?); data class Dest(rider, lat, lon, name); data class Race(rider, action: String, startAtMs: Long?); data class RoomStateMsg(dest: Dest?, race: Race?) }` + `object RoomMessageParser { fun parse(json: String): RoomMessage? }` (null = malformado, con Timber.w). En RoomClient: `Peer` gana `headingDeg: Double?`; `data class SharedDest(rider, lat, lon, name)`; `data class RaceState(startedBy: String, startAtMs: Long)`; `roomState: StateFlow<RoomState>` con `RoomState(dest: SharedDest? = null, race: RaceState? = null, legacyServer: Boolean = false)`; `fun shareDestination(lat: Double, lon: Double, name: String)`; `fun startRace()` (manda start_at_ms = now+5000); `fun stopRace()`; `fun setGhost(enabled: Boolean)` / `ghost: StateFlow<Boolean>`; hello al conectar + timeout 3 s sin room_state → `legacyServer = true`.

- [ ] **Step 1: TDD del parser** — tests: pos con y sin type, pos sin speed/heading, dest, race start/stop, room_state con dest null, basura → null. RED → implementación (org.json como el código actual) → GREEN.
- [ ] **Step 2: RoomClient** — listener delega TODO a `RoomMessageParser.parse` y despacha: Pos → mapa de peers (semántica actual + headingDeg + prune staleness); Dest/Race/RoomStateMsg → `_roomState`. `hello` al `onOpen` + `scope.launch { delay(3_000); if (aún sin room_state) _roomState.update { it.copy(legacyServer = true) } }`. `sendRaw(json: JSONObject)` privado + públicos `shareDestination/startRace/stopRace` (NO throttleados). El feed de pos respeta `ghost` (no emite) y agrega `heading_deg` desde `routeHolder.lastHeadingDeg` (Task 3 lo provee; hasta entonces null-safe con `runCatching` o acceso condicionado — usar `lastHeadingDeg` solo si Task 3 ya mergeó; este task puede referenciarlo porque Task 3 va antes en el orden de ejecución… NO: este task es 2 y heading es 3. RESOLUCIÓN: este task manda `heading_deg` = null fijo con un TODO-free comentario "Task 3 lo puebla"; Task 3 lo conecta).
- [ ] **Step 3: Compile + tests + commit** — `:core:obd:testDebugUnitTest` verde.

```bash
git add core/obd
git commit -m "feat: protocolo de sala v2 en el cliente — parser tipado, destino compartido, carrera y modo fantasma"
```

---

### Task 3 (APP): heading + pins con nombre y velocidad

**Files:**
- Modify: `core/obd/src/main/kotlin/com/revscope/core/obd/service/LiveRouteHolder.kt` (`lastHeadingDeg: StateFlow<Double?>` + update)
- Modify: `core/obd/src/main/kotlin/com/revscope/core/obd/service/GpsTrackRecorder.kt` (alimentar heading desde `location.bearing` si `hasBearing()`)
- Modify: `core/obd/src/main/kotlin/com/revscope/core/obd/social/RoomClient.kt` (heading_deg real en pos)
- Modify: `core/maps/src/main/kotlin/com/revscope/core/maps/MapStyleProvider.kt` (glyphs en los 3 estilos)
- Modify: `feature/map/src/main/kotlin/com/revscope/feature/map/LiveMapLayers.kt` (labels de peers + icono direccional)

**Interfaces:**
- Consumes: `Peer.headingDeg` (Task 2).
- Produces: layer `peer-labels` (symbol, `textField "{label}"` alimentado por property `label = "$rider\n${speed} km/h"`, `textFont ["Noto Sans Regular"]`, `textOffset` sobre el pin, `textColor` blanco con halo oscuro); icono nuevo `icono-peer-rumbo` (flecha) con `iconRotate` por `heading` property cuando `headingDeg != null`, pin actual si null. `styleJson` con `"glyphs"` (Global Constraints) — labels requieren red; sin red degradan a pins sin texto (documentado).

- [ ] **Step 1:** LiveRouteHolder + GpsTrackRecorder (patrón de lastSpeedKmh — replicar).
- [ ] **Step 2:** RoomClient: `"heading_deg"` desde `routeHolder.lastHeadingDeg.value`.
- [ ] **Step 3:** MapStyleProvider: `"glyphs": "..."` en vectorStyle/rasterStyle/backgroundOnlyStyle (propiedad top-level del style JSON).
- [ ] **Step 4:** LiveMapLayers: features de peers ganan properties `label` y `heading`; layer symbol nuevo para labels; icono flecha dibujado en código (mismo helper de bitmaps existente). Peers sin heading → pin actual.
- [ ] **Step 5:** Compile + tests + commit — `:core:obd:testDebugUnitTest :feature:map:compileDebugKotlin :core:maps:compileDebugKotlin`.

```bash
git add core/obd core/maps feature/map
git commit -m "feat: pins de sala con nombre, velocidad y rumbo"
```

---

### Task 4 (APP): destino compartido + leaderboard de ETAs

**Files:**
- Create: `feature/map/src/main/kotlin/com/revscope/feature/map/social/Leaderboard.kt` (componente + cálculo puro)
- Create: `feature/map/src/test/kotlin/com/revscope/feature/map/social/LeaderboardTest.kt`
- Modify: `feature/map/src/main/kotlin/com/revscope/feature/map/LiveMapViewModel.kt` + `LiveMapScreen.kt` (+ `RouteInfoChip` botón compartir + banner receptor)

**Interfaces:**
- Consumes: `roomState`, `peers`, `shareDestination` (Task 2).
- Produces: `object RankingCalc { data class Entry(name: String, remainingM: Double, etaMin: Double?, arrived: Boolean, isSelf: Boolean); fun rank(self: Pair<String, LiveRouteHolder.RoutePoint>?, selfSpeedKmh: Double?, peers: Collection<RoomClient.Peer>, dest: RoomClient.SharedDest, arrivalRadiusM: Double = 40.0): List<Entry> }` — orden: llegados primero (por orden de inserción estable), luego por remainingM asc; ETA = remaining / max(speed, 5 km/h) en minutos, null sin velocidad (va al fondo de los no llegados). Test puro: orden, llegada <40 m, ETA null al fondo, self marcado.
- UI: botón "Compartir con la sala" en RouteInfoChip (visible con sala activa y destino propio fijado); banner `SharedDestBanner` al recibir dest ajeno ("{rider} propone: {name} — Ir / ✕"); panel Leaderboard colapsable (chip "🏁 Posiciones" cuando hay dest compartido) con las entries.

- [ ] Steps: TDD RankingCalc (RED→GREEN) → wiring VM (exponer `sharedDest`, `ranking: StateFlow<List<Entry>>` combinando peers+roomState+posición propia cada emisión de peers) → UI → compile+tests → commit `feat: destino compartido de sala con posiciones y ETAs en vivo`.

---

### Task 5 (APP): carreras

**Files:**
- Modify: `feature/map/src/main/kotlin/com/revscope/feature/map/social/Leaderboard.kt` (modo carrera + countdown)
- Modify: `LiveMapViewModel.kt` + `LiveMapScreen.kt`

**Interfaces:**
- Consumes: `startRace/stopRace`, `roomState.race`, `RankingCalc` (Task 4), `NavigationVoice`/TTS existente (grep cómo anuncia la nav — reusar el mismo canal para "Llegaste — posición N").
- Produces: con dest compartido y sala: botón "Largar carrera" (cualquiera); al recibir race start → overlay countdown (start_at_ms - now, mostrando 5..1 → "¡YA!"); leaderboard pasa a modo carrera (posiciones prominentes); cruce propio (<40 m del dest) → TTS "Llegaste, posición N" una sola vez; "Detener" visible para quien largó.

- [ ] Steps: VM (estado carrera derivado de roomState.race + reloj; posición propia de llegada = índice en ranking al momento del cruce) → UI (countdown overlay + modo carrera) → compile+tests → commit `feat: carreras de sala — largada, posiciones en vivo y llegada anunciada`.

---

### Task 6 (APP): rutas alternativas + panorámica

**Files:**
- Modify: `core/navigation/src/main/kotlin/com/revscope/core/navigation/OsrmRouteParser.kt` (parsear TODAS las routes)
- Modify: `feature/map/src/main/kotlin/com/revscope/feature/map/routing/OsrmRouteFetcher.kt` (`alternatives: Boolean = false` → query `&alternatives=true`)
- Create: `core/navigation/src/test/kotlin/com/revscope/core/navigation/RouteScoringTest.kt` + `RouteScoring.kt` (sinuosidad)
- Modify: `LiveMapViewModel.kt` (lista de alternativas + selección), `LiveMapScreen.kt`/`RouteInfoChip` (chips Rápida/Alt/Curvas), `LiveMapLayers.kt` (líneas alternativas grises)

**Interfaces:**
- Produces: `object RouteScoring { fun sinuosity(routeDistanceM: Double, originLat: Double, originLon: Double, destLat: Double, destLon: Double): Double; fun labelAlternatives(routes: List<NavigationRoute>): List<String> }` — labels: la de menor duración = "Rápida"; otra con sinuosity > 1.15 = "Curvas"; resto "Alt". Fetcher: `fetchAlternatives(...): List<NavigationRoute>` (fetch actual intacto para reroute). VM: `routeAlternatives`, `selectAlternative(index)` — `_plannedRoute` = elegida; nav/carrera usan la elegida. Layers: source `src-rutas-alt` con las no elegidas en gris.
- Test puro: sinuosidad de ruta recta ≈1.0; etiquetado con 3 rutas sintéticas.

- [ ] Steps: TDD RouteScoring → parser lista → fetcher → VM/UI/layers → compile+tests → commit `feat: rutas alternativas con etiqueta de curvas para moto`.

---

### Task 7: Verificación integral F

- [ ] App: `"$GRADLE" test :app:assembleDebug` exit 0 real. Server: `python -m pytest` verde.
- [ ] `adb devices` — device: installDebug; sin device: skip.
- [ ] Checklist manual (user, requiere 2 dispositivos o 2 sesiones + server corriendo): crear sala, compartir destino, ver leaderboard, largar carrera, ghost, pins con nombre/velocidad, chips de rutas.

---

## Self-review

- Spec coverage: F1 ✅T1+T2; F2 ✅T2; F3 ✅T4; F4 ✅T5; F5 ✅T3; F6 ✅T6. Errores: legacy server ✅T2 (legacyServer flag → UI oculta con hint — wiring del hint en T4/T5 al mostrar botones sociales solo si `!legacyServer`); race sin dest ✅T5 (botón gated); peer sin velocidad ✅T4 (ETA null al fondo); WS caído mid-carrera → room_state repuebla al re-join manual ✅T1 (replay).
- Placeholders: T2 Step 2 heading = null con conexión en T3 — resolución explícita, no TBD. T4/T5 steps condensados con Produces exactos (componentes nuevos con contrato completo; detalles visuales a criterio del implementer con la paleta del mapa).
- Tipos: RoomMessage sealed + RoomState + RankingCalc.Entry + RouteScoring consistentes entre tasks; `SharedDest(rider,lat,lon,name)` igual en T2 (produce) y T4 (consume).
- Riesgo señalado: TestClient websockets síncronos con broadcast async — si el orden de recepción en tests es flaky, usar timeouts/receive con reintento (documentar en el report de T1).
