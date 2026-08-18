# Mapa base vivo — Plan de implementación (sub-proyecto A)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** El mapa funciona standalone como Google Maps abierto — puck GPS vivo, centrado al abrir, radares presentes sin tocar Ajustes ni iniciar viaje, cero GPS con el mapa cerrado.

**Architecture:** Un `MapLocationProvider` (LocationManager, vida atada a la pantalla) alimenta un `liveFix` en el ViewModel que reusa el puck `ICON_ME` y el follow existentes; los DAOs de radares/huecos pasan de one-shot a `Flow` (invalidation de Room) para que cualquier descarga aparezca al instante; el provider también entrega fixes a `CameraCoverageTracker` (ya session-independiente en su API) para auto-descargar radares sin viaje activo. La decisión de centrado inicial se extrae a una máquina de estados pura y testeada.

**Tech Stack:** Kotlin, Jetpack Compose, Hilt, Room 2.7 (Flow), MapLibre 13.4.1, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-08-18-mapa-base-vivo-design.md`

## Global Constraints

- Repo: `c:\personal\OBD2`. NO hay `gradlew` en el repo — usar: `GRADLE="$HOME/.gradle/wrapper/dists/gradle-8.11.1-bin/bpt9gzteqjrbo1mjrsomdt32c/gradle-8.11.1/bin/gradle"` (bash) desde la raíz del repo.
- Unit tests JVM: `"$GRADLE" :feature:map:testDebugUnitTest --tests "<clase>"`.
- Compilación rápida: `"$GRADLE" :feature:map:compileDebugKotlin` (y `:core:data:compileDebugKotlin` cuando se toque core:data).
- Sin dependencias nuevas. `room-ktx` ya está; el patrón `observeAll(): Flow` ya existe en `SessionDao.kt:21`.
- Commits en español, formato `tipo: descripción` (feat/fix/test/docs). SIN `Co-Authored-By`.
- Comentarios solo cuando el POR QUÉ no es obvio (estilo del repo: español, explican restricciones).
- NO tocar: `ObdForegroundService`, `GpsTrackRecorder`, `SpeedCameraUpdater`, `NavigationController`.

---

### Task 1: Máquina de estados pura de centrado inicial

**Files:**
- Create: `feature/map/src/main/kotlin/com/revscope/feature/map/location/InitialCentering.kt`
- Test: `feature/map/src/test/kotlin/com/revscope/feature/map/location/InitialCenteringTest.kt`

**Interfaces:**
- Consumes: `LiveRouteHolder.RoutePoint(lat: Double, lon: Double)` de `com.revscope.core.obd.service.LiveRouteHolder` (data class existente).
- Produces: `class InitialCentering` con `fun onLastKnown(p: LiveRouteHolder.RoutePoint?): CenterAction?`, `fun onLiveFix(p: LiveRouteHolder.RoutePoint?): CenterAction?`, `fun onUserPan()`; `data class CenterAction(val lat: Double, val lon: Double, val zoom: Double)`; constantes `InitialCentering.IDLE_ZOOM = 13.0` y `InitialCentering.INITIAL_ZOOM = 16.0`. Task 5 la consume desde `LiveMapScreen`.

Reglas de negocio: al abrir el mapa se centra UNA vez en lastKnown (zoom lejano 13); el primer fix GPS vivo re-centra UNA vez con zoom cercano 16; después de eso el follow normal manda; si el usuario paneó antes del primer fix, no se le pelea la cámara.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.revscope.feature.map.location

import com.revscope.core.obd.service.LiveRouteHolder.RoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InitialCenteringTest {

    private val medellin = RoutePoint(6.2442, -75.5812)
    private val envigado = RoutePoint(6.1759, -75.5915)

    @Test
    fun `lastKnown centra una vez con zoom lejano`() {
        val c = InitialCentering()
        assertEquals(
            CenterAction(medellin.lat, medellin.lon, InitialCentering.IDLE_ZOOM),
            c.onLastKnown(medellin),
        )
        assertNull(c.onLastKnown(medellin))
    }

    @Test
    fun `lastKnown null no centra`() {
        assertNull(InitialCentering().onLastKnown(null))
    }

    @Test
    fun `primer fix vivo recentra una vez con zoom cercano`() {
        val c = InitialCentering()
        c.onLastKnown(medellin)
        assertEquals(
            CenterAction(envigado.lat, envigado.lon, InitialCentering.INITIAL_ZOOM),
            c.onLiveFix(envigado),
        )
        assertNull(c.onLiveFix(envigado))
    }

    @Test
    fun `fix vivo funciona aunque nunca hubo lastKnown`() {
        assertEquals(
            CenterAction(envigado.lat, envigado.lon, InitialCentering.INITIAL_ZOOM),
            InitialCentering().onLiveFix(envigado),
        )
    }

    @Test
    fun `tras el fix vivo el lastKnown tardio ya no centra`() {
        val c = InitialCentering()
        c.onLiveFix(envigado)
        assertNull(c.onLastKnown(medellin))
    }

    @Test
    fun `pan del usuario cancela todo centrado futuro`() {
        val c = InitialCentering()
        c.onUserPan()
        assertNull(c.onLastKnown(medellin))
        assertNull(c.onLiveFix(envigado))
    }

    @Test
    fun `fix null no consume el centrado`() {
        val c = InitialCentering()
        assertNull(c.onLiveFix(null))
        assertEquals(
            CenterAction(envigado.lat, envigado.lon, InitialCentering.INITIAL_ZOOM),
            c.onLiveFix(envigado),
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `"$GRADLE" :feature:map:testDebugUnitTest --tests "com.revscope.feature.map.location.InitialCenteringTest"`
Expected: FAIL — `Unresolved reference: InitialCentering` (error de compilación cuenta como rojo).

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.revscope.feature.map.location

import com.revscope.core.obd.service.LiveRouteHolder.RoutePoint

data class CenterAction(val lat: Double, val lon: Double, val zoom: Double)

/**
 * Decide los centrados automáticos al abrir el mapa. Máquina de estados pura:
 * lastKnown centra una vez (lejos), el primer fix vivo re-centra una vez (cerca),
 * y un pan del usuario cancela todo — la cámara nunca le pelea al dedo.
 */
class InitialCentering {

    private var doneLastKnown = false
    private var doneLiveFix = false
    private var cancelled = false

    fun onLastKnown(p: RoutePoint?): CenterAction? {
        if (cancelled || doneLastKnown || doneLiveFix || p == null) return null
        doneLastKnown = true
        return CenterAction(p.lat, p.lon, IDLE_ZOOM)
    }

    fun onLiveFix(p: RoutePoint?): CenterAction? {
        if (cancelled || doneLiveFix || p == null) return null
        doneLiveFix = true
        doneLastKnown = true
        return CenterAction(p.lat, p.lon, INITIAL_ZOOM)
    }

    fun onUserPan() {
        cancelled = true
    }

    companion object {
        const val IDLE_ZOOM = 13.0
        const val INITIAL_ZOOM = 16.0
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `"$GRADLE" :feature:map:testDebugUnitTest --tests "com.revscope.feature.map.location.InitialCenteringTest"`
Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```bash
git add feature/map/src/main/kotlin/com/revscope/feature/map/location/InitialCentering.kt feature/map/src/test/kotlin/com/revscope/feature/map/location/InitialCenteringTest.kt
git commit -m "feat: máquina de estados pura para el centrado inicial del mapa"
```

---

### Task 2: Radares y huecos reactivos (Flow de Room)

**Files:**
- Modify: `core/data/src/main/kotlin/com/revscope/core/data/db/dao/SpeedCameraDao.kt` (después de `all()`, línea 17)
- Modify: `core/data/src/main/kotlin/com/revscope/core/data/db/dao/PotholeDao.kt` (después de `all()`, línea 13)
- Modify: `feature/map/src/main/kotlin/com/revscope/feature/map/LiveMapViewModel.kt:215-227`

**Interfaces:**
- Consumes: patrón existente `SessionDao.observeAll(): Flow<List<SessionEntity>>` (`core/data/.../dao/SessionDao.kt:21`).
- Produces: `SpeedCameraDao.observeAll(): Flow<List<SpeedCameraEntity>>` y `PotholeDao.observeAll(): Flow<List<PotholeEntity>>`. `LiveMapViewModel.cameras`/`potholes` conservan su tipo público `StateFlow<List<…>>` (la pantalla no cambia).

- [ ] **Step 1: Add Flow queries to both DAOs**

En `SpeedCameraDao.kt`, debajo de `all()`:

```kotlin
    /** Variante observable: el mapa se actualiza solo cuando una descarga reemplaza la tabla. */
    @Query("SELECT * FROM speed_cameras")
    fun observeAll(): Flow<List<SpeedCameraEntity>>
```

Import necesario: `import kotlinx.coroutines.flow.Flow`.

En `PotholeDao.kt`, debajo de `all()`:

```kotlin
    @Query("SELECT * FROM potholes")
    fun observeAll(): Flow<List<PotholeEntity>>
```

Import necesario: `import kotlinx.coroutines.flow.Flow`.

- [ ] **Step 2: Replace the one-shot read in the ViewModel**

En `LiveMapViewModel.kt`, reemplazar el bloque de líneas 215-227:

```kotlin
    private val _cameras = MutableStateFlow<List<SpeedCameraEntity>>(emptyList())
    val cameras: StateFlow<List<SpeedCameraEntity>> = _cameras.asStateFlow()

    private val _potholes = MutableStateFlow<List<PotholeEntity>>(emptyList())
    val potholes: StateFlow<List<PotholeEntity>> = _potholes.asStateFlow()

    init {
        loadLastKnownLocation()
        viewModelScope.launch {
            runCatching { cameraDao.all() }.onSuccess { _cameras.value = it }
            runCatching { potholeDao.all() }.onSuccess { _potholes.value = it }
        }
    }
```

por:

```kotlin
    // Flow y no one-shot: este ViewModel sobrevive los cambios de tab (restoreState),
    // así que una lectura única dejaba el mapa ciego a descargas posteriores.
    val cameras: StateFlow<List<SpeedCameraEntity>> = cameraDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val potholes: StateFlow<List<PotholeEntity>> = potholeDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        loadLastKnownLocation()
    }
```

`SharingStarted` y `stateIn` ya están importados (los usa `speedKmh`). Quitar los imports que queden sin uso tras el cambio (ninguno esperado: `MutableStateFlow`/`asStateFlow` siguen usados por otros campos).

- [ ] **Step 3: Compile both modules**

Run: `"$GRADLE" :core:data:compileDebugKotlin :feature:map:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run existing map tests (no regression)**

Run: `"$GRADLE" :feature:map:testDebugUnitTest`
Expected: PASS (los tests existentes de routing/search + InitialCenteringTest).

- [ ] **Step 5: Commit**

```bash
git add core/data/src/main/kotlin/com/revscope/core/data/db/dao/SpeedCameraDao.kt core/data/src/main/kotlin/com/revscope/core/data/db/dao/PotholeDao.kt feature/map/src/main/kotlin/com/revscope/feature/map/LiveMapViewModel.kt
git commit -m "fix: radares y huecos del mapa observan la DB con Flow en vez de una lectura única"
```

---

### Task 3: MapLocationProvider + wiring en el ViewModel

**Files:**
- Create: `feature/map/src/main/kotlin/com/revscope/feature/map/location/MapLocationProvider.kt`
- Modify: `feature/map/src/main/kotlin/com/revscope/feature/map/LiveMapViewModel.kt` (constructor, `lastKnownPoint()` línea 192-193, nuevo bloque)

**Interfaces:**
- Consumes: `CameraCoverageTracker.onGpsFix(latitude: Double, longitude: Double)` — `@Singleton` existente en `core/obd/.../cameras/CameraCoverageTracker.kt:40` (ya es session-independiente: throttle 60 s, cooldown 30 min, chequeo >35 km internos — NO tocar).
- Produces: `MapLocationProvider` con `val fix: StateFlow<LiveRouteHolder.RoutePoint?>`, `fun start()`, `fun stop()`. En el ViewModel: `val liveFix: StateFlow<LiveRouteHolder.RoutePoint?>`, `fun onMapVisible()`, `fun onMapHidden()`. Task 4 y 5 los consumen.

- [ ] **Step 1: Create the provider**

```kotlin
package com.revscope.feature.map.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import com.revscope.core.obd.service.LiveRouteHolder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * GPS para el mapa sin viaje activo. Vive solo mientras la pantalla del mapa está
 * visible (la pantalla llama start/stop): con el mapa cerrado no consume nada.
 * Durante un viaje el service sigue siendo la fuente del puck; este provider solo
 * llena el vacío cuando no hay sesión.
 */
class MapLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val _fix = MutableStateFlow<LiveRouteHolder.RoutePoint?>(null)
    val fix: StateFlow<LiveRouteHolder.RoutePoint?> = _fix.asStateFlow()

    private var listener: LocationListener? = null

    @SuppressLint("MissingPermission")
    fun start() {
        if (listener != null) return
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val l = LocationListener { location: Location ->
            _fix.value = LiveRouteHolder.RoutePoint(location.latitude, location.longitude)
        }
        // Sin permiso o sin provider GPS: el mapa queda en lastKnown, el banner de la
        // pantalla se encarga de pedirlo — acá no se revienta.
        runCatching {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, UPDATE_INTERVAL_MS, UPDATE_MIN_DISTANCE_M, l)
        }.onSuccess { listener = l }
    }

    fun stop() {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        listener?.let { lm.removeUpdates(it) }
        listener = null
    }

    private companion object {
        const val UPDATE_INTERVAL_MS = 1_000L
        const val UPDATE_MIN_DISTANCE_M = 3f
    }
}
```

- [ ] **Step 2: Wire it into the ViewModel**

En `LiveMapViewModel.kt`:

1. Imports nuevos:

```kotlin
import com.revscope.core.obd.cameras.CameraCoverageTracker
import com.revscope.feature.map.location.MapLocationProvider
import kotlinx.coroutines.flow.filterNotNull
```

2. Constructor — agregar dos parámetros al final:

```kotlin
    private val locationProvider: MapLocationProvider,
    private val coverageTracker: CameraCoverageTracker,
```

3. Después del bloque de `initialCenter` (tras la línea de `loadLastKnownLocation`, ~128), agregar:

```kotlin
    // ── GPS vivo sin viaje ───────────────────────────────────────────────────

    /** Fix del provider del mapa — null sin permiso, sin señal o con el mapa cerrado. */
    val liveFix: StateFlow<LiveRouteHolder.RoutePoint?> = locationProvider.fix

    fun onMapVisible() = locationProvider.start()

    fun onMapHidden() = locationProvider.stop()
```

4. En `init` (queda de Task 2 solo con `loadLastKnownLocation()`), agregar la alimentación del tracker:

```kotlin
    init {
        loadLastKnownLocation()
        // El tracker ya trae throttle/cooldown/chequeo de cobertura: acá solo se le
        // entregan los fixes que antes solo veía durante un viaje activo.
        viewModelScope.launch {
            locationProvider.fix.filterNotNull().collect { coverageTracker.onGpsFix(it.lat, it.lon) }
        }
    }
```

5. `lastKnownPoint()` (líneas 192-193) — el fix vivo entra a la cadena (mejora el origen de búsqueda/ruteo):

```kotlin
    private fun lastKnownPoint(): LiveRouteHolder.RoutePoint? =
        route.value.lastOrNull() ?: locationProvider.fix.value ?: _initialCenter.value
```

6. `setDestination()` (línea 197) — mismo fallback:

```kotlin
        val origin = route.value.lastOrNull() ?: locationProvider.fix.value ?: _initialCenter.value ?: return
```

- [ ] **Step 3: Compile**

Run: `"$GRADLE" :feature:map:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run map unit tests**

Run: `"$GRADLE" :feature:map:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add feature/map/src/main/kotlin/com/revscope/feature/map/location/MapLocationProvider.kt feature/map/src/main/kotlin/com/revscope/feature/map/LiveMapViewModel.kt
git commit -m "feat: GPS vivo del mapa sin viaje — provider propio y auto-descarga de radares por fix"
```

---

### Task 4: Puck standalone en las capas

**Files:**
- Modify: `feature/map/src/main/kotlin/com/revscope/feature/map/LiveMapLayers.kt` (`LiveMapData` ~línea 58, `markerFeatures()` línea 215-230)

**Interfaces:**
- Consumes: `LiveMapData` (data class del mismo archivo), `liveFix` de Task 3.
- Produces: `LiveMapData` gana `val liveFix: LiveRouteHolder.RoutePoint? = null` (último campo, con default para no romper llamadas existentes). El puck `ICON_ME` se dibuja en `route.lastOrNull() ?: liveFix`. Task 5 llena el campo desde la pantalla.

- [ ] **Step 1: Add the field**

En `LiveMapData`, agregar como último campo:

```kotlin
    val liveFix: LiveRouteHolder.RoutePoint? = null,
```

- [ ] **Step 2: Draw the puck from route or standalone fix**

En `markerFeatures()`, reemplazar la línea 227:

```kotlin
    data.route.lastOrNull()?.let { features += marker(it.lat, it.lon, ICON_ME) }
```

por:

```kotlin
    // Con viaje activo el puck sigue la ruta viva; sin viaje, el fix del provider del mapa.
    (data.route.lastOrNull() ?: data.liveFix)?.let { features += marker(it.lat, it.lon, ICON_ME) }
```

- [ ] **Step 3: Compile**

Run: `"$GRADLE" :feature:map:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add feature/map/src/main/kotlin/com/revscope/feature/map/LiveMapLayers.kt
git commit -m "feat: puck del mapa visible sin viaje activo (fix standalone en LiveMapData)"
```

---

### Task 5: Integración en la pantalla — ciclo de vida, permiso, centrado y follow

**Files:**
- Modify: `feature/map/src/main/kotlin/com/revscope/feature/map/LiveMapScreen.kt` (constantes línea 61-62, colección de estado ~67-103, `LiveMapData` 105-114, listeners 130-142, efecto de datos 146-148, efecto de cámara 150-182, overlays ~184+)

**Interfaces:**
- Consumes: `InitialCentering`/`CenterAction` (Task 1), `viewModel.liveFix`/`onMapVisible()`/`onMapHidden()` (Task 3), `LiveMapData.liveFix` (Task 4).
- Produces: pantalla final. Nada nuevo para otros tasks.

- [ ] **Step 1: Lifecycle + permission state**

En `LiveMapScreen.kt`:

1. Imports nuevos:

```kotlin
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.core.content.ContextCompat
import com.revscope.feature.map.location.InitialCentering
```

2. Borrar las constantes muertas de la línea 61-62 (`INITIAL_ZOOM`, `IDLE_ZOOM`) — viven en `InitialCentering` desde Task 1.

3. Dentro de `LiveMapScreen`, junto a los `collectAsState()` existentes, agregar:

```kotlin
    val liveFix by viewModel.liveFix.collectAsState()
    val initialCenter by viewModel.initialCenter.collectAsState()
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasLocationPermission = granted
        if (granted) viewModel.onMapVisible()
    }
    val centering = remember { InitialCentering() }
```

Nota: `context` ya existe en la línea 88; mover su declaración arriba de `hasLocationPermission` si hace falta por orden.

4. Debajo de los `remember`, el ciclo de vida del GPS:

```kotlin
    // GPS solo mientras el mapa está en pantalla: al salir del tab se corta.
    DisposableEffect(hasLocationPermission) {
        if (hasLocationPermission) viewModel.onMapVisible()
        onDispose { viewModel.onMapHidden() }
    }
```

- [ ] **Step 2: Pass the fix to the layers and fix the effect keys**

1. En la construcción de `LiveMapData` (línea 105-114), agregar el campo:

```kotlin
        liveFix = liveFix,
```

2. En el `LaunchedEffect` de datos (línea 146), agregar `mapRef` y `liveFix` a las keys:

```kotlin
        LaunchedEffect(mapRef, styleEpoch, routeRevision, cameras, potholes, peers, approaching, alertRadiusM, destination, plannedRoute, liveFix) {
            mapRef?.style?.let { if (it.isFullyLoaded()) updateLiveMapData(it, data) }
        }
```

3. En el `LaunchedEffect` de listeners (línea 130), avisar el pan a la máquina de centrado — dentro de `onMoveBegin`:

```kotlin
                override fun onMoveBegin(detector: MoveGestureDetector) {
                    followEnabled = false
                    centering.onUserPan()
                }
```

- [ ] **Step 3: Rewrite the camera effect**

Reemplazar el `LaunchedEffect` de cámara completo (líneas 150-182) por:

```kotlin
        LaunchedEffect(mapRef, styleEpoch, routeRevision, followEnabled, headingUp, liveFix, initialCenter) {
            val map = mapRef ?: return@LaunchedEffect
            val last = route.lastOrNull()
            if (last != null) {
                if (followEnabled && routeRevision != lastCenteredRevision) {
                    lastCenteredRevision = routeRevision
                    // Sin .zoom(): el follow re-centra pero no re-zoomea, así que el pinch del
                    // usuario se respeta indefinidamente.
                    map.animateCamera(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder()
                                .target(LatLng(last.lat, last.lon))
                                .bearing(if (headingUp) currentBearingDegrees(route) else 0.0)
                                .build(),
                        ),
                    )
                } else if (!headingUp && map.cameraPosition.bearing != 0.0) {
                    // Apagar rumbo-arriba des-rota de inmediato, sin esperar revisión.
                    map.animateCamera(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder().bearing(0.0).build(),
                        ),
                    )
                }
            } else {
                centering.onLiveFix(liveFix)?.let {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.lat, it.lon), it.zoom))
                    return@LaunchedEffect
                }
                centering.onLastKnown(initialCenter)?.let {
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.lat, it.lon), it.zoom))
                    return@LaunchedEffect
                }
                // Sin viaje pero con follow armado: el puck standalone también se sigue.
                if (followEnabled && liveFix != null) {
                    map.animateCamera(
                        CameraUpdateFactory.newLatLng(LatLng(liveFix!!.lat, liveFix!!.lon)),
                    )
                }
            }
        }
```

Borrar la variable `hasCenteredInitial` (línea 103) — la reemplaza `centering`.

- [ ] **Step 4: Permission banner**

Dentro del `Box`, después del texto de atribución (línea 189), agregar:

```kotlin
        if (!hasLocationPermission) {
            Surface(
                color = Color(0xF2121218),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 28.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Ubicación desactivada",
                        color = Color(0xFFF0F0F8),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp),
                    )
                    TextButton(
                        onClick = { permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                    ) {
                        Text("Permitir", color = Color(0xFFE8FF00), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
```

(`Surface`, `Row`, `TextButton`, `FontWeight` ya están importados — se usan en el toast de error de navegación.)

- [ ] **Step 5: Compile and run all map tests**

Run: `"$GRADLE" :feature:map:compileDebugKotlin && "$GRADLE" :feature:map:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, tests PASS.

- [ ] **Step 6: Commit**

```bash
git add feature/map/src/main/kotlin/com/revscope/feature/map/LiveMapScreen.kt
git commit -m "feat: mapa vivo standalone — GPS por pantalla, centrado inicial, banner de permiso y follow sin viaje"
```

---

### Task 6: Build completo + QA manual en dispositivo

**Files:**
- Ninguno nuevo (verificación).

- [ ] **Step 1: Full build + all unit tests**

Run: `"$GRADLE" :app:assembleDebug test`
Expected: BUILD SUCCESSFUL, todos los unit tests JVM en verde. (Recordar: exit code real, no `| tail` — un pipe enmascara el fallo.)

- [ ] **Step 2: Install on device**

Run: `"$GRADLE" installDebug` (device por USB/WiFi ADB; si `connect` falla con ping OK → re-pair, el puerto rota — usar serial mDNS).
Expected: instalado.

- [ ] **Step 3: Manual QA checklist**

Con datos de app borrados (instalación "limpia") y SIN adaptador OBD:

1. Abrir app → saltar onboarding → tab Mapa.
2. El mapa centra en la última ubicación conocida de inmediato (zoom lejano) y, al primer fix GPS (segundos, a cielo abierto), re-centra con zoom cercano. NO queda en vista mundo.
3. El puck amarillo aparece y se mueve al caminar/conducir SIN iniciar viaje.
4. En ~1-2 min (throttle del tracker), los radares aparecen solos en el mapa — sin tocar Ajustes. Verificar en logcat: `CameraCoverageTracker: left coverage — downloading around …`.
5. Cambiar a otro tab y volver al mapa: radares siguen; el GPS se cortó al salir (verificar icono GPS de la status bar apagado en otros tabs).
6. Revocar permiso de ubicación (Ajustes Android) → reabrir mapa → banner "Ubicación desactivada" + botón Permitir funciona.
7. Con viaje GPS iniciado: follow y rumbo-arriba se comportan como antes (regresión).
8. Descargar radares manualmente desde Ajustes con el mapa abierto en segundo plano → volver al mapa → aparecen sin reiniciar.

- [ ] **Step 4: Final commit (if QA fixes were needed)**

Cualquier fix de QA se commitea con `fix: <qué>` antes de cerrar el sub-proyecto.
