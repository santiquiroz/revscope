# CLAUDE.md — RevScope

Instrucciones de proyecto para asistentes AI. Contexto de arquitectura y estado para retomar sin perder hilo.

## Qué es RevScope

App Android de telemetría OBD2 en tiempo real, UI estilo HUD racing, open source (Apache 2.0). Se conecta a cualquier adaptador OBD2 (Bluetooth Classic, BLE, WiFi) y muestra RPM, velocidad, boost, torque, marcha estimada, fuel trims y códigos de falla — con una capa de IA que **aprende el vehículo específico** con el uso.

- Adaptador primario: Vgate iCar Pro 2S (Classic BT `Android-Vlink`, PIN 1234).
- Vehículos objetivo: Mazda CX-30 GT, Renault Kardian, Nissan March, TVS Apache 160 4V FI.

## Diferenciadores (la "inteligencia")

- **Gear learner adaptativo:** construye un modelo estadístico de los ratios de marcha con pares RPM/velocidad reales — deja de estimar y acierta para *tu* auto.
- **Detector de anomalías:** algoritmo online de Welford sobre cada lectura; conoce lo "normal" a temperatura de operación y marca drift fuera de 3σ (fuel trim, coolant, MAP vs throttle). Espera un baseline antes de alarmar (sin falsos positivos en frío).
- **DTCs con significado real**, no solo el código.

## Stack

Kotlin 2.0 · Jetpack Compose 1.7+ · MVVM + Clean Architecture · Hilt (DI) · Coroutines + StateFlow/SharedFlow · Room · Vico (gráficas Compose-native) · BluetoothSocket RFCOMM (Classic) + blessed-android-coroutines (BLE) · Min SDK API 26.

## Layout (multi-módulo Gradle)

| Módulo | Responsabilidad |
|---|---|
| `:app` | Entry point, navegación, wiring Hilt |
| `:core:obd` | Transport (BT/BLE/WiFi), protocolo ELM327, PidRegistry (fórmulas con exp4j) |
| `:core:data` | Room (sesiones, trips), repos |
| `:core:common` | Utilidades compartidas |
| `:core:intelligence` | Gear learner, anomaly detector (Welford), modelos adaptativos |
| `:feature:dashboard` `:gear` `:sensors` `:dtc` `:session` `:vehicle` `:settings` | Pantallas Compose por feature |

## Estado

- **Fase 1 completa:** Transport, protocolo ELM327, PidRegistry con exp4j, ~70 unit tests.
- **Fase 2 (siguiente):** PidScheduler + TelemetryEngine (orquestación del sampling de PIDs en tiempo real).
- Ver `PLAN.md` para el plan de implementación completo por fases.

## Comandos

```bash
./gradlew build                    # compilar todo
./gradlew test                     # unit tests (JVM)
./gradlew :core:obd:test           # tests de un módulo
./gradlew installDebug             # instalar en device/emulador
./gradlew lint                     # análisis estático
```

## Convenciones

- Clean Architecture: domain no depende de Android; data/infra implementan interfaces del domain.
- Coroutines + Flow para todo lo async/tiempo real; nada de callbacks crudos.
- Funciones atómicas, complejidad ciclomática baja; sin comentarios de doc salvo el POR QUÉ no obvio.
- TDD donde aplique; los cálculos de PIDs y la intelligence deben tener tests.
- Commits en español, sin `Co-Authored-By`.
