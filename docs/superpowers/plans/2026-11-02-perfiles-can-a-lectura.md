# Perfiles CAN — Plan A: perfil, lectura por bus y prueba en vivo

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** RevScope lee las señales broadcast del perfil CAN de un vehículo (marcha real, pata, embrague, kill switch, RPM y acelerador por bus) en modo Combinado o Solo bus, con fuente elegible por señal y prueba en vivo antes de usar cualquier señal; trae incluido el perfil de la TVS Apache RTR 160 4V.

**Architecture:** Paquete puro nuevo `core/obd/.../perfilcan/` (modelo, parser/validador, decodificador, resolutor de fuentes) + `LectorBusCan`, que abre ventanas `AT MA` bajo `PollingGate.conceder` y emite `ObdReading` con las mismas claves de siempre; `ObdSessionManager.startTelemetry` mezcla ese flujo con el del `PidScheduler` y filtra cada flujo según la fuente efectiva. Datos del vehículo en `VehicleProfileEntity` (migración 19→20); UI en `feature/vehicle` y tablero.

**Tech Stack:** Kotlin, coroutines/Flow, Hilt, Room, Jetpack Compose, org.json, JUnit4 + kotlinx-coroutines-test, Roborazzi.

**Spec:** `docs/superpowers/specs/2026-11-02-perfiles-can-design.md` (planes siguientes: B asistente, C MCP, D compartir/sugerencia).

## Global Constraints

- Identificadores y textos en español; funciones pequeñas de una responsabilidad; sin comentarios de documentación salvo el porqué no obvio (una línea).
- Solo señales con `estado = verificada` alimentan lecturas.
- Nunca publicar un valor viejo: caducidad → fuente estándar + aviso único por sesión.
- Perfil sin VIN, placa ni datos personales; datos CC-BY-SA-4.0 con `autor` y `atribucion`.
- Todo perfil (incluido el de la Apache) entra con sus señales `sin_verificar`.
- Modo Estándar = comportamiento actual sin cambios; vehículo sin perfil → Estándar.
- Protocolo no CAN (`AT DPN` fuera de 6-9) → el bus se ignora, sin errores.
- Commits en español, formato `<tipo>: <descripción>`, sin `Co-Authored-By`.

## Review Focus

1. Trama más corta que el campo de la señal → `null`, nunca excepción ni valor parcial (Task 2).
2. Marcha `07` (entre marchas) o cualquier valor de `invalidos` → no se publica; si dura, caduca y vuelve la marcha estimada (Tasks 2 y 4).
3. Adaptador sin modo monitor (`AT MA` responde `?` o falla) en Combinado → bus desactivado esa sesión, aviso único, PIDs normales, sin reintentar en bucle (Task 5).
4. Ventana interrumpida (cancelación, desconexión, excepción) → igual se mandan los comandos de restauración; el siguiente PID parsea bien (Task 5).
5. Llave OFF/ON o reconexión con señales de bus activas → nada congelado: caducan y el tablero vuelve a la fuente estándar (Task 4).

---

## Mapa de archivos

| Archivo | Responsabilidad |
|---|---|
| `core/obd/src/main/kotlin/com/revscope/core/obd/perfilcan/PerfilCan.kt` | Modelo inmutable (`PerfilCan`, `SenalCan`, enums) y claves |
| `.../perfilcan/CodecPerfilCan.kt` | JSON ↔ modelo + validación con errores legibles |
| `.../perfilcan/DecodificadorSenal.kt` | Bytes de trama → valor |
| `.../perfilcan/PerfilesCanRepositorio.kt` | Incluidos (assets) + propios (`filesDir/perfiles_can/`), adoptar, verificar |
| `.../perfilcan/ResolutorFuentes.kt` | Fuente efectiva por clave + caducidad |
| `.../perfilcan/LectorBusCan.kt` | Ventanas `AT MA` y flujo de lecturas de bus |
| `core/obd/src/main/assets/perfiles_can/tvs.apache_rtr_160_4v.2024.json` | Perfil incluido |
| `core/data/.../entities/VehicleProfileEntity.kt`, `db/Migrations.kt`, `db/AppDatabase.kt` | Columnas nuevas, migración 19→20 |
| `core/obd/.../session/ObdSessionManager.kt` | Cableado en `startTelemetry` |
| `feature/vehicle/.../perfilcan/` | Sección "Datos del bus CAN" y prueba en vivo |
| `feature/dashboard/...` | Indicadores pata/embrague/kill |

---

### Task 1: Modelo y codec del perfil

**Files:**
- Create: `core/obd/src/main/kotlin/com/revscope/core/obd/perfilcan/PerfilCan.kt`, `.../perfilcan/CodecPerfilCan.kt`
- Test: `core/obd/src/test/kotlin/com/revscope/core/obd/perfilcan/CodecPerfilCanTest.kt`

**Interfaces:**
- Produces:
  - `enum class TipoSenal { NUMERO, ENUM, BANDERA }`, `enum class OrdenBytes { BIG, LITTLE }`, `enum class EstadoSenal { VERIFICADA, SIN_VERIFICAR, APRENDIDA }`
  - `data class SenalCan(clave: String, nombre: String, id: String, bitInicio: Int, largo: Int, orden: OrdenBytes, firmado: Boolean, factor: Double, offset: Double, unidad: String, tipo: TipoSenal, valores: Map<Long, String>, invalidos: Set<Long>, periodoMs: Long, estado: EstadoSenal, origen: String)`
  - `data class VehiculoPerfil(marca: String, modelo: String, anios: String, tipo: String)`
  - `data class PerfilCan(version: Int, id: String, vehiculo: VehiculoPerfil, calibracionesEcu: List<String>, protocolo: String, licencia: String, autor: String, atribucion: String, senales: List<SenalCan>)`
  - `object ClavesSenal { GEAR="GEAR"; RPM="0C"; VELOCIDAD="0D"; TEMP_MOTOR="05"; TEMP_AIRE="0F"; ACELERADOR="11"; PATA_LATERAL; EMBRAGUE; KILL_SWITCH; MOTOR_EN_MARCHA; VELOCIDAD_RUEDA; val todas: Set<String> }` (las nuevas con su mismo nombre como valor)
  - `object CodecPerfilCan { fun leer(json: String): Result<PerfilCan>; fun escribir(p: PerfilCan): String }` — JSON con los nombres de campo de la spec §1 (enums en minúsculas: `"big"`, `"enum"`, `"verificada"`, `"sin_verificar"`).

- [ ] **Step 1: Tests que fallan**
  - `ida y vuelta conserva todos los campos` (perfil con una señal de cada tipo).
  - `rechaza version distinta de 1`, `rechaza id CAN no hexadecimal de 3 u 8 digitos`, `rechaza bitInicio mas largo fuera de 0..63`, `rechaza clave desconocida`, `rechaza json de mas de 64 KB`, `rechaza campo vin` — cada uno `assertTrue(r.isFailure)` y el mensaje contiene el campo culpable (`"version"`, `"id"`, `"bitInicio"`, `"clave"`, `"tamaño"`, `"vin"`).
  - `campos opcionales ausentes toman defaults`: sin `invalidos`/`valores` → vacíos; sin `estado` → `SIN_VERIFICAR`.
- [ ] **Step 2:** `./gradlew :core:obd:testDebugUnitTest --tests "*CodecPerfilCanTest*"` → FAIL (no compila).
- [ ] **Step 3:** Implementar modelo y codec con `org.json` (como `CodecCatalogo`). Un perfil inválido falla entero.
- [ ] **Step 4:** Mismo comando → PASS.
- [ ] **Step 5:** `git commit -m "feat: modelo y codec JSON de perfiles CAN con validación"`

### Task 2: Decodificador

**Files:**
- Create: `.../perfilcan/DecodificadorSenal.kt`
- Test: `core/obd/src/test/kotlin/com/revscope/core/obd/perfilcan/DecodificadorSenalTest.kt`

**Interfaces:**
- Consumes: `SenalCan` (Task 1).
- Produces: `object DecodificadorSenal { fun valor(senal: SenalCan, datos: ByteArray): Double?; fun texto(senal: SenalCan, datos: ByteArray): String? }` — `texto` aplica `valores` para ENUM y "Sí/No" para BANDERA; `null` si trama corta o valor crudo en `invalidos`.

Convención de bits (la de la spec): bit 0 = LSB del byte 0; un campo BIG de `largo` > 8 empieza en el byte `bitInicio/8` y sigue con los bytes siguientes (más significativo primero); LITTLE al revés; campos ≤ 8 bits dentro de un byte se extraen con desplazamiento `bitInicio % 8`.

- [ ] **Step 1: Tests con tramas reales de la Apache** (`hex("0000450000004604")` helper):
  - marcha: `500 0000450000004604` → 0.0 y texto `"N"`; `0000440003004B04` → 3.0 / `"3"`; byte 4 = `07` → `null`.
  - pata: `0000450000404604` (bit 46) → 1.0; `0000450000004604` → 0.0.
  - kill: `0000450000004604` (bit 58) → 1.0; `0000450000004600` → 0.0. Motor en marcha `0000450000004D0C` (bit 59) → 1.0.
  - RPM `300 0C0DF8000000D200` (bitInicio 8, largo 16, BIG) → 3576.0; acelerador byte 0 factor 0.5 → 6.0.
  - temp motor `500 00005C000000540C` byte 2 offset −40 → 52.0; aire byte 6 → 44.0.
  - embrague `300 000000010000D340` bit 24 → 1.0.
  - velocidad rueda `301 0240FFFF02010000` factor 0.01 → 5.76 (±1e-9).
  - `trama de 4 bytes para campo en byte 6 da null`; `campo firmado negativo` (largo 8, `FF` firmado → −1.0).
- [ ] **Step 2:** `--tests "*DecodificadorSenalTest*"` → FAIL.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** PASS.
- [ ] **Step 5:** `git commit -m "feat: decodificador de señales CAN con las tramas reales de la Apache como prueba"`

### Task 3: Repositorio de perfiles y perfil incluido de la Apache

**Files:**
- Create: `.../perfilcan/PerfilesCanRepositorio.kt`, `core/obd/src/main/assets/perfiles_can/tvs.apache_rtr_160_4v.2024.json`
- Test: `core/obd/src/test/kotlin/com/revscope/core/obd/perfilcan/PerfilesCanRepositorioTest.kt`

**Interfaces:**
- Consumes: `CodecPerfilCan`.
- Produces: `@Singleton class PerfilesCanRepositorio internal constructor(carpeta: File, incluidos: () -> List<String>)` (constructor `@Inject` con `@ApplicationContext` que lee `assets/perfiles_can/*.json`), con:
  - `fun disponibles(): List<PerfilCan>` (propios primero; un incluido con el mismo `id` que uno propio se oculta),
  - `fun leer(id: String): PerfilCan?`,
  - `fun adoptar(id: String): PerfilCan` (copia a `carpeta` con todas las señales `SIN_VERIFICAR` si aún no existe; devuelve la copia),
  - `fun marcarEstado(id: String, clave: String, estado: EstadoSenal): PerfilCan` (reescribe el archivo propio),
  - `val cambios: StateFlow<Int>` (contador que sube en cada escritura, para que la lectura recargue).

Perfil JSON de la Apache: las 11 señales de la tabla de la spec §7 con `periodoMs` 300→50, 301→10, 500→100; `GEAR` con `valores` 0..5 y `invalidos [7]`; todas `sin_verificar`, `origen "manual"`, `autor "santiquiroz"`, `atribucion "Mapeado con RevScope sobre una Apache RTR 160 4V (2026-11-02)"`, `calibracionesEcu ["U507EBS6B1A1a706"]`, `licencia "CC-BY-SA-4.0"`.

- [ ] **Step 1: Tests**: `el incluido de la Apache parsea y tiene 11 señales sin verificar` (lee el archivo real de `src/main/assets`); `adoptar copia una sola vez`; `marcarEstado persiste y sube cambios`; `un json corrupto en la carpeta se ignora sin romper disponibles()`; `id con barras o puntos dobles se rechaza` (no escapa de la carpeta).
- [ ] **Step 2:** FAIL. **Step 3:** Implementar. **Step 4:** PASS.
- [ ] **Step 5:** `git commit -m "feat: repositorio de perfiles CAN y perfil incluido de la TVS Apache RTR 160 4V"`

### Task 4: Fuentes por vehículo, migración y resolutor

**Files:**
- Modify: `core/data/src/main/kotlin/com/revscope/core/data/db/entities/VehicleProfileEntity.kt`, `core/data/src/main/kotlin/com/revscope/core/data/db/Migrations.kt`, `core/data/src/main/kotlin/com/revscope/core/data/db/AppDatabase.kt` (version 20 + registrar migración donde se registran las demás)
- Create: `.../perfilcan/ResolutorFuentes.kt`
- Test: `core/data/src/test/kotlin/com/revscope/core/data/db/MigrationTest.kt` (caso nuevo), `core/obd/src/test/kotlin/com/revscope/core/obd/perfilcan/ResolutorFuentesTest.kt`

**Interfaces:**
- Produces:
  - Columnas: `perfilCanId TEXT NULL`, `fuenteDatos TEXT NOT NULL DEFAULT 'ESTANDAR'`, `fuentesPorSenal TEXT NOT NULL DEFAULT '{}'`; `val MIGRATION_19_20`.
  - `enum class ModoFuente { ESTANDAR, COMBINADO, SOLO_BUS }`, `enum class FuenteSenal { AUTO, PID, BUS, GPS, CALCULADA }`
  - `class ResolutorFuentes(perfil: PerfilCan?, modo: ModoFuente, preferencias: Map<String, FuenteSenal>, esCan: Boolean, ahora: () -> Long)` con:
    - `fun usaBus(clave: String): Boolean` — true solo si `esCan`, modo ≠ ESTANDAR, la señal existe, está `VERIFICADA`, preferencia es `AUTO` o `BUS`, y está fresca;
    - `fun registrarTrama(clave: String)` (marca frescura) y `fun caducadas(): Set<String>` (claves que usaban bus y dejaron de estar frescas; cada una se reporta una sola vez por instancia);
    - frescura: última trama hace ≤ 3 × `max(periodoMs, intervaloVentanaMs)`, con `intervaloVentanaMs` parámetro del constructor (Combinado lo fija la Task 5; Solo bus = 0).
  - `fun ModoFuente.efectivo(perfil: PerfilCan?) = if (perfil == null) ESTANDAR else this`.

- [ ] **Step 1: Tests**:
  - Migración: `migra 19 a 20 con defaults` (fila vieja → `fuenteDatos = ESTANDAR`, `fuentesPorSenal = {}`, `perfilCanId = null`).
  - Resolutor: `señal sin_verificar nunca usa bus`; `verificada y fresca usa bus`; `preferencia PID gana sobre bus`; `modo ESTANDAR nunca usa bus`; `protocolo no CAN nunca usa bus`; `sin tramas en 3 periodos caduca y se reporta una sola vez`; `vuelve a usar bus cuando llegan tramas de nuevo`; `marcha invalida no cuenta como trama fresca` (el lector no llama `registrarTrama` cuando `valor` es null: test en Task 5; aquí `sin registrar no hay bus`).
- [ ] **Step 2:** FAIL. **Step 3:** Implementar (exportar el schema 20 de Room como las versiones anteriores). **Step 4:** `./gradlew :core:data:testDebugUnitTest :core:obd:testDebugUnitTest --tests "*ResolutorFuentes*"` PASS.
- [ ] **Step 5:** `git commit -m "feat: fuente de datos por vehículo (migración 19→20) y resolutor de fuentes con caducidad"`

### Task 5: LectorBusCan

**Files:**
- Create: `.../perfilcan/LectorBusCan.kt`
- Test: `core/obd/src/test/kotlin/com/revscope/core/obd/perfilcan/LectorBusCanTest.kt` (transporte falso como en `EscuchaCanTest`)

**Interfaces:**
- Consumes: `Transport.monitorear(comando, duracionMs, detener, alLinea)`, `LineaMonitor.parsear`, `PollingGate.conceder`, `DecodificadorSenal`, `ResolutorFuentes.registrarTrama`.
- Produces: `class LectorBusCan(transporte: Transport, gate: PollingGate, perfil: PerfilCan, modo: ModoFuente, resolutor: ResolutorFuentes, ahora: () -> Long)` con `fun lecturas(): Flow<ObdReading>` (una lectura por señal decodificada no nula, clave = `senal.clave`, unidad = `senal.unidad`, `value` numérico; ENUM/BANDERA como su valor numérico) y `val sinMonitor: StateFlow<Boolean>`.
  - Constantes: `VENTANA_COMBINADO_MS = 1.5 × max(periodoMs de señales verificadas)` con tope `200`, `INTERVALO_COMBINADO_MS = 500` (una ventana cada 500 ms, también el `intervaloVentanaMs` del resolutor), `VENTANA_SOLO_BUS_MS = 2_000` con pausa entre ventanas de 50 ms para que voltaje/MIL tengan turno.
  - Cada ventana: `gate.conceder("bus_can") { AT H1; AT S0; AT CAF0; AT CSM1; monitorear("AT MA", ventana); restaurar }` — restauración `AT CAF1, AT AR, AT S0, AT H0` en `finally` (igual que `EscuchaCan.RESTAURACION`; extraer a una constante compartida en lugar de duplicarla).
  - Solo se decodifican tramas de IDs presentes en el perfil; señales no verificadas no se emiten.
  - `AT MA` que responde `?` o lanza `UnsupportedOperationException` → `sinMonitor = true` y el flujo termina (no reintenta).

- [ ] **Step 1: Tests**: `una ventana manda setup, AT MA y restauracion en ese orden`; `restaura aunque monitorear lance IOException`; `emite GEAR 3 desde la trama 500 de tercera`; `no emite señales sin verificar`; `marcha 07 no emite ni registra frescura`; `adaptador sin AT MA marca sinMonitor y termina sin reintentar` (cuenta de `AT MA` enviados = 1); `combinado espera INTERVALO entre ventanas` (reloj de `runTest`).
- [ ] **Step 2:** FAIL. **Step 3:** Implementar. **Step 4:** PASS.
- [ ] **Step 5:** `git commit -m "feat: lector de señales del bus en ventanas AT MA bajo la compuerta de sondeo"`

### Task 6: Cableado en la sesión

**Files:**
- Modify: `core/obd/src/main/kotlin/com/revscope/core/obd/session/ObdSessionManager.kt` (`startTelemetry`, ~línea 907-925)
- Create: `.../perfilcan/FiltroFuentes.kt`
- Test: `core/obd/src/test/kotlin/com/revscope/core/obd/perfilcan/FiltroFuentesTest.kt`

**Interfaces:**
- Consumes: Tasks 3-5, perfil activo (`_activeProfile.value` con las columnas nuevas), `protocoloEsCan`, `pollingGate`.
- Produces:
  - `object FiltroFuentes { fun aceptaPid(r: ObdReading, res: ResolutorFuentes): Boolean; fun aceptaBus(r: ObdReading, res: ResolutorFuentes): Boolean; fun aceptaDerivada(r: ObdReading, res: ResolutorFuentes): Boolean }` — PID/derivada se descartan cuando `res.usaBus(r.pid)`; bus se descarta cuando no.
  - `ObdSessionManager.avisoBus: StateFlow<String?>` — último aviso único ("No llegan datos del bus para Marcha; usando la estimación", "Tu adaptador no permite escuchar el bus; usando el OBD estándar").
  - En `startTelemetry`: si el modo efectivo ≠ ESTANDAR y `protocoloEsCan`, crear resolutor + lector sobre `bt` (no `polled`: el lector ya toma la compuerta), `rawFlow = merge(scheduler.filter(aceptaPid), captura, lector.filter(aceptaBus))`, `derivedFlow.filter(aceptaDerivada)`; en SOLO_BUS `scheduler.setPaused(true)`; un `launch` que cada segundo publica en `avisoBus` las `caducadas()` y el `sinMonitor`. Recargar al cambiar `PerfilesCanRepositorio.cambios` o el perfil del vehículo reiniciando solo el lector.
  - Mientras `pollingGate.duenoConcesion` sea de un lease de diagnóstico, el lector simplemente espera su turno en `conceder` (sin código extra).

- [ ] **Step 1: Tests** del filtro: `pid 0C se descarta cuando el bus es la fuente`; `GEAR derivada se descarta con GEAR de bus fresca`; `GEAR derivada vuelve cuando la del bus caduca`; `lectura de bus de señal no elegida se descarta`.
- [ ] **Step 2:** FAIL. **Step 3:** Implementar filtro y cableado. **Step 4:** `./gradlew :core:obd:testDebugUnitTest` completo PASS (los 1 700+ existentes siguen verdes).
- [ ] **Step 5:** `git commit -m "feat: la sesión mezcla lecturas del bus según la fuente efectiva de cada señal"`

### Task 7: Ajustes del vehículo y prueba en vivo

**Files:**
- Create: `feature/vehicle/src/main/kotlin/com/revscope/feature/vehicle/perfilcan/DatosBusCanSection.kt`, `.../perfilcan/PruebaEnVivoScreen.kt`, `.../perfilcan/PerfilCanViewModel.kt`
- Modify: `feature/vehicle/.../VehicleProfileScreen.kt` (insertar la sección), navegación en `app/.../navigation/Screen.kt` y `RevScopeNavGraph.kt`
- Test: `feature/vehicle/src/test/kotlin/com/revscope/feature/vehicle/perfilcan/PerfilCanViewModelTest.kt`

**Interfaces:**
- Consumes: `PerfilesCanRepositorio` (`disponibles`, `adoptar`, `marcarEstado`), DAO de vehículos (guardar `perfilCanId`, `fuenteDatos`, `fuentesPorSenal` como JSON `{"GEAR":"AUTO",...}`), `ObdSessionManager` para leer tramas en vivo: `fun probarSenal(senal: SenalCan): Flow<String?>` (nuevo, en `ObdSessionManager`, implementado con `LectorBusCan` de una sola señal sin filtro de estado; solo con conexión y protocolo CAN).
- Produces UI:
  - Sección "Datos del bus CAN" en el perfil del vehículo: selector de perfil (Ninguno + `disponibles()`), modo (Estándar / Combinado / Solo bus, con la frase de la spec §3 para cada uno), lista avanzada plegada por señal (`AUTO|PID|BUS|GPS|CALCULADA`, solo las opciones que aplican: GPS solo para `0D`, CALCULADA solo para `GEAR`). Elegir `0D = GPS` escribe también `PreferencesKeys.SPEED_SOURCE_GPS = true` (y PID/AUTO → false) para que el interruptor actual del tablero siga coherente.
  - Por señal: estado (Verificada ✓ / Sin verificar) y botón "Probar en vivo" → `PruebaEnVivoScreen`: nombre de la señal, valor decodificado grande en vivo, instrucción ("Mueve la pata lateral y mira si cambia"), botones "No, no coincide" (queda sin verificar) / "Sí, coincide" (`marcarEstado(VERIFICADA)`). ID/byte en letra pequeña. Sin conexión: mensaje y botones deshabilitados.
- [ ] **Step 1: Tests del ViewModel**: `elegir perfil adopta y guarda perfilCanId`; `cambiar modo persiste`; `0D en GPS escribe SPEED_SOURCE_GPS`; `confirmar en vivo marca verificada`; `sin conexion la prueba queda deshabilitada`.
- [ ] **Step 2:** FAIL. **Step 3:** Implementar, siguiendo el estilo de las pantallas del Taller (botones ≥ 48 dp). **Step 4:** `./gradlew :feature:vehicle:testDebugUnitTest :app:assembleDebug` PASS.
- [ ] **Step 5:** `git commit -m "feat: datos del bus CAN en el perfil del vehículo y prueba en vivo por señal"`

### Task 8: Indicadores en el tablero y documentación

**Files:**
- Modify: `feature/dashboard/src/main/kotlin/com/revscope/feature/dashboard/DashboardScreen.kt` (junto a la marcha, ~línea 96), `DashboardViewModel.kt`, `docs/manual-usuario.md`, `docs/configuracion.md`, `README.md` (sección de funciones si lista el Taller)
- Test: `feature/dashboard/src/test/kotlin/.../IndicadoresBusTest.kt` (función pura de estado + captura Roborazzi de la fila)

**Interfaces:**
- Consumes: lecturas `PATA_LATERAL`, `EMBRAGUE`, `KILL_SWITCH`, `MOTOR_EN_MARCHA` del mapa de lecturas; `avisoBus`.
- Produces: `fun indicadoresBus(lecturas: Map<String, ObdReading>): IndicadoresBus` con `pataAbajo`, `embrague`, `killOff`, `alerta: Boolean` (`pataAbajo && motorEnMarcha`); la fila solo aparece si alguna de esas lecturas existe; `avisoBus` como snackbar una vez.
- [ ] **Step 1: Tests**: `sin lecturas del bus no hay fila`; `pata abajo con motor en marcha es alerta`; `pata abajo con motor apagado no es alerta`; captura Roborazzi de la fila con pata abajo (mirarla antes de commitear).
- [ ] **Step 2:** FAIL. **Step 3:** Implementar + documentar (manual: cómo elegir el perfil, los tres modos y la prueba en vivo; configuracion.md: columnas nuevas no aplican; README: una línea). **Step 4:** `./gradlew testDebugUnitTest :app:assembleDebug` PASS.
- [ ] **Step 5:** `git commit -m "feat: indicadores de pata, embrague y kill switch en el tablero; documentación de perfiles CAN"`
