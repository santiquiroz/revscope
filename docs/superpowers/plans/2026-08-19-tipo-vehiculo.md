# Tipo de Vehículo Auto/Moto — Implementation Plan (sub-proyecto C)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** El campo `type` (CAR/MOTORCYCLE) del perfil deja de ser cosmético: gobierna detección de caída, lean angle, marchas y gauges.

**Architecture:** Un enum `VehicleType` parsea el String existente y se propaga desde el perfil activo (`ObdSessionManager._activeProfile`, plumbing ya existente) hacia los consumidores por parámetro — nadie lee DB por su cuenta. Los umbrales de crash pasan de constantes a un `CrashThresholds` por tipo. El gear learner se parametriza por `gearCount` (campo nuevo, Room v18) con ratios default por tipo desde una única fuente (`GearDefaults` en core:obd). Los gauges reciben redline/max reales por parámetro.

**Tech Stack:** Kotlin, Compose, Room 17→18, Hilt, coroutines/Flow.

**Spec:** docs/superpowers/specs/2026-08-18-tipo-vehiculo-design.md

## Global Constraints

- Trabajar desde `C:\personal\OBD2`. NO gradlew: `GRADLE="$HOME/.gradle/wrapper/dists/gradle-8.11.1-bin/bpt9gzteqjrbo1mjrsomdt32c/gradle-8.11.1/bin/gradle"` desde la raíz.
- Commits español `tipo: descripción`, SIN Co-Authored-By. Comentarios solo con porqué no obvio.
- Alcance definido por el usuario: IMU/seguridad + gauges. **NO tocar PIDs por tipo, `enabledPids`, packs de sonido, temp gauge, ni Android Auto.**
- Defaults por tipo (exactos): MOTO `maxRpm=12000, redlineRpm=10500, gearCount=5`; CAR `maxRpm=8000, redlineRpm=6500, gearCount=6`.
- SpeedGauge max por tipo: CAR `260` (actual), MOTO `299`.
- Fallback redline unificado: sin perfil → CAR `6500`, MOTO `10500` (mata el 10500 fantasma en autos de AlertsEngine/SessionAggregator).
- Room sube a **v18** con migración real (NUNCA fallback destructivo). `gearCount` es columna NOT NULL DEFAULT 6 → requiere `@ColumnInfo(defaultValue = "6")` (patrón odometerBaseKm, v13).
- Tests existentes deben seguir verdes: `CrashDetector` default a los umbrales de moto (valores actuales) para no romper sus 16 tests.
- MapLibre/mapa NO se toca en este sub-proyecto.

---

### Task 1: VehicleType enum + gearCount + defaults por tipo en el form (Room v18)

**Files:**
- Create: `core/data/src/main/kotlin/com/revscope/core/data/db/entities/VehicleType.kt`
- Create: `core/data/src/test/kotlin/com/revscope/core/data/db/entities/VehicleTypeTest.kt` (si core:data no tiene src/test ni junit, agregar `testImplementation(libs.junit)` a su build.gradle.kts copiando el patrón de core:navigation — documentar en el report)
- Modify: `core/data/src/main/kotlin/com/revscope/core/data/db/entities/VehicleProfileEntity.kt` (campo `gearCount`)
- Modify: `core/data/src/main/kotlin/com/revscope/core/data/db/AppDatabase.kt` (version = 18)
- Modify: `core/data/src/main/kotlin/com/revscope/core/data/db/Migrations.kt` (+ registro en DataModule si aplica, mismo patrón que MIGRATION_16_17)
- Modify: `feature/vehicle/src/main/kotlin/com/revscope/feature/vehicle/VehicleViewModel.kt` (form gearCount + defaults por tipo)
- Modify: `feature/vehicle/src/main/kotlin/com/revscope/feature/vehicle/VehicleProfileScreen.kt` (campo Marchas junto a maxRpm/redline)

**Interfaces:**
- Produces (tasks 2-5 consumen): `enum class VehicleType { CAR, MOTORCYCLE }` con `companion object { fun from(raw: String?): VehicleType }` (null/desconocido → CAR). `VehicleProfileEntity.gearCount: Int` (default 6). Extensión `VehicleProfileEntity.vehicleType: VehicleType` (val computada `get() = VehicleType.from(type)` en el mismo archivo del enum o de la entity).

- [ ] **Step 1: Write the failing test**

`VehicleTypeTest.kt`:

```kotlin
package com.revscope.core.data.db.entities

import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleTypeTest {

    @Test
    fun `MOTORCYCLE parsea exacto`() {
        assertEquals(VehicleType.MOTORCYCLE, VehicleType.from("MOTORCYCLE"))
    }

    @Test
    fun `CAR parsea exacto`() {
        assertEquals(VehicleType.CAR, VehicleType.from("CAR"))
    }

    @Test
    fun `null y basura caen a CAR`() {
        assertEquals(VehicleType.CAR, VehicleType.from(null))
        assertEquals(VehicleType.CAR, VehicleType.from(""))
        assertEquals(VehicleType.CAR, VehicleType.from("TRUCK"))
        assertEquals(VehicleType.CAR, VehicleType.from("motorcycle"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `"$GRADLE" :core:data:testDebugUnitTest --tests "com.revscope.core.data.db.entities.VehicleTypeTest"`
Expected: FAIL (enum no existe). Si el task de test no existe por falta de infra, primero agregar junit al build.gradle.kts de core:data (documentado), luego RED real.

- [ ] **Step 3: Write minimal implementation**

`VehicleType.kt`:

```kotlin
package com.revscope.core.data.db.entities

/** Tipo de vehículo del perfil. El String crudo vive en la DB desde v1; esto lo vuelve seguro. */
enum class VehicleType {
    CAR,
    MOTORCYCLE;

    companion object {
        fun from(raw: String?): VehicleType = if (raw == "MOTORCYCLE") MOTORCYCLE else CAR
    }
}

val VehicleProfileEntity.vehicleType: VehicleType
    get() = VehicleType.from(type)
```

- [ ] **Step 4: Run test to verify it passes**

Run: mismo comando del Step 2. Expected: PASS 3/3.

- [ ] **Step 5: gearCount en entity + migración v18**

En `VehicleProfileEntity.kt`, después de `redlineRpm` (mirar cómo declara `odometerBaseKm` su `@ColumnInfo(defaultValue = "0")` y copiar el patrón):

```kotlin
    /** Número de marchas — gobierna el gear learner. Default 6 (auto típico). */
    @ColumnInfo(defaultValue = "6")
    val gearCount: Int = 6,
```

`Migrations.kt`:

```kotlin
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE vehicle_profiles ADD COLUMN gearCount INTEGER NOT NULL DEFAULT 6")
    }
}
```

Registrarla donde está MIGRATION_16_17 (DataModule `.addMigrations`). `AppDatabase` version = 18. Compilar core:data para generar `18.json` y verificar el schema contra el SQL.

- [ ] **Step 6: Defaults por tipo en VehicleViewModel**

En `VehicleViewModel.kt`:
- Agregar `_formGearCount = MutableStateFlow("6")` + `formGearCount` expuesto + `setGearCount(v: String)` (solo dígitos).
- En `setType(type: String)` (localizarla): además de setear `_formType`, si NO se está editando un perfil existente (`_editingProfile.value == null`), aplicar los defaults del tipo elegido:

```kotlin
        if (_editingProfile.value == null) {
            if (type == "MOTORCYCLE") {
                _formMaxRpm.value = "12000"
                _formRedlineRpm.value = "10500"
                _formGearCount.value = "5"
            } else {
                _formMaxRpm.value = "8000"
                _formRedlineRpm.value = "6500"
                _formGearCount.value = "6"
            }
        }
```

- En `saveProfile()`: persistir `gearCount = _formGearCount.value.toIntOrNull()?.coerceIn(3, 8) ?: 6` en insert Y update. En la carga de un perfil al form (donde se leen maxRpm/redline), cargar también gearCount.
- En `VehicleProfileScreen.kt`: campo numérico "Marchas" junto a los de RPM (mismo estilo OutlinedTextField del form), wired a `formGearCount`/`setGearCount`.

- [ ] **Step 7: Compile + tests + commit**

Run: `"$GRADLE" :core:data:compileDebugKotlin :core:data:testDebugUnitTest :feature:vehicle:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

```bash
git add core/data feature/vehicle
git commit -m "feat: VehicleType tipado, gearCount por perfil y defaults por tipo — Room v18"
```

---

### Task 2: CrashThresholds por tipo

**Files:**
- Create: `core/obd/src/main/kotlin/com/revscope/core/obd/safety/CrashThresholds.kt`
- Modify: `core/obd/src/main/kotlin/com/revscope/core/obd/safety/CrashDetector.kt` (constantes → thresholds inyectados)
- Modify: `core/obd/src/main/kotlin/com/revscope/core/obd/safety/CrashResponder.kt` (recibe y pasa el tipo)
- Modify: el wiring del responder en `core/obd/src/main/kotlin/com/revscope/core/obd/service/ObdForegroundService.kt` (localizar con grep `CrashResponder` — pasarle el `vehicleType` del perfil activo del `ObdSessionManager`)
- Modify: `feature/settings/src/main/kotlin/com/revscope/feature/settings/SettingsScreen.kt` (~578-585, copy dinámico)
- Test: `core/obd/src/test/kotlin/.../CrashDetectorTest.kt` (existente — agregar 1 caso; localizar con glob)

**Interfaces:**
- Consumes: `VehicleType` (Task 1).
- Produces: `data class CrashThresholds(...)` con `companion { val MOTORCYCLE; val CAR; fun forType(type: VehicleType): CrashThresholds }`. `CrashDetector(thresholds: CrashThresholds = CrashThresholds.MOTORCYCLE, ...)` — resto de la firma intacta.

- [ ] **Step 1: CrashThresholds**

```kotlin
package com.revscope.core.obd.safety

import com.revscope.core.data.db.entities.VehicleType

/**
 * Umbrales de detección de accidente por tipo de vehículo. Los valores de moto son los
 * originales del detector (diseñados para caída/highside); el set de auto arranca con el
 * mismo esqueleto — sin supuestos de caída lateral — y queda tuneable por separado.
 */
data class CrashThresholds(
    val impactG: Double,
    val impactMinHorizontalG: Double,
    val catastrophicG: Double,
    val impactMinSpeedKmh: Double,
    val speedCollapseWindowMs: Long,
    val immobilitySpeedKmh: Double,
    val immobilityAccelG: Double,
    val immobilityDurationMs: Long,
    val recoverySpeedKmh: Double,
) {
    companion object {
        val MOTORCYCLE = CrashThresholds(
            impactG = 6.0,
            impactMinHorizontalG = 2.5,
            catastrophicG = 12.0,
            impactMinSpeedKmh = 20.0,
            speedCollapseWindowMs = 8_000L,
            immobilitySpeedKmh = 3.0,
            immobilityAccelG = 1.3,
            immobilityDurationMs = 30_000L,
            recoverySpeedKmh = 10.0,
        )

        // Un choque de auto llega amortiguado por la carrocería al teléfono montado:
        // umbral de impacto algo menor y sin el sesgo de caída lateral en el copy.
        val CAR = MOTORCYCLE.copy(impactG = 5.0)

        fun forType(type: VehicleType): CrashThresholds =
            if (type == VehicleType.MOTORCYCLE) MOTORCYCLE else CAR
    }
}
```

- [ ] **Step 2: CrashDetector consume thresholds**

- Constructor gana `private val thresholds: CrashThresholds = CrashThresholds.MOTORCYCLE` (primer parámetro con default → tests existentes intactos).
- Reemplazar cada uso de las constantes del companion por `thresholds.<campo>` (IMPACT_G_THRESHOLD→impactG, IMPACT_MIN_HORIZONTAL_G→impactMinHorizontalG, CATASTROPHIC_G_THRESHOLD→catastrophicG, IMPACT_MIN_SPEED_KMH→impactMinSpeedKmh, SPEED_COLLAPSE_WINDOW_MS→speedCollapseWindowMs, IMMOBILITY_SPEED_KMH→immobilitySpeedKmh, IMMOBILITY_ACCEL_G→immobilityAccelG, IMMOBILITY_DURATION_MS→immobilityDurationMs, RECOVERY_SPEED_KMH→recoverySpeedKmh).
- Las constantes `SPEED_HISTORY_WINDOW_MS` y `SPEED_HISTORY_RETENTION_MS` NO son por tipo — quedan en el companion.
- Borrar del companion las 9 constantes migradas (los tests que las referencien pasan a `CrashThresholds.MOTORCYCLE.<campo>` — ajustar el test file, NO los valores).

- [ ] **Step 3: Test nuevo**

En `CrashDetectorTest.kt` agregar:

```kotlin
    @Test
    fun `umbral de auto acepta impacto de 5G que moto ignora`() {
        // 5.5G total con 3G horizontal a 40 km/h: bajo el umbral moto (6.0), sobre el de auto (5.0).
        // Construir el detector con CrashThresholds.CAR y verificar que el impacto califica;
        // con el default (MOTORCYCLE) el mismo pico no debe calificar.
        // Usar los mismos helpers/fixtures del resto del archivo para inyectar picos y velocidad.
    }
```

(Implementar el cuerpo con los helpers reales del archivo — leerlo primero; el contrato es: mismo estímulo, CAR dispara, MOTORCYCLE no.)

- [ ] **Step 4: CrashResponder + wiring + copy**

- `CrashResponder.start(...)`: agregar parámetro `vehicleType: VehicleType = VehicleType.MOTORCYCLE` y construir su `CrashDetector(thresholds = CrashThresholds.forType(vehicleType))` (localizar dónde instancia el detector).
- `ObdForegroundService`: en el punto donde llama `crashResponder.start(...)` (grep), pasar `vehicleType = sessionManager.activeProfile.value?.vehicleType ?: VehicleType.MOTORCYCLE` (localizar el nombre exacto del StateFlow del perfil activo en ObdSessionManager — grep `_activeProfile`).
- `SettingsScreen.kt` ~578-585: el copy "un posible accidente de moto" pasa a ser dinámico: si el perfil activo (ya expuesto en SettingsViewModel o agregarlo con el mismo patrón de otros VMs) es CAR → "un posible choque", si MOTO → texto actual.

- [ ] **Step 5: Compile + tests + commit**

Run: `"$GRADLE" :core:obd:compileDebugKotlin :core:obd:testDebugUnitTest :feature:settings:compileDebugKotlin`
Expected: BUILD SUCCESSFUL, CrashDetectorTest completo verde (16 + nuevo).

```bash
git add core/obd feature/settings
git commit -m "feat: umbrales de detección de accidente por tipo de vehículo y copy dinámico"
```

---

### Task 3: Lean solo moto

**Files:**
- Modify: `feature/dashboard/src/main/kotlin/com/revscope/feature/dashboard/TrackModeScreen.kt` (~232-236: fila de lean)
- Modify: `feature/session/src/main/kotlin/com/revscope/feature/session/SessionDetailScreen.kt` (~374-381) y su ViewModel si hace falta exponer el tipo
- Modify: `feature/session/src/main/kotlin/com/revscope/feature/session/SessionCompareScreen.kt` (~164-166)
- Modify: `feature/session/src/main/kotlin/com/revscope/feature/session/TripShareCard.kt` (~92)
- Modify: wiring de `WetLeanGuard` en `ObdForegroundService` (gate por tipo)

**Interfaces:**
- Consumes: `VehicleType`/`vehicleType` (Task 1); `SessionDetailViewModel` ya calcula `isMotorcycle` para el debrief (~línea 144) — REUSARLO, no duplicar la lógica.

- [ ] **Step 1: SessionDetail + ShareCard + Compare**

- `SessionDetailViewModel`: exponer el `isMotorcycle` ya calculado como `StateFlow<Boolean>` (si hoy es local, promoverlo). La pantalla oculta la fila/stat de lean cuando es false, y pasa el flag a `TripShareCard` (parámetro nuevo `showLean: Boolean = true`) para omitir el lean del PNG.
- `SessionCompareScreen`: mismo gate — el tipo sale de los perfiles de las sesiones comparadas si están disponibles vía su ViewModel; si el ViewModel no tiene perfil, usar el perfil activo (mismo patrón que use SessionDetail). Ocultar la fila de lean cuando NINGUNA de las dos sesiones es de moto.

- [ ] **Step 2: TrackMode**

`TrackModeScreen` recibe/observa el perfil activo (mirar cómo la pantalla obtiene hoy sus datos — DashboardViewModel/manager) y oculta el bloque de lean para CAR.

- [ ] **Step 3: WetLeanGuard gate**

En `ObdForegroundService`, donde se instala/alimenta `WetLeanGuard` (grep `WetLeanGuard`): solo instalarlo si `vehicleType == VehicleType.MOTORCYCLE` (mismo acceso al perfil activo del Task 2). Comentario de una línea: el guard era implícitamente moto; ahora es explícito.

- [ ] **Step 4: Compile + tests + commit**

Run: `"$GRADLE" :feature:dashboard:compileDebugKotlin :feature:session:compileDebugKotlin :core:obd:compileDebugKotlin :core:obd:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

```bash
git add feature/dashboard feature/session core/obd
git commit -m "feat: lean angle y WetLeanGuard solo para motos"
```

---

### Task 4: Marchas por perfil — learner parametrizado

**Files:**
- Create: `core/obd/src/main/kotlin/com/revscope/core/obd/telemetry/GearDefaults.kt`
- Create: `core/obd/src/test/kotlin/com/revscope/core/obd/telemetry/GearDefaultsTest.kt`
- Modify: `core/intelligence/src/main/kotlin/com/revscope/core/intelligence/gear/AdaptiveGearLearner.kt` (constructor parametrizado)
- Modify: `core/obd/src/main/kotlin/com/revscope/core/obd/telemetry/DerivedMetricsEngine.kt` (~13-20: tabla desde GearDefaults)
- Modify: sitio de construcción del learner (grep `AdaptiveGearLearner(` — IntelligenceOrchestrator o DashboardViewModel) para pasarle gearCount/tipo del perfil activo
- Modify: `feature/dashboard/src/main/kotlin/com/revscope/feature/dashboard/gauges/GearDisplay.kt` (colores 1..N)
- Test: test existente del learner si lo hay (glob `*GearLearner*Test*`) — mantener verde con defaults

**Interfaces:**
- Consumes: `VehicleType`, `VehicleProfileEntity.gearCount` (Task 1).
- Produces: `object GearDefaults { fun ratios(gearCount: Int, type: VehicleType): List<Double> }` (tamaño = gearCount coercido 3..8). `AdaptiveGearLearner(gearCount: Int = 6, type: VehicleType = VehicleType.CAR)`. `GearDisplay(gear, isCalibrated, gearCount: Int = 6, modifier)`.

- [ ] **Step 1: Write the failing test**

`GearDefaultsTest.kt`:

```kotlin
package com.revscope.core.obd.telemetry

import com.revscope.core.data.db.entities.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GearDefaultsTest {

    @Test
    fun `auto 6 marchas devuelve la tabla historica`() {
        assertEquals(listOf(12.0, 20.0, 31.0, 43.0, 56.0, 77.0), GearDefaults.ratios(6, VehicleType.CAR))
    }

    @Test
    fun `moto 5 marchas devuelve 5 ratios crecientes y menores que los de auto`() {
        val moto = GearDefaults.ratios(5, VehicleType.MOTORCYCLE)
        assertEquals(5, moto.size)
        assertTrue(moto.zipWithNext().all { (a, b) -> a < b })
        assertTrue(moto.last() < 20.0)
    }

    @Test
    fun `gearCount fuera de rango se coerce a 3-8`() {
        assertEquals(3, GearDefaults.ratios(1, VehicleType.CAR).size)
        assertEquals(8, GearDefaults.ratios(12, VehicleType.CAR).size)
    }
}
```

- [ ] **Step 2: Run to verify FAIL** — `"$GRADLE" :core:obd:testDebugUnitTest --tests "com.revscope.core.obd.telemetry.GearDefaultsTest"` (GearDefaults no existe).

- [ ] **Step 3: Implementation**

```kotlin
package com.revscope.core.obd.telemetry

import com.revscope.core.data.db.entities.VehicleType

/**
 * Única fuente de ratios default (speed*1000/rpm) por tipo. Una moto gira mucho más alto
 * por km/h que un auto, por eso su escala es ~4x menor. El learner converge desde aquí.
 */
object GearDefaults {

    private val CAR_RATIOS = listOf(12.0, 20.0, 31.0, 43.0, 56.0, 77.0, 92.0, 105.0)
    private val MOTO_RATIOS = listOf(4.0, 6.5, 9.0, 11.5, 14.0, 16.5, 19.0, 21.5)

    fun ratios(gearCount: Int, type: VehicleType): List<Double> {
        val n = gearCount.coerceIn(3, 8)
        val base = if (type == VehicleType.MOTORCYCLE) MOTO_RATIOS else CAR_RATIOS
        return base.take(n)
    }
}
```

- [ ] **Step 4: Run to verify PASS** — mismo comando, 3/3.

Nota de dependencia: core:obd ya depende de core:data (usa VehicleProfileEntity) — el import de VehicleType compila. Verificar; si no, es un error de suposición del plan: reportar BLOCKED.

- [ ] **Step 5: Learner parametrizado**

`AdaptiveGearLearner`:

```kotlin
class AdaptiveGearLearner(
    gearCount: Int = 6,
    type: VehicleType = VehicleType.CAR,
) {
```

- `DEFAULT_CLUSTERS` del companion se reemplaza por `private val defaultClusters = GearDefaults.ratios(gearCount, type).mapIndexed { i, r -> GearCluster(gear = i + 1, centerRatio = r) }` y `_gearTable = MutableStateFlow(defaultClusters)`.
- OJO dependencia: core:intelligence depende de core:obd (usa ObdReading) — importar GearDefaults desde core:obd es legal. Verificar con grep en su build.gradle.kts; si no está la dep, reportar BLOCKED (no agregar deps circulares).
- `isCalibrated()` no cambia (opera sobre la lista, ahora de tamaño N) — el bug de la moto 5 velocidades muere solo.
- Si el companion `DEFAULT_CLUSTERS` tiene usos externos (grep antes de borrar): reemplazarlos por `GearDefaults`/instancia según el caso.
- Sitio de construcción (grep `AdaptiveGearLearner(`): pasarle `gearCount` y `vehicleType` del perfil activo. Si el learner se crea antes de conocer el perfil, recrearlo al activarse un perfil (mirar cómo el orquestador reacciona hoy a perfil activo; si no reacciona, crear con defaults del perfil activo en el momento de arrancar sesión).
- `DerivedMetricsEngine` (~13-20): `DEFAULT_GEAR_TABLE` pasa a construirse con `GearDefaults.ratios(6, VehicleType.CAR).mapIndexed { i, r -> (i + 1) to r }` (comportamiento idéntico al actual); si el engine recibe tabla del learner por push (ya existe ese camino), no hay más cambios.

- [ ] **Step 6: GearDisplay 1..N**

```kotlin
@Composable
fun GearDisplay(
    gear: Int,
    isCalibrated: Boolean,
    gearCount: Int = 6,
    modifier: Modifier = Modifier,
) {
    val label = if (gear == 0) "N" else gear.toString()
    val color = when {
        gear <= 0 -> RevScopeColors.TextMuted
        gear <= gearCount / 3 -> RevScopeColors.Success
        gear <= gearCount * 2 / 3 -> RevScopeColors.Accent
        gear <= gearCount -> RevScopeColors.Warning
        else -> RevScopeColors.TextMuted
    }
    // resto igual
```

Call sites de GearDisplay (grep): pasar `gearCount` del perfil activo (fallback 6).

- [ ] **Step 7: Compile + tests + commit**

Run: `"$GRADLE" :core:obd:testDebugUnitTest :core:intelligence:compileDebugKotlin :core:intelligence:testDebugUnitTest :feature:dashboard:compileDebugKotlin`
Expected: BUILD SUCCESSFUL, tests del learner (si existen) verdes.

```bash
git add core/obd core/intelligence feature/dashboard
git commit -m "feat: marchas por perfil — learner y display parametrizados por gearCount y tipo"
```

---

### Task 5: Gauges por tipo + fallback redline unificado

**Files:**
- Modify: `feature/dashboard/src/main/kotlin/com/revscope/feature/dashboard/gauges/RpmGauge.kt` (banda roja desde redline real)
- Modify: `feature/dashboard/src/main/kotlin/com/revscope/feature/dashboard/gauges/SpeedGauge.kt` (max por parámetro efectivo)
- Modify: `feature/dashboard/src/main/kotlin/com/revscope/feature/dashboard/DashboardScreen.kt` (~128-132, ~284-311: pasar redline/maxSpeed por tipo)
- Modify: `core/obd/src/main/kotlin/com/revscope/core/obd/alerts/AlertsEngine.kt` (~33: fallback por tipo)
- Modify: `core/obd/src/main/kotlin/com/revscope/core/obd/session/SessionAggregator.kt` (~109,116: fallback por tipo)

**Interfaces:**
- Consumes: `VehicleType`, perfil activo. Task 1.
- Produces: `RpmGauge(rpm, maxRpm, redlineRpm: Int, modifier, size)` — banda naranja/roja calculada. `SpeedGauge` llamado con `maxSpeed` explícito por tipo.

- [ ] **Step 1: RpmGauge con redline real**

Firma: agregar `redlineRpm: Int` tras `maxRpm`. Reemplazar los sweeps fijos:

```kotlin
            val redFrac = (redlineRpm.toFloat() / maxRpm).coerceIn(0.1f, 0.98f)
            // La banda naranja anuncia la roja: 10% del arco antes del redline real.
            val orangeFrac = (redFrac - 0.10f).coerceAtLeast(0.05f)
            val greenSweep = ARC_SWEEP * orangeFrac
            val orangeSweep = ARC_SWEEP * (redFrac - orangeFrac)
            val redSweep = ARC_SWEEP * (1f - redFrac)
```

y el tick pasa de `0.85f` a `redFrac`. Nada más cambia.

- [ ] **Step 2: DashboardScreen pasa los reales**

Donde arma `gaugeMaxRpm`/`redline` (~128-132): pasar `redlineRpm = redline.toInt()` al RpmGauge. Para SpeedGauge: `maxSpeed = if (activeProfile?.vehicleType == VehicleType.MOTORCYCLE) 299 else 260`. (SpeedGauge ya tiene `maxSpeed: Int = 260` — solo pasar el argumento.)

- [ ] **Step 3: Fallbacks unificados**

- `AlertsEngine`: `DEFAULT_REDLINE_RPM = 10_500` muere; donde se usa como fallback, reemplazar por `if (activeType == VehicleType.MOTORCYCLE) 10_500 else 6_500` — el engine ya recibe el perfil vía `setActiveProfile`/`notifyProfileUpdated` (~72-75, 112-115): guardar también el tipo en ese camino.
- `SessionAggregator` (~109,116): mismo tratamiento — recibe el perfil/redline por el camino existente; el fallback duro 10_500 pasa a ser por tipo con default CAR (6_500) cuando no hay perfil.
- Si algún test referencia `DEFAULT_REDLINE_RPM` (grep), actualizar la referencia sin cambiar los valores esperados de moto.

- [ ] **Step 4: Compile + tests + commit**

Run: `"$GRADLE" :feature:dashboard:compileDebugKotlin :core:obd:compileDebugKotlin :core:obd:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, tests verdes.

```bash
git add feature/dashboard core/obd
git commit -m "feat: gauges por tipo — banda roja desde el redline real y fallbacks unificados"
```

---

### Task 6: Verificación integral

**Files:** ninguno.

- [ ] **Step 1:** `"$GRADLE" test :app:assembleDebug` con `echo "EXIT: $?"` (sin pipes). Expected EXIT: 0.
- [ ] **Step 2:** `adb devices` — si hay device, `installDebug` + verificar migración v18 (patrón verify_db: user_version 18, historial intacto). Sin device: skip documentado.
- [ ] **Step 3:** Checklist manual (queda para el usuario si no hay device): crear perfil moto → defaults 12000/10500/5 marchas; gauge RPM con banda roja en el redline real; velocímetro a 299; TrackMode sin lean en perfil auto; settings copy "choque" con auto activo.

---

## Self-review (hecho al escribir)

- Spec coverage: C1 ✅ T1 (enum+extensión); C2 ✅ T2; C3 ✅ T3 (UI + WetLeanGuard; debrief ya estaba gated); C4 ✅ T1 (campo+migración+form) + T4 (learner/engine/display; persistir `gearRatios` aprendidos queda documentado como fuera de objetivo en el spec); C5 ✅ T5 + T1 (defaults form). Fuera de alcance respetado (sin PIDs, sonido, temp, AA).
- Placeholders: el cuerpo del test de CrashDetector en T2 Step 3 delega en los helpers reales del archivo con contrato explícito — decisión consciente (el archivo no está en el contexto del plan), no un TBD: el implementer tiene el archivo delante.
- Tipos: `VehicleType.from(String?)`, `vehicleType` extension, `CrashThresholds.forType(VehicleType)`, `GearDefaults.ratios(Int, VehicleType): List<Double>`, `AdaptiveGearLearner(Int, VehicleType)`, `GearDisplay(..., gearCount: Int = 6)`, `RpmGauge(..., redlineRpm: Int)` — consistentes entre Produces/Consumes.
- Riesgo señalado: dependencia core:intelligence→core:obd y core:obd→core:data asumidas (verificación temprana en T4 Step 4/5 con BLOCKED como salida).
