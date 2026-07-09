# Plan 1: Energía (fin de viaje automático) + Navegación 5 tabs + Mapa en vivo

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** La app deja de consumir batería tras apagar el vehículo (cierre limpio + notificación resumen), la navegación pasa a 5 pestañas por intención de uso con hub Taller, y aparece la pestaña Mapa con posición/ruta/radares en vivo.

**Architecture:** `EngineOffDetector` (puro) + sondeo del adaptador clasifican la pérdida de enlace como "motor apagado" vs "falla transitoria"; solo la falla reintenta (backoff con tope), ambas terminan en `finalShutdown` que detiene servicio/GPS/IMU y postea resumen. La navegación se reestructura en `RevScopeNavGraph` sin tocar las pantallas existentes. El mapa lee la ruta de un `LiveRouteHolder` singleton alimentado por `GpsTrackRecorder`.

**Tech Stack:** Kotlin, Compose, Hilt, Room, osmdroid 6.1.20, coroutines/StateFlow.

## Global Constraints

- Gradle NO está en el repo. Ejecutar siempre: `C:\Users\santi\.gradle\wrapper\dists\gradle-8.11.1-bin\bpt9gzteqjrbo1mjrsomdt32c\gradle-8.11.1\bin\gradle.bat` (abrevio `$GRADLE`).
- Commits en español, formato `tipo: descripción`. NUNCA agregar `Co-Authored-By`.
- `CancellationException` SIEMPRE se relanza antes del catch genérico (patrón del repo).
- Textos de UI en español.
- NO subir la versión de Room (sigue v9 en este plan — no hay cambios de schema).
- Spec de referencia: `docs/superpowers/specs/2026-07-08-suite-mecanico-rediseno-design.md`.

---

### Task 1: EngineOffDetector

**Files:**
- Create: `core/obd/src/main/kotlin/com/revscope/core/obd/session/EngineOffDetector.kt`
- Test: `core/obd/src/test/kotlin/com/revscope/core/obd/session/EngineOffDetectorTest.kt`

**Interfaces:**
- Produces: `EngineOffDetector` con `onSpeed(kmh: Double)`, `movedRecently(): Boolean`, `reset()`, enum `LinkLossCause { ENGINE_OFF, LINK_FAULT }`, constantes `MOVING_THRESHOLD_KMH = 3.0`, `RECENT_MOVEMENT_WINDOW_MS = 30_000L`. Task 3 lo consume.

- [ ] **Step 1: Test que falla**

```kotlin
package com.revscope.core.obd.session

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EngineOffDetectorTest {

    private var now = 0L
    private val detector = EngineOffDetector(clock = { now })

    @Test
    fun `sin movimiento nunca reporta movimiento reciente`() {
        assertThat(detector.movedRecently()).isFalse()
    }

    @Test
    fun `movimiento dentro de la ventana cuenta como reciente`() {
        detector.onSpeed(45.0)
        now += 29_000
        assertThat(detector.movedRecently()).isTrue()
    }

    @Test
    fun `movimiento fuera de la ventana ya no es reciente`() {
        detector.onSpeed(45.0)
        now += 31_000
        assertThat(detector.movedRecently()).isFalse()
    }

    @Test
    fun `velocidad bajo el umbral es ruido y no cuenta`() {
        detector.onSpeed(2.0)
        assertThat(detector.movedRecently()).isFalse()
    }

    @Test
    fun `reset olvida el movimiento`() {
        detector.onSpeed(45.0)
        detector.reset()
        assertThat(detector.movedRecently()).isFalse()
    }
}
```

Nota: si `com.google.common.truth` no está en las deps de test de `:core:obd`, usar `org.junit.Assert.assertTrue/assertFalse` — revisar un test existente del módulo (p. ej. `LaunchTimerEngineTest`) y seguir su estilo de aserciones.

- [ ] **Step 2: Verificar que falla**

Run: `$GRADLE :core:obd:testDebugUnitTest --tests "com.revscope.core.obd.session.EngineOffDetectorTest"`
Expected: FAIL — `Unresolved reference: EngineOffDetector`

- [ ] **Step 3: Implementación mínima**

```kotlin
package com.revscope.core.obd.session

/**
 * Tracks recent vehicle movement so a link loss can be classified:
 * stationary + link dead usually means the ignition was turned off.
 */
class EngineOffDetector(private val clock: () -> Long = System::currentTimeMillis) {

    enum class LinkLossCause { ENGINE_OFF, LINK_FAULT }

    private var lastMovementTs: Long? = null

    fun onSpeed(kmh: Double) {
        if (kmh >= MOVING_THRESHOLD_KMH) lastMovementTs = clock()
    }

    fun movedRecently(): Boolean =
        lastMovementTs?.let { clock() - it <= RECENT_MOVEMENT_WINDOW_MS } ?: false

    fun reset() {
        lastMovementTs = null
    }

    companion object {
        const val MOVING_THRESHOLD_KMH = 3.0
        const val RECENT_MOVEMENT_WINDOW_MS = 30_000L
    }
}
```

- [ ] **Step 4: Verificar que pasa**

Run: `$GRADLE :core:obd:testDebugUnitTest --tests "com.revscope.core.obd.session.EngineOffDetectorTest"`
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
git add core/obd/src/main/kotlin/com/revscope/core/obd/session/EngineOffDetector.kt core/obd/src/test/kotlin/com/revscope/core/obd/session/EngineOffDetectorTest.kt
git commit -m "feat: detector de motor apagado para clasificar pérdidas de enlace"
```

---

### Task 2: TripSummaryNotifier

**Files:**
- Create: `core/obd/src/main/kotlin/com/revscope/core/obd/service/TripSummaryNotifier.kt`
- Test: `core/obd/src/test/kotlin/com/revscope/core/obd/service/TripSummaryNotifierTest.kt`

**Interfaces:**
- Consumes: `SessionEntity` (core:data, ya es dependencia de core:obd), `R.drawable.ic_stat_revscope` (ya existe).
- Produces: `TripSummaryNotifier.post(session: SessionEntity)` inyectable @Singleton; `TripSummaryNotifier.EXTRA_SESSION_ID = "open_session_id"`; funciones puras de companion `summaryText(session): String` y `shouldNotify(session): Boolean`. Tasks 3 y 5 los consumen.

- [ ] **Step 1: Test que falla (solo la lógica pura)**

```kotlin
package com.revscope.core.obd.service

import com.revscope.core.data.db.entities.SessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripSummaryNotifierTest {

    private fun session(distanceKm: Float, maxSpeed: Int, durationMin: Long) = SessionEntity(
        id = 1L, vehicleProfileId = 0L,
        startedAt = 1_000_000L, endedAt = 1_000_000L + durationMin * 60_000,
        adapterName = "vLinker", maxRpm = 9000, maxSpeed = maxSpeed, distanceKm = distanceKm,
    )

    @Test
    fun `resumen con distancia velocidad y duracion`() {
        val text = TripSummaryNotifier.summaryText(session(23.4f, 82, 34))
        assertEquals("23,4 km · 82 km/h máx · 34 min", text)
    }

    @Test
    fun `viaje real se notifica`() {
        assertTrue(TripSummaryNotifier.shouldNotify(session(5.2f, 60, 12)))
    }

    @Test
    fun `prueba de garaje no se notifica`() {
        assertFalse(TripSummaryNotifier.shouldNotify(session(0.05f, 0, 3)))
    }
}
```

Nota: `summaryText` usa `Locale` por defecto — si el runner de tests usa locale inglés el separador decimal será `.`. Fijar `Locale("es", "CO")` explícito en la implementación para que el test sea determinista.

- [ ] **Step 2: Verificar que falla**

Run: `$GRADLE :core:obd:testDebugUnitTest --tests "com.revscope.core.obd.service.TripSummaryNotifierTest"`
Expected: FAIL — `Unresolved reference: TripSummaryNotifier`

- [ ] **Step 3: Implementación**

```kotlin
package com.revscope.core.obd.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import com.revscope.core.data.db.entities.SessionEntity
import com.revscope.core.obd.R
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Posts the dismissable "trip saved" summary after a clean automatic shutdown. */
@Singleton
class TripSummaryNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun post(session: SessionEntity) {
        if (!shouldNotify(session)) return
        createChannel()
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.putExtra(EXTRA_SESSION_ID, session.id)
            ?: return
        val pending = PendingIntent.getActivity(
            context, session.id.toInt(), launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_revscope)
            .setContentTitle("Viaje guardado")
            .setContentText(summaryText(session))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIFICATION_ID, notification)
        }.onFailure { Timber.w(it, "TripSummaryNotifier: could not post summary") }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Resumen de viaje", NotificationManager.IMPORTANCE_DEFAULT,
        )
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    companion object {
        const val EXTRA_SESSION_ID = "open_session_id"
        private const val CHANNEL_ID = "revscope_trip_summary"
        private const val NOTIFICATION_ID = 2001
        private const val MIN_NOTIFY_DISTANCE_KM = 0.2f
        private val LOCALE_ES = Locale("es", "CO")

        fun summaryText(session: SessionEntity): String {
            val minutes = ((session.endedAt ?: session.startedAt) - session.startedAt) / 60_000
            return String.format(
                LOCALE_ES, "%.1f km · %d km/h máx · %d min",
                session.distanceKm, session.maxSpeed, minutes,
            )
        }

        fun shouldNotify(session: SessionEntity): Boolean =
            session.distanceKm >= MIN_NOTIFY_DISTANCE_KM
    }
}
```

- [ ] **Step 4: Verificar que pasa**

Run: `$GRADLE :core:obd:testDebugUnitTest --tests "com.revscope.core.obd.service.TripSummaryNotifierTest"`
Expected: PASS (3 tests)

- [ ] **Step 5: Commit**

```bash
git add core/obd/src/main/kotlin/com/revscope/core/obd/service/TripSummaryNotifier.kt core/obd/src/test/kotlin/com/revscope/core/obd/service/TripSummaryNotifierTest.kt
git commit -m "feat: notificación resumen de viaje al cierre automático"
```

---

### Task 3: Cierre limpio + reconexión con backoff en ObdSessionManager

**Files:**
- Modify: `core/obd/src/main/kotlin/com/revscope/core/obd/session/ObdSessionManager.kt`

**Interfaces:**
- Consumes: `EngineOffDetector` (Task 1), `TripSummaryNotifier` (Task 2), `SessionDao.getById` (existe).
- Produces: comportamiento — tras pérdida de enlace clasificada como motor apagado, o tras agotar el backoff, el estado queda `Disconnected`, el servicio muere y se postea el resumen.

- [ ] **Step 1: Inyectar colaboradores y crear el detector**

En el constructor agregar el parámetro (después de `trackModeEngine`):

```kotlin
    private val tripSummaryNotifier: TripSummaryNotifier,
```

Import: `com.revscope.core.obd.service.TripSummaryNotifier`.

Como campo (junto a `derivedEngine`):

```kotlin
    private val engineOffDetector = EngineOffDetector()
```

- [ ] **Step 2: Alimentar el detector y resetearlo por sesión**

En `startTelemetry`, después de `launchTimer.reset()` agregar:

```kotlin
        engineOffDetector.reset()
```

En el collector de `allFlow` (bloque `allFlow.collect { reading -> ... }`), agregar tras `launchTimer.process(reading)`:

```kotlin
                            if (reading.pid == "0D") engineOffDetector.onSpeed(reading.value)
```

- [ ] **Step 3: Reemplazar el catch de telemetría con clasificación**

Reemplazar el bloque `catch (e: Exception)` de `telemetryJob` (el que hoy hace disconnect + `scheduleAutoReconnect()`) por:

```kotlin
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // PidScheduler's circuit breaker lands here when the ECU stops answering
                Timber.e(e, "ObdSessionManager: telemetry link lost")
                voltageJob?.cancel()
                val closedSessionId = _currentSessionIdFlow.value
                closedSessionId?.let { id -> runCatching { updateSessionEnd(id) } }
                _currentSessionIdFlow.value = null
                // Probe BEFORE dropping the transport — the adapter may still answer
                val cause = classifyLinkLoss(transport)
                runCatching { transport?.disconnect() }
                transport = null
                when (cause) {
                    EngineOffDetector.LinkLossCause.ENGINE_OFF -> {
                        Timber.i("ObdSessionManager: engine off — clean shutdown")
                        finalShutdown(closedSessionId)
                    }
                    EngineOffDetector.LinkLossCause.LINK_FAULT -> {
                        _connectionState.value =
                            ConnectionState.Error("Connection lost — adapter not responding")
                        scheduleAutoReconnect(closedSessionId)
                    }
                }
            }
```

- [ ] **Step 4: Agregar classifyLinkLoss y finalShutdown**

Después de `scheduleAutoReconnect` agregar:

```kotlin
    /**
     * Ignition-off signature: the ELM adapter still answers (battery-powered)
     * but the ECU is silent. A dead socket falls back to the movement heuristic.
     */
    private suspend fun classifyLinkLoss(bt: ClassicBtTransport?): EngineOffDetector.LinkLossCause {
        if (bt != null) {
            val adapterAnswer = runCatching { bt.exchange("AT RV\r", VOLTAGE_TIMEOUT_MS) }.getOrNull()
            if (adapterAnswer != null && parseVoltage(adapterAnswer) != null) {
                val ecuAnswer = runCatching { bt.exchange("010C\r", PROBE_TIMEOUT_MS) }.getOrNull()
                val ecuSilent = ecuAnswer == null ||
                    ecuAnswer.contains("NO DATA", ignoreCase = true) ||
                    ecuAnswer.contains("UNABLE", ignoreCase = true) ||
                    ecuAnswer.contains("STOPPED", ignoreCase = true)
                return if (ecuSilent) EngineOffDetector.LinkLossCause.ENGINE_OFF
                else EngineOffDetector.LinkLossCause.LINK_FAULT
            }
        }
        return if (engineOffDetector.movedRecently()) EngineOffDetector.LinkLossCause.LINK_FAULT
        else EngineOffDetector.LinkLossCause.ENGINE_OFF
    }

    /** Stops everything battery-hungry and posts the trip summary. Terminal state. */
    private suspend fun finalShutdown(summarySessionId: Long?) {
        voltageJob?.cancel()
        runCatching { transport?.disconnect() }
        transport = null
        ObdForegroundService.stop(appContext)
        _connectionState.value = ConnectionState.Disconnected
        _readings.value = emptyMap()
        summarySessionId?.let { id ->
            runCatching { sessionDao.getById(id) }.getOrNull()?.let { tripSummaryNotifier.post(it) }
        }
    }
```

- [ ] **Step 5: Backoff con tope en scheduleAutoReconnect**

Reemplazar `scheduleAutoReconnect()` completo por:

```kotlin
    private fun scheduleAutoReconnect(pendingSummarySessionId: Long?) {
        val address = currentDeviceAddress ?: _lastAdapterAddress.value ?: return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            AUTO_RECONNECT_BACKOFF_MS.forEachIndexed { attempt, waitMs ->
                delay(waitMs)
                if (_connectionState.value is ConnectionState.Connected) return@launch
                Timber.i("ObdSessionManager: auto-reconnect attempt ${attempt + 1} to $address")
                connect(address, ConnectMode.BACKGROUND)
            }
            // Give the last attempt time to finish its 12 s connect watchdog
            delay(RECONNECT_FINAL_GRACE_MS)
            if (_connectionState.value !is ConnectionState.Connected) {
                Timber.i("ObdSessionManager: reconnect exhausted — clean shutdown")
                finalShutdown(pendingSummarySessionId)
            }
        }
    }
```

En el companion, reemplazar las constantes `AUTO_RECONNECT_INTERVAL_MS` y `AUTO_RECONNECT_MAX_ATTEMPTS` por:

```kotlin
        // 15 s > 12 s connect watchdog, so attempts never overlap; total ≈ 3 min
        private val AUTO_RECONNECT_BACKOFF_MS = listOf(15_000L, 30_000L, 60_000L, 60_000L)
        private const val RECONNECT_FINAL_GRACE_MS = 15_000L
        private const val PROBE_TIMEOUT_MS = 3_000L
```

Importante: `finalShutdown` NO cancela `reconnectJob` (se llamaría desde dentro del propio job y se auto-cancelaría a mitad). Los entrypoints `connectToDevice` y `disconnect` ya lo cancelan.

- [ ] **Step 6: Compilar y correr toda la suite del módulo**

Run: `$GRADLE :core:obd:testDebugUnitTest`
Expected: PASS (130+ tests, ninguno roto)

- [ ] **Step 7: Commit**

```bash
git add core/obd/src/main/kotlin/com/revscope/core/obd/session/ObdSessionManager.kt
git commit -m "fix: cierre limpio al apagar el motor y backoff con tope en reconexión — fin del drenaje de batería en segundo plano"
```

---

### Task 4: LiveRouteHolder + wiring GPS

**Files:**
- Create: `core/obd/src/main/kotlin/com/revscope/core/obd/service/LiveRouteHolder.kt`
- Modify: `core/obd/src/main/kotlin/com/revscope/core/obd/service/GpsTrackRecorder.kt`
- Modify: `core/obd/src/main/kotlin/com/revscope/core/obd/service/ObdForegroundService.kt`
- Test: `core/obd/src/test/kotlin/com/revscope/core/obd/service/LiveRouteHolderTest.kt`

**Interfaces:**
- Produces: `LiveRouteHolder` @Singleton con `points: StateFlow<List<LiveRouteHolder.RoutePoint>>` (`RoutePoint(lat: Double, lon: Double)`), `append(lat, lon)`, `clear()`. Task 7 lo consume.

- [ ] **Step 1: Test que falla**

```kotlin
package com.revscope.core.obd.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveRouteHolderTest {

    private val holder = LiveRouteHolder()

    @Test
    fun `append acumula puntos en orden`() {
        holder.append(6.24, -75.58)
        holder.append(6.25, -75.59)
        assertEquals(2, holder.points.value.size)
        assertEquals(6.24, holder.points.value.first().lat, 0.0001)
    }

    @Test
    fun `clear vacia la ruta`() {
        holder.append(6.24, -75.58)
        holder.clear()
        assertTrue(holder.points.value.isEmpty())
    }

    @Test
    fun `append limita la ruta al maximo de puntos`() {
        repeat(LiveRouteHolder.MAX_POINTS + 100) { holder.append(6.0 + it * 0.0001, -75.0) }
        assertEquals(LiveRouteHolder.MAX_POINTS, holder.points.value.size)
    }
}
```

- [ ] **Step 2: Verificar que falla**

Run: `$GRADLE :core:obd:testDebugUnitTest --tests "com.revscope.core.obd.service.LiveRouteHolderTest"`
Expected: FAIL — `Unresolved reference: LiveRouteHolder`

- [ ] **Step 3: Implementación**

```kotlin
package com.revscope.core.obd.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** In-memory route of the active trip, consumed by the live map. */
@Singleton
class LiveRouteHolder @Inject constructor() {

    data class RoutePoint(val lat: Double, val lon: Double)

    private val _points = MutableStateFlow<List<RoutePoint>>(emptyList())
    val points: StateFlow<List<RoutePoint>> = _points.asStateFlow()

    fun append(lat: Double, lon: Double) {
        _points.value = (_points.value + RoutePoint(lat, lon)).takeLast(MAX_POINTS)
    }

    fun clear() {
        _points.value = emptyList()
    }

    companion object {
        // 1 fix/s con min-distance 3 m → ~5 h de viaje; protege la memoria
        const val MAX_POINTS = 18_000
    }
}
```

- [ ] **Step 4: Verificar que pasa**

Run: `$GRADLE :core:obd:testDebugUnitTest --tests "com.revscope.core.obd.service.LiveRouteHolderTest"`
Expected: PASS (3 tests)

- [ ] **Step 5: Alimentarlo desde GpsTrackRecorder**

En `GpsTrackRecorder`, agregar parámetro de constructor (después de `cameraAlerter`):

```kotlin
    private val routeHolder: LiveRouteHolder? = null,
```

En `onLocation`, después de `cameraAlerter?.onGpsFix(...)`:

```kotlin
        routeHolder?.append(location.latitude, location.longitude)
```

En `ObdForegroundService`:
- Agregar inyección: `@Inject lateinit var routeHolder: LiveRouteHolder`
- En `observeSession`, dentro del `collect { sessionId -> ... }`, agregar `routeHolder.clear()` justo después de los `stop()` iniciales (limpia la ruta anterior en cada sesión nueva y al terminar).
- Pasar el holder al recorder: en la construcción de `GpsTrackRecorder(...)` agregar `routeHolder = routeHolder,` tras `cameraAlerter = cameraAlerter,`.

- [ ] **Step 6: Compilar módulo**

Run: `$GRADLE :core:obd:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add core/obd/src/main/kotlin/com/revscope/core/obd/service/ core/obd/src/test/kotlin/com/revscope/core/obd/service/LiveRouteHolderTest.kt
git commit -m "feat: ruta en vivo en memoria alimentada por el track GPS"
```

---

### Task 5: Navegación 5 tabs + ConnectionChip + deep link del resumen

**Files:**
- Modify: `app/src/main/kotlin/com/revscope/app/navigation/Screen.kt`
- Modify: `app/src/main/kotlin/com/revscope/app/navigation/RevScopeNavGraph.kt`
- Create: `app/src/main/kotlin/com/revscope/app/navigation/ConnectionChip.kt`
- Modify: `app/src/main/kotlin/com/revscope/app/MainActivity.kt` (localizarlo con Glob si el path difiere)
- Modify: `feature/dashboard/src/main/kotlin/com/revscope/feature/dashboard/DashboardScreen.kt`
- Modify: `feature/settings/src/main/kotlin/com/revscope/feature/settings/SettingsScreen.kt`

**Interfaces:**
- Consumes: `TripSummaryNotifier.EXTRA_SESSION_ID` (Task 2), `ConnectionViewModel.connectionState` (existe).
- Produces: rutas `Screen.Workshop ("workshop")` y `Screen.LiveMap ("map")` — Tasks 6 y 7 registran sus composables ahí; `RevScopeNavGraph(initialSessionId: Long?)`.

- [ ] **Step 1: Rutas nuevas en Screen.kt**

```kotlin
    object Workshop : Screen("workshop")
    object LiveMap : Screen("map")
```

- [ ] **Step 2: ConnectionChip.kt**

```kotlin
package com.revscope.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revscope.core.obd.connection.ConnectionState

@Composable
fun ConnectionChip(state: ConnectionState, onClick: () -> Unit) {
    val (color, label) = when (state) {
        is ConnectionState.Connected -> Color(0xFF4CAF50) to state.deviceName
        ConnectionState.Connecting -> Color(0xFFFFC107) to "Conectando…"
        is ConnectionState.Error -> Color(0xFFFF5252) to "Error de enlace"
        ConnectionState.Disconnected -> Color(0xFF6B7089) to "Sin conexión"
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1C1C28),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(color, CircleShape)
            )
            Text(
                text = label,
                color = Color(0xFFB0B4C8),
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
```

- [ ] **Step 3: RevScopeNavGraph — 5 tabs, topBar y deep link**

Cambios en `RevScopeNavGraph.kt`:

1. Nuevos imports de iconos: `androidx.compose.material.icons.filled.Build`, `.Map`, `.Settings`; imports de `TopAppBar`/fila superior: usar un `Row` simple (no `TopAppBar` de material3 experimental): `androidx.compose.foundation.layout.Row`, `Arrangement`, `fillMaxWidth`; import `com.revscope.core.obd.session` no hace falta — usar `com.revscope.core.obd.service.TripSummaryNotifier` solo en MainActivity.
2. Reemplazar `bottomNavItems`:

```kotlin
private val bottomNavItems = listOf(
    BottomNavItem(Screen.Dashboard, "Conducir", Icons.Default.Speed),
    BottomNavItem(Screen.LiveMap, "Mapa", Icons.Default.Map),
    BottomNavItem(Screen.Workshop, "Taller", Icons.Default.Build),
    BottomNavItem(Screen.Sessions, "Viajes", Icons.Default.History),
    BottomNavItem(Screen.Settings, "Ajustes", Icons.Default.Settings),
)
```

3. Firma con deep link:

```kotlin
@Composable
fun RevScopeNavGraph(
    navController: NavHostController = rememberNavController(),
    initialSessionId: Long? = null,
) {
```

y dentro, tras obtener `connectionVm`:

```kotlin
    LaunchedEffect(initialSessionId) {
        initialSessionId?.let { navController.navigate(Screen.SessionDetail.withId(it)) }
    }
```

(imports: `androidx.compose.runtime.LaunchedEffect`, `androidx.compose.runtime.collectAsState`)

4. `topBar` en el Scaffold (encima de `bottomBar`):

```kotlin
        topBar = {
            if (currentRoute in bottomNavRoutes) {
                val connState by connectionVm.connectionState.collectAsState()
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    ConnectionChip(state = connState) {
                        navController.navigate(Screen.AdapterScan.route)
                    }
                }
            }
        },
```

(si `ConnectionViewModel` no expone `connectionState`, revisar el nombre real del StateFlow en `core/obd/viewmodel/ConnectionViewModel.kt` y usarlo)

5. La ruta `Screen.Dashboard` gana el lambda de Modo Pista:

```kotlin
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    onNavigateToAdapterScan = { navController.navigate(Screen.AdapterScan.route) },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    onNavigateToTrackMode = { navController.navigate(Screen.TrackMode.route) },
                    connectionVm = connectionVm,
                )
            }
```

6. La ruta de Settings pierde los lambdas movidos a Taller:

```kotlin
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToVehicleProfiles = { navController.navigate(Screen.VehicleProfile.route) },
                )
            }
```

7. Placeholders temporales para que compile hasta que Tasks 6-7 registren las pantallas reales:

```kotlin
            composable(Screen.Workshop.route) { /* Task 6 registra WorkshopScreen */ }
            composable(Screen.LiveMap.route) { /* Task 7 registra LiveMapScreen */ }
```

(dejar `Text("En construcción")` como cuerpo si se quiere instalar entre tareas)

- [ ] **Step 4: DashboardScreen — botón Modo Pista**

Agregar parámetro `onNavigateToTrackMode: () -> Unit` a `DashboardScreen`. En la fila superior donde ya están los IconButton de adaptador/ajustes, agregar antes del de ajustes:

```kotlin
        IconButton(onClick = onNavigateToTrackMode) {
            Icon(Icons.Default.Flag, contentDescription = "Modo Pista", tint = AccentColor)
        }
```

(import `androidx.compose.material.icons.filled.Flag`; usar el mismo tint que los botones vecinos — leer el archivo primero y seguir su patrón exacto)

- [ ] **Step 5: SettingsScreen — quitar lo movido**

En `SettingsScreen.kt`: eliminar los parámetros `onNavigateToScanner`, `onNavigateToGearAnalyzer` y `onNavigateToTrackMode` de la firma, y eliminar las cards/botones que los usaban (Escáner Mode 22, Analizador de marchas, Modo Pista). Conservar `onNavigateToVehicleProfiles` y su card. No tocar nada más del archivo.

- [ ] **Step 6: MainActivity — leer el extra del resumen**

En `MainActivity` (localizar con `Glob app/src/main/**/MainActivity.kt`), donde se llama `RevScopeNavGraph()`:

```kotlin
            val openSessionId = intent
                .getLongExtra(TripSummaryNotifier.EXTRA_SESSION_ID, -1L)
                .takeIf { it > 0 }
            RevScopeNavGraph(initialSessionId = openSessionId)
```

(import `com.revscope.core.obd.service.TripSummaryNotifier`)

- [ ] **Step 7: Compilar app completa**

Run: `$GRADLE :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (fallará solo si quedó alguna referencia a los parámetros eliminados de SettingsScreen — buscarlas con Grep y limpiar)

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/com/revscope/app/ feature/dashboard/src/main/kotlin/com/revscope/feature/dashboard/DashboardScreen.kt feature/settings/src/main/kotlin/com/revscope/feature/settings/SettingsScreen.kt
git commit -m "refactor: navegación en 5 pestañas por intención de uso, chip de conexión global y deep link del resumen de viaje"
```

---

### Task 6: Módulo feature:workshop con el hub Taller

**Files:**
- Create: `feature/workshop/build.gradle.kts`
- Create: `feature/workshop/src/main/AndroidManifest.xml`
- Create: `feature/workshop/src/main/kotlin/com/revscope/feature/workshop/WorkshopScreen.kt`
- Modify: `settings.gradle.kts` (raíz)
- Modify: `app/build.gradle.kts` (agregar dependencia)
- Modify: `app/src/main/kotlin/com/revscope/app/navigation/RevScopeNavGraph.kt`

**Interfaces:**
- Consumes: `ConnectionViewModel` (core:obd), rutas existentes Dtc/Sensors/Mode22Scanner/GearAnalyzer/VehicleProfile.
- Produces: `WorkshopScreen(connectionVm, onOpenDtc, onOpenSensors, onOpenScanner, onOpenGearAnalyzer, onOpenProfiles)` — el Plan 2 le agregará las cards de Chequeo de salud y Mezcla en vivo.

- [ ] **Step 1: Boilerplate del módulo**

`feature/workshop/build.gradle.kts` — copiar `feature/dtc/build.gradle.kts` y cambiar: `namespace = "com.revscope.feature.workshop"`, quitar `implementation(project(":core:intelligence"))`. Agregar la dependencia de iconos extendidos con el mismo alias que usa `feature/dashboard` o `app` (buscar `icons` en `gradle/libs.versions.toml`).

`feature/workshop/src/main/AndroidManifest.xml`:

```xml
<manifest />
```

En `settings.gradle.kts` raíz, junto a los demás `include(":feature:...")`:

```kotlin
include(":feature:workshop")
```

En `app/build.gradle.kts`, junto a las demás dependencias de features:

```kotlin
    implementation(project(":feature:workshop"))
```

- [ ] **Step 2: WorkshopScreen.kt**

```kotlin
package com.revscope.feature.workshop

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.viewmodel.ConnectionViewModel

private val AccentColor = Color(0xFFE8FF00)
private val SurfaceColor = Color(0xFF12121A)
private val TextColor = Color(0xFFE6E8F0)
private val TextMutedColor = Color(0xFF6B7089)

private data class WorkshopTool(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val needsConnection: Boolean,
    val onOpen: () -> Unit,
)

@Composable
fun WorkshopScreen(
    connectionVm: ConnectionViewModel,
    onOpenDtc: () -> Unit,
    onOpenSensors: () -> Unit,
    onOpenScanner: () -> Unit,
    onOpenGearAnalyzer: () -> Unit,
    onOpenProfiles: () -> Unit,
) {
    val connState by connectionVm.connectionState.collectAsState()
    val isConnected = connState is ConnectionState.Connected

    val tools = listOf(
        WorkshopTool(Icons.Default.BugReport, "Códigos de falla (DTC)",
            "Leer, explicar con IA y borrar códigos de error", true, onOpenDtc),
        WorkshopTool(Icons.Default.Timeline, "Gráficas de sensores",
            "Curvas en tiempo real de cualquier PID", true, onOpenSensors),
        WorkshopTool(Icons.Default.Search, "Escáner avanzado (Mode 22)",
            "Descubrir PIDs propietarios del fabricante", true, onOpenScanner),
        WorkshopTool(Icons.Default.Settings, "Analizador de marchas",
            "Calibrar la relación RPM/velocidad por marcha", true, onOpenGearAnalyzer),
        WorkshopTool(Icons.Default.DirectionsCar, "Perfiles de vehículo",
            "Vehículos guardados, línea roja y VIN", false, onOpenProfiles),
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Taller", color = TextColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            if (!isConnected) {
                Text(
                    "Conecta el adaptador para usar las herramientas de diagnóstico",
                    color = TextMutedColor, fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        items(tools) { tool ->
            val enabled = isConnected || !tool.needsConnection
            ToolCard(tool, enabled)
        }
    }
}

@Composable
private fun ToolCard(tool: WorkshopTool, enabled: Boolean) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = SurfaceColor,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = tool.onOpen),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(
                tool.icon,
                contentDescription = null,
                tint = if (enabled) AccentColor else TextMutedColor,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    tool.title,
                    color = if (enabled) TextColor else TextMutedColor,
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (enabled) tool.description else "Requiere conexión",
                    color = TextMutedColor, fontSize = 12.sp,
                )
            }
        }
    }
}
```

- [ ] **Step 3: Registrar en el NavGraph**

Reemplazar el placeholder de `Screen.Workshop.route`:

```kotlin
            composable(Screen.Workshop.route) {
                WorkshopScreen(
                    connectionVm = connectionVm,
                    onOpenDtc = { navController.navigate(Screen.Dtc.route) },
                    onOpenSensors = { navController.navigate(Screen.Sensors.route) },
                    onOpenScanner = { navController.navigate(Screen.Mode22Scanner.route) },
                    onOpenGearAnalyzer = { navController.navigate(Screen.GearAnalyzer.route) },
                    onOpenProfiles = { navController.navigate(Screen.VehicleProfile.route) },
                )
            }
```

(import `com.revscope.feature.workshop.WorkshopScreen`)

- [ ] **Step 4: Compilar**

Run: `$GRADLE :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts app/build.gradle.kts feature/workshop/ app/src/main/kotlin/com/revscope/app/navigation/RevScopeNavGraph.kt
git commit -m "feat: hub Taller — herramientas de diagnóstico agrupadas en pestaña propia"
```

---

### Task 7: Módulo feature:map con LiveMapScreen

**Files:**
- Create: `feature/map/build.gradle.kts`
- Create: `feature/map/src/main/AndroidManifest.xml`
- Create: `feature/map/src/main/kotlin/com/revscope/feature/map/LiveMapViewModel.kt`
- Create: `feature/map/src/main/kotlin/com/revscope/feature/map/LiveMapScreen.kt`
- Modify: `settings.gradle.kts`, `app/build.gradle.kts`, `RevScopeNavGraph.kt`

**Interfaces:**
- Consumes: `LiveRouteHolder` (Task 4), `SpeedCameraDao.all()` (existe), `ObdSessionManager.readings` (existe), osmdroid (patrón de `feature/session/.../RealTrackMap.kt` — leerlo antes de escribir el mapa).

- [ ] **Step 1: Boilerplate del módulo**

Igual que Task 6 pero: `namespace = "com.revscope.feature.map"`, deps `":core:obd"`, `":core:data"`, `":core:common"` + osmdroid con el mismo alias que usa `feature/session` (buscar `osmdroid` en `feature/session/build.gradle.kts`). `include(":feature:map")` en settings, `implementation(project(":feature:map"))` en app.

`AndroidManifest.xml`:

```xml
<manifest />
```

- [ ] **Step 2: LiveMapViewModel**

```kotlin
package com.revscope.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revscope.core.data.db.dao.SpeedCameraDao
import com.revscope.core.data.db.entities.SpeedCameraEntity
import com.revscope.core.obd.service.LiveRouteHolder
import com.revscope.core.obd.session.ObdSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LiveMapViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    routeHolder: LiveRouteHolder,
    private val cameraDao: SpeedCameraDao,
    sessionManager: ObdSessionManager,
) : ViewModel() {

    val route: StateFlow<List<LiveRouteHolder.RoutePoint>> = routeHolder.points

    // Centro inicial cuando no hay viaje activo (mismo patrón que SettingsViewModel)
    private val _initialCenter = MutableStateFlow<LiveRouteHolder.RoutePoint?>(null)
    val initialCenter: StateFlow<LiveRouteHolder.RoutePoint?> = _initialCenter.asStateFlow()

    @SuppressLint("MissingPermission")
    private fun loadLastKnownLocation() {
        runCatching {
            val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }.getOrNull()?.let {
            _initialCenter.value = LiveRouteHolder.RoutePoint(it.latitude, it.longitude)
        }
    }

    val speedKmh: StateFlow<Int?> = sessionManager.readings
        .map { it["0D"]?.value?.toInt() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _cameras = MutableStateFlow<List<SpeedCameraEntity>>(emptyList())
    val cameras: StateFlow<List<SpeedCameraEntity>> = _cameras.asStateFlow()

    init {
        loadLastKnownLocation()
        viewModelScope.launch {
            runCatching { cameraDao.all() }.onSuccess { _cameras.value = it }
        }
    }
}
```

(imports extra: `android.annotation.SuppressLint`, `android.content.Context`, `android.location.LocationManager`, `dagger.hilt.android.qualifiers.ApplicationContext`. Verificar los nombres de campos de `SpeedCameraEntity` — lat/lon/límite — con Read antes de usarlos en la pantalla)

- [ ] **Step 3: LiveMapScreen**

Antes de escribir: **leer `feature/session/src/main/kotlin/com/revscope/feature/session/RealTrackMap.kt`** y replicar su init de osmdroid (Configuration userAgent, tile source, atribución © OSM). Estructura:

```kotlin
package com.revscope.feature.map

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import com.revscope.core.obd.service.LiveRouteHolder
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

@Composable
fun LiveMapScreen(viewModel: LiveMapViewModel = hiltViewModel()) {
    val route by viewModel.route.collectAsState()
    val cameras by viewModel.cameras.collectAsState()
    val speed by viewModel.speedKmh.collectAsState()
    val context = LocalContext.current

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                // Replicar aquí el init exacto de RealTrackMap (Configuration, tile source,
                // multi-touch, atribución © OpenStreetMap contributors)
                MapView(ctx).apply {
                    controller.setZoom(16.0)
                }
            },
            update = { map ->
                map.overlays.clear()
                // Radares: marcador + círculo de 400 m
                cameras.forEach { cam ->
                    map.overlays.add(Polygon(map).apply {
                        points = Polygon.pointsAsCircle(GeoPoint(cam.latitude, cam.longitude), 400.0)
                        fillPaint.color = 0x22FF5252
                        outlinePaint.color = 0x66FF5252.toInt()
                        outlinePaint.strokeWidth = 2f
                    })
                    map.overlays.add(Marker(map).apply {
                        position = GeoPoint(cam.latitude, cam.longitude)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = "Radar" + (cam.maxSpeedKmh?.let { " · $it km/h" } ?: "")
                    })
                }
                // Ruta activa
                if (route.isNotEmpty()) {
                    map.overlays.add(Polyline(map).apply {
                        setPoints(route.map { GeoPoint(it.lat, it.lon) })
                        outlinePaint.color = 0xFFE8FF00.toInt()
                        outlinePaint.strokeWidth = 8f
                    })
                    val last = route.last()
                    map.overlays.add(Marker(map).apply {
                        position = GeoPoint(last.lat, last.lon)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        title = "Tú"
                    })
                    map.controller.setCenter(GeoPoint(last.lat, last.lon))
                } else {
                    viewModel.initialCenter.value?.let {
                        map.controller.setCenter(GeoPoint(it.lat, it.lon))
                        map.controller.setZoom(13.0)
                    }
                }
                map.invalidate()
            },
        )

        // Velocidad actual
        speed?.let {
            Surface(
                color = Color(0xCC12121A),
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
            ) {
                Text(
                    "$it km/h",
                    color = Color(0xFFE8FF00),
                    fontSize = 28.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        // Abrir navegación externa
        FloatingActionButton(
            onClick = {
                val target = route.lastOrNull()
                val uri = if (target != null) "geo:${target.lat},${target.lon}" else "geo:0,0"
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri.toUri())) }
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(Icons.Default.Navigation, contentDescription = "Abrir en Maps")
        }
    }
}
```

Ajustar el campo del límite de velocidad (`maxSpeedKmh`) al nombre real de `SpeedCameraEntity`. Si el marcador "Tú" queda feo sin icono custom, es aceptable para esta versión.

- [ ] **Step 4: Registrar en NavGraph**

Reemplazar el placeholder:

```kotlin
            composable(Screen.LiveMap.route) { LiveMapScreen() }
```

(import `com.revscope.feature.map.LiveMapScreen`)

- [ ] **Step 5: Compilar**

Run: `$GRADLE :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add settings.gradle.kts app/build.gradle.kts feature/map/ app/src/main/kotlin/com/revscope/app/navigation/RevScopeNavGraph.kt
git commit -m "feat: pestaña Mapa — posición y ruta en vivo sobre OSM con radares y velocidad"
```

---

### Task 8: Build integral, tests y despliegue

- [ ] **Step 1: Suite completa**

Run: `$GRADLE :core:obd:testDebugUnitTest`
Expected: PASS — todos los tests (130 previos + 11 nuevos)

- [ ] **Step 2: APK debug**

Run: `$GRADLE :app:assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Instalar en el S25 (si está conectado por adb WiFi)**

```bash
adb devices
adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <serial> shell am start -n com.revscope.app/.MainActivity
```

Si no hay dispositivo, reportarlo y seguir.

- [ ] **Step 4: Push**

```powershell
$env:GITHUB_TOKEN = $null; git push
```

(el token de trabajo pisa el keyring — limpiarlo siempre antes de push en este repo personal)
