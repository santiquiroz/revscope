# Mapa social estilo Wheelz — sub-proyecto F (2026-08-19)

Referencia del usuario: Wheelz.app (nav en grupo viral) — "esto es lo que quiero que sea el apartado del mapa finalmente". Selección del usuario: destino compartido + ETAs, carreras con posiciones en vivo, pins con velocidad y nombre, modo fantasma, rutas alternativas/panorámica. El look oscuro premium llega con G (PMTiles).

## Base existente (explorada)

Salas ya funcionan: `RoomClient` (WS OkHttp, 1 msg/s `{lat,lon,speed_kmh}`, peers por `rider`) contra `revscope-server` (`rooms.py`, 57 líneas: relay con whitelist fija de 3 campos, salas en memoria `{rider→WebSocket}`, sin estado de sala, sin replay, WS SIN auth). El cliente solo transmite con viaje activo (piggyback de LiveRouteHolder). Los pins de peers son genéricos sin nombre ni velocidad; el symbol layer no tiene textField y el estilo no define glyphs.

## Diseño

### F1. Protocolo v2 (ambos repos)

- **Envelope**: todo mensaje nuevo lleva `type`. Compat: mensaje sin `type` = posición (clientes/servers viejos siguen).
- Tipos: `pos` (lat, lon, speed_kmh, heading_deg nuevo), `dest` (lat, lon, name, by), `race` (action: start|stop, start_at_ms, by), `hello` (v: 2), `room_state` (dest?, race?) — server→cliente al unirse o tras hello.
- **Server** (`revscope-server/app/routers/rooms.py`): dispatch por type; `pos` mantiene proyección actual + heading_deg; `dest`/`race` se guardan en estado de sala y se broadcastean; al conectar un socket se le manda `room_state` con el estado vigente (replay para late joiners). Estructura: `_rooms[code] = {"members": {...}, "state": {...}}`.
- **Auth del WS**: cerrar el gap — el handshake WS valida identidad con el mismo `get_identity` (token por header; en `none` sigue self-declared). Nombre duplicado: sufijo numérico en vez de pisar el socket.
- **Feature-detect en el cliente**: al unirse manda `hello`; si no llega `room_state` en 3 s → modo legacy (solo posiciones; UI social avanzada deshabilitada con hint "el servidor necesita actualizarse").

### F2. RoomClient v2 (app)

- `sendRaw(type, payload)` que bypasea el throttle de posiciones (solo `pos` se throttlea).
- Listener con dispatch por `type`; sin type → Peer legacy. `Peer` gana `headingDeg: Double?`.
- `roomState: StateFlow<RoomState>` — `RoomState(dest: SharedDest?, race: RaceState?, legacyServer: Boolean)`.
- Ghost mode: `setGhost(Boolean)` — deja de emitir `pos` (sigue recibiendo). Persistido por sala (en memoria basta).

### F3. Destino compartido + ETAs/ranking

- En `RouteInfoChip` (con destino fijado y sala activa): botón "Compartir con la sala" → `dest`.
- Al recibir `dest`: banner "{by} propone destino: {name} — [Ir]"; tap = `setDestination(lat, lon)` local (ruta propia de cada uno).
- Ranking en vivo (cliente): por peer, `restanteM = haversine(peer, dest)`; ETA ≈ restante / max(velocidad, 5 km/h). Lista ordenada (leaderboard) en un panel colapsable: posición, nombre, restante, ETA. El propio resaltado. Sin servidor árbitro: cada cliente computa lo mismo con los mismos datos.

### F4. Carreras

- Con destino compartido activo, cualquier miembro puede "Largar carrera" → `race start` con `start_at_ms = now + 5000` → countdown 5s en todos → leaderboard pasa a modo carrera (posiciones grandes).
- Llegada: peer con `restanteM < 40` queda "llegó" con orden por el timestamp local de cruce (v1: sin arbitraje de server; discrepancias de segundos aceptadas y documentadas). El propio cruce dispara TTS "Llegaste — posición N".
- "Detener carrera" (quien la largó) → `race stop`.

### F5. Pins con velocidad y nombre + heading

- `MapStyleProvider.styleJson` gana `"glyphs": "https://protomaps.github.io/basemaps-assets/fonts/{fontstack}/{range}.pbf"` (labels requieren red hasta G; degradación sin red = pins sin texto, aceptada).
- Layer nuevo `peer-labels` (symbol con textField "{rider}\n{speed} km/h", offset sobre el pin, font Noto Sans Regular). Peers con heading: `iconRotate` según `headingDeg` con icono direccional nuevo (flecha/moto estilizada); sin heading, pin actual.
- Ghost peers no aparecen (no emiten).

### F6. Rutas alternativas + panorámica

- `OsrmRouteFetcher.fetch` gana `alternatives: Boolean` → `alternatives=true` (hasta 3 rutas); parser devuelve lista.
- Sinuosidad = distanciaRuta / haversine(origen, destino); la de mayor sinuosidad (si > 1.15 y no es la más rápida) se etiqueta "Curvas".
- `RouteInfoChip` muestra chips: "Rápida" | "Alt" | "Curvas 🏍" (los disponibles); seleccionar redibuja (activa cyan, resto gris) y la nav/carrera usa la elegida. Reroute NO pide alternativas (directo, como hoy).

## Errores

- Server legacy: todo lo social nuevo oculto + hint de actualización; posiciones siguen.
- Race sin destino compartido: botón deshabilitado.
- Peer sin velocidad: ETA "—" y va al fondo del ranking.
- WS caído a mitad de carrera: al reconectar (manual hoy), `room_state` repuebla.

## Fuera de alcance

Arbitraje de llegadas server-side, avatares/fotos, historial de carreras persistido, reconexión automática del WS (deuda preexistente anotada), rutas alternativas en reroute.

## Archivos

App: RoomClient.kt, ServerClient.kt (si hace falta header), LiveMapViewModel/Screen, LiveMapLayers.kt, MapStyleProvider.kt, OsrmRouteFetcher/OsrmRouteParser, RouteInfoChip, componentes nuevos (Leaderboard, SharedDestBanner). Server: app/routers/rooms.py (+ tests del relay).
