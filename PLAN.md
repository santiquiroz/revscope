# RevScope — Plan de Implementación v1

## Resumen

App Android de telemetría OBD2 en tiempo real con UI estilo HUD racing. Open source (Apache 2.0).  
Adaptador primario: Vgate iCar Pro 2S (Classic BT `Android-Vlink`, PIN 1234).  
Vehículos objetivo: Mazda CX-30 GT · Renault Kardian · Nissan March · TVS Apache 160 4V FI 2026.

---

## Stack tecnológico

| Capa | Tecnología | Razón |
|------|-----------|-------|
| Lenguaje | Kotlin | Idiomático Android moderno |
| UI | Jetpack Compose 1.7+ | UI declarativa, animaciones |
| Arquitectura | MVVM + Clean Architecture | Testeable, escalable |
| DI | Hilt | Estándar Android |
| Async | Coroutines + StateFlow/SharedFlow | Telemetría en tiempo real |
| BT Classic | Android BluetoothSocket (RFCOMM) | Vgate usa Classic BT en Android |
| BLE | blessed-android-coroutines | 6 UUID families, API 26+, coroutine-native |
| Base de datos | Room | Sesiones y trips |
| Gráficas | Vico (Compose-native) | Bonita, no MPAndroidChart anticuado |
| Android Auto | Car App Library 1.8 | v2 — templates únicamente |
| Min SDK | API 26 (Android 8.0) | Requerido por blessed-android-coroutines |
| Target SDK | API 35 (Android 15) | Última versión estable |

---

## Arquitectura de capas

```
┌──────────────────────────────────────────────────────┐
│  UI Layer (Jetpack Compose)                          │
│  ├─ DashboardScreen   (gauges tiempo real)           │
│  ├─ GearAnalyzerScreen (RPM vs torque, gear est.)    │
│  ├─ SensorGraphScreen  (líneas de tiempo)            │
│  ├─ DtcScreen          (errores DTC)                 │
│  ├─ SessionHistoryScreen (trips guardados)           │
│  ├─ VehicleProfileScreen (perfiles por vehículo)     │
│  └─ SettingsScreen     (adaptador, intervalos)       │
├──────────────────────────────────────────────────────┤
│  ViewModel Layer                                     │
│  ├─ DashboardViewModel                               │
│  ├─ GearViewModel                                    │
│  ├─ SessionViewModel                                 │
│  └─ ConnectionViewModel                              │
├──────────────────────────────────────────────────────┤
│  Domain / Use Cases                                  │
│  ├─ ConnectToAdapterUseCase                          │
│  ├─ StartTelemetrySessionUseCase                     │
│  ├─ ReadDtcCodesUseCase                              │
│  ├─ ClearDtcCodesUseCase                             │
│  ├─ SaveSessionUseCase                               │
│  └─ EstimateGearUseCase  (RPM/speed ratio)           │
├──────────────────────────────────────────────────────┤
│  TelemetryEngine                                     │
│  ├─ PidScheduler         (coroutine polling loop)    │
│  ├─ DerivedMetricsEngine (boost, gear, power calc)   │
│  └─ SessionRecorder      (Room writes)               │
├──────────────────────────────────────────────────────┤
│  OBD Protocol Layer                                  │
│  ├─ ElmCommandBuilder    (AT cmds + PID requests)    │
│  ├─ PidRegistry          (JSON → EvalEx runtime)     │
│  ├─ ResponseParser       (ASCII hex → typed values)  │
│  └─ ProtocolNegotiator   (ELM327 init sequence)      │
├──────────────────────────────────────────────────────┤
│  Connection Layer (sealed interface Transport)       │
│  ├─ ClassicBtTransport   (SPP/RFCOMM UUID std)       │
│  ├─ BleTransport         (6 UUID families auto-det.) │
│  └─ WifiTransport        (TCP socket :35000)         │
├──────────────────────────────────────────────────────┤
│  Data Layer                                          │
│  ├─ Room DB                                          │
│  │   ├─ SessionEntity                                │
│  │   ├─ TelemetryPointEntity                         │
│  │   └─ VehicleProfileEntity                         │
│  └─ DataStore (preferencias usuario)                 │
└──────────────────────────────────────────────────────┘
```

---

## Estructura de módulos Android

```
revscope/
├── app/                          # Módulo principal
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── kotlin/com/revscope/
│           ├── MainActivity.kt
│           └── RevScopeApp.kt    # Application class + Hilt
│
├── core/
│   ├── obd/                      # Protocolo OBD2 / ELM327
│   │   ├── connection/           # Transports (BT, BLE, WiFi)
│   │   ├── protocol/             # Parser, builder, negotiator
│   │   ├── pid/                  # Registry, PID definitions JSON
│   │   └── model/                # OBD data models
│   │
│   ├── data/                     # Room + DataStore
│   │   ├── db/
│   │   └── datastore/
│   │
│   └── common/                   # Extensions, utils
│
├── feature/
│   ├── dashboard/                # Pantalla principal gauges
│   ├── gear/                     # Analizador RPM/torque
│   ├── sensors/                  # Gráficas de sensores
│   ├── dtc/                      # Códigos de error
│   ├── session/                  # Historial de sesiones
│   ├── vehicle/                  # Perfiles de vehículo
│   └── settings/                 # Configuración
│
└── auto/                         # Car App Library (v2, desactivado en v1)
```

---

## PIDs implementados (v1)

### Estándar SAE J1979 (Mode 01)

| PID | Nombre | Fórmula | Rango |
|-----|--------|---------|-------|
| 0x0C | RPM motor | `((A*256)+B)/4` | 0–16383 rpm |
| 0x0D | Velocidad | `A` | 0–255 km/h |
| 0x04 | Carga motor | `A*100/255` | 0–100% |
| 0x05 | Temp refrigerante | `A-40` | -40–215°C |
| 0x0F | Temp aire admisión | `A-40` | -40–215°C |
| 0x10 | MAF (flujo aire) | `((A*256)+B)/100` | 0–655.35 g/s |
| 0x11 | Posición mariposa | `A*100/255` | 0–100% |
| 0x0B | MAP (presión múltiple) | `A` kPa abs | 0–255 kPa |
| 0x62 | Torque actual % | `A-125` | -125–130% |
| 0x63 | Torque referencia Nm | `(A*256)+B` | 0–65535 Nm |
| 0x06 | Fuel trim corto banco1 | `(A-128)*100/128` | -100–99.2% |
| 0x07 | Fuel trim largo banco1 | `(A-128)*100/128` | -100–99.2% |
| 0x14 | Sensor O2 B1S1 | `A/200` V | 0–1.275 V |
| 0x46 | Temp ambiente | `A-40` | -40–215°C |
| 0x5E | Tasa consumo combustible | `((A*256)+B)*0.05` L/h | 0–3212.75 |

### Derivados (calculados en app)

| Métrica | Cálculo | Notas |
|---------|---------|-------|
| Boost estimado | `MAP - 101` kPa | 101 = presión atmosférica aprox |
| Gear estimado | `speed / (rpm/1000)` ratio table | Sin PID estándar |
| Potencia estimada | `torque_Nm * rpm / 9549` kW | Aproximación |
| Eficiencia térmica | Combo temp_agua + fuel_trim | Indicador custom |

### Modo 03 (DTC)
- Leer códigos de falla activos
- Modo 07: códigos pendientes  
- Modo 0A: códigos permanentes
- Modo 04: borrar DTCs

### Modo 09 (Vehicle Info)
- VIN (PID 0x02)
- ECU name (PID 0x0A)

---

## PIDs propietarios por vehículo (v1 — manual, experimental)

### Mazda CX-30 Grand Touring (Modo 22)
- `220C` RPM alternativo  
- `221A` AWD torque split (pendiente investigación)
- `2201` presión turbo (si aplica)

### Renault Kardian
- Protocolo KWP2000 en algunos módulos — ELM327 auto-detecta
- PIDs modo ECO/Sport: investigar con `AT MA` (monitor all)

### Nissan March
- CVT gear ratio vía PID `0xA4` si soportado: `(256*C+D)/1000`

### TVS Apache 160 4V FI 2026 (moto)
- Protocolo: ISO 9141-2 o KWP2000 (ELM327 auto-detecta con `AT SP 0`)
- Conector OBD2 de 16 pines estándar (verificar en físico)
- PIDs disponibles limitados vs carro — RPM, temp motor, posición mariposa esperados

---

## Compatibilidad multi-adaptador

### Classic Bluetooth (Vgate iCar Pro 2S y similares)
```kotlin
// UUID estándar SPP
val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
// Nombre dispositivo para Vgate: "Android-Vlink", PIN: "1234"
```

### BLE — 6 familias de chips (AndrOBD-verified)
```kotlin
val BLE_UUID_SETS = mapOf(
    "CC254X"    to Pair("0000FFE0-...", "0000FFE1-..."),
    "VLink"     to Pair("000018F0-...", "00002AF0-..."),
    "Nexas"     to Pair("0000FFF0-...", "0000FFF1-..."),
    "Nordic"    to Pair("6e400001-b5a3-f393-e0a9-e50e24dcca9e", "6e400003-..."),
    "Microchip" to Pair("49535343-fe7d-4ae5-8fa9-9fafd205e455", "49535343-1e4d-..."),
    "TIO"       to Pair("0000FEFB-...", "00000001-...")
)
```

### WiFi (TCP)
```
Host: 192.168.0.10  Port: 35000  (ELM327 WiFi estándar)
```

---

## Diseño visual

**Dirección**: Dark luxury + Racing HUD. NO el negro genérico de apps OBD típicas.

### Paleta de colores
```
Background:  #0A0A0F  (negro azulado profundo)
Surface:     #12121A  (cards/panels)
Accent:      #E8FF00  (amarillo racing — gauges activos)
Warning:     #FF8C00  (naranja — temperaturas altas)
Danger:      #FF3040  (rojo — alertas críticas)
Success:     #00E676  (verde — sistemas OK)
Text:        #F0F0F8  (blanco cálido)
TextMuted:   #6B7089  (gris para labels)
```

### Tipografía
- Números/valores: **Space Grotesk** (tabular, monoespaciado para números)
- Labels/texto: **Inter**

### Componentes clave
- **RPM Gauge**: Arco 270° con gradiente verde→amarillo→rojo, aguja animada con `spring()`
- **Velocity Gauge**: Arco 180°, numeral grande central
- **Temp Gauge**: Vertical, fill animado
- **Boost Bar**: Barra horizontal con zona de presión óptima marcada
- **Gear Display**: Numeral enorme (1–6 + N + R), inferido de ratio RPM/velocidad
- **Line Charts**: Vico, líneas luminosas, fondo oscuro, scroll en tiempo

---

## Fases de desarrollo v1

### Fase 1 — Capa de conexión y protocolo (semana 1-2)
- [ ] Proyecto Android + estructura de módulos
- [ ] `ClassicBtTransport`: scan, pair, connect, read/write stream
- [ ] `ElmCommandBuilder`: AT Z, AT E0, AT L0, AT S0, AT H0, AT SP 0
- [ ] `ResponseParser`: ASCII hex → byte array → typed value
- [ ] `PidRegistry`: cargar `pids_mode01.json`, eval fórmulas con `exp4j`
- [ ] Tests unitarios: parser + registry

### Fase 2 — Motor de telemetría (semana 2-3)
- [ ] `PidScheduler`: coroutine loop, polling por prioridad
- [ ] `DerivedMetricsEngine`: boost, gear, potencia
- [ ] `SessionRecorder`: Room, graba puntos cada 500ms
- [ ] `ConnectionViewModel`: StateFlow de estado de conexión
- [ ] Tests: scheduler con mock transport

### Fase 3 — UI Dashboard (semana 3-4)
- [ ] Tema Compose (Dark luxury, Space Grotesk, Inter)
- [ ] `RpmGauge` composable (arco animado)
- [ ] `SpeedGauge` composable
- [ ] `GearDisplay` composable
- [ ] `TempGauge` composable
- [ ] `BoostBar` composable
- [ ] `DashboardScreen` ensamblando gauges
- [ ] Animaciones spring() para transiciones de valores

### Fase 4 — Pantallas secundarias (semana 4-5)
- [ ] `SensorGraphScreen` con Vico (selección de PIDs)
- [ ] `GearAnalyzerScreen` (curva RPM/torque, zona óptima)
- [ ] `DtcScreen` (leer/borrar códigos, descripción)
- [ ] `SessionHistoryScreen` (trips con estadísticas)
- [ ] `VehicleProfileScreen` (nombre, tipo car/moto, PIDs disponibles)

### Fase 5 — BLE + WiFi + multi-adaptador (semana 5-6)
- [ ] `BleTransport`: blessed-android-coroutines, auto-detect 6 UUID sets
- [ ] `WifiTransport`: TCP socket
- [ ] `AdapterScanner`: UI de búsqueda y conexión
- [ ] `SettingsScreen`: selección de adaptador, intervalo polling

### Fase 6 — Pulido y open source (semana 6-7)
- [ ] ProGuard rules
- [ ] Permisos mínimos en manifest
- [ ] CONTRIBUTING.md
- [ ] GitHub Actions CI (lint + tests)
- [ ] Capturas de pantalla para README
- [ ] Release v1.0.0

---

## Permisos requeridos

```xml
<!-- Bluetooth Classic -->
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30"/>
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30"/>
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT"/>
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"/>

<!-- BLE -->
<uses-feature android:name="android.hardware.bluetooth_le" android:required="false"/>

<!-- WiFi -->
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>

<!-- Ubicación (requerido para BT scan en Android 10 y anteriores) -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
```

---

## Notas de investigación

- **"3 Mbps" de Vgate es marketing falso** — BLE 5.2 max físico 2 Mbps, real ~1.3 Mbps. Para OBD2 es irrelevante (los datos son lentos por CAN bus 500 kbps máx).
- **Secuencia AT init no es canónica** — cada firmware de ELM327 clone es diferente. Usar secuencia mínima: `AT Z` → `AT E0` → `AT SP 0`. Agregar `AT L0`, `AT S0`, `AT H0` opcionales.
- **Gear detection**: No hay PID estándar. Ratio `(speed_kmh * 1000) / rpm` da una tabla de ratios que se mapea a marcha con umbral por perfil de vehículo.
- **Apache 160 moto**: verificar conector físico de 16 pines antes de conectar. ELM327 `AT SP 0` auto-detecta protocolo. Esperar PIDs limitados.
- **Android Auto v1**: excluido. No hay categoría aprobada para apps OBD2. Implementar en v2 como PaneTemplate con 4 métricas.
- **Boost en motor atmosférico** (March, Apache): `MAP - 101` dará negativo. Mostrar como "vacío de admisión" en lugar de boost.

---

## Plan v2 — Android Auto

### El problema de categoría (bloqueante)

Android Auto requiere que cada app pertenezca a una categoría aprobada por Google Play. **No existe categoría para diagnóstico OBD2.** Categorías actuales (junio 2026):

```
✅ Navigation, Audio/Media, Messaging, VoIP
✅ EV charging, Weather, Games*, Video*, Browsers*
   (* solo estacionado — Tier 1)
❌ Vehicle diagnostics / OBD2 / telemetry — NO EXISTE
```

**Estrategia para v2**: declarar la app bajo la categoría más cercana posible (candidata: IoT o una futura "Vehicle Info" que Google ha mencionado). Monitorear el listado oficial antes de implementar. Si Google rechaza, distribuir vía sideload (APK directo) — válido para open source.

### Restricciones de UI en Android Auto (Car App Library)

Car App Library **prohíbe UI custom**. Solo templates predefinidos:

| Template | Uso en RevScope |
|----------|----------------|
| `PaneTemplate` | Pantalla principal — 4 métricas en tiempo real |
| `ListTemplate` | Lista de PIDs disponibles, lista de DTCs |
| `GridTemplate` | Selector de pantalla (Dashboard / DTC / Perfil) |
| `MessageTemplate` | Alertas de temperatura / presión crítica |
| `SectionedItemTemplate` *(CAL 1.8 alpha)* | Grupos de sensores agrupados por sistema |

**Prohibido**: Canvas custom, Compose en pantalla del carro, gauges dibujados, gráficas animadas.

**Permitido dentro de templates**: colores de acento propietarios, iconos custom, texto formateado.

### Requisitos de calidad Google Play (verificados)

```
UX-1: touch targets mínimo 64dp
UX-2: separación mínima 24dp entre targets y bordes
UX-3: fuente mínima 24sp
```

### Pantallas wide / aspect ratio (carros nuevos)

Car App Library 1.8 maneja automáticamente pantallas ultrawide (3:1, 4:1).  
`SectionedItemTemplate` adapta columnas según espacio disponible.  
RevScope no necesita lógica especial — la librería lo abstrae.

### Arquitectura v2

```
┌─────────────────────────────────────────┐
│  Módulo :auto (Car App Library 1.8)     │
│  ├─ RevScopeCarAppService               │  ← CarAppService entry point
│  ├─ DashboardCarScreen                  │  ← PaneTemplate, 4 métricas
│  ├─ DtcCarScreen                        │  ← ListTemplate, códigos error
│  └─ AlertCarScreen                      │  ← MessageTemplate, alertas
├─────────────────────────────────────────┤
│  Foreground Service (nuevo en v2)       │
│  ├─ OBD2ConnectionService               │  ← mantiene conexión BT viva
│  │   al pasar a Android Auto            │
│  └─ TelemetryBroadcaster               │  ← SharedFlow → CarScreen
└─────────────────────────────────────────┘
```

**Problema arquitectural clave**: cuando Android Auto toma control, la app pasa a background. La conexión Bluetooth Classic (ClassicBtTransport) debe vivir en un `ForegroundService` con notificación persistente para no ser matada por el sistema.

### Pantalla principal v2 (PaneTemplate)

```
┌─────────────────────────────────────────────┐
│  RevScope                    [icono]         │
├──────────────┬──────────────────────────────┤
│  RPM         │  VELOCIDAD                   │
│  3.240       │  87 km/h                     │
├──────────────┼──────────────────────────────┤
│  TEMP MOTOR  │  MARCHA EST.                 │
│  92 °C       │  3ª                          │
├──────────────┴──────────────────────────────┤
│  [DTCs]  [Sensores]  [Desconectar]          │
└─────────────────────────────────────────────┘
```

Solo lectura mientras se conduce. Interacción de botones solo cuando el carro está estacionado (Car App Library lo enforcea automáticamente).

### Prerrequisitos para iniciar v2

1. v1 completamente estable (todas las fases)
2. `OBD2ConnectionService` (ForegroundService) extraído de v1 como base
3. Definir estrategia de categoría Google Play (monitorear anuncios de Google)
4. Car App Library 1.8 estable (actualmente en alpha a junio 2026)
5. Dispositivo de prueba con Android Auto activo (Mazda CX-30 o cualquier carro compatible)

### Lo que v2 NO puede hacer (definitivo)

- Gauges animados en pantalla del carro
- Gráficas de sensores en tiempo real
- UI de conexión / emparejamiento mientras se conduce
- Pantalla personalizada tipo HUD

Esas features quedan exclusivamente en la app de teléfono (v1).

---

## Licencia

Apache License 2.0 — permite uso comercial, modificación, distribución, uso privado.  
Requiere preservar aviso de copyright y licencia.

---

---

## Especificaciones de implementación (para nueva sesión de Claude)

### Dependencias exactas (build.gradle.kts)

```kotlin
// Versiones a usar — no inventar otras
val composeVersion = "1.7.8"
val hiltVersion = "2.51.1"
val roomVersion = "2.7.1"
val coroutinesVersion = "1.9.0"

dependencies {
    // Compose BOM
    implementation(platform("androidx.compose:compose-bom:2025.05.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.10.1")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.9")

    // Hilt
    implementation("com.google.dagger:hilt-android:$hiltVersion")
    kapt("com.google.dagger:hilt-compiler:$hiltVersion")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Room
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    kapt("androidx.room:room-compiler:$roomVersion")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.4")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVersion")

    // BLE
    implementation("com.github.weliem:blessed-android-coroutines:2.0.8")

    // Charts
    implementation("com.patrykandpatrick.vico:compose-m3:2.1.2")

    // PID formula evaluation — usar exp4j (no EvalEx)
    implementation("net.objecthunter:exp4j:0.4.8")

    // Fonts — Space Grotesk + Inter via Google Fonts
    implementation("androidx.compose.ui:ui-text-google-fonts")

    // Timber logging
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
    testImplementation("io.mockk:mockk:1.13.12")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
```

**Corrección**: usar **exp4j** en todo el proyecto. EvalEx no aplica aquí. exp4j evalúa strings como `"((A*256)+B)/4"` con variables A, B, C, D.

### Package name

```
com.revscope.app
```

Paquete de módulos:
- `com.revscope.core.obd`
- `com.revscope.core.data`
- `com.revscope.feature.dashboard`
- etc.

### Interfaz Transport (contrato sellado)

```kotlin
// core/obd/connection/Transport.kt
interface Transport {
    val isConnected: Boolean
    suspend fun connect(): Result<Unit>
    suspend fun disconnect()
    suspend fun send(command: String)      // envía AT cmd o PID request
    suspend fun receive(): String          // lee hasta '>' o timeout
    fun observeConnectionState(): Flow<ConnectionState>
}

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting : ConnectionState()
    data class Connected(val deviceName: String) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}
```

### Schema JSON de PIDs

```json
// core/obd/src/main/assets/pids_mode01.json
[
  {
    "mode": "01",
    "pid": "0C",
    "name": "Engine RPM",
    "nameEs": "RPM Motor",
    "bytes": 2,
    "formula": "((A*256)+B)/4",
    "unit": "rpm",
    "min": 0,
    "max": 16383,
    "priority": 1
  },
  {
    "mode": "01",
    "pid": "0D",
    "name": "Vehicle Speed",
    "nameEs": "Velocidad",
    "bytes": 1,
    "formula": "A",
    "unit": "km/h",
    "min": 0,
    "max": 255,
    "priority": 1
  }
]
```

Campo `priority`: 1 = alta frecuencia (100ms), 2 = media (500ms), 3 = baja (2000ms).

### Room — schemas de entidades

```kotlin
// SessionEntity
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleProfileId: Long,
    val startedAt: Long,       // epoch ms
    val endedAt: Long?,
    val adapterName: String,
    val maxRpm: Int,
    val maxSpeed: Int,
    val distanceKm: Float
)

// TelemetryPointEntity
@Entity(tableName = "telemetry_points",
        foreignKeys = [ForeignKey(entity = SessionEntity::class,
                                  parentColumns = ["id"],
                                  childColumns = ["sessionId"],
                                  onDelete = CASCADE)],
        indices = [Index("sessionId")])
data class TelemetryPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long,       // epoch ms
    val pid: String,           // "0C", "0D", etc.
    val value: Float
)

// VehicleProfileEntity
@Entity(tableName = "vehicle_profiles")
data class VehicleProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,          // "Mazda CX-30"
    val type: String,          // "CAR" | "MOTORCYCLE"
    val vin: String?,
    val enabledPids: String,   // JSON array de PIDs activos: ["0C","0D","05"]
    val gearRatios: String?,   // JSON array floats por marcha: [3.5,2.1,1.4,1.0,0.8,0.6]
    val createdAt: Long
)
```

### DataStore — claves de preferencias

```kotlin
object PreferencesKeys {
    val ADAPTER_TYPE = stringPreferencesKey("adapter_type")     // "CLASSIC_BT" | "BLE" | "WIFI"
    val ADAPTER_ADDRESS = stringPreferencesKey("adapter_address") // MAC o IP
    val ACTIVE_PROFILE_ID = longPreferencesKey("active_profile_id")
    val POLLING_INTERVAL_MS = intPreferencesKey("polling_interval_ms") // default 200
    val UNITS_METRIC = booleanPreferencesKey("units_metric")     // true = km/h, °C
    val THEME_DARK = booleanPreferencesKey("theme_dark")         // siempre true en v1
}
```

### BLE UUID sets completos

```kotlin
// core/obd/connection/BleUuidSets.kt
object BleUuidSets {
    val CC254X = BleUuidSet(
        service    = "0000FFE0-0000-1000-8000-00805F9B34FB",
        writeChar  = "0000FFE1-0000-1000-8000-00805F9B34FB",
        notifyChar = "0000FFE1-0000-1000-8000-00805F9B34FB"
    )
    val VLINK = BleUuidSet(
        service    = "000018F0-0000-1000-8000-00805F9B34FB",
        writeChar  = "00002AF0-0000-1000-8000-00805F9B34FB",
        notifyChar = "00002AF1-0000-1000-8000-00805F9B34FB"
    )
    val NEXAS = BleUuidSet(
        service    = "0000FFF0-0000-1000-8000-00805F9B34FB",
        writeChar  = "0000FFF2-0000-1000-8000-00805F9B34FB",
        notifyChar = "0000FFF1-0000-1000-8000-00805F9B34FB"
    )
    val NORDIC_UART = BleUuidSet(
        service    = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E",
        writeChar  = "6E400002-B5A3-F393-E0A9-E50E24DCCA9E",
        notifyChar = "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"
    )
    val MICROCHIP = BleUuidSet(
        service    = "49535343-FE7D-4AE5-8FA9-9FAFD205E455",
        writeChar  = "49535343-8841-43F4-A8D4-ECBE34729BB3",
        notifyChar = "49535343-1E4D-4BD9-BA61-23C647249616"
    )
    val TIO = BleUuidSet(
        service    = "0000FEFB-0000-1000-8000-00805F9B34FB",
        writeChar  = "00000002-0000-0000-0000-000000000000",
        notifyChar = "00000001-0000-0000-0000-000000000000"
    )
    val ALL = listOf(CC254X, VLINK, NEXAS, NORDIC_UART, MICROCHIP, TIO)
}

data class BleUuidSet(val service: String, val writeChar: String, val notifyChar: String)
```

### ELM327 — secuencia de inicialización

```
Paso 1: "AT Z\r"    → reset (esperar "ELM327 v..." + ">")
Paso 2: "AT E0\r"   → echo off
Paso 3: "AT L0\r"   → line feeds off
Paso 4: "AT S0\r"   → spaces off en respuesta (IMPORTANTE para parser)
Paso 5: "AT H0\r"   → headers off
Paso 6: "AT SP 0\r" → auto-detect protocolo del vehículo
Paso 7: "01 00\r"   → query supported PIDs (verifica conexión con ECU)
```

Timeout por comando: 2000ms. Si paso 7 falla → no hay ECU, no continuar.  
Con `AT S0` activo, la respuesta de `01 0C` es `410C0FA0>` en vez de `41 0C 0F A0 >`. El parser debe manejar ambos formatos.

### Supported PIDs — query obligatorio antes de pedir datos

OBD-II permite saber qué PIDs soporta el vehículo antes de pedirlos. Sin este paso, pedir un PID no soportado retorna `NO DATA` y bloquea el loop.

```
Request:  "01 00\r"  → bitmask PIDs 0x01-0x20
Request:  "01 20\r"  → bitmask PIDs 0x21-0x40
Request:  "01 40\r"  → bitmask PIDs 0x41-0x60
Request:  "01 60\r"  → bitmask PIDs 0x61-0x80 (torque, gear ratio)

Ejemplo respuesta "01 00": "4100BE1FA813"
→ bytes: BE 1F A8 13
→ bit 0 de BE = PID 0x01 soportado, etc.
```

`PidRegistry` debe filtrar `pids_mode01.json` contra el bitmask antes de iniciar polling.

### Estrategia de error handling

| Escenario | Acción |
|-----------|--------|
| BT desconectado durante sesión | `ConnectionState.Error` → UI muestra banner, intenta reconectar 3 veces con backoff 1s/2s/4s |
| PID retorna `"NO DATA"` | Marcar PID como no soportado, excluir del scheduler |
| PID retorna `"UNABLE TO CONNECT"` | ECU no responde → detener sesión, mostrar error |
| Timeout sin `'>'` en 3000ms | Reenviar comando una vez. Si falla de nuevo → reconectar |
| `"BUFFER FULL"` | Reducir frecuencia de polling 50% automáticamente |
| Error de parsing (respuesta inesperada) | Log + skip, no crashear |

### Navegación Compose

```kotlin
// Rutas
sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object GearAnalyzer : Screen("gear")
    object Sensors : Screen("sensors")
    object Dtc : Screen("dtc")
    object Sessions : Screen("sessions")
    object VehicleProfile : Screen("vehicle/{profileId}") {
        fun createRoute(id: Long) = "vehicle/$id"
    }
    object Settings : Screen("settings")
    object AdapterScan : Screen("adapter_scan")
}
```

Bottom navigation bar: Dashboard · Sensores · DTC · Historial  
Settings y AdapterScan accesibles vía ícono en TopBar.

### BLE — manejo de MTU

BLE default MTU = 23 bytes → 20 bytes útiles por paquete.  
Respuestas ELM327 largas (ej. VIN, múltiples DTCs) llegan en múltiples paquetes.  
`BleTransport.receive()` debe acumular paquetes hasta encontrar `'>'`.

```kotlin
// Patrón de acumulación en BleTransport
private val buffer = StringBuilder()

fun onCharacteristicChanged(bytes: ByteArray) {
    buffer.append(String(bytes))
    if (buffer.contains('>')) {
        val complete = buffer.toString()
        buffer.clear()
        responseChannel.trySend(complete)
    }
}
```

Pedir MTU mayor con `requestMtu(512)` via blessed-android-coroutines — reduce fragmentación.

*Última actualización: 2026-06-28*
