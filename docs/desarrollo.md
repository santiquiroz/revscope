# Desarrollo

> Índice: [Módulos](#módulos) · [Stack](#stack) · [Compilar](#compilar) · [Tests](#tests) · [Pipeline OBD](#pipeline-obd) · [Room y migraciones](#room-y-migraciones) · [Guías de extensión](#guías-de-extensión) · [docs/superpowers](#docssuperpowers)
>
> See also: [Instalación](instalacion.md) · [Manual de usuario](manual-usuario.md) · [Configuración](configuracion.md) · [FAQ](faq.md)

This page targets contributors. English/Spanish mixed is fine here — the app UI itself is Spanish-only (see [FAQ](faq.md#en-qué-idiomas-está-disponible)), but this doc assumes you're comfortable reading Kotlin.

## Módulos

Multi-module Gradle project, declared in `settings.gradle.kts`:

| Módulo | Contenido |
|---|---|
| `:app` | Punto de entrada, navegación (`RevScopeNavGraph`), onboarding, `MainActivity` |
| `:core:common` | Utilidades sin dependencias de Android |
| `:core:data` | Room (`AppDatabase`, entities, DAOs, migraciones), DataStore preferences, backup |
| `:core:intelligence` | Proveedores de IA, detección de anomalías, eficiencia de conducción |
| `:core:obd` | Todo el pipeline OBD2/GPS/IMU: transporte Bluetooth, `PidScheduler`, `ObdSessionManager`, alertas, pico y placa, MCP, safety (caída) |
| `:feature:dashboard` | Pantalla Conducir, escáner de adaptador, Modo Pista |
| `:feature:map` | Pestaña Mapa (osmdroid) |
| `:feature:workshop` | Pestaña Taller — las 14 herramientas, "Vehículo al día", chat con IA |
| `:feature:session` | Historial y reporte de viajes (pestaña Viajes) |
| `:feature:vehicle` | Perfiles de vehículo |
| `:feature:settings` | Pestaña Ajustes |
| `:feature:dtc`, `:feature:sensors`, `:feature:gear` | Herramientas de diagnóstico específicas, montadas dentro de Taller |
| `:feature:auto` | `RevScopeCarAppService` — panel para Android Auto |
| `:wear` | App independiente para Wear OS (mismo `applicationId`, streaming de ritmo cardíaco) |

## Stack

Kotlin 2.0 · Jetpack Compose (Material 3) · Hilt 2.51 para DI · Room 2.7 con migraciones reales · Vico 2.1 (gráficas) · osmdroid 6.1 (mapas OSM) · NanoHTTPD 2.3 (servidor MCP embebido) · exp4j (evaluación de fórmulas de PIDs) · coroutines/`StateFlow` en todo el estado reactivo.

## Compilar

> ⚠️ **El wrapper de Gradle no está incluido en el repositorio.** No hay `gradlew`/`gradlew.bat` en la raíz — hay que tener **Gradle 8.11.1** instalado en el sistema (o generar el wrapper tú mismo con `gradle wrapper --gradle-version 8.11.1` una vez que tengas cualquier Gradle disponible).

Con Gradle en el `PATH`, desde la raíz del repo:

```bash
gradle :app:assembleDebug            # APK debug del teléfono
gradle :wear:assembleDebug           # APK debug del reloj
gradle :core:obd:testDebugUnitTest   # suite de tests de un módulo
gradle test                          # toda la suite de tests unitarios
```

Para depurar builds rotos rápido, dirígete al módulo específico primero (`:core:obd:compileDebugKotlin`, `:feature:workshop:compileDebugKotlin`, etc.) en vez de compilar todo el proyecto.

## Tests

**400+ pruebas unitarias** (`@Test` de JUnit4), la gran mayoría en `core/obd/src/test/kotlin/...` — es el módulo con toda la lógica pura y offline (motores de PIDs, pico y placa, diagnóstico, detección de caída, cálculo de eco-score, etc.), más un puñado en `core/intelligence/src/test` y `feature/workshop/src/test`.

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
  | 1 | 100 ms | RPM, velocidad, marcha — lo que alimenta los gauges |
  | 2 | 500 ms | Temperatura, boost, voltaje |
  | 3 | 2 000 ms | Datos de baja frecuencia |
  | 4 | 1 000 ms | PIDs de Taller — **solo sondean con `setWorkshopMode(true)`**, es decir, mientras una pantalla de diagnóstico está abierta, para no gastar ancho de banda del enlace cuando nadie los está viendo |

- **Batching CAN**: en vehículos con protocolo CAN, `PidScheduler` empaqueta varios PIDs de Modo 01 en una sola petición (`packIntoFrames`), aprovechando que **un frame CAN carga 7 bytes útiles** de respuesta; si el ELM rechaza la sintaxis multi-PID, se desactiva el batching automáticamente y cae a sondeo individual.
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
