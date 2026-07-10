# Plan 6: Viaje sin adaptador (GPS), verificación de kilometraje, IA ampliada y MCP local

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development. EN ORDEN.

## Global Constraints
- Gradle: `C:\Users\santi\.gradle\wrapper\dists\gradle-8.11.1-bin\bpt9gzteqjrbo1mjrsomdt32c\gradle-8.11.1\bin\gradle.bat` (=$GRADLE) desde c:/personal/OBD2.
- Commits español, NUNCA Co-Authored-By. UI español. CancellationException se relanza.
- **REGLA DE ORO de este plan: cero regresiones al camino OBD.** El modo GPS es aditivo: ninguna rama existente de conexión/telemetría/cierre cambia de comportamiento cuando hay adaptador. Los tests existentes (350+) intactos.

---

### Task 1: Viaje sin adaptador (sesión GPS)

1. `ObdSessionManager`: nuevas funciones `startGpsSession()` / `stopGpsSession()` — camino PARALELO que NO toca connect/startTelemetry:
   - `startGpsSession()`: guard (no si ya hay sesión OBD o GPS activa); crea SessionEntity con `adapterName = "GPS"` y perfil activo; `_currentSessionIdFlow.value = id` (eso ya arranca GPS+IMU+alerters+crash en el service); arranca `ObdForegroundService.start`; flag interno `gpsSessionActive`; publica pseudo-reading `ObdReading("GPS_SPEED", kmh, "km/h")` — alimentado desde un callback del service (ver 3). NO llama resetSessionFlags de MIL ni voltage/mil watchers (no hay bt).
   - `stopGpsSession()`: cierra sesión (SessionAggregator), detiene servicio (vía requestShutdown con la gracia de caída intacta), notificación resumen igual.
   - Si el usuario conecta un adaptador con sesión GPS activa: `connectToDevice` primero `stopGpsSession()` (cierre limpio) — verificar que connect ya cierra sesión previa vía stopTelemetry y no duplicar.
2. `SessionAggregator.close`: fallback GPS — si no hay puntos "0D": `maxSpeed` = MAX(speedKmh) de gps_points, `distanceKm` = haversine acumulada de gps_points (agregar métodos a GpsDao si faltan; TripStatsCalculator tiene haversine — reusar). EcoScore: rpm vacío OK (ya tolera). Fuel: sin datos → null (ya tolera). NO cambiar el camino cuando SÍ hay 0D.
3. GPS speed en vivo: `GpsTrackRecorder.onLocation` ya recibe speed — agregar callback opcional `onSpeed: ((Float) -> Unit)? = null`; el service lo conecta al manager SOLO en modo GPS (manager expone `fun publishGpsSpeed(kmh: Float)` que setea el pseudo-reading GPS_SPEED y alimenta engineOffDetector.onSpeed para el auto-cierre).
4. **Auto-cierre por inactividad**: en modo GPS, job en el manager: si engineOffDetector no ve movimiento (>3 km/h) en 4 min → stopGpsSession() (mismo espíritu del motor apagado). El crash-grace sigue funcionando (el service ya lo maneja al perder sessionId con movimiento reciente).
5. **Conducir (Dashboard)**: cuando `connectionState != Connected`:
   - Botón primario "Iniciar viaje GPS" (reemplaza el vacío actual) / "Finalizar viaje GPS" cuando activo.
   - Con sesión GPS activa: velocímetro grande usa GPS_SPEED; gauges OBD (RPM/temp/boost/marcha) atenuados con etiqueta "Requiere adaptador"; lean/G si el dashboard los muestra (verificar) siguen.
   - Con adaptador conectado: TODO EXACTAMENTE COMO HOY (velocidad = 0D; ni rastro del modo GPS).
6. TrackMode/LaunchTimer en modo GPS: LaunchTimer usa 0D — en GPS mode alimentar launchTimer con GPS_SPEED?? NO en v1 (precisión GPS 1Hz insuficiente para 0-100 fino) — dejar launch timer solo-OBD, documentado. TrackMode usa GPS puro → ya funciona si la sesión existe (verificar que TrackModeScreen no exige Connected).
7. Historial: los viajes GPS se ven normales (chips de filtro iguales); el reporte muestra "Fuente: GPS" cuando adapterName=="GPS" (línea pequeña bajo la fecha).

Verificar: `$GRADLE :core:obd:testDebugUnitTest :app:assembleDebug` — tests existentes INTACTOS + tests nuevos de SessionAggregator fallback GPS (puro: puntos gps → distancia/maxSpeed). Commit: `feat: viaje sin adaptador con GPS — grabación, alertas y cierre automático sin tocar el camino OBD`.

---

### Task 2: Verificación de kilometraje real (odómetro OBD + registro anti-manipulación)

**Realidad técnica (documentar en UI):** el PID estándar 01 A6 (odómetro, 4 bytes ×0.1 km) existe solo en vehículos recientes (J1979-DA 2015+); muchas motos no lo exponen (la Apache probablemente no). Copias del odómetro en otros módulos (forense real de fraude) requieren herramientas de fabricante. Lo honesto y valioso: leer A6 cuando exista + Mode 22 propietario como alternativa + un REGISTRO HISTÓRICO propio a prueba de manipulación.

1. PID nuevo en pids_mode01.json: `{"mode":"01","pid":"A6","name":"Odometer","nameEs":"Odómetro ECU","bytes":4,"formula":"((A*16777216)+(B*65536)+(C*256)+D)/10","unit":"km","min":0,"max":429496729.5,"priority":4}` — verificar que evalFormula soporta 4 bytes (A-D sí).
2. `OdometerVerifier` (core/obd/.../workshop/): al conectar (tras negotiation, one-shot): lee A6 vía rawExchange; si soportado → guarda lectura histórica en DataStore por perfil (JSON array {epochMs, km} — máx 50 entradas) y compara: (a) lectura < lectura anterior → ALERTA ROJA "el odómetro retrocedió — posible manipulación"; (b) delta odómetro ECU vs delta km GPS acumulado de la app entre lecturas con desviación >20% → ámbar "el odómetro avanza menos que la distancia GPS registrada". Resultado como Diagnosis reutilizando DiagnosticRules.Nivel.
3. UI: card en Taller sección Vehículo "Verificación de kilometraje": última lectura ECU, histórico (lista corta), estado (verde/ámbar/rojo/no soportado con explicación honesta "tu vehículo no expone el odómetro por OBD — usa el escáner Mode 22 para buscar el DID del fabricante"). Botón "Leer ahora" (conectado). Export CSV del histórico (CsvShare).
4. Integrar al chequeo de salud: ítem "Odómetro" cuando A6 soportado.
5. Tests puros: parser/fórmula A6, lógica de comparación (retroceso, desviación, primera lectura).

Commit: `feat: verificación de kilometraje — lectura del odómetro ECU e histórico anti-manipulación`.

---

### Task 3: IA ampliada — "Pregúntale al mecánico" con contexto del vehículo

Pantalla de chat simple en Taller ("Mecánico IA"): input + historial de la conversación (en memoria por sesión de pantalla); cada pregunta va al AiProvider actual con system prompt que incluye CONTEXTO REAL: perfil activo (marca/tipo/km), último chequeo de salud (items), DTCs activos si hay, lecturas en vivo clave si conectado (rpm/temp/voltaje/trims), últimos 3 viajes (distancia/eco). Prompt system: "Eres un mecánico experto en <tipo>. Responde corto y práctico en español. Datos actuales del vehículo: <json compacto>". Sin web search (no lo necesita). Requiere API key → mismo empty-state del DTC. Historial NO persistido (v1). Card en Taller Diagnóstico.
Commit: `feat: chat de mecánico con IA con contexto real del vehículo`.

---

### Task 4: Servidor MCP en red local (las IAs del PC pueden consultar el vehículo)

**Qué es**: RevScope expone un servidor MCP (Model Context Protocol) por HTTP en la red local — Claude Desktop/LM Studio/cualquier cliente MCP del PC se conecta a `http://<ip-del-celular>:8765/mcp` y obtiene TOOLS del vehículo real.
1. Dependencia: NanoHTTPD (org.nanohttpd:nanohttpd:2.3.1 — liviano) o Ktor CIO embedded si ya hay ktor (verificar catálogo; NanoHTTPD más simple). Implementar MCP Streamable HTTP transport MINIMAL: POST /mcp con JSON-RPC 2.0: métodos `initialize` (capabilities tools), `tools/list`, `tools/call`. (Sin SSE en v1 — streamable http con respuesta única JSON es válido para tools.)
2. Tools expuestos (solo lectura, JSON): `get_estado` (conexión, perfil activo, lecturas en vivo), `get_viajes` (últimos N con stats), `get_viaje_detalle` (por id: agregados), `get_chequeo_salud` (último informe), `get_dtc` (si conectado, lee), `get_mantenimiento`, `get_documentos` (al día). Cada tool = función suspend que consulta daos/manager existentes.
3. Seguridad: apagado por defecto; toggle en Ajustes "Servidor MCP (red local)" + token generado (mostrado en UI, header Authorization: Bearer requerido); bind solo a la IP WiFi local; foreground service propio liviano o adjunto al existente (notificación "Servidor MCP activo en http://IP:8765" con acción detener). Solo mientras la app viva.
4. Ajustes: sección "Servidor MCP" con toggle, URL mostrada, token copiable, e instrucciones cortas ("En Claude Desktop: agregar servidor MCP tipo streamable-http con esta URL y token").
5. Tests puros: el dispatcher JSON-RPC (initialize/tools list/call desconocido/args inválidos) con requests sintéticos.

Commit: `feat: servidor MCP en red local — tus IAs del PC consultan el estado real del vehículo`.

---

### Task 5 (inline): build + install + push + verificación en dispositivo
