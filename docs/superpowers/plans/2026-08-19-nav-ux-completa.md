# UX de Navegación Completa — Implementation Plan (sub-proyecto B)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Navegación al nivel de Google Maps sobre el stack existente: un tap para navegar, recálculo automático al desviarse, banner con maniobra encadenada, cámara de navegación inclinada, lugares guardados (Casa/Trabajo/favoritos/recientes) y modo nocturno automático.

**Architecture:** Todo se apoya en lo que ya existe: Ferrostar ya detecta `offRoute` (NavigationState.offRoute), la voz ya anuncia "Se salió de la ruta, recalculando" (ManeuverAnnouncer.OFF_ROUTE_PHRASE), y NavigationProgressBar ya muestra distancia/tiempo/hora de llegada. Lo nuevo: un `RerouteDecider` puro decide cuándo re-pedir la ruta a OSRM; `NavigationController.start()` ya reemplaza la sesión (stop interno), así que el swap de ruta es gratis. Lugares guardados = tabla Room nueva v17. Nocturno auto = `SunTimes` puro + preferencia tri-estado.

**Tech Stack:** Kotlin, Compose, MapLibre GL (OpenGL), Ferrostar core 0.53, OSRM público, Room, Hilt, DataStore.

**Spec:** docs/superpowers/specs/2026-08-18-nav-ux-completa-design.md

## Global Constraints

- Trabajar desde `C:\personal\OBD2`. NO hay gradlew: en bash `GRADLE="$HOME/.gradle/wrapper/dists/gradle-8.11.1-bin/bpt9gzteqjrbo1mjrsomdt32c/gradle-8.11.1/bin/gradle"` y correr `"$GRADLE" <task>` desde la raíz.
- Commits en español, formato `tipo: descripción`, SIN línea Co-Authored-By.
- Comentarios solo cuando el porqué no es obvio, en español (estilo del repo).
- Throttle reroute: mínimo `10_000` ms entre recálculos, desvío sostenido `3_000` ms antes del primero.
- Cámara nav: pitch `50.0`; zoom por velocidad: `17.5` parado → `15.5` a 90+ km/h; acercamiento a maniobra desde `300` m hasta zoom `17.5`.
- Recientes: cap 20, LRU por `lastUsedAt`. HOME/WORK únicos (REPLACE por type).
- Targets táctiles nuevos ≥ 48dp (hallazgo research: estándar Android + "radical reachability").
- Colores del mapa existentes: panel `0xF2121218`/`0xE6121218`, acento `0xFFE8FF00`, muted `0xFF6B7089`, texto `0xFFF0F0F8`.
- Room sube a **v17** con migración real (NUNCA fallbackToDestructiveMigration — incidente 2026-07-09).
- Tests unitarios JVM en los módulos que ya los tienen (`core:navigation`, `feature:map`, `core:common`). Sin tests instrumentados nuevos.

---

### Task 1: Navegación de un tap + auto-reroute

**Files:**
- Create: `feature/map/src/main/kotlin/com/revscope/feature/map/routing/RerouteDecider.kt`
- Create: `feature/map/src/test/kotlin/com/revscope/feature/map/routing/RerouteDeciderTest.kt`
- Modify: `feature/map/src/main/kotlin/com/revscope/feature/map/LiveMapViewModel.kt` (startNavigation ~línea 56-72; agregar bloque reroute en init)
- Modify: `feature/map/src/main/kotlin/com/revscope/feature/map/routing/OsrmRouteFetcher.kt` (sin cambios de firma — se reusa `fetch`)

**Interfaces:**
- Consumes: `NavigationController.state: StateFlow<NavigationState?>` (`offRoute: Boolean`), `NavigationController.start(route, origin, destination): Boolean`, `OsrmRouteFetcher.fetch(fromLat, fromLon, toLat, toLon): NavigationRoute?`, `ObdSessionManager.startGpsSession()` (ya inyectado como `sessionManager`), `LiveRouteHolder.RoutePoint`.
- Produces: `RerouteDecider` con `fun shouldReroute(offRoute: Boolean, nowMs: Long): Boolean` y `fun reset()`. En el VM: reroute automático transparente (ningún API nuevo para la pantalla).

- [ ] **Step 1: Write the failing test**

`feature/map/src/test/kotlin/com/revscope/feature/map/routing/RerouteDeciderTest.kt`:

```kotlin
package com.revscope.feature.map.routing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RerouteDeciderTest {

    @Test
    fun `en ruta nunca dispara`() {
        val decider = RerouteDecider()
        assertFalse(decider.shouldReroute(offRoute = false, nowMs = 0L))
        assertFalse(decider.shouldReroute(offRoute = false, nowMs = 60_000L))
    }

    @Test
    fun `desvio breve no dispara — exige sostenido`() {
        val decider = RerouteDecider()
        assertFalse(decider.shouldReroute(offRoute = true, nowMs = 0L))
        assertFalse(decider.shouldReroute(offRoute = true, nowMs = 2_000L))
    }

    @Test
    fun `desvio sostenido 3s dispara una vez`() {
        val decider = RerouteDecider()
        assertFalse(decider.shouldReroute(offRoute = true, nowMs = 0L))
        assertTrue(decider.shouldReroute(offRoute = true, nowMs = 3_000L))
        // Inmediatamente después no repite: cooldown de 10 s.
        assertFalse(decider.shouldReroute(offRoute = true, nowMs = 3_500L))
    }

    @Test
    fun `sigue desviado tras cooldown — reintenta`() {
        val decider = RerouteDecider()
        decider.shouldReroute(offRoute = true, nowMs = 0L)
        assertTrue(decider.shouldReroute(offRoute = true, nowMs = 3_000L))
        assertFalse(decider.shouldReroute(offRoute = true, nowMs = 12_000L))
        assertTrue(decider.shouldReroute(offRoute = true, nowMs = 13_100L))
    }

    @Test
    fun `volver a ruta resetea el sostenido`() {
        val decider = RerouteDecider()
        decider.shouldReroute(offRoute = true, nowMs = 0L)
        assertFalse(decider.shouldReroute(offRoute = false, nowMs = 2_000L))
        // Nuevo desvío arranca la cuenta de cero.
        assertFalse(decider.shouldReroute(offRoute = true, nowMs = 2_500L))
        assertFalse(decider.shouldReroute(offRoute = true, nowMs = 4_000L))
        assertTrue(decider.shouldReroute(offRoute = true, nowMs = 5_500L))
    }

    @Test
    fun `reset limpia todo`() {
        val decider = RerouteDecider()
        decider.shouldReroute(offRoute = true, nowMs = 0L)
        decider.shouldReroute(offRoute = true, nowMs = 3_000L)
        decider.reset()
        assertFalse(decider.shouldReroute(offRoute = true, nowMs = 20_000L))
        assertTrue(decider.shouldReroute(offRoute = true, nowMs = 23_000L))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `"$GRADLE" :feature:map:testDebugUnitTest --tests "com.revscope.feature.map.routing.RerouteDeciderTest"`
Expected: FAIL (RerouteDecider no existe — error de compilación).

- [ ] **Step 3: Write minimal implementation**

`feature/map/src/main/kotlin/com/revscope/feature/map/routing/RerouteDecider.kt`:

```kotlin
package com.revscope.feature.map.routing

/**
 * Decide **cuándo** re-pedir la ruta a OSRM tras un desvío. Puro y determinístico, como
 * ManeuverAnnouncer: mismo tren de estados, mismas decisiones.
 *
 * Exige desvío sostenido para no recalcular por ruido de GPS, y un cooldown para no
 * castigar al OSRM público (es gratis; una petición por segundo sería abusar).
 */
class RerouteDecider(
    private val sustainedMs: Long = SUSTAINED_MS,
    private val cooldownMs: Long = COOLDOWN_MS,
) {

    private var offRouteSinceMs: Long = NEVER
    private var lastRerouteAtMs: Long = NEVER

    fun shouldReroute(offRoute: Boolean, nowMs: Long): Boolean {
        if (!offRoute) {
            offRouteSinceMs = NEVER
            return false
        }
        if (offRouteSinceMs == NEVER) offRouteSinceMs = nowMs
        if (nowMs - offRouteSinceMs < sustainedMs) return false
        if (lastRerouteAtMs != NEVER && nowMs - lastRerouteAtMs < cooldownMs) return false
        lastRerouteAtMs = nowMs
        return true
    }

    fun reset() {
        offRouteSinceMs = NEVER
        lastRerouteAtMs = NEVER
    }

    private companion object {
        const val NEVER = Long.MIN_VALUE
        const val SUSTAINED_MS = 3_000L
        const val COOLDOWN_MS = 10_000L
    }
}
```

Nota del test `reset limpia todo`: tras reset, el desvío en 20_000 arranca la cuenta sostenida de nuevo (20_000→23_000 = 3 s) y el cooldown quedó olvidado.

- [ ] **Step 4: Run test to verify it passes**

Run: `"$GRADLE" :feature:map:testDebugUnitTest --tests "com.revscope.feature.map.routing.RerouteDeciderTest"`
Expected: PASS 6/6.

- [ ] **Step 5: Wire en LiveMapViewModel — un tap + reroute**

En `LiveMapViewModel.kt`:

(a) Reemplazar `startNavigation()` (el guardrail muere — un tap arranca viaje GPS):

```kotlin
    /** Arranca la guía por voz sobre la ruta ya calculada. Sin viaje activo, lo inicia. */
    fun startNavigation() {
        val route = _plannedRoute.value ?: return
        val origin = lastKnownPoint() ?: return
        val destination = _destination.value ?: return
        // La navegación recibe el GPS del servicio en primer plano; si no hay viaje,
        // se arranca uno GPS aquí mismo — un tap, como Google Maps. startGpsSession()
        // es no-op si ya hay sesión o el OBD está conectando.
        if (sessionManager.currentSessionId.value == null) sessionManager.startGpsSession()
        val started = navigationController.start(
            route = route,
            origin = LatLon(origin.lat, origin.lon),
            destination = LatLon(destination.lat, destination.lon),
        )
        if (!started) _navigationError.value = "No se pudo iniciar la navegación"
    }
```

(b) Agregar imports `com.revscope.feature.map.routing.RerouteDecider` y campo + collect de reroute. Campos junto a `_navigationError`:

```kotlin
    private val rerouteDecider = RerouteDecider()
    private var rerouteJob: Job? = null
```

En `init`, después del collect de `locationProvider.fix` existente, agregar:

```kotlin
        viewModelScope.launch {
            navigationController.state.collect { state ->
                if (state == null) { rerouteDecider.reset(); return@collect }
                maybeReroute(state)
            }
        }
```

(c) Función privada nueva (después de `stopNavigation()`):

```kotlin
    private fun maybeReroute(state: NavigationState) {
        if (!state.offRoute) { rerouteDecider.shouldReroute(false, System.currentTimeMillis()); return }
        if (rerouteJob?.isActive == true) return
        if (!rerouteDecider.shouldReroute(true, System.currentTimeMillis())) return
        val current = state.snapped ?: lastKnownPoint()?.let { LatLon(it.lat, it.lon) } ?: return
        val destination = _destination.value ?: return
        rerouteJob = viewModelScope.launch(Dispatchers.IO) {
            val fresh = OsrmRouteFetcher.fetch(current.lat, current.lon, destination.lat, destination.lon)
                ?: return@launch // falla de red: la ruta vieja sigue; el cooldown regula el reintento
            _plannedRoute.value = fresh
            navigationController.start(
                route = fresh,
                origin = current,
                destination = LatLon(destination.lat, destination.lon),
            )
        }
    }
```

Nota: `navigationController.start()` ya hace `stop()` interno — el swap de sesión es atómico para la pantalla. La voz "Se salió de la ruta, recalculando" ya la anuncia `ManeuverAnnouncer` al entrar en offRoute; no duplicar.

- [ ] **Step 6: Compile + full map tests**

Run: `"$GRADLE" :feature:map:compileDebugKotlin :feature:map:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, todos verdes.

- [ ] **Step 7: Commit**

```bash
git add feature/map/src/main/kotlin/com/revscope/feature/map/routing/RerouteDecider.kt feature/map/src/test/kotlin/com/revscope/feature/map/routing/RerouteDeciderTest.kt feature/map/src/main/kotlin/com/revscope/feature/map/LiveMapViewModel.kt
git commit -m "feat: navegación de un tap y recálculo automático de ruta al desviarse"
```

---

### Task 2: Maniobra encadenada en el banner

**Files:**
- Modify: `core/navigation/src/main/kotlin/com/revscope/core/navigation/NavigationState.kt` (agregar campo)
- Modify: `core/navigation/src/main/kotlin/com/revscope/core/navigation/NavigationSession.kt` (describe(), ~línea 56-66)
- Modify: `feature/map/src/main/kotlin/com/revscope/feature/map/navigation/NavigationBanner.kt`
- Test: `core/navigation/src/test/kotlin/com/revscope/core/navigation/StepCursorTest.kt` (agregar caso) — si el helper nuevo vive en StepCursor

**Interfaces:**
- Consumes: `StepCursor.nextManeuverIndex(totalSteps, remainingSteps): Int` (existente), `steps: List<RouteStep>` con `maneuver: Maneuver`.
- Produces: `NavigationState.nextManeuver: Maneuver?` (default `null`; `IDLE` no cambia de forma), consumido por el banner. Umbral UI: encadenar solo si `distanceToManeuverM < 150`.

- [ ] **Step 1: Agregar campo a NavigationState**

En `NavigationState.kt`, después de `val maneuver: Maneuver?`:

```kotlin
    /** La maniobra que sigue a la actual — para encadenar "luego gire..." cuando vienen pegadas. */
    val nextManeuver: Maneuver? = null,
```

(campo con default: `IDLE` y los `copy` existentes compilan sin cambios).

- [ ] **Step 2: Poblarlo en NavigationSession.describe()**

En el branch `TripState.Navigating`, calcular el índice una vez y agregar el campo:

```kotlin
        is TripState.Navigating -> {
            val maneuverIndex = StepCursor.nextManeuverIndex(steps.size, trip.remainingSteps.size)
            NavigationState(
                maneuver = StepCursor.maneuverAhead(steps, trip.remainingSteps.size),
                nextManeuver = steps.getOrNull(maneuverIndex + 1)?.maneuver,
                maneuverIndex = maneuverIndex,
                distanceToManeuverM = trip.progress.distanceToNextManeuver.toInt(),
                distanceRemainingM = trip.progress.distanceRemaining.toInt(),
                durationRemainingS = trip.progress.durationRemaining.toInt(),
                snapped = trip.snappedUserLocation.coordinates.toLatLon(),
                offRoute = trip.deviation is RouteDeviation.Deviation,
                arrived = false,
            )
        }
```

- [ ] **Step 3: Mostrarlo en el banner**

En `NavigationBanner.kt`, dentro del `Column` central, después del `subtitle(state)?.let { ... }`:

```kotlin
                chained(state)?.let {
                    Text(it, color = MUTED, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
```

Y la función privada (junto a `subtitle`):

```kotlin
/** "Luego: ..." solo cuando las maniobras vienen pegadas; si no, es ruido. */
private fun chained(state: NavigationState): String? {
    if (state.arrived || state.offRoute) return null
    if (state.distanceToManeuverM >= CHAIN_THRESHOLD_M) return null
    val next = state.nextManeuver ?: return null
    return "Luego: ${maneuverLabel(next)}"
}

private const val CHAIN_THRESHOLD_M = 150
```

- [ ] **Step 4: Compile + tests de core:navigation**

Run: `"$GRADLE" :core:navigation:compileDebugKotlin :core:navigation:testDebugUnitTest :feature:map:compileDebugKotlin`
Expected: BUILD SUCCESSFUL, tests existentes verdes (el campo nuevo tiene default — `StepCursorTest`, `ManeuverAnnouncerTest` intactos).

- [ ] **Step 5: Commit**

```bash
git add core/navigation/src/main/kotlin/com/revscope/core/navigation/NavigationState.kt core/navigation/src/main/kotlin/com/revscope/core/navigation/NavigationSession.kt feature/map/src/main/kotlin/com/revscope/feature/map/navigation/NavigationBanner.kt
git commit -m "feat: banner de navegación encadena la maniobra siguiente cuando vienen pegadas"
```

---

### Task 3: Cámara de navegación (course-up, pitch, zoom dinámico)

**Files:**
- Create: `feature/map/src/main/kotlin/com/revscope/feature/map/navigation/NavCamera.kt`
- Create: `feature/map/src/test/kotlin/com/revscope/feature/map/navigation/NavCameraTest.kt`
- Modify: `feature/map/src/main/kotlin/com/revscope/feature/map/LiveMapScreen.kt` (efecto de cámara)

**Interfaces:**
- Consumes: `NavigationState` (`snapped`, `distanceToManeuverM`), `viewModel.speedKmh: StateFlow<Int?>`, `currentBearingDegrees(route)` (helper existente en LiveMapScreen), `followEnabled` (estado existente), MapLibre `CameraPosition.Builder().target().bearing().zoom().tilt()`.
- Produces: `object NavCamera { const val PITCH = 50.0; fun zoom(speedKmh: Int?, distToManeuverM: Int): Double }`.

- [ ] **Step 1: Write the failing test**

`feature/map/src/test/kotlin/com/revscope/feature/map/navigation/NavCameraTest.kt`:

```kotlin
package com.revscope.feature.map.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavCameraTest {

    @Test
    fun `parado y lejos de maniobra - zoom maximo urbano`() {
        assertEquals(17.5, NavCamera.zoom(speedKmh = 0, distToManeuverM = 5_000), 0.01)
    }

    @Test
    fun `rapido y lejos - zoom alejado`() {
        assertEquals(15.5, NavCamera.zoom(speedKmh = 90, distToManeuverM = 5_000), 0.01)
        // Más rápido no aleja más: 15.5 es el piso.
        assertEquals(15.5, NavCamera.zoom(speedKmh = 140, distToManeuverM = 5_000), 0.01)
    }

    @Test
    fun `velocidad intermedia interpola`() {
        val mid = NavCamera.zoom(speedKmh = 45, distToManeuverM = 5_000)
        assertTrue(mid > 15.5 && mid < 17.5)
    }

    @Test
    fun `acercandose a maniobra - zoom in progresivo hasta el tope`() {
        val far = NavCamera.zoom(speedKmh = 90, distToManeuverM = 300)
        val near = NavCamera.zoom(speedKmh = 90, distToManeuverM = 50)
        assertTrue(near > far)
        assertEquals(17.5, NavCamera.zoom(speedKmh = 90, distToManeuverM = 0), 0.01)
    }

    @Test
    fun `velocidad nula se trata como parado`() {
        assertEquals(17.5, NavCamera.zoom(speedKmh = null, distToManeuverM = 5_000), 0.01)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `"$GRADLE" :feature:map:testDebugUnitTest --tests "com.revscope.feature.map.navigation.NavCameraTest"`
Expected: FAIL (NavCamera no existe).

- [ ] **Step 3: Write minimal implementation**

`feature/map/src/main/kotlin/com/revscope/feature/map/navigation/NavCamera.kt`:

```kotlin
package com.revscope.feature.map.navigation

/**
 * Cámara durante la navegación, estilo Google Maps: inclinada, rumbo arriba, y con el zoom
 * gobernado por dos señales — velocidad (rápido ve más lejos) y cercanía a la maniobra
 * (el giro se mira de cerca). Función pura: la pantalla solo aplica el número.
 */
object NavCamera {

    const val PITCH = 50.0

    private const val ZOOM_NEAR = 17.5
    private const val ZOOM_FAR = 15.5
    private const val SPEED_FOR_FAR_KMH = 90.0
    private const val APPROACH_START_M = 300.0

    fun zoom(speedKmh: Int?, distToManeuverM: Int): Double {
        val speed = (speedKmh ?: 0).coerceAtLeast(0).toDouble()
        val bySpeed = ZOOM_NEAR - (ZOOM_NEAR - ZOOM_FAR) * (speed / SPEED_FOR_FAR_KMH).coerceAtMost(1.0)
        if (distToManeuverM >= APPROACH_START_M) return bySpeed
        // Dentro de la ventana de aproximación, interpola de bySpeed hacia ZOOM_NEAR.
        val t = 1.0 - (distToManeuverM / APPROACH_START_M)
        return bySpeed + (ZOOM_NEAR - bySpeed) * t
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `"$GRADLE" :feature:map:testDebugUnitTest --tests "com.revscope.feature.map.navigation.NavCameraTest"`
Expected: PASS 5/5.

- [ ] **Step 5: Aplicarla en LiveMapScreen**

En `LiveMapScreen.kt`: leer `navigation` ya existe (`val navigation by viewModel.navigation.collectAsState()`); `speedKmh` ya se colecta en `SpeedOverlay` — agregar arriba `val speedKmh by viewModel.speedKmh.collectAsState()` si no está como estado local.

En el `LaunchedEffect` de cámara (el que hoy maneja follow/heading-up/standalone), agregar `navigation` a las keys, y como PRIMERA rama del cuerpo:

```kotlin
            // Navegando: cámara dedicada — course-up, inclinada, zoom por velocidad y maniobra.
            val nav = navigation
            if (nav != null && !nav.arrived && followEnabled) {
                val target = nav.snapped ?: route.lastOrNull()?.let { LatLon(it.lat, it.lon) }
                if (target != null) {
                    map.animateCamera(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder()
                                .target(LatLng(target.lat, target.lon))
                                .bearing(currentBearingDegrees(route))
                                .zoom(NavCamera.zoom(speedKmh, nav.distanceToManeuverM))
                                .tilt(NavCamera.PITCH)
                                .build(),
                        ),
                    )
                }
                return@LaunchedEffect
            }
```

Y al TERMINAR la navegación (transición nav != null → null) des-inclinar una vez: agregar un `LaunchedEffect(navigation == null)` pequeño:

```kotlin
        // Fin de navegación: quitar la inclinación; zoom y centro quedan como estaban.
        LaunchedEffect(navigation == null) {
            if (navigation == null) {
                mapRef?.let { map ->
                    if (map.cameraPosition.tilt != 0.0) {
                        map.animateCamera(
                            CameraUpdateFactory.newCameraPosition(
                                CameraPosition.Builder().tilt(0.0).build(),
                            ),
                        )
                    }
                }
            }
        }
```

Imports nuevos: `com.revscope.feature.map.navigation.NavCamera`, `com.revscope.core.navigation.LatLon`.

Semántica intacta: pan del usuario apaga `followEnabled` (la cámara nav respeta eso), FAB MyLocation la rearma.

- [ ] **Step 6: Compile + tests**

Run: `"$GRADLE" :feature:map:compileDebugKotlin :feature:map:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add feature/map/src/main/kotlin/com/revscope/feature/map/navigation/NavCamera.kt feature/map/src/test/kotlin/com/revscope/feature/map/navigation/NavCameraTest.kt feature/map/src/main/kotlin/com/revscope/feature/map/LiveMapScreen.kt
git commit -m "feat: cámara de navegación — rumbo arriba, inclinada y zoom por velocidad y maniobra"
```

---

### Task 4: saved_places — entidad, DAO y migración v17

**Files:**
- Create: `core/data/src/main/kotlin/com/revscope/core/data/db/entities/SavedPlaceEntity.kt`
- Create: `core/data/src/main/kotlin/com/revscope/core/data/db/dao/SavedPlaceDao.kt`
- Modify: `core/data/src/main/kotlin/com/revscope/core/data/db/AppDatabase.kt` (entity, dao, version = 17)
- Modify: `core/data/src/main/kotlin/com/revscope/core/data/db/Migrations.kt` (MIGRATION_16_17 + registrarla donde se listan las demás)

**Interfaces:**
- Produces (Task 5 los consume con estos nombres exactos):
  - `SavedPlaceEntity(id: Long = 0, type: String, name: String, lat: Double, lon: Double, lastUsedAt: Long)` — `type` ∈ `"HOME" | "WORK" | "FAVORITE" | "RECENT"`.
  - `SavedPlaceDao.observeAll(): Flow<List<SavedPlaceEntity>>` (orden: lastUsedAt DESC)
  - `SavedPlaceDao.upsertSpecial(place: SavedPlaceEntity)` — borra el type previo e inserta (HOME/WORK únicos)
  - `SavedPlaceDao.recordRecent(place: SavedPlaceEntity)` — inserta RECENT y poda a 20 por LRU
  - `SavedPlaceDao.insert(place): Long`, `SavedPlaceDao.delete(id: Long)`, `SavedPlaceDao.touch(id: Long, nowMs: Long)`

- [ ] **Step 1: Entity**

`SavedPlaceEntity.kt`:

```kotlin
package com.revscope.core.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Un lugar guardado del mapa. type: HOME | WORK | FAVORITE | RECENT. */
@Entity(tableName = "saved_places")
data class SavedPlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val lastUsedAt: Long,
)
```

- [ ] **Step 2: DAO**

`SavedPlaceDao.kt`:

```kotlin
package com.revscope.core.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.revscope.core.data.db.entities.SavedPlaceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedPlaceDao {

    @Query("SELECT * FROM saved_places ORDER BY lastUsedAt DESC")
    fun observeAll(): Flow<List<SavedPlaceEntity>>

    @Insert
    suspend fun insert(place: SavedPlaceEntity): Long

    @Query("DELETE FROM saved_places WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE saved_places SET lastUsedAt = :nowMs WHERE id = :id")
    suspend fun touch(id: Long, nowMs: Long)

    @Query("DELETE FROM saved_places WHERE type = :type")
    suspend fun deleteByType(type: String)

    /** HOME y WORK son únicos: reemplazar es borrar el anterior e insertar el nuevo. */
    @Transaction
    suspend fun upsertSpecial(place: SavedPlaceEntity) {
        deleteByType(place.type)
        insert(place)
    }

    @Query(
        "DELETE FROM saved_places WHERE type = 'RECENT' AND id NOT IN " +
            "(SELECT id FROM saved_places WHERE type = 'RECENT' ORDER BY lastUsedAt DESC LIMIT 20)",
    )
    suspend fun pruneRecents()

    /** Un destino usado entra al historial; el historial no crece sin tope (LRU 20). */
    @Transaction
    suspend fun recordRecent(place: SavedPlaceEntity) {
        insert(place)
        pruneRecents()
    }
}
```

- [ ] **Step 3: Migración + registro**

En `Migrations.kt`, siguiendo el patrón de las migraciones existentes (mirar MIGRATION_15_16 como referencia de estilo), agregar:

```kotlin
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `saved_places` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`lat` REAL NOT NULL, " +
                "`lon` REAL NOT NULL, " +
                "`lastUsedAt` INTEGER NOT NULL)",
        )
    }
}
```

y registrarla en el mismo lugar donde se registran las demás (array/lista `ALL_MIGRATIONS` o `.addMigrations(...)` — seguir lo que exista en el repo; grep `MIGRATION_15_16` para encontrar el punto de registro).

En `AppDatabase.kt`: agregar `SavedPlaceEntity::class` a `entities`, `version = 17`, y `abstract fun savedPlaceDao(): SavedPlaceDao`.

- [ ] **Step 4: Compile (Room valida el schema en compile-time via KSP)**

Run: `"$GRADLE" :core:data:compileDebugKotlin`
Expected: BUILD SUCCESSFUL y el schema JSON `17.json` generado en `core/data/schemas/`.

- [ ] **Step 5: Commit**

```bash
git add core/data/src/main/kotlin/com/revscope/core/data/db/entities/SavedPlaceEntity.kt core/data/src/main/kotlin/com/revscope/core/data/db/dao/SavedPlaceDao.kt core/data/src/main/kotlin/com/revscope/core/data/db/AppDatabase.kt core/data/src/main/kotlin/com/revscope/core/data/db/Migrations.kt core/data/schemas/
git commit -m "feat: tabla saved_places (casa, trabajo, favoritos, recientes) — Room v17"
```

---

### Task 5: Lugares guardados en búsqueda — chips, recientes y favoritos

**Files:**
- Modify: `feature/map/src/main/kotlin/com/revscope/feature/map/LiveMapViewModel.kt` (inyectar dao, exponer lugares, grabar recientes)
- Modify: `feature/map/src/main/kotlin/com/revscope/feature/map/SearchOverlay.kt` (chips + recientes + estrella)
- Modify: `feature/map/src/main/kotlin/com/revscope/feature/map/LiveMapScreen.kt` (pasar los callbacks nuevos)

**Interfaces:**
- Consumes: `SavedPlaceDao` (Task 4, firmas exactas de su bloque Produces), `PlaceResult(name, subtitle, lat, lon)` existente.
- Produces (la pantalla los consume): en el VM — `savedPlaces: StateFlow<List<SavedPlaceEntity>>`, `fun selectSavedPlace(place: SavedPlaceEntity)`, `fun saveHome(place: PlaceResult)`, `fun saveWork(place: PlaceResult)`, `fun saveFavorite(place: PlaceResult)`, `fun removePlace(id: Long)`. En SearchOverlay — parámetros nuevos `savedPlaces: List<SavedPlaceEntity>`, `onSelectSaved: (SavedPlaceEntity) -> Unit`, `onSaveFavorite: (PlaceResult) -> Unit`, `onRemoveSaved: (Long) -> Unit`.

- [ ] **Step 1: ViewModel**

En `LiveMapViewModel.kt`:

(a) Constructor: agregar `private val savedPlaceDao: SavedPlaceDao,` (import `com.revscope.core.data.db.dao.SavedPlaceDao`, `com.revscope.core.data.db.entities.SavedPlaceEntity`).

(b) Junto a `cameras`/`potholes` (mismo patrón stateIn):

```kotlin
    val savedPlaces: StateFlow<List<SavedPlaceEntity>> = savedPlaceDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
```

(c) Reemplazar `selectSearchResult` y agregar las funciones de lugares (después de `clearSearch()`):

```kotlin
    /** Elegir un resultado reusa el mismo camino que el long-press y entra al historial. */
    fun selectSearchResult(place: PlaceResult) {
        clearSearch()
        setDestination(place.lat, place.lon)
        viewModelScope.launch {
            savedPlaceDao.recordRecent(
                SavedPlaceEntity(
                    type = "RECENT",
                    name = place.name,
                    lat = place.lat,
                    lon = place.lon,
                    lastUsedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun selectSavedPlace(place: SavedPlaceEntity) {
        clearSearch()
        setDestination(place.lat, place.lon)
        viewModelScope.launch { savedPlaceDao.touch(place.id, System.currentTimeMillis()) }
    }

    fun saveHome(place: PlaceResult) = saveSpecial("HOME", place)

    fun saveWork(place: PlaceResult) = saveSpecial("WORK", place)

    fun saveFavorite(place: PlaceResult) {
        viewModelScope.launch {
            savedPlaceDao.insert(
                SavedPlaceEntity(
                    type = "FAVORITE",
                    name = place.name,
                    lat = place.lat,
                    lon = place.lon,
                    lastUsedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun removePlace(id: Long) {
        viewModelScope.launch { savedPlaceDao.delete(id) }
    }

    private fun saveSpecial(type: String, place: PlaceResult) {
        viewModelScope.launch {
            savedPlaceDao.upsertSpecial(
                SavedPlaceEntity(
                    type = type,
                    name = place.name,
                    lat = place.lat,
                    lon = place.lon,
                    lastUsedAt = System.currentTimeMillis(),
                ),
            )
        }
    }
```

- [ ] **Step 2: SearchOverlay — chips y recientes**

En `SearchOverlay.kt`, nueva firma:

```kotlin
@Composable
fun SearchOverlay(
    query: String,
    results: List<PlaceResult>,
    searching: Boolean,
    savedPlaces: List<SavedPlaceEntity>,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onSelect: (PlaceResult) -> Unit,
    onSelectSaved: (SavedPlaceEntity) -> Unit,
    onSaveFavorite: (PlaceResult) -> Unit,
    onRemoveSaved: (Long) -> Unit,
    modifier: Modifier = Modifier,
)
```

Imports nuevos: `SavedPlaceEntity`, iconos `Icons.Default.Home`, `Icons.Default.Work`, `Icons.Default.Star`, `Icons.Default.History`, `androidx.compose.foundation.layout.Row`, `androidx.compose.foundation.horizontalScroll`, `androidx.compose.foundation.rememberScrollState`, `androidx.compose.foundation.layout.height`, `androidx.compose.foundation.layout.Spacer`, `androidx.compose.foundation.layout.width`, `androidx.compose.foundation.layout.size`.

Debajo del `OutlinedTextField` y ANTES del panel de resultados, la fila de chips (solo cuando hay lugares y no se está escribiendo):

```kotlin
        val specials = savedPlaces.filter { it.type == "HOME" || it.type == "WORK" }
        val favorites = savedPlaces.filter { it.type == "FAVORITE" }
        if (query.isEmpty() && (specials.isNotEmpty() || favorites.isNotEmpty())) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .horizontalScroll(rememberScrollState()),
            ) {
                (specials + favorites).forEach { place ->
                    PlaceChip(place, onSelectSaved)
                    Spacer(Modifier.width(8.dp))
                }
            }
        }
```

El chip (48dp de alto — target táctil, hallazgo del research):

```kotlin
@Composable
private fun PlaceChip(place: SavedPlaceEntity, onSelect: (SavedPlaceEntity) -> Unit) {
    Surface(
        color = PanelColor,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.height(48.dp).clickable { onSelect(place) },
    ) {
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp),
        ) {
            Icon(
                imageVector = when (place.type) {
                    "HOME" -> Icons.Default.Home
                    "WORK" -> Icons.Default.Work
                    else -> Icons.Default.Star
                },
                contentDescription = null,
                tint = AccentColor,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                when (place.type) { "HOME" -> "Casa"; "WORK" -> "Trabajo"; else -> place.name },
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
```

Recientes: cuando `query.isEmpty()` no hay panel hoy. Agregar después de la fila de chips:

```kotlin
        val recents = savedPlaces.filter { it.type == "RECENT" }.take(6)
        if (query.isEmpty() && recents.isNotEmpty()) {
            Surface(
                color = PanelColor,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) {
                Column {
                    recents.forEach { place -> RecentRow(place, onSelectSaved, onRemoveSaved) }
                }
            }
        }
```

```kotlin
@Composable
private fun RecentRow(
    place: SavedPlaceEntity,
    onSelect: (SavedPlaceEntity) -> Unit,
    onRemove: (Long) -> Unit,
) {
    Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { onSelect(place) }.padding(start = 14.dp),
    ) {
        Icon(Icons.Default.History, contentDescription = null, tint = TextMuted, modifier = Modifier.size(16.dp))
        Text(
            place.name,
            color = TextPrimary,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 14.dp),
        )
        IconButton(onClick = { onRemove(place.id) }) {
            Icon(Icons.Default.Close, contentDescription = "Quitar de recientes", tint = TextMuted)
        }
    }
}
```

Estrella en los resultados: en `ResultRow`, nueva firma `ResultRow(place, onSelect, onSaveFavorite)` — convertir el `Column` en `Row` con el texto en `weight(1f)` y al final:

```kotlin
        IconButton(onClick = { onSaveFavorite(place) }) {
            Icon(Icons.Default.Star, contentDescription = "Guardar favorito", tint = TextMuted)
        }
```

IMPORTANTE (regla del recordatorio de recientes/panel): la condición `showPanel` existente sigue igual — chips y recientes son bloques nuevos independientes que solo aparecen con `query.isEmpty()`.

- [ ] **Step 3: LiveMapScreen — pasar lo nuevo**

En la llamada a `SearchOverlay(...)` agregar:

```kotlin
            savedPlaces = savedPlaces,
            onSelectSaved = viewModel::selectSavedPlace,
            onSaveFavorite = viewModel::saveFavorite,
            onRemoveSaved = viewModel::removePlace,
```

con `val savedPlaces by viewModel.savedPlaces.collectAsState()` arriba.

Nota de alcance: `saveHome`/`saveWork` quedan expuestos en el VM; la UI de asignar Casa/Trabajo (long-press en un resultado, por ejemplo) entra en D (onboarding/ajustes) — aquí solo los chips que muestran lo ya guardado. Documentado como decisión, no como olvido.

- [ ] **Step 4: Compile + tests**

Run: `"$GRADLE" :feature:map:compileDebugKotlin :feature:map:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add feature/map/src/main/kotlin/com/revscope/feature/map/LiveMapViewModel.kt feature/map/src/main/kotlin/com/revscope/feature/map/SearchOverlay.kt feature/map/src/main/kotlin/com/revscope/feature/map/LiveMapScreen.kt
git commit -m "feat: lugares guardados en la búsqueda — chips Casa/Trabajo, favoritos y recientes"
```

---

### Task 6: Modo nocturno automático

**Files:**
- Create: `core/common/src/main/kotlin/com/revscope/core/common/SunTimes.kt` (si core:common no tiene src kotlin, crear la ruta análoga a la existente — grep un archivo de core:common para confirmar el package base `com.revscope.core.common`)
- Create: `core/common/src/test/kotlin/com/revscope/core/common/SunTimesTest.kt`
- Modify: `core/data/src/main/kotlin/com/revscope/core/data/datastore/PreferencesKeys.kt` (key nueva)
- Modify: `feature/map/src/main/kotlin/com/revscope/feature/map/LiveMapViewModel.kt` (pref + dark efectivo)
- Modify: `feature/map/src/main/kotlin/com/revscope/feature/map/LiveMapScreen.kt` (FAB tri-estado)

**Interfaces:**
- Produces: `object SunTimes { fun isNight(latDeg: Double, lonDeg: Double, epochMs: Long): Boolean }` (UTC internamente — sin zona horaria del device). `PreferencesKeys.MAP_NIGHT_MODE: Preferences.Key<String>` (valores `"auto" | "on" | "off"`, default `"auto"`). En VM: `nightMode: StateFlow<String>`, `darkTiles: StateFlow<Boolean>`, `fun cycleNightMode()`.
- Consumes: `locationProvider.fix`/`_initialCenter` para lat/lon del cálculo solar; DataStore ya inyectable (mirar cómo otros VMs de feature:map/settings inyectan `DataStore<Preferences>`).

- [ ] **Step 1: Write the failing test**

`SunTimesTest.kt`:

```kotlin
package com.revscope.core.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SunTimesTest {

    // Medellín: lat 6.24, lon -75.58 (UTC-5). Sol sale ~06:00 local (11:00 UTC),
    // se pone ~18:10 local (23:10 UTC), estable todo el año por estar en el trópico.

    @Test
    fun `mediodia en Medellin es de dia`() {
        val noonLocal = Instant.parse("2026-08-19T17:00:00Z") // 12:00 UTC-5
        assertFalse(SunTimes.isNight(6.24, -75.58, noonLocal.toEpochMilli()))
    }

    @Test
    fun `once de la noche en Medellin es de noche`() {
        val nightLocal = Instant.parse("2026-08-20T04:00:00Z") // 23:00 UTC-5
        assertTrue(SunTimes.isNight(6.24, -75.58, nightLocal.toEpochMilli()))
    }

    @Test
    fun `tres de la manana es de noche`() {
        val predawn = Instant.parse("2026-08-19T08:00:00Z") // 03:00 UTC-5
        assertTrue(SunTimes.isNight(6.24, -75.58, predawn.toEpochMilli()))
    }

    @Test
    fun `ocho de la noche es de noche y nueve de la manana es de dia`() {
        val evening = Instant.parse("2026-08-20T01:00:00Z") // 20:00 UTC-5
        assertTrue(SunTimes.isNight(6.24, -75.58, evening.toEpochMilli()))
        val morning = Instant.parse("2026-08-19T14:00:00Z") // 09:00 UTC-5
        assertFalse(SunTimes.isNight(6.24, -75.58, morning.toEpochMilli()))
    }
}
```

(Los asserts quedan lejos de los bordes del amanecer/atardecer a propósito: la aproximación tiene ±20 min y el test no debe ser frágil.)

- [ ] **Step 2: Run test to verify it fails**

Run: `"$GRADLE" :core:common:testDebugUnitTest --tests "com.revscope.core.common.SunTimesTest"` (si core:common es JVM puro, el task es `:core:common:test`)
Expected: FAIL (SunTimes no existe).

- [ ] **Step 3: Write minimal implementation**

`SunTimes.kt`:

```kotlin
package com.revscope.core.common

import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Amanecer/atardecer con la ecuación del ángulo horario y declinación aproximada
 * (±20 min, de sobra para conmutar tiles). Todo en UTC: la zona horaria del
 * dispositivo no participa, así el cálculo es puro y testeable.
 */
object SunTimes {

    fun isNight(latDeg: Double, lonDeg: Double, epochMs: Long): Boolean {
        val utc = Instant.ofEpochMilli(epochMs).atOffset(ZoneOffset.UTC)
        val dayOfYear = utc.dayOfYear
        val hourUtc = utc.hour + utc.minute / 60.0

        val declRad = Math.toRadians(-23.44 * cos(Math.toRadians(360.0 / 365.0 * (dayOfYear + 10))))
        val latRad = Math.toRadians(latDeg)
        val cosOmega = -tan(latRad) * tan(declRad)
        // Sol de medianoche / noche polar: fuera del trópico extremo no pasa en Colombia,
        // pero el clamp evita NaN si alguien navega en Laponia.
        val omegaDeg = Math.toDegrees(acos(cosOmega.coerceIn(-1.0, 1.0)))

        val solarNoonUtc = 12.0 - lonDeg / 15.0
        val sunriseUtc = solarNoonUtc - omegaDeg / 15.0
        val sunsetUtc = solarNoonUtc + omegaDeg / 15.0

        // La hora UTC puede caer "ayer/mañana" respecto al día solar local; normalizar a [0,24).
        val h = ((hourUtc - sunriseUtc).mod(24.0))
        val dayLength = sunsetUtc - sunriseUtc
        return h > dayLength
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: mismo comando del Step 2.
Expected: PASS 4/4. Si el módulo no compila por no existir `src/test`, crear el directorio y revisar que `build.gradle.kts` de core:common tenga junit (mirar cómo lo declaran core:navigation o core:obd y replicar).

- [ ] **Step 5: Preferencia + VM**

(a) `PreferencesKeys.kt`: agregar junto a las keys de mapa/alertas existentes:

```kotlin
    /** Modo nocturno del mapa: "auto" (por sol), "on", "off". */
    val MAP_NIGHT_MODE = stringPreferencesKey("map_night_mode")
```

(b) `LiveMapViewModel.kt`: inyectar `private val settings: DataStore<Preferences>` (imports `androidx.datastore.core.DataStore`, `androidx.datastore.preferences.core.Preferences`, `androidx.datastore.preferences.core.edit`, `com.revscope.core.data.datastore.PreferencesKeys`, `kotlinx.coroutines.flow.combine`). Agregar:

```kotlin
    val nightMode: StateFlow<String> = settings.data
        .map { it[PreferencesKeys.MAP_NIGHT_MODE] ?: "auto" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "auto")

    /** Tick de un minuto: en modo auto el atardecer conmuta sin tocar nada. */
    private val minuteTick = kotlinx.coroutines.flow.flow {
        while (true) { emit(Unit); delay(60_000L) }
    }

    val darkTiles: StateFlow<Boolean> = combine(nightMode, liveFix, initialCenter, minuteTick) { mode, fix, center, _ ->
        when (mode) {
            "on" -> true
            "off" -> false
            else -> {
                val at = fix ?: center
                if (at == null) false else SunTimes.isNight(at.lat, at.lon, System.currentTimeMillis())
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun cycleNightMode() {
        val next = when (nightMode.value) { "auto" -> "on"; "on" -> "off"; else -> "auto" }
        viewModelScope.launch { settings.edit { it[PreferencesKeys.MAP_NIGHT_MODE] = next } }
    }
```

(import `com.revscope.core.common.SunTimes` — confirmar que feature:map depende de core:common; si no, agregar `implementation(project(":core:common"))` en `feature/map/build.gradle.kts`).

(c) `LiveMapScreen.kt`: borrar `var darkTiles by remember { mutableStateOf(false) }`; en su lugar `val darkTiles by viewModel.darkTiles.collectAsState()` y `val nightMode by viewModel.nightMode.collectAsState()`. El FAB nocturno pasa a ciclar y mostrar el estado:

```kotlin
            SmallFloatingActionButton(
                onClick = viewModel::cycleNightMode,
                containerColor = if (darkTiles) Color(0xFFE8FF00) else Color(0xFF1C1C28),
            ) {
                Icon(
                    if (nightMode == "auto") Icons.Default.BrightnessAuto else Icons.Default.DarkMode,
                    contentDescription = "Mapa nocturno: $nightMode",
                    tint = if (darkTiles) Color(0xFF0A0A0F) else Color(0xFFF0F0F8),
                )
            }
```

(import `androidx.compose.material.icons.filled.BrightnessAuto`). `styleJson` ya se recalcula con `remember(darkTiles)` — sin más cambios.

- [ ] **Step 6: Compile + tests**

Run: `"$GRADLE" :core:common:build :feature:map:compileDebugKotlin :feature:map:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add core/common feature/map core/data/src/main/kotlin/com/revscope/core/data/datastore/PreferencesKeys.kt
git commit -m "feat: modo nocturno automático del mapa por posición del sol (auto/on/off)"
```

---

### Task 7: Verificación integral

**Files:** ninguno (solo verificación).

- [ ] **Step 1: Suite completa + APK**

Run (SIN pipes que enmascaren el exit code):
```bash
"$GRADLE" test :app:assembleDebug
echo "EXIT: $?"
```
Expected: EXIT: 0, todos los módulos verdes.

- [ ] **Step 2: Checklist manual (device, si hay)**

1. Buscar dirección → resultado → chip → Iniciar SIN viaje previo → arranca viaje GPS + voz.
2. Desviarse a propósito → "Se salió de la ruta, recalculando" → ruta nueva en <15 s.
3. Cámara inclinada rumbo-arriba durante nav; pan la pausa; FAB la rearma; al terminar se des-inclina.
4. Maniobras pegadas → banner muestra "Luego: …".
5. Buscar y elegir 2 destinos → aparecen en recientes; estrella → favorito como chip.
6. FAB nocturno cicla auto→on→off; en auto y de noche, tiles oscuros.
7. Migración: instalar sobre v16 con datos → historial intacto (patrón verify_db, user_version 17).

Sin device conectado: pasos quedan como checklist para el usuario en el resumen final.

- [ ] **Step 3: Commit final si hubo ajustes**

Solo si la verificación exigió fixes; mensaje `fix: <qué>`.

---

## Self-review (hecho al escribir)

- Spec coverage: B1 ✅ T1(a); B2 ✅ T1; B3 ✅ T3; B4 ✅ T4+T5 (asignar Casa/Trabajo desde UI queda para D — decisión documentada en T5); B5 ✅ ya existía (NavigationProgressBar con ETA/llegada — verificado en código, se deja como está); B6 ✅ T2 (countdown grande ya existía: headline 30sp); B7 ✅ T6. Errores del spec: OSRM caído en fetch inicial → chip "Sin ruta" existente (mejora de retry queda fuera — deuda registrada); reroute fallido → mantiene ruta vieja ✅ T1.
- Placeholders: ninguno — todo el código está inline.
- Consistencia de tipos: `RerouteDecider.shouldReroute(Boolean, Long): Boolean` idéntico en test/impl/VM; `SavedPlaceEntity` campos idénticos en T4 (Produces) y T5 (uso); `NavCamera.zoom(Int?, Int): Double` idéntico; `nextManeuver` default null no rompe `IDLE.copy` de NavigationSession (T2 usa constructor completo).
