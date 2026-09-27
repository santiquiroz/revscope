# CLAUDE.md — RevScope

Instrucciones de proyecto para asistentes AI. Contexto de arquitectura y estado para retomar sin perder hilo.

## Qué es RevScope

App Android de telemetría OBD2 en tiempo real, UI estilo HUD racing, open source (Apache 2.0). Se conecta a adaptadores OBD2 ELM327 por Bluetooth Classic o BLE y muestra RPM, velocidad, boost, torque, marcha estimada, fuel trims y códigos de falla — con una capa de IA que **aprende el vehículo específico** con el uso.

- Adaptador primario: Vgate iCar Pro 2S (Classic BT `Android-Vlink`, PIN 1234).
- Vehículos objetivo: Mazda CX-30 GT, Renault Kardian, Nissan March, TVS Apache 160 4V FI.

## Diferenciadores (la "inteligencia")

- **Gear learner adaptativo:** construye un modelo estadístico de los ratios de marcha con pares RPM/velocidad reales — deja de estimar y acierta para *tu* auto.
- **Detector de anomalías:** algoritmo online de Welford sobre cada lectura; conoce lo "normal" a temperatura de operación y marca drift fuera de 3σ (fuel trim, coolant, MAP vs throttle). Espera un baseline antes de alarmar (sin falsos positivos en frío).
- **DTCs con significado real**, no solo el código.

## Stack

Versiones según `gradle/libs.versions.toml`:

Kotlin 2.2.21 (KSP 2.2.21-2.0.5) · AGP 8.10.1 · Gradle 8.11.1 (wrapper) · JDK 17 · Jetpack Compose (BOM 2025.05.00, Material 3) · MVVM + Clean Architecture · Hilt 2.56.2 (vía KSP) · Coroutines 1.9 + StateFlow/SharedFlow · Room 2.7.1 · DataStore · WorkManager · Vico 2.1.2 (gráficas) · Roborazzi 1.75.0 (capturas JVM) · exp4j (fórmulas de PIDs) · MapLibre Android 13.4.1 (variante OpenGL) + PMTiles · Ferrostar 0.53.0 (solo `core`, navegación turn-by-turn) · OkHttp 4.12 (WebSocket) · NanoHTTPD 2.3.1 (servidor MCP) · Car App Library 1.4.0 · Wear Compose 1.4.0 + Health Services · BluetoothSocket RFCOMM (Classic) + blessed-android-coroutines 0.4.2 (BLE) · compileSdk 36, targetSdk 35, minSdk 26 (reloj: minSdk 30).

## Layout (multi-módulo Gradle)

Los 20 módulos de `settings.gradle.kts`:

| Módulo | Responsabilidad |
|---|---|
| `:app` | Entry point, navegación (`RevScopeNavGraph`), onboarding, wiring Hilt, trabajos periódicos de WorkManager |
| `:core:common` | Utilidades compartidas (User-Agent HTTP `RevScopeHttp`, formato, export CSV) |
| `:core:data` | Room (`AppDatabase`, entities, DAOs, migraciones), DataStore, backup |
| `:core:intelligence` | Proveedores de IA, gear learner, detector de anomalías (Welford), eficiencia, debrief de viaje |
| `:core:obd` | Transporte (BT clásico y BLE), protocolo ELM327, PidRegistry (exp4j), `PidScheduler`, `ObdSessionManager`, alertas, radares, lluvia, pico y placa, MCP, detección de caída, rodadas en grupo (revscope-server), aviso de actualización |
| `:core:maps` | MapLibre: estilos, cascada de tiles (`.pmtiles` local > `.pmtiles` remoto > ráster OSM) y descarga del mapa offline de Colombia |
| `:core:navigation` | Navegación turn-by-turn sobre Ferrostar: parseo de rutas OSRM, maniobras y voz |
| `:core:designsystem` | Tokens (`RevScopeColors`, `RevScopeType` con cifras tabulares), `RevScopeTheme` y componentes compartidos |
| `:core:ui-testing` | `MatrizCaptura`: capturas JVM con Roborazzi + Robolectric (360/412 dp × letra 1,0/1,3/2,0) |
| `:feature:dashboard` | Pantalla Conducir, escáner de adaptador, Modo Pista |
| `:feature:map` | Pestaña Mapa: mapa en vivo, búsqueda (Photon), rutas (OSRM), navegación, mapa social |
| `:feature:workshop` | Pestaña Taller: herramientas de diagnóstico, "Vehículo al día", chat con IA |
| `:feature:session` | Historial y reporte de viajes |
| `:feature:vehicle` | Perfiles de vehículo |
| `:feature:settings` | Pestaña Ajustes |
| `:feature:dtc` `:feature:sensors` `:feature:gear` | Herramientas de diagnóstico montadas dentro de Taller |
| `:feature:auto` | `RevScopeCarAppService`: panel para Android Auto |
| `:wear` | App de Wear OS (mismo `applicationId`, streaming de ritmo cardíaco) |

## Estado

- **v1.21.0** (versionCode 25, `revscope.versionName` en `gradle.properties`), publicada por GitHub Releases (sideload, no Play Store).
- Ya implementado: telemetría OBD2 en tiempo real, Taller y diagnóstico, IA opcional con llave propia, servidor MCP en red local, radares y alertas por voz, detección de caída, Android Auto, Wear OS, rodadas en grupo, mapa MapLibre con mapa offline de Colombia y navegación turn-by-turn.
- **1 739 tests unitarios JVM** (`testDebugUnitTest`, la mayoría en `:core:obd`), incluidas las capturas de pantalla JVM (Roborazzi; PNG en `<módulo>/src/test/screenshots`), más un puñado de tests instrumentados en `androidTest` que necesitan emulador o dispositivo.
- `PLAN.md` es el plan original de la v1 (histórico); el diseño y los planes de cada feature posterior están en `docs/superpowers/`.

## Comandos

El repo trae el wrapper de Gradle 8.11.1; basta un JDK 17 o superior (en Windows, `gradlew.bat`).

```bash
./gradlew :app:assembleDebug              # APK debug del teléfono
./gradlew :wear:assembleDebug             # APK debug del reloj
./gradlew testDebugUnitTest               # unit tests (JVM) de todos los módulos
./gradlew :core:obd:testDebugUnitTest     # tests de un módulo
./gradlew :app:lintDebug                  # lint de Android
./gradlew recordRoborazziDebug            # regenerar capturas JVM (mirarlas antes de commitear)
./gradlew verifyRoborazziDebug            # comparar contra las capturas guardadas
./gradlew :app:installDebug               # instalar el teléfono en device/emulador
./gradlew :wear:installDebug              # instalar el reloj
```

## Convenciones

- Clean Architecture: domain no depende de Android; data/infra implementan interfaces del domain.
- Coroutines + Flow para todo lo async/tiempo real; nada de callbacks crudos.
- Funciones atómicas, complejidad ciclomática baja; sin comentarios de doc salvo el POR QUÉ no obvio.
- TDD donde aplique; los cálculos de PIDs y la intelligence deben tener tests.
- Commits en español, sin `Co-Authored-By`.
