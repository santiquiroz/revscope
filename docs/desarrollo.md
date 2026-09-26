# Desarrollo

> Índice: [Módulos](#módulos) · [Stack](#stack) · [Compilar](#compilar) · [Tests](#tests) · [Pipeline OBD](#pipeline-obd) · [Room y migraciones](#room-y-migraciones) · [Guías de extensión](#guías-de-extensión) · [docs/superpowers](#docssuperpowers)
>
> See also: [Instalación](instalacion.md) · [Manual de usuario](manual-usuario.md) · [Configuración](configuracion.md) · [FAQ](faq.md)

This page targets contributors. English/Spanish mixed is fine here — the app UI itself is Spanish-only (see [FAQ](faq.md#en-qué-idiomas-está-disponible)), but this doc assumes you're comfortable reading Kotlin.

## Módulos

Multi-module Gradle project, declared in `settings.gradle.kts` (20 módulos):

| Módulo | Contenido |
|---|---|
| `:app` | Punto de entrada, navegación (`RevScopeNavGraph`), onboarding, `MainActivity` |
| `:core:common` | Utilidades sin dependencias de Android |
| `:core:data` | Room (`AppDatabase`, entities, DAOs, migraciones), DataStore preferences, backup |
| `:core:intelligence` | Proveedores de IA, detección de anomalías, eficiencia de conducción |
| `:core:obd` | Todo el pipeline OBD2/GPS/IMU: transporte Bluetooth, `PidScheduler`, `ObdSessionManager`, alertas, pico y placa, MCP, safety (caída) |
| `:core:maps` | Motor de mapas MapLibre: estilos, cascada de tiles (`.pmtiles` local > `.pmtiles` remoto > ráster OSM) y descarga del mapa offline de Colombia |
| `:core:navigation` | Navegación turn-by-turn sobre Ferrostar: parseo de rutas OSRM, maniobras y voz |
| `:core:designsystem` | Tokens (`RevScopeColors`, `RevScopeType` con cifras tabulares), `RevScopeTheme` y componentes compartidos (selector de vehículo, chips, avisos, filas etiqueta/valor, texto que se ajusta) |
| `:core:ui-testing` | `MatrizCaptura`: capturas JVM con Roborazzi sobre Robolectric (solo `testImplementation`) |
| `:feature:dashboard` | Pantalla Conducir, escáner de adaptador, Modo Pista |
| `:feature:map` | Pestaña Mapa (MapLibre): mapa en vivo, búsqueda (Photon), rutas (OSRM), navegación, mapa social |
| `:feature:workshop` | Pestaña Taller — las 14 herramientas, "Vehículo al día", chat con IA |
| `:feature:session` | Historial y reporte de viajes (pestaña Viajes) |
| `:feature:vehicle` | Perfiles de vehículo |
| `:feature:settings` | Pestaña Ajustes |
| `:feature:dtc`, `:feature:sensors`, `:feature:gear` | Herramientas de diagnóstico específicas, montadas dentro de Taller |
| `:feature:auto` | `RevScopeCarAppService` — panel para Android Auto |
| `:wear` | App independiente para Wear OS (mismo `applicationId`, streaming de ritmo cardíaco) |

## Stack

Versiones en `gradle/libs.versions.toml`: Kotlin 2.2.21 con KSP · AGP 8.10.1 · Jetpack Compose (BOM 2025.05.00, Material 3) · Hilt 2.56.2 para DI · Room 2.7 con migraciones reales · Vico 2.1 (gráficas) · MapLibre Android 13.4.1 (variante OpenGL) con tiles vectoriales PMTiles · Ferrostar 0.53.0 (solo `core`, navegación turn-by-turn) · OkHttp 4.12 (WebSocket de rodadas en grupo) · NanoHTTPD 2.3 (servidor MCP embebido) · exp4j (evaluación de fórmulas de PIDs) · coroutines/`StateFlow` en todo el estado reactivo. compileSdk 36, minSdk 26 (reloj: 30).

## Compilar

El repo incluye el wrapper de Gradle (**8.11.1**): no hace falta tener Gradle instalado, solo un JDK 17 o superior. Desde la raíz del repo (en Windows, `gradlew.bat` en vez de `./gradlew`):

```bash
./gradlew :app:assembleDebug          # APK debug del teléfono
./gradlew :wear:assembleDebug         # APK debug del reloj
./gradlew :core:obd:testDebugUnitTest # suite de tests de un módulo
./gradlew test                        # toda la suite de tests unitarios
```

Para depurar builds rotos rápido, dirígete al módulo específico primero (`:core:obd:compileDebugKotlin`, `:feature:workshop:compileDebugKotlin`, etc.) en vez de compilar todo el proyecto.

## Tests

**890+ pruebas unitarias JVM** (`@Test` de JUnit4, `./gradlew testDebugUnitTest`), más de la mitad en `core/obd/src/test/kotlin/...` — es el módulo con toda la lógica pura y offline (motores de PIDs, pico y placa, diagnóstico, detección de caída, cálculo de eco-score, etc.). El resto está en `feature/map`, `core/navigation`, `core/maps`, `core/intelligence`, `feature/settings`, `core/data`, `core/common`, `feature/workshop` y `app`. Los pocos tests de `androidTest` necesitan emulador o dispositivo.

### Capturas de pantalla (Roborazzi)

Las pantallas y componentes Compose tocados se renderizan en la JVM con `MatrizCaptura` (`:core:ui-testing`): dos teléfonos (360×640 dp xhdpi y 412×915 dp xxhdpi) por tres escalas de letra (1,0 · 1,3 · 2,0), con el tema RevScope. Los PNG viven en `<módulo>/src/test/screenshots/<Componente>_<w360|w412>_fs<100|130|200>.png`.

```bash
./gradlew :feature:dashboard:recordRoborazziDebug   # regenera las capturas de un módulo
./gradlew verifyRoborazziDebug                      # compara contra las guardadas; falla si cambian
```

- Las pantallas con `hiltViewModel()` se parten en `XxxScreen` (con ViewModel) y un `XxxContent(estado, acciones)` sin estado, que es lo que se captura.
- Un contenido que en el teléfono se recorre con scroll se captura entero con `altoMinimoDp` (alarga la ventana, no cambia ancho ni densidad).
- Antes de commitear capturas nuevas o regeneradas, **mírelas**: la captura prueba que no hay recortes, solapes ni textos partidos letra por letra, pero solo si alguien la revisa. Las fuentes de Google no se descargan en Robolectric (se ve la fuente por defecto) y el antialias puede diferir del teléfono.

### Regla de `Row` con texto y acciones

En un `Row` con texto y una acción (botón, ícono o enlace), **el texto lleva `Modifier.weight(1f)`** (o `weight(1f, fill = false)` si no debe estirarse). `Row` mide primero los hijos sin peso y en orden: si el texto no tiene peso se queda con casi todo el ancho y la acción recibe el sobrante, que Compose parte letra por letra (fue el caso de la tarjeta «Configurar adaptador» de Conducir). Si la acción puede crecer —etiquetas largas o letra al 200 %— se apila en una `Column` o se usa `FlowRow`. Pares etiqueta/valor: `FilaEtiquetaValor` del sistema de diseño. Áreas táctiles de 48 dp: preferir `Button`, `IconButton`, `TextButton`, `FilterChip` o `Surface(onClick)` con `heightIn(min = 48.dp)` en vez de `Modifier.clickable` sobre un `Text`.

Convenciones:
- **`org.junit.Assert`** (`assertEquals`, `assertTrue`, `assertNull`…) como base, no Truth ni Kotest — sigue el estilo de los tests ya existentes en el módulo antes de escribir uno nuevo.
- **TDD para toda lógica pura**: los motores sin dependencias de Android (`PicoYPlacaEngine`, `DiagnosticRules`, `EcoScoreCalculator`, `CrashDetector`, `ReadinessParser`…) se escriben test-primero — falla, implementación mínima, pasa, refactor.
- Los `object`/clases con efectos de Android (notificaciones, Bluetooth, Room) se mantienen delgados y delegan el cálculo real a funciones puras testeables por separado.

## Pipeline OBD

El corazón del proyecto vive en `core/obd/src/main/kotlin/com/revscope/core/obd/`:

- **Transporte** (`connection/ClassicBtTransport.kt`): el ELM327 habla **half-duplex** por Bluetooth clásico (SPP) — un comando, una respuesta, nunca en paralelo. El transporte serializa todo el acceso con un `Mutex` de coroutines para que ninguna herramienta de Taller ni el sondeo de fondo puedan pisarse con la telemetría.
- **Sondeo por prioridad** (`telemetry/PidScheduler.kt`): cuatro grupos de PIDs, cada uno en su propio `launch`:

  | Prioridad | Intervalo | Uso |
  |---|---|---|
  | 1 | 100 ms | RPM, velocidad, mariposa — lo que alimenta los gauges |
  | 2 | 500 ms | Carga, refrigerante, MAF, MAP, torque |
  | 3 | 2 000 ms | Temperaturas, fuel trims, O2, consumo |
  | 4 | 1 000 ms | PIDs de Taller (incluidos pedal y mariposa `45`/`47`/`49`/`4A`/`4B`/`4C`/`5A`) — **solo sondean con `setWorkshopMode(true)`**, es decir, mientras una pantalla de diagnóstico está abierta, para no gastar ancho de banda del enlace cuando nadie los está viendo |

  El voltaje no es un PID: lo lee `VoltagePoller` con `AT RV` cada 10 s. Esos intervalos son el preset `SamplingPreset.ESTANDAR_2S`; los demás presets (1 s, 500 ms, 250 ms, Máximo) aplican `mín(base, preset)` y el scheduler vivo los toma con `setPreset`. El periodo es **a tasa fija**: el siguiente ciclo arranca en `inicioCiclo + intervalo` (reloj monotónico inyectable para los tests), sin sumar la duración de las peticiones. Con la pantalla apagada (`setIdleMode`) los grupos se estiran ×5/×3/×2, salvo con un espectador MCP (`McpActivityTracker`: alguna `tools/call` en los últimos 60 s). El multiplicador de `BUFFER FULL` se duplica hasta ×8 y se divide entre 2 tras 30 s sin eventos. `MAXIMO` (intervalo 0) no se multiplica en vacío: con multiplicador > 1 o factor de reposo > 1 usa los intervalos de `CUARTO_SEGUNDO`, y con un `LectorDispositivo` inyectado el scheduler evalúa cada 5 s `CaptureSafeguards.decidirMuestreo` (Limitar → `CUARTO_SEGUNDO`, Detener → `ESTANDAR_2S`). `setPaused(true)` detiene los grupos antes de cada petición (lo usa la captura rápida).

- **Batching CAN**: en vehículos con protocolo CAN, `PidScheduler` empaqueta varios PIDs de Modo 01 en una sola petición (`packIntoFrames`), aprovechando que **un frame CAN carga 7 bytes útiles** de respuesta; si el ELM rechaza la sintaxis multi-PID, se desactiva el batching automáticamente y cae a sondeo individual.
- **Compuerta y concesión de diagnóstico** (`telemetry/PollingGate.kt`, `session/DiagnosticLease.kt`): el sondeo toma la compuerta por petición (`GatedTransport`) y una lectura de DTC la retiene durante toda su secuencia (03 → 07 → 0A → 02…), acotada por timeout, sin soltar el adaptador ni el viaje. Quien deja el ELM en un estado no estándar registra un `AjusteConcesion` para devolverlo al estándar mientras dura la concesión.
- **Captura rápida** (`telemetry/captura/`): `CapturaRapida` pausa los grupos, aplica el afinado (`ElmSpeedTuning`: `AT SH 7E0` + `AT CRA 7E8` solo en CAN 11-bit, `AT AT 2`; lo rechazado se omite) y `FastPoller` sondea 1-6 PIDs con varios por trama y sufijo `1`, con guardia de refrigerante cada 5 s. La marca de tiempo y la latencia de cada lote se toman dentro de la `PollingGate` (parámetro `enCanal`), así una lectura DTC por concesión o el `AT RV` en cola no desplazan la muestra. `RateMeter` mide Hz por PID y latencia p50/p95, `FastCaptureBuffer` es el anillo de 200 000 muestras con cursor (`seq`) que pagina `get_captura`, `CapturaCsv` + `ArchivoCaptura` escriben el CSV en ms y `CaptureSafeguards` decide continuar, limitar a 10 Hz o detener. Corre en el scope del enlace y revierte todo en un `finally` NonCancellable: `ElmSpeedTuning.revertir` primero resincroniza (`AT E0` hasta un `OK` limpio, que absorbe el `STOPPED` y la respuesta rezagada del intercambio cortado), reintenta cada AT sin `OK` y devuelve lo que no pudo revertir; si queda el filtro al ECM, la `PollingGate` guarda un `AjusteConcesion` que lo reintenta antes de la siguiente concesión. `FakeElmTransport.modelarInterrupcion` modela ese ELM ocupado en los tests. Las muestras entran al mismo flujo que el sondeo; `SessionRecorder` guarda como mucho una fila cada ~90 ms por PID.
- **Circuit breaker**: `MAX_CONSECUTIVE_LINK_FAILURES = 3` — tres pares petición/respuesta fallidos seguidos y `PidScheduler` da el enlace por muerto, lo que dispara la clasificación de pérdida de enlace en `ObdSessionManager`.
- **Clasificación motor-apagado vs falla transitoria**: antes de soltar el transporte, `ObdSessionManager.classifyLinkLoss` sondea `AT RV\r` (voltaje — si el adaptador sigue respondiendo, sigue alimentado) y luego `010C\r` (RPM). Adaptador vivo + ECU en silencio = **motor apagado** → cierre limpio (`finalShutdown`, para GPS/IMU, notificación resumen). Cualquier otra combinación = **falla transitoria** → reintento.
- **Backoff de reconexión**: `15s → 30s → 60s → 60s` (`AUTO_RECONNECT_BACKOFF_MS`), con 15s de gracia final antes de rendirse y cerrar limpio.

## Room y migraciones

Base de datos en **versión 14** (`core/data/.../db/AppDatabase.kt`), con migraciones reales acumuladas desde la 9 (`Migrations.kt`: `MIGRATION_9_10` … `MIGRATION_13_14`) — **sin `fallbackToDestructiveMigration()`**: un salto de versión sin ruta de migración explícita debe fallar ruidosamente, no borrar los datos del usuario en silencio (fue justamente un incidente de pérdida de datos el que llevó a esta regla).

Patrón para cualquier cambio de esquema:
1. `ALTER TABLE ... ADD COLUMN` aditivo (nunca `DROP`/`RENAME` destructivo) o `CREATE TABLE IF NOT EXISTS` para tablas nuevas, dentro de un `object MIGRATION_N_M : Migration(N, M)`.
2. Subir `version` en `AppDatabase` y encadenar la migración en `DataModule` (`.addMigrations(...)`).
3. **Verificar el SQL de la migración contra el esquema exportado** en `core/data/schemas/com.revscope.core.data.db.AppDatabase/<version>.json` (Room lo genera al compilar) — columna por columna, tipos y `DEFAULT` exactos.
4. Instalar sobre una base de datos real con datos, no solo un emulador limpio, y confirmar que las tablas existentes (viajes, perfiles) siguen intactas después de migrar.

## Guías de extensión

### Agregar un PID (parámetro OBD)

Edita `core/obd/src/main/assets/pids_mode01.json`, agregando un objeto con `mode`, `pid`, `name`, `nameEs`, `bytes`, `formula` (expresión exp4j con variables `A`-`D`), `unit`, `min`, `max` y `priority` (1-4, ver [tabla de arriba](#pipeline-obd)). Si es un parámetro propietario del fabricante en vez de estándar, primero descúbrelo con la herramienta "Escáner Mode 22" y considera si debería vivir como PID personalizado del usuario en vez de en el JSON base.

### Agregar una regla de diagnóstico

`core/obd/src/main/kotlin/com/revscope/core/obd/workshop/DiagnosticRules.kt` — objeto puro con una función `evaluarX(...): Diagnosis` por parámetro, cada una con TDD en `DiagnosticRulesTest.kt`. Sigue el patrón existente: `Nivel` (OK/ATENCION/FALLA), umbrales como constantes nombradas en el companion, mensaje de causa probable en español.

### Agregar una ciudad de pico y placa

`core/obd/src/main/kotlin/com/revscope/core/obd/legal/PicoYPlacaEngine.kt` define el motor (`CityRules`, `Scheme.WEEKDAY_ROTATION` o `Scheme.DATE_PARITY`, `check(...)`) y `CityRegistry.kt` la lista de ciudades incorporadas (id, nombre, coordenadas, radio de detección GPS y sus `CityRules`, o `null` si aún no hay reglas confirmadas — así está hoy Cali). Cualquier ciudad nueva necesita TDD en `PicoYPlacaEngineTest.kt` cubriendo al menos: dentro/fuera de restricción, dentro/fuera de horario, fin de semana, y vigencia vencida.

### Agregar un proveedor de IA

`core/intelligence/src/main/kotlin/com/revscope/core/intelligence/provider/` — implementa `AiProvider` (interfaz en `AiProvider.kt`, junto a `AnthropicProvider`, `OpenAiProvider`, `GeminiProvider` y `OpenAiCompatibleProvider` como referencia) y regístralo en `AiProviderFactory.kt`. Los parsers de respuesta de cada proveedor viven en `AiResponseParsers.kt` con sus propios tests contra fixtures JSON sintéticos.

### Agregar una herramienta MCP

`core/obd/src/main/kotlin/com/revscope/core/obd/mcp/` — cada tool es una clase que implementa `McpTool` (`name`, `description`, `inputSchema`, `call(arguments): String`), siguiendo el patrón de `GetEstadoTool.kt` / `GetViajesTool.kt`. Regístrala en `core/obd/src/main/kotlin/com/revscope/core/obd/di/McpModule.kt` para que `McpDispatcher` la incluya en `tools/list` y `tools/call`. Ver la lista completa de tools activas en [Configuración → Servidor MCP](configuracion.md#servidor-mcp-red-local).

## docs/superpowers

`docs/superpowers/` guarda el historial de diseño del proyecto: `specs/` (especificaciones de features antes de implementarlas) y `plans/` (planes de ejecución paso a paso, con comandos de build y criterios de aceptación, que se fueron ejecutando en orden cronológico). No es documentación de usuario — es el registro de **por qué** el código quedó como quedó, útil para entender decisiones de diseño (por ejemplo, por qué el pico y placa distingue esquemas por ciudad, o por qué `ObdSessionManager` clasifica la pérdida de enlace antes de reconectar) sin tener que arqueológicamente reconstruirlas desde los commits.

---

¿Buscas cómo usar la app en vez de cómo está construida? Ve al [Manual de usuario](manual-usuario.md).
