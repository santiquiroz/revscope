# Deuda Técnica y Pulido — Implementation Plan (sub-proyecto E)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cerrar todos los deferred/parked de los reviews finales de A/B/C/D: compat de permisos y listeners, pulido de navegación, modo GPS inferido, resiliencia de Flows de DB y tests adeudados.

**Architecture:** Sin features nuevas grandes — cada task implementa fixes ya diagnosticados y ruleados por los reviews finales (fable) de los sub-proyectos A-D. Las referencias exactas de cada finding están en cada task. Un solo helper nuevo (`stateInSafe`) centraliza el `.catch` de Flows de DB.

**Tech Stack:** Kotlin, Compose, Hilt, Room, DataStore, coroutines.

**Spec:** los findings de los reviews finales A-D (citados textualmente en cada task) — no hay spec aparte; este plan ES el registro.

## Global Constraints

- Trabajar desde `C:\personal\OBD2`. NO gradlew: `GRADLE="$HOME/.gradle/wrapper/dists/gradle-8.11.1-bin/bpt9gzteqjrbo1mjrsomdt32c/gradle-8.11.1/bin/gradle"`.
- Commits español `tipo: descripción`, SIN Co-Authored-By.
- NINGUNA migración de Room. NINGÚN cambio de comportamiento no listado.
- Tests existentes verdes en cada task.
- SIN release al final de este plan (la release es conjunta E+F+G).

---

### Task 1: Compat de permisos y listener (follow-ups de A)

**Files:**
- Modify: `app/src/main/kotlin/com/revscope/app/onboarding/OnboardingScreen.kt` (launcher de ubicación del Step0)
- Modify: `core/obd/src/main/kotlin/com/revscope/core/obd/service/GpsTrackRecorder.kt` (~línea 81-88, SAM lambda)

**Findings (review final A, textual):** (1) "OnboardingScreen pide ACCESS_FINE_LOCATION solo — mismo no-op silencioso en Android 12+" que se arregló en el mapa: un request de FINE sin COARSE en el mismo diálogo es ignorado por el sistema. (2) "GpsTrackRecorder.kt:81 SAM lambda LocationListener — AbstractMethodError API 26-29" (la interfaz de plataforma declara onStatusChanged/onProviderEnabled/onProviderDisabled abstractos pre-30).

- [ ] **Step 1: Step0 con FINE+COARSE**

En `OnboardingScreen.kt`: el `locationLauncher` pasa de `RequestPermission()` a `RequestMultiplePermissions()`; lanzar `arrayOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)`; `locationGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true`. Comentario de una línea: Android 12+ ignora FINE sin COARSE en el mismo diálogo. (`ACCESS_COARSE_LOCATION` ya está en el manifest desde A.)

- [ ] **Step 2: Listener explícito en GpsTrackRecorder**

Reemplazar la SAM lambda por `object : LocationListener` con `onLocationChanged` (lógica intacta) + no-ops `onStatusChanged` (@Suppress("OVERRIDE_DEPRECATION") si el compilador lo pide — patrón de MapLocationProvider), `onProviderEnabled`, `onProviderDisabled`. Mismo comentario que MapLocationProvider (API 26-29 los declara abstractos).

- [ ] **Step 3: Compile + tests + commit**

Run: `"$GRADLE" :app:compileDebugKotlin :core:obd:compileDebugKotlin :core:obd:testDebugUnitTest` → BUILD SUCCESSFUL.

```bash
git add app core/obd
git commit -m "fix: permisos FINE+COARSE en onboarding y listener GPS compatible API 26-29"
```

---

### Task 2: Pulido de navegación (M1-M4, M6 del review final B)

**Files:**
- Modify: `feature/map/src/main/kotlin/com/revscope/feature/map/LiveMapScreen.kt`
- Modify: `feature/map/src/main/kotlin/com/revscope/feature/map/LiveMapViewModel.kt`

**Findings (review final B, textual):**
- M1: "con nav activa, follow off y headingUp off, la rama de de-rotación (`bearing != 0`) dispara en la siguiente emisión tras un pan, moviendo la cámara sola" → guard `navigation == null` en esa rama.
- M2: "si OBD está Connecting, startGpsSession() no-opea y la nav arranca muda sin feedback" → si tras llamar `startGpsSession()` sigue `currentSessionId == null`, poner `_navigationError.value = "Esperando el GPS — reintentá en unos segundos"` y NO arrancar la navegación (return).
- M3: "la cámara queda inclinada a 50° mientras se muestra 'arrived'" → el efecto de de-tilt también dispara cuando `navigation?.arrived == true` (key pasa de `navigation == null` a un Boolean derivado `navIdle = navigation == null || navigation?.arrived == true`).
- M4: "setDestination sin generation-guard: dos selects rápidos pueden aterrizar fuera de orden" → guard de generación: contador `routingGeneration` incrementado al entrar; el resultado solo se aplica si la generación no cambió.
- M6: "flash claro al abrir de noche" + "contentDescription mezcla español/tokens" → (a) el valor inicial del stateIn de `darkTiles` no puede saber la hora sin fix — mitigación barata: si `nightMode` inicial es "on" no hay flash; para "auto" aceptar el flash de UNA composición pero evaluar SunTimes sincrónicamente en el primer combine (ya pasa) — el fix real: initial del stateIn = evaluación síncrona con `initialCenter` si está disponible; si el VM no puede, dejarlo y documentar. (b) contentDescription: "Mapa nocturno: automático/encendido/apagado" según el modo.

- [ ] **Step 1: M1 + M3 en LiveMapScreen**

- Rama de de-rotación (dentro del branch `last != null`): condición gana `&& navigation == null`.
- De-tilt: `val navIdle = navigation == null || navigation?.arrived == true` y `LaunchedEffect(navIdle) { if (navIdle) { ...tilt 0... } }` (reemplaza la key `navigation == null`).

- [ ] **Step 2: M2 + M4 en LiveMapViewModel**

M2 — en `startNavigation()`, tras el `startGpsSession()`:

```kotlin
        if (sessionManager.currentSessionId.value == null) {
            sessionManager.startGpsSession()
            if (sessionManager.currentSessionId.value == null) {
                // El OBD está negociando o el service aún no arrancó: sin GPS la guía sería muda.
                _navigationError.value = "Esperando el GPS — reintentá en unos segundos"
                return
            }
        }
```

(startGpsSession setea `_currentSessionIdFlow` síncronamente en el camino feliz — verificar leyendo; si es asíncrono, el guard igual aplica y el usuario reintenta.)

M4 — generación en `setDestination`:

```kotlin
    private var routingGeneration = 0L
```

y dentro de `setDestination`: `val generation = ++routingGeneration` antes del launch; al volver el fetch: `if (generation != routingGeneration) return@launch` antes de aplicar `_plannedRoute`/`_routing`.

- [ ] **Step 3: M6**

- contentDescription del FAB nocturno: `"Mapa nocturno: " + when (nightMode) { "auto" -> "automático"; "on" -> "encendido"; else -> "apagado" }`.
- darkTiles initial: cambiar el `stateIn(..., false)` por un initial calculado: `initialValue = run { val at = initialCenter.value; at != null && SunTimes.isNight(at.lat, at.lon, System.currentTimeMillis()) }` — cubre el caso "auto de noche con lastKnown disponible" sin flash. (nightMode "on" persistido tarda una emisión igual — aceptado y documentado en el commit.)

- [ ] **Step 4: Compile + tests + commit**

Run: `"$GRADLE" :feature:map:compileDebugKotlin :feature:map:testDebugUnitTest` → BUILD SUCCESSFUL.

```bash
git add feature/map
git commit -m "fix: pulido de navegación — cámara quieta tras pan, de-tilt al llegar, feedback sin GPS, guard de generación y nocturno sin flash"
```

---

### Task 3: Modo GPS inferido + pulido del wizard (D2, M7-M10 del review final D)

**Files:**
- Modify: `feature/dashboard/src/main/kotlin/com/revscope/feature/dashboard/DashboardViewModel.kt` (o donde vive `gpsOnlyMode`)
- Modify: `feature/dashboard/src/main/kotlin/com/revscope/feature/dashboard/DashboardScreen.kt`
- Modify: `app/src/main/kotlin/com/revscope/app/onboarding/OnboardingScreen.kt`
- Modify: `app/src/main/kotlin/com/revscope/app/onboarding/AiValueScreen.kt`
- Modify: `app/src/main/kotlin/com/revscope/app/navigation/RevScopeNavGraph.kt`

**Findings (review final D, textual):**
- D2 spec: "gpsOnlyMode activo (elegido en paso 3 **o inferido por ausencia de adaptador configurado**)" — la inferencia no se implementó. Fix: el hero mode del dashboard se activa si `gpsOnlyMode == true` O (`ADAPTER_ADDRESS` no configurado Y sin conexión OBD activa). Leer `PreferencesKeys.ADAPTER_ADDRESS` (grep el nombre exacto) en el mismo VM (map+stateIn) y componer: `gpsHeroMode = (gpsOnlyMode || adapterAddress.isNullOrEmpty()) && !connected`.
- M7: caption duplicado en hero+trip ("RPM · temperatura · marcha · boost — requieren adaptador" al lado del CTA que dice lo mismo) → suprimir el caption `if (isGpsTrip)` cuando `gpsHeroMode`.
- M8: re-run del wizard deja back stack `[Dashboard, Settings, Dashboard]` → en el `onFinished` del NavGraph, si se llegó por re-run (ONBOARDING_DONE ya era true — detectable con `vm.onboardingDone.value == true` al entrar, o más simple: si `navController.previousBackStackEntry != null`), hacer `popBackStack()` en vez del navigate+popUpTo.
- M9: `compact` muerto en AiValueContent → borrar el parámetro y el arg del call site.
- M10: `remember` → `rememberSaveable` para `name/type/plate` del Step1Vehicle y `gpsOnlySelected` del Step2Adapter (rotación conserva el form).

- [ ] **Step 1: D2 inferencia + M7** (dashboard)
- [ ] **Step 2: M8 + M9 + M10** (onboarding/NavGraph)
- [ ] **Step 3: Compile + commit**

Run: `"$GRADLE" :feature:dashboard:compileDebugKotlin :app:compileDebugKotlin` → BUILD SUCCESSFUL.

```bash
git add feature/dashboard app
git commit -m "feat: modo GPS inferido sin adaptador configurado y pulido del wizard (back stack, rotación, caption)"
```

---

### Task 4: Learner observable + pulido C (minors del review final C)

**Files:**
- Modify: `core/intelligence/src/main/kotlin/com/revscope/core/intelligence/gear/AdaptiveGearLearner.kt`
- Modify: `feature/dashboard/src/main/kotlin/com/revscope/feature/dashboard/DashboardViewModel.kt` (~83, 132)
- Modify: `feature/gear/src/main/kotlin/com/revscope/feature/gear/GearAnalyzerScreen.kt` (o donde viva el literal 30 y los gearColors — grep)
- Modify: `core/obd/src/main/kotlin/com/revscope/core/obd/telemetry/DerivedMetricsEngine.kt` (KDoc)

**Findings (review final C, textual):**
- "Hardcoded 30 en 3 lugares (DashboardViewModel.kt:83,132 y GearAnalyzerScreen) duplicando MIN_OBSERVATIONS_PER_GEAR privado — exponer un `isCalibrated` flow del learner".
- "gearColors de GearAnalyzerScreen hardcodea 6 entradas — no usa el thirds-banding nuevo".
- "DerivedMetricsEngine KDoc dice 'estimated gear 1–6' — ahora 1..gearCount".

- [ ] **Step 1: Learner expone calibración**

En `AdaptiveGearLearner`: agregar

```kotlin
    /** Progreso de calibración por marcha (0..1) y bandera derivada — una sola fuente del umbral 30. */
    val calibrated: StateFlow<Boolean>
```

implementado como `_gearTable.map { table -> table.all { it.observationCount >= MIN_OBSERVATIONS_PER_GEAR } }.stateIn(...)` — OJO: el learner no tiene scope propio; alternativa sin scope: exponer `fun minObservationsPerGear(): Int = MIN_OBSERVATIONS_PER_GEAR` (constante pública en vez de flow) y que los 3 call sites usen `AdaptiveGearLearner.MIN_OBSERVATIONS_PER_GEAR` (hacerla `const val` pública en companion). ELEGIR la constante pública (sin scope nuevo, cero riesgo); reemplazar los tres literales 30 por la referencia.

- [ ] **Step 2: GearAnalyzer thirds + KDoc**

- `GearAnalyzerScreen`: reemplazar la lista fija de 6 colores por la misma lógica de tercios de `GearDisplay` (reusar una función si es exportable barato; si no, replicar el `when` con comentario apuntando a GearDisplay).
- `DerivedMetricsEngine` KDoc: "1..gearCount".

- [ ] **Step 3: Compile + tests + commit**

Run: `"$GRADLE" :core:intelligence:testDebugUnitTest :feature:dashboard:compileDebugKotlin :feature:gear:compileDebugKotlin :core:obd:compileDebugKotlin` → BUILD SUCCESSFUL.

```bash
git add core/intelligence feature/dashboard feature/gear core/obd
git commit -m "refactor: umbral de calibración con una sola fuente y colores de marchas por tercios"
```

---

### Task 5: Flows de DB resilientes — stateInSafe

**Files:**
- Create: `core/common/src/main/kotlin/com/revscope/core/common/FlowExt.kt`
- Create: `core/common/src/test/kotlin/com/revscope/core/common/FlowExtTest.kt`
- Modify: los ViewModels con el patrón `dao.observeAll().stateIn(...)` sin catch — grep `observeAll().stateIn|observeAll()\n.*stateIn` en feature/ y app/: LiveMapViewModel (cameras, potholes, savedPlaces), VehiclePickerViewModel, SessionDetailViewModel, SessionViewModel (x2), VehicleViewModel (lista exacta por grep).

**Finding (parked en B/A, textual):** "Flow→stateIn sin .catch: si Room lanza (corrupción/I/O), la excepción es fatal al proceso — patrón idéntico en 4+ ViewModels; el fix correcto es app-wide."

- [ ] **Step 1: Write the failing test**

```kotlin
package com.revscope.core.common

import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FlowExtTest {

    @Test
    fun `un flow que lanza no mata el scope - emite lo previo y se queda en el ultimo valor`() = runTest {
        val source = flow {
            emit(listOf(1))
            throw IllegalStateException("db corrupta")
        }
        val state = source.stateInSafe(backgroundScope, emptyList())
        // Dar tiempo a colectar
        kotlinx.coroutines.yield()
        assertEquals(listOf(1), state.value)
        // No crashea el test: la excepción quedó contenida.
    }
}
```

(Si `kotlinx-coroutines-test` no está en core:common, agregar `testImplementation(libs.kotlinx.coroutines.test)` — verificar el alias real en libs.versions.toml con grep; si no existe alias, usar la coordenada del catalog de otro módulo que ya lo tenga.)

- [ ] **Step 2: RED** — `"$GRADLE" :core:common:testDebugUnitTest --tests "*FlowExtTest"` (o `:core:common:test`).

- [ ] **Step 3: Implementation**

```kotlin
package com.revscope.core.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber

/**
 * stateIn con contención: si la fuente (típicamente Room) lanza, se loggea y el StateFlow
 * conserva el último valor en vez de matar el proceso. Corrupción de DB degrada, no crashea.
 */
fun <T> Flow<T>.stateInSafe(
    scope: CoroutineScope,
    initialValue: T,
    started: SharingStarted = SharingStarted.WhileSubscribed(5_000),
): StateFlow<T> = this
    .catch { Timber.w(it, "stateInSafe: la fuente falló — se conserva el último valor") }
    .stateIn(scope, started, initialValue)
```

(Timber en core:common: verificar dependencia; si no está, usar `android.util.Log.w` NO — core:common puede ser puro; alternativa: parámetro `onError: (Throwable) -> Unit = {}` y que los call sites pasen Timber. ELEGIR: si Timber no está disponible en core:common, firma con callback default silencioso y call sites pasan `{ Timber.w(it, ...) }` — documentar la elección en el report.)

- [ ] **Step 4: GREEN** — mismo comando.

- [ ] **Step 5: Aplicar a los ViewModels**

Grep de todos los `observeAll()` + `.stateIn(` sin `.catch` en feature/ y app/ → reemplazar por `stateInSafe(viewModelScope, <initial existente>)`. NO tocar flows que no vengan de DAOs. Lista esperada (verificar): LiveMapViewModel (3), VehiclePickerViewModel, SessionDetailViewModel, SessionViewModel (2), VehicleViewModel.

- [ ] **Step 6: Compile + tests + commit**

Run: `"$GRADLE" :core:common:testDebugUnitTest :feature:map:testDebugUnitTest :feature:session:compileDebugKotlin :feature:vehicle:compileDebugKotlin :app:compileDebugKotlin` → BUILD SUCCESSFUL.

```bash
git add core/common feature app
git commit -m "fix: los Flows de DB contienen errores de Room en vez de matar el proceso (stateInSafe)"
```

---

### Task 6: Tests adeudados (A minors + spec D)

**Files:**
- Modify: `feature/map/src/test/kotlin/com/revscope/feature/map/location/InitialCenteringTest.kt` (2 casos simétricos)
- Create: `app/src/test/kotlin/com/revscope/app/onboarding/WizardStepsTest.kt` (si app tiene infra de test JVM — verificar `app/build.gradle.kts` testImplementation junit y src/test; si falta, agregarla copiando core:navigation)

**Findings:** A minors: "falta test simétrico onLastKnown(null) no consume estado; falta test pan entre lastKnown y liveFix". Spec D Tests: "máquina de estados del wizard: avanzar, saltar cada paso, salir a mitad" — la máquina vive en OnboardingViewModel (DataStore+DAO+manager inyectados). Para testearla sin instrumentación: los métodos step/next/back/goTo NO tocan las dependencias — construir el VM con mocks nulos no compila... RULING del plan: testear la ARITMÉTICA de pasos extrayéndola a un objeto puro `WizardSteps` (`fun next(current: Int): Int`, `fun back(current: Int): Int`, `fun clamp(step: Int): Int`, `const val TOTAL = 5`) en el mismo package, y el VM delega. Extracción de 10 líneas + test puro. `createFirstProfile` defaults quedan cubiertos por el test de tipo (VehicleType) + review — documentar como cobertura parcial consciente.

- [ ] **Step 1: Tests simétricos de InitialCentering**

```kotlin
    @Test
    fun `onLastKnown null no consume el estado`() {
        val centering = InitialCentering()
        assertNull(centering.onLastKnown(null))
        val action = centering.onLastKnown(LiveRouteHolder.RoutePoint(6.2, -75.5))
        assertEquals(InitialCentering.IDLE_ZOOM, action?.zoom)
    }

    @Test
    fun `pan entre lastKnown y liveFix cancela el liveFix`() {
        val centering = InitialCentering()
        centering.onLastKnown(LiveRouteHolder.RoutePoint(6.2, -75.5))
        centering.onUserPan()
        assertNull(centering.onLiveFix(LiveRouteHolder.RoutePoint(6.3, -75.6)))
    }
```

(Ajustar asserts a la API real — imports del archivo existente.)

- [ ] **Step 2: WizardSteps puro + test + delegación del VM**

Extraer a `app/src/main/kotlin/com/revscope/app/onboarding/WizardSteps.kt`:

```kotlin
package com.revscope.app.onboarding

/** Aritmética de pasos del wizard — pura para poder testearla sin Android. */
object WizardSteps {
    const val TOTAL = 5
    fun next(current: Int): Int = (current + 1).coerceAtMost(TOTAL - 1)
    fun back(current: Int): Int = (current - 1).coerceAtLeast(0)
    fun clamp(step: Int): Int = step.coerceIn(0, TOTAL - 1)
}
```

VM delega (`_step.value = WizardSteps.next(_step.value)` etc.; `TOTAL_STEPS` del companion pasa a alias de `WizardSteps.TOTAL` — mantener el nombre público para no romper la pantalla). Test:

```kotlin
class WizardStepsTest {
    @Test fun `next avanza y se detiene en el ultimo`() {
        assertEquals(1, WizardSteps.next(0)); assertEquals(4, WizardSteps.next(4))
    }
    @Test fun `back retrocede y se detiene en cero`() {
        assertEquals(0, WizardSteps.back(0)); assertEquals(3, WizardSteps.back(4))
    }
    @Test fun `clamp acota ambos extremos`() {
        assertEquals(0, WizardSteps.clamp(-3)); assertEquals(4, WizardSteps.clamp(99))
    }
}
```

- [ ] **Step 3: Compile + tests + commit**

Run: `"$GRADLE" :feature:map:testDebugUnitTest :app:testDebugUnitTest` → BUILD SUCCESSFUL, todos verdes.

```bash
git add feature/map app
git commit -m "test: casos simétricos de centrado y máquina de pasos del wizard extraída y testeada"
```

---

### Task 7: Verificación integral E

**Files:** ninguno.

- [ ] **Step 1:** `"$GRADLE" test :app:assembleDebug` con `echo "EXIT: $?"` (sin pipes). Expected EXIT: 0.
- [ ] **Step 2:** `adb devices` — si hay device, `installDebug`; sin device, skip documentado.

---

## Self-review (hecho al escribir)

- Cobertura del backlog E: FINE-only onboarding ✅T1; GpsTrackRecorder SAM ✅T1; M1-M4+M6 de B ✅T2; D2 inferencia ✅T3; M7-M10 de D ✅T3; literal 30 + gearColors + KDoc de C ✅T4; .catch app-wide ✅T5; tests A simétricos + máquina wizard ✅T6. Fuera (consciente, anotado): tests de SecureKeyStore/deep-link del spec D (manuales), unificación MAX_PLAUSIBLE_LEAN 70/65 (umbrales de contextos distintos, no era backlog firme), reloadThresholds order quirk (preexistente enmascarado, sin repro), fila RPM angosta (visual, QA device), remember del padding ResultRow (cosmético ya aceptado).
- Placeholders: T2 M6 initial de darkTiles documenta su límite ("on" persistido tarda una emisión — aceptado); T5 tiene fork Timber-en-common con decisión delegada y documentada; ninguno es TBD.
- Tipos: `stateInSafe(scope, initialValue, started)` consistente; `WizardSteps.next/back/clamp(Int): Int`.
