# MCP de taller avanzado: lecturas completas, escrituras UDS y catálogo propietario

Fecha: 2026-10-02 · Rama: `feature/mcp-taller-avanzado` (apilada sobre `mejoras/auditoria-2026-09-23`)

## Origen

Tras un cambio de bujía, la Apache 160 4V "se desfuerza y suena a crispetera" a ciertas RPM. El dueño
quiere que la IA (Claude en el PC vía MCP) pueda hacer lo que hace un escáner de taller: leer todo lo
que el vehículo expone y ejecutar servicios (borrar códigos, reiniciar la ECU, rutinas de fabricante).

Corrección registrada: ninguna ECU de moto guarda un "valor de bujía" que haya que resetear. Lo real
son las adaptaciones aprendidas (LTFT, PID 07, que la Apache sí reporta); el modo 04 suele borrarlas.

## Decisiones del dueño

| Tema | Decisión |
|---|---|
| Autorización de escrituras | Toque Permitir/Rechazar en el teléfono por cada escritura + interruptor de bypass |
| Escrituras estándar | Modo 04, UDS $14 por módulo, UDS $11 reinicio, modo 08 |
| Lectura libre | Sí, filtrada por lista blanca de servicios de solo lectura |
| Propietario (TVS, Mazda…) | "Toda la libertad": catálogo de operaciones de fabricante + comandos crudos de escritura |

## Supuestos (aceptados con "sí a todo")

- Las guardas físicas no se saltan nunca, ni con bypass: adaptador conectado y vehículo detenido
  (velocidad 0D reciente = 0). El reinicio de ECU exige además motor apagado (RPM 0C reciente = 0).
- El bypass vive en memoria: se apaga al detener el servidor MCP o al morir el proceso.
- Toda escritura queda en un registro de auditoría (qué, a qué módulo, quién autorizó, respuesta).
- El borrado guarda antes la foto de diagnóstico (ya lo hace `borrar_dtc` con `antes`/`despues`).
- Bloqueo duro, también con bypass: programación/flasheo (`10 02`, `34`–`38`, `3D`). Un ELM327 por
  Bluetooth no flashea de forma confiable; un corte a mitad deja la ECU inservible.

## Estado de partida (rama de auditoría)

Ya existe: permisos `LECTURA`/`CONTROL`/`BORRADO` con interruptores en Ajustes, `borrar_dtc`
(modo 04, guarda de velocidad, relectura antes/después, aviso), `get_dtc` (01 01, 03, 07, 0A,
freeze frame), concesión de diagnóstico (`withDiagnosticLease`), `targetedExchange(header, req)`.

## Diseño

### Permisos

Nuevo `McpPermiso.ESCRITURA`, activo solo si `MCP_CONTROL_ENABLED` y el nuevo
`MCP_WRITE_ENABLED` están encendidos (mismo patrón que `BORRADO`). El dispatcher no cambia.

### Puerta de escritura (`mcp/escritura/`)

Toda tool que escribe pasa por `EjecutorEscritura.ejecutar(solicitud, accion)`:

1. Guardas físicas (`GuardasEscritura`, pura): conectado, detenido, motor apagado si se pide.
2. Validación del comando (`ComandoHex`, pura): hex válido, sin servicios de flasheo.
3. Autorización (`AutorizadorEscritura`): bypass activo → permitido; si no, notificación de alta
   prioridad con Permitir/Rechazar (`ConfirmacionEscrituraReceiver`), espera 45 s; sin respuesta =
   rechazado. 45 s queda por debajo del timeout típico de clientes MCP (60 s).
4. Ejecución dentro de `withDiagnosticLease`.
5. Registro (`RegistroEscrituras`): JSONL en `filesDir/mcp_escrituras.jsonl`, últimas 500 entradas.

`BypassEscrituras` (@Singleton, `StateFlow<Boolean>`): lo enciende el interruptor de Ajustes (con
diálogo de advertencia) y lo apaga `McpServerController.stop()`. Mientras está activo, la
notificación del servidor MCP lo dice.

### Protocolo

- `RespuestaUds` (pura): reensambla tramas ISO-TP con headers (`AT H1`: `7E8 03 7F 22 31`), separa por
  header de respuesta, distingue positiva (servicio+0x40), negativa (`7F SS NRC` con nombre del NRC),
  `NO DATA`/error, y toma la respuesta final cuando hay `78` (response pending).
- `ComandoHex` (pura): normaliza (`"22f190"` → `"22 F1 90"`), valida y clasifica por servicio:
  - Lectura permitida: `01 02 03 06 07 09 0A 19 21 22 1A`, `10 01`, `10 03`, `3E`, y `AT RV`/`AT DPN`/`AT I`.
  - Bloqueado siempre: `10 02`, `34 35 36 37 38 3D`, y cualquier `AT` fuera de la lista anterior.
  - El resto es escritura (pasa por la puerta).

### Tools nuevas

Lectura (`LECTURA`):

| Tool | Hace |
|---|---|
| `get_info_ecu` | VIN (09 02), CALID (09 04), CVN (09 06), nombre ECU (09 0A), protocolo, voltaje |
| `get_mode06` | Resultados de pruebas a bordo (MIDs soportados, valor vs límites, nombre) |
| `descubrir_modulos` | Sondea headers 11-bit candidatos y dice cuáles contestan |
| `leer_did` | UDS $22 de uno o varios DIDs (máx. 32) a un header opcional |
| `comando_lectura` | Comando crudo de la lista blanca de lectura; decodifica modo 01 con PidRegistry |
| `get_catalogo` | Lista el catálogo propietario filtrado por marca/modelo/tipo/texto |
| `leer_catalogo` | Ejecuta una entrada de tipo lectura del catálogo y aplica su fórmula |
| `get_registro_escrituras` | Últimas escrituras auditadas |

Escritura (`ESCRITURA`, siempre por la puerta):

| Tool | Hace |
|---|---|
| `borrar_dtc_modulo` | UDS `14 FF FF FF` (o grupo dado) a un header |
| `reiniciar_ecu` | UDS `11 01` (hard), `11 02` (key off/on) o `11 03` (soft); motor apagado |
| `prueba_a_bordo` | Modo 08: sin TID lista los soportados (08 00, sin puerta); con TID la ejecuta |
| `ejecutar_catalogo` | Ejecuta una entrada escritura/rutina del catálogo |
| `comando_escritura` | Secuencia cruda de pasos hex a un header (todo menos flasheo) |

Control (`CONTROL`): `guardar_en_catalogo` (agrega/actualiza una entrada en el catálogo del usuario).

`borrar_dtc` (existente) pasa también por la puerta de autorización (toque o bypass).

### Catálogo propietario

- Asset `core/obd/src/main/assets/catalogo_propietario.json` (fuentes públicas, cada entrada con URL
  de fuente y licencia; `verificado=false` salvo prueba en el vehículo) + `filesDir/catalogo_usuario.json`
  (lo que la IA o el dueño descubren). Mismo esquema; la entrada del usuario gana por `id`.
- Esquema por entrada: `id, marca, modelos[], anios, modulo, header, tipo (lectura|escritura|rutina),
  pasos[], formula (exp4j, bytes A–H tras el eco del servicio/DID), unidad, descripcion,
  requiereSecurityAccess, riesgo (bajo|medio|alto), fuente, licencia, verificado, notas`.
- `leer_catalogo` rechaza entradas cuyos pasos no sean todos de lectura; `ejecutar_catalogo` rechaza
  las de lectura (para eso está la otra) y pasa por la puerta.

### Ajustes (UI)

En la sección Servidor MCP: interruptor "Permitir escrituras de taller (UDS, modo 08, catálogo,
comandos crudos)" y botón "Saltar confirmaciones (bypass)" con diálogo de advertencia; ambos
dependen de "Permitir control desde MCP".

## Pruebas

Unitarias JVM para todo lo puro (`RespuestaUds`, `ComandoHex`, `GuardasEscritura`, catálogo y su
fórmula, autorizador con bypass/timeout/rechazo, registro) y por tool con `ObdSessionManager`
mockeado, siguiendo `BorrarDtcToolTest`. Build completo + suite `:core:obd:test` + APK debug.

## Tareas

1. Núcleo (orquestador): `RespuestaUds`, `ComandoHex`, permiso `ESCRITURA` + pref, puerta de
   escritura completa (guardas, autorizador, bypass, notificación + receiver, registro, ejecutor).
2. Lecturas (delegado): `get_info_ecu`, `get_mode06`, `descubrir_modulos` (mover `ModuleDiscovery`
   a core), `leer_did`, `comando_lectura`, `get_registro_escrituras`.
3. Escrituras (delegado): `borrar_dtc_modulo`, `reiniciar_ecu`, `prueba_a_bordo`,
   `comando_escritura`, puerta en `borrar_dtc`.
4. Catálogo (delegado + investigación): modelo, carga asset+usuario, `get_catalogo`, `leer_catalogo`,
   `ejecutar_catalogo`, `guardar_en_catalogo`; asset sembrado desde fuentes públicas.
5. Ajustes UI + wiring en `McpModule` (orquestador).
6. Docs del manual MCP, build, tests, APK.
