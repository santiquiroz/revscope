# Fase 1 — Migración osmdroid → MapLibre: plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reemplazar osmdroid (archivado en 2024) por MapLibre Native en las dos pantallas de mapa de RevScope, sin cambiar ninguna funcionalidad visible.

**Architecture:** Un módulo nuevo `:core:maps` concentra el `AndroidView` con el ciclo de vida de MapLibre, el proveedor de estilo con cascada local→servidor→degradado, y los helpers de capas. `feature:map` y `feature:session` dependen de él y solo describen qué dibujar.

**Tech Stack:** Kotlin, Jetpack Compose, MapLibre Native Android `13.4.1` (variante OpenGL), PMTiles, estilos Protomaps, JUnit4.

Spec: `docs/superpowers/specs/2026-08-12-fase1-maplibre-design.md`.

## Global Constraints

- Artefacto: `org.maplibre.gl:android-sdk-opengl:13.4.1`. **No** usar `android-sdk` (default Vulkan desde 13.0.0).
- `MapLibre.getInstance(context)` debe ejecutarse **antes de construir el `MapView`**, o el constructor lanza `MapLibreConfigurationException`.
- Anchos de trazo de osmdroid son **píxeles físicos**; `lineWidth` de MapLibre es densidad-independiente. Toda conversión pasa por `physicalPxToDp`. Nunca copiar el número crudo.
- Nunca guardar referencias a `Style`, `Source` ni `Layer` fuera del scope del callback de estilo. **Guardar solo ids.**
- Tras cambiar de estilo, las fuentes viejas quedan `detached` y `setGeoJson()` **retorna en silencio** — sin excepción ni log. Todo cambio de estilo debe recrear fuentes y capas.
- `style.addImage()` hace `.recycle()` del `Bitmap`. Decodificar uno nuevo en cada registro; nunca reusar un campo.
- El servidor **no es requisito**: sin red y sin servidor la pantalla debe seguir dibujando sus capas sin crash.
- Radio del círculo de radar = `CameraAlertRadius` desde DataStore (default 250 m, rango 100–1000). Una sola fuente de verdad con el aviso por voz.
- Commits en español, sin `Co-Authored-By`.
- Build: `~/.gradle/wrapper/dists/gradle-8.11.1-bin/bpt9gzteqjrbo1mjrsomdt32c/gradle-8.11.1/bin/gradle.bat -p /c/personal/OBD2`. Usar `set -o pipefail` si se pipea la salida.

---

## Estructura de archivos

**Crear:**
- `core/maps/build.gradle.kts` — módulo nuevo
- `core/maps/src/main/AndroidManifest.xml` — manifest vacío
- `core/maps/src/main/kotlin/com/revscope/core/maps/MapUnits.kt` — conversión de unidades, pura
- `core/maps/src/main/kotlin/com/revscope/core/maps/MapGeometry.kt` — círculo geodésico y bounds
- `core/maps/src/main/kotlin/com/revscope/core/maps/MapLibreMapView.kt` — composable con ciclo de vida
- `core/maps/src/main/kotlin/com/revscope/core/maps/MapStyleProvider.kt` — cascada de origen de tiles
- `core/maps/src/main/kotlin/com/revscope/core/maps/MapLayerIds.kt` — ids de fuentes y capas
- `core/maps/src/test/kotlin/com/revscope/core/maps/MapUnitsTest.kt`
- `core/maps/src/test/kotlin/com/revscope/core/maps/MapGeometryTest.kt`
- `core/maps/src/test/kotlin/com/revscope/core/maps/MapStyleProviderTest.kt`

**Modificar:**
- `settings.gradle.kts` — incluir `:core:maps`
- `gradle/libs.versions.toml` — agregar maplibre, quitar osmdroid al final
- `feature/map/build.gradle.kts`, `feature/session/build.gradle.kts`
- `feature/map/src/main/kotlin/com/revscope/feature/map/LiveMapScreen.kt` — reescritura del render
- `feature/session/src/main/kotlin/com/revscope/feature/session/RealTrackMap.kt` — reescritura del render
- `docs/manual-usuario.md`, `docs/configuracion.md`

---

## Task 1: Dependencia y conflicto de Kotlin

Resolver el choque de versiones **antes** de escribir UI. El proyecto va en Kotlin 2.0.21; `android-sdk-opengl:13.4.1` arrastra `kotlin-stdlib:2.2.10`.

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `settings.gradle.kts`
- Create: `core/maps/build.gradle.kts`
- Create: `core/maps/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: módulo `:core:maps` compilable con MapLibre en el classpath.

- [ ] **Step 1: Agregar la versión y el alias**

En `gradle/libs.versions.toml`, bloque `[versions]`:

```toml
maplibre = "13.4.1"
```

En `[libraries]`:

```toml
# Motor de mapas vectoriales. Variante OpenGL a propósito: desde 13.0.0 el
# artefacto por defecto usa Vulkan, cuyo paquete filtra dispositivos por
# android.hardware.vulkan.version y tiene un fix pendiente de release para
# VK_ERROR_DEVICE_LOST en Adreno 600 con el indicador de ubicación.
maplibre = { group = "org.maplibre.gl", name = "android-sdk-opengl", version.ref = "maplibre" }
```

- [ ] **Step 2: Registrar el módulo**

En `settings.gradle.kts`, junto a los otros `include(":core:…")`:

```kotlin
include(":core:maps")
```

- [ ] **Step 3: Crear el build del módulo**

`core/maps/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.revscope.core.maps"
    compileSdk = 35
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.maplibre)
    implementation(libs.timber)
    testImplementation(libs.junit)
}
```

`core/maps/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
```

- [ ] **Step 4: Compilar y observar el conflicto de stdlib**

Run: `gradle.bat -p /c/personal/OBD2 :core:maps:compileDebugKotlin --console=plain`

Esperado: compila, posiblemente con warning `Runtime JAR files in the classpath have the version 2.2.10, expected 2.0.21`.

- [ ] **Step 5: Resolver según lo observado**

Si **solo hay warning**: no hacer nada, anotar en el commit que se difiere.

Si **falla la compilación**, forzar el stdlib a la versión del proyecto en `core/maps/build.gradle.kts`:

```kotlin
configurations.all {
    resolutionStrategy {
        // MapLibre 13.4.1 arrastra kotlin-stdlib 2.2.10 y el proyecto compila con 2.0.21.
        // Se fija al del compilador; subir Kotlin es un cambio transversal aparte.
        force("org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}")
    }
}
```

Volver a correr el Step 4 hasta que compile.

- [ ] **Step 6: Verificar que el resto del proyecto sigue compilando**

Run: `set -o pipefail; gradle.bat -p /c/personal/OBD2 :app:assembleDebug --console=plain 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml settings.gradle.kts core/maps
git commit -m "feat: módulo :core:maps con MapLibre en el classpath

Variante OpenGL a propósito: el artefacto por defecto usa Vulkan desde 13.0.0,
que filtra dispositivos por android.hardware.vulkan.version y arrastra un fix
sin publicar de VK_ERROR_DEVICE_LOST en Adreno 600 con el indicador de
ubicación, que es justo lo que usa una app de navegación."
```

---

## Task 2: Helpers puros — unidades y geometría

**Files:**
- Create: `core/maps/src/main/kotlin/com/revscope/core/maps/MapUnits.kt`
- Create: `core/maps/src/main/kotlin/com/revscope/core/maps/MapGeometry.kt`
- Test: `core/maps/src/test/kotlin/com/revscope/core/maps/MapUnitsTest.kt`
- Test: `core/maps/src/test/kotlin/com/revscope/core/maps/MapGeometryTest.kt`

**Interfaces:**
- Produces: `physicalPxToDp(physicalPx: Float, density: Float): Float`; `boundsOf(points: List<Pair<Double, Double>>): DoubleArray?` devolviendo `[minLat, minLon, maxLat, maxLon]`; `geodesicCircle(lat: Double, lon: Double, radiusMeters: Double): Polygon`.

- [ ] **Step 1: Escribir los tests que fallan**

`MapUnitsTest.kt`:

```kotlin
package com.revscope.core.maps

import org.junit.Assert.assertEquals
import org.junit.Test

class MapUnitsTest {

    @Test
    fun `en pantalla 3x ocho pixeles fisicos son dos coma seis siete dp`() {
        assertEquals(2.667f, physicalPxToDp(8f, 3f), 0.001f)
    }

    @Test
    fun `en pantalla 1x el valor no cambia`() {
        assertEquals(8f, physicalPxToDp(8f, 1f), 0.001f)
    }

    @Test
    fun `densidad cero no divide por cero`() {
        assertEquals(8f, physicalPxToDp(8f, 0f), 0.001f)
    }

    @Test
    fun `el casing de dieciseis pixeles sigue siendo mas ancho que el segmento de doce`() {
        val casing = physicalPxToDp(16f, 3f)
        val segment = physicalPxToDp(12f, 3f)
        org.junit.Assert.assertTrue(casing > segment)
    }
}
```

`MapGeometryTest.kt`:

```kotlin
package com.revscope.core.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapGeometryTest {

    @Test
    fun `sin puntos no hay bounds`() {
        assertNull(boundsOf(emptyList()))
    }

    @Test
    fun `bounds de un punto es degenerado pero valido`() {
        val b = boundsOf(listOf(6.2 to -75.6))!!
        assertEquals(6.2, b[0], 1e-9)
        assertEquals(-75.6, b[1], 1e-9)
        assertEquals(6.2, b[2], 1e-9)
        assertEquals(-75.6, b[3], 1e-9)
    }

    @Test
    fun `bounds cubre todos los puntos`() {
        val b = boundsOf(listOf(6.2 to -75.6, 6.3 to -75.4, 6.1 to -75.7))!!
        assertEquals(6.1, b[0], 1e-9)
        assertEquals(-75.7, b[1], 1e-9)
        assertEquals(6.3, b[2], 1e-9)
        assertEquals(-75.4, b[3], 1e-9)
    }

    @Test
    fun `el circulo se cierra y tiene los vertices pedidos`() {
        val poly = geodesicCircle(6.2442, -75.5812, 250.0)
        val ring = poly.coordinates()[0]
        assertTrue(ring.size >= 60)
        assertEquals(ring.first().latitude(), ring.last().latitude(), 1e-9)
        assertEquals(ring.first().longitude(), ring.last().longitude(), 1e-9)
    }
}
```

- [ ] **Step 2: Correr y ver que fallan**

Run: `gradle.bat -p /c/personal/OBD2 :core:maps:testDebugUnitTest --console=plain`
Expected: FAIL, `Unresolved reference: physicalPxToDp`

- [ ] **Step 3: Implementar**

`MapUnits.kt`:

```kotlin
package com.revscope.core.maps

/**
 * osmdroid aplicaba `strokeWidth` directo al Canvas: eran píxeles FÍSICOS. MapLibre
 * interpreta `lineWidth` de forma densidad-independiente, así que copiar el número
 * crudo dibujaría la línea 3-4x más gruesa en una pantalla 3x o 4x.
 */
fun physicalPxToDp(physicalPx: Float, density: Float): Float =
    if (density <= 0f) physicalPx else physicalPx / density
```

`MapGeometry.kt`:

```kotlin
package com.revscope.core.maps

import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import org.maplibre.turf.TurfConstants
import org.maplibre.turf.TurfTransformation

/** Vértices del círculo. osmdroid usaba 60; se mantiene para que el borde se vea igual. */
private const val CIRCLE_STEPS = 60

/**
 * Círculo geodésico de radio en METROS. `circle-radius` de MapLibre es en píxeles de
 * pantalla, así que un CircleLayer cambiaría de tamaño físico con el zoom y mostraría
 * un radio de alerta que no es el real.
 */
fun geodesicCircle(lat: Double, lon: Double, radiusMeters: Double): Polygon =
    TurfTransformation.circle(
        Point.fromLngLat(lon, lat),
        radiusMeters,
        CIRCLE_STEPS,
        TurfConstants.UNIT_METRES,
    )

/** `[minLat, minLon, maxLat, maxLon]`, o null si no hay puntos. */
fun boundsOf(points: List<Pair<Double, Double>>): DoubleArray? {
    if (points.isEmpty()) return null
    var minLat = Double.MAX_VALUE
    var minLon = Double.MAX_VALUE
    var maxLat = -Double.MAX_VALUE
    var maxLon = -Double.MAX_VALUE
    for ((lat, lon) in points) {
        if (lat < minLat) minLat = lat
        if (lon < minLon) minLon = lon
        if (lat > maxLat) maxLat = lat
        if (lon > maxLon) maxLon = lon
    }
    return doubleArrayOf(minLat, minLon, maxLat, maxLon)
}
```

- [ ] **Step 4: Correr y ver que pasan**

Run: `gradle.bat -p /c/personal/OBD2 :core:maps:testDebugUnitTest --console=plain`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/maps
git commit -m "feat: helpers puros de unidades y geometría para el mapa

physicalPxToDp cubre la trampa de paridad: los anchos de osmdroid eran píxeles
físicos y los de MapLibre son densidad-independientes. geodesicCircle envuelve
TurfTransformation porque circle-radius del style spec es en píxeles y un port
literal mostraría un radio de alerta falso."
```

---

## Task 3: `MapLibreMapView` — el composable con ciclo de vida

**Files:**
- Create: `core/maps/src/main/kotlin/com/revscope/core/maps/MapLibreMapView.kt`

**Interfaces:**
- Consumes: nada.
- Produces: `@Composable fun MapLibreMapView(modifier: Modifier, styleJson: String, onStyleInstalled: (MapLibreMap, Style) -> Unit)`. El callback corre en la carga inicial **y en cada cambio de `styleJson`**; el consumidor debe reinstalar fuentes y capas ahí (ver Task 10).

- [ ] **Step 1: Implementar**

```kotlin
package com.revscope.core.maps

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * `MapView` de MapLibre con la cadena de ciclo de vida completa.
 *
 * osmdroid se conformaba con `onDetach()`. MapLibre exige onCreate/onStart/onResume/
 * onPause/onStop/onDestroy/onLowMemory: hacerlo mal no falla en el emulador, falla al
 * volver de la pantalla apagada — el caso de uso real en la moto.
 */
@Composable
fun MapLibreMapView(
    modifier: Modifier = Modifier,
    styleJson: String,
    onStyleInstalled: (MapLibreMap, Style) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnStyleInstalled by rememberUpdatedState(onStyleInstalled)
    // El bloque `update` corre en CADA recomposición. Sin este centinela se llamaría
    // setStyle todo el tiempo, y cada recarga deja detached las fuentes agregadas a
    // mano — el bug silencioso que describe la Task 10.
    val appliedStyle = remember { mutableStateOf<String?>(null) }

    // getInstance DEBE correr antes de construir el MapView, o el constructor lanza
    // MapLibreConfigurationException.
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(Bundle()) }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { mapView },
        update = { view ->
            if (appliedStyle.value != styleJson) {
                appliedStyle.value = styleJson
                view.getMapAsync { map ->
                    map.setStyle(Style.Builder().fromJson(styleJson)) { style ->
                        currentOnStyleInstalled(map, style)
                    }
                }
            }
        },
    )
}
```

Imports adicionales para el centinela: `androidx.compose.runtime.mutableStateOf`.

- [ ] **Step 2: Compilar**

Run: `gradle.bat -p /c/personal/OBD2 :core:maps:compileDebugKotlin --console=plain`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add core/maps
git commit -m "feat: composable MapLibreMapView con ciclo de vida completo

Resuelto una sola vez para las dos pantallas. RealTrackMap hoy no llama a ningún
método de ciclo de vida, ni siquiera onDetach: esto cierra ese bug latente."
```

---

## Task 4: `MapStyleProvider` — cascada local → servidor → degradado

**Files:**
- Create: `core/maps/src/main/kotlin/com/revscope/core/maps/MapStyleProvider.kt`
- Test: `core/maps/src/test/kotlin/com/revscope/core/maps/MapStyleProviderTest.kt`

**Interfaces:**
- Produces: `object MapStyleProvider { fun tilesUrl(localFile: File?, serverBaseUrl: String?): String?; fun styleJson(tilesUrl: String?, dark: Boolean): String }`.

- [ ] **Step 1: Escribir los tests que fallan**

```kotlin
package com.revscope.core.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MapStyleProviderTest {

    @Test
    fun `el archivo local gana sobre el servidor`() {
        val local = File.createTempFile("colombia", ".pmtiles").apply { deleteOnExit() }
        val url = MapStyleProvider.tilesUrl(local, "https://servidor.example")
        assertEquals("pmtiles://file://${local.absolutePath}", url)
    }

    @Test
    fun `sin archivo local usa el servidor`() {
        val url = MapStyleProvider.tilesUrl(null, "https://servidor.example")
        assertEquals("pmtiles://https://servidor.example/colombia.pmtiles", url)
    }

    @Test
    fun `un archivo local inexistente no gana`() {
        val url = MapStyleProvider.tilesUrl(File("/no/existe.pmtiles"), "https://servidor.example")
        assertEquals("pmtiles://https://servidor.example/colombia.pmtiles", url)
    }

    @Test
    fun `sin archivo ni servidor no hay origen`() {
        assertNull(MapStyleProvider.tilesUrl(null, null))
    }

    @Test
    fun `sin origen el estilo sigue siendo valido y sin fuentes`() {
        val json = MapStyleProvider.styleJson(null, dark = false)
        assertTrue(json.contains("\"version\""))
        assertTrue(json.contains("\"sources\""))
    }

    @Test
    fun `el estilo oscuro difiere del claro`() {
        val claro = MapStyleProvider.styleJson("pmtiles://https://x/y.pmtiles", dark = false)
        val oscuro = MapStyleProvider.styleJson("pmtiles://https://x/y.pmtiles", dark = true)
        org.junit.Assert.assertNotEquals(claro, oscuro)
    }
}
```

- [ ] **Step 2: Correr y ver que fallan**

Run: `gradle.bat -p /c/personal/OBD2 :core:maps:testDebugUnitTest --tests "*MapStyleProviderTest*" --console=plain`
Expected: FAIL, `Unresolved reference: MapStyleProvider`

- [ ] **Step 3: Implementar**

```kotlin
package com.revscope.core.maps

import java.io.File

/**
 * Resuelve de dónde salen los tiles, en cascada:
 * 1. archivo `.pmtiles` local (Fase 4 lo descarga; acá ya se respeta si existe)
 * 2. `revscope-server`
 * 3. nada: la app sigue dibujando sus capas sobre fondo vacío
 *
 * El servidor NO es un requisito: sin él la pantalla debe seguir funcionando.
 */
object MapStyleProvider {

    const val PMTILES_FILE_NAME = "colombia.pmtiles"

    private const val BACKGROUND_LIGHT = "#f8f4f0"
    private const val BACKGROUND_DARK = "#1c1c28"

    fun tilesUrl(localFile: File?, serverBaseUrl: String?): String? {
        if (localFile != null && localFile.isFile) {
            // MapLibre exige la URL interna completamente especificada.
            return "pmtiles://file://${localFile.absolutePath}"
        }
        val base = serverBaseUrl?.trimEnd('/') ?: return null
        return "pmtiles://$base/$PMTILES_FILE_NAME"
    }

    /**
     * Estilo mínimo propio en vez del de Protomaps completo: para la Fase 1 alcanza con
     * fondo + la fuente vectorial, y deja el JSON bajo control para el modo oscuro.
     * El estilo cartográfico completo entra cuando se genere el extracto.
     */
    fun styleJson(tilesUrl: String?, dark: Boolean): String {
        val background = if (dark) BACKGROUND_DARK else BACKGROUND_LIGHT
        val sources = if (tilesUrl == null) "" else
            """"protomaps": { "type": "vector", "url": "$tilesUrl" }"""
        return """
            {
              "version": 8,
              "sources": { $sources },
              "layers": [
                { "id": "fondo", "type": "background",
                  "paint": { "background-color": "$background" } }
              ]
            }
        """.trimIndent()
    }
}
```

- [ ] **Step 4: Correr y ver que pasan**

Run: `gradle.bat -p /c/personal/OBD2 :core:maps:testDebugUnitTest --console=plain`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add core/maps
git commit -m "feat: MapStyleProvider con cascada local, servidor y degradado

El servidor no puede ser requisito: sin archivo local ni servidor el estilo sigue
siendo válido y la pantalla dibuja sus capas sobre fondo vacío."
```

---

## Task 5: Migrar `RealTrackMap`

La pantalla más chica: valida el módulo antes de tocar la grande.

**Files:**
- Modify: `feature/session/build.gradle.kts`
- Modify: `feature/session/src/main/kotlin/com/revscope/feature/session/RealTrackMap.kt`

**Interfaces:**
- Consumes: `MapLibreMapView`, `physicalPxToDp`, `boundsOf`, `MapStyleProvider`.

Contrato de paridad de esta pantalla (del spec §7): casing blanco `0xCCFFFFFF` de 16 px físicos debajo, segmentos de 12 px encima, `SEGMENT_SIZE = 8` con vértice compartido entre segmentos consecutivos, gradiente `#3D8BFF → #E8FF00 → #FF3D5A` relativo a la velocidad máxima **de esa sesión**, y si `speeds.size != track.size` todos los segmentos quedan en `#E8FF00`. Bounding box con escala 1,25. El scroll de la pantalla contenedora no debe quedar secuestrado por el mapa.

- [ ] **Step 1: Cambiar la dependencia del módulo**

En `feature/session/build.gradle.kts`, reemplazar `implementation(libs.osmdroid)` por:

```kotlin
implementation(project(":core:maps"))
implementation(libs.maplibre)
```

- [ ] **Step 2: Reescribir el render**

Puntos obligatorios de la reescritura:

- Una `GeoJsonSource` para el casing y otra para los segmentos; el casing se agrega **antes** para quedar debajo.
- Cada segmento es un `Feature` con propiedad `"color"` (string hex), y una sola `LineLayer` con `PropertyFactory.lineColor(Expression.get("color"))` — evita crear 76 capas.
- Anchos: `PropertyFactory.lineWidth(physicalPxToDp(16f, density))` para el casing y `physicalPxToDp(12f, density)` para los segmentos, con `density = LocalContext.current.resources.displayMetrics.density`.
- Cámara: `boundsOf(...)` → `LatLngBounds` → `map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))`. El 1,25 de osmdroid se traduce en padding; ajustar hasta que se vea equivalente.
- Mantener el `setOnTouchListener` que llama `requestDisallowInterceptTouchEvent(true)`: sin eso el mapa secuestra el scroll de la pantalla de detalle.
- La lógica de segmentación y `speedToColor` **no se toca**: se copia tal cual, solo cambia cómo se dibuja.

- [ ] **Step 3: Compilar**

Run: `set -o pipefail; gradle.bat -p /c/personal/OBD2 :feature:session:compileDebugKotlin --console=plain 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Verificar en el dispositivo**

Instalar y abrir un viaje del historial con track GPS. Verificar contra el contrato de paridad de arriba: halo blanco visible a cada lado, gradiente por velocidad, encuadre al recorrido, y que la pantalla siga scrolleando al arrastrar sobre el mapa.

- [ ] **Step 5: Commit**

```bash
git add feature/session
git commit -m "refactor: RealTrackMap sobre MapLibre

Una sola LineLayer con lineColor por expresión en vez de 76 polylines. Los anchos
pasan por physicalPxToDp: los 16f/12f de osmdroid eran píxeles físicos."
```

---

## Task 6: `LiveMapScreen` — base y ruta viva

**Files:**
- Modify: `feature/map/build.gradle.kts`
- Modify: `feature/map/src/main/kotlin/com/revscope/feature/map/LiveMapScreen.kt`
- Create: `core/maps/src/main/kotlin/com/revscope/core/maps/MapLayerIds.kt`

**Interfaces:**
- Produces: `object MapLayerIds` con constantes `SRC_LIVE_ROUTE`, `LYR_LIVE_ROUTE`, `SRC_PLANNED_ROUTE`, `LYR_PLANNED_ROUTE`, `SRC_CAMERA_CIRCLES`, `LYR_CAMERA_FILL`, `LYR_CAMERA_OUTLINE`, `SRC_MARKERS`, `LYR_MARKERS`, `IMG_PIN`, `IMG_ME`.

- [ ] **Step 1: Definir los ids**

```kotlin
package com.revscope.core.maps

/**
 * Ids de fuentes, capas e imágenes. Nunca se guardan referencias a Source/Layer/Style:
 * reusar un wrapper cuyo peer nativo ya murió es crash nativo. Solo ids.
 */
object MapLayerIds {
    const val SRC_PLANNED_ROUTE = "src-ruta-planeada"
    const val LYR_PLANNED_ROUTE = "lyr-ruta-planeada"
    const val SRC_CAMERA_CIRCLES = "src-circulos-radar"
    const val LYR_CAMERA_FILL = "lyr-circulos-radar-relleno"
    const val LYR_CAMERA_OUTLINE = "lyr-circulos-radar-borde"
    const val SRC_MARKERS = "src-marcadores"
    const val LYR_MARKERS = "lyr-marcadores"
    const val SRC_LIVE_ROUTE = "src-ruta-viva"
    const val LYR_LIVE_ROUTE = "lyr-ruta-viva"
    const val IMG_PIN = "img-pin"
    const val IMG_ME = "img-yo"
}
```

- [ ] **Step 2: Cambiar la dependencia del módulo**

En `feature/map/build.gradle.kts`, reemplazar `implementation(libs.osmdroid)` por `implementation(project(":core:maps"))` + `implementation(libs.maplibre)`.

- [ ] **Step 3: Dibujar la ruta viva**

La ruta viva es amarilla `0xFFE8FF00`, ancho equivalente a 8 px físicos, sin casing.

Crear la fuente **vacía** al cargar el estilo y actualizarla después. Pasar el `LineString` pelado, **no** un `FeatureCollection`:

```kotlin
// setGeoJson(FeatureCollection) hace una copia defensiva extra; para una sola línea
// se pasa la geometría directo.
val src = style.getSourceAs<GeoJsonSource>(MapLayerIds.SRC_LIVE_ROUTE)
src?.setGeoJson(LineString.fromLngLats(points))
```

Mantener la lógica de throttling existente: la ruta solo se re-escribe cuando cambia `routeRevision`, nunca en cada recomposición. Cada `setGeoJson` marshalla todos los puntos por JNI y re-indexa el dataset completo.

- [ ] **Step 4: Verificar en el dispositivo**

Con un viaje activo: la línea amarilla crece, tiene grosor comparable al anterior, y la app no se pone a tirones al pasar de varios miles de puntos.

- [ ] **Step 5: Commit**

```bash
git add feature/map core/maps
git commit -m "refactor: ruta viva de LiveMapScreen sobre MapLibre"
```

---

## Task 7: Círculos y marcadores de radar

**Files:**
- Modify: `feature/map/src/main/kotlin/com/revscope/feature/map/LiveMapScreen.kt`

Contrato de paridad: el círculo usa el **radio configurado en metros** (`CameraAlertRadius`, default 250), con tres estados excluyentes:

| Estado | Relleno | Borde | Ancho |
|---|---|---|---|
| objetivo | `0x44FF1744` | `0xFFFF1744` | 4 px físicos |
| normal | `0x22FF5252` | `0x66FF5252` | 2 px físicos |
| atenuado | `0x0DFF5252` | `0x26FF5252` | 1 px físicos |

- [ ] **Step 1: Generar los círculos**

Un `Feature` por cámara, con `geodesicCircle(lat, lon, alertRadiusM.toDouble())` como geometría y una propiedad `"estado"` con `"objetivo"`, `"normal"` o `"atenuado"`.

- [ ] **Step 2: Pintar por expresión**

Dos capas sobre la misma fuente — `FillLayer` para el relleno y `LineLayer` para el borde — con `Expression.match(Expression.get("estado"), …)` mapeando cada estado a su color y ancho. Así el cambio de radar objetivo es un `setGeoJson`, sin recrear capas.

**No usar `feature-state` para esto:** solo opera sobre propiedades *paint* data-driven, y exige ids de Feature enteros.

- [ ] **Step 3: Marcadores de radar**

`SymbolLayer` sobre `SRC_MARKERS`, con `PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM)` para los pines de radar. Registrar el ícono con `style.addImage(MapLayerIds.IMG_PIN, bitmap)` — decodificando un `Bitmap` nuevo, porque `addImage` hace `.recycle()`.

El resalte del objetivo va por `iconOpacity` u `iconColor` con ícono SDF, **no** cambiando `iconImage` con feature-state.

- [ ] **Step 4: Verificar en el dispositivo**

Con radares descargados: el círculo mide lo mismo que el radio configurado (cambiar el radio en Ajustes a 500 y ver que el círculo crece), y al acercarse a uno el objetivo se resalta y el resto se atenúa.

- [ ] **Step 5: Commit**

```bash
git add feature/map
git commit -m "refactor: círculos y marcadores de radar sobre MapLibre

El círculo se genera con TurfTransformation en metros: circle-radius del style spec
es en píxeles y habría mostrado un radio de alerta falso."
```

---

## Task 8: Huecos, peers, destino y ruta planeada

**Files:**
- Modify: `feature/map/src/main/kotlin/com/revscope/feature/map/LiveMapScreen.kt`

Orden de capas exacto, de abajo hacia arriba: ruta planeada (cian `0xFF00E5FF`, 10 px físicos) → destino → peers → huecos → círculos y pines de radar → ruta viva → marcador "Tú".

Anclajes que difieren del default y hay que respetar: los huecos van **centrados** en el punto (`ICON_ANCHOR_CENTER`), con opacidad 0,85; el marcador "Tú" va **centrado**; destino y peers van anclados abajo.

- [ ] **Step 1: Agregar las capas en orden**

Usar `style.addLayerBelow(layer, MapLayerIds.LYR_LIVE_ROUTE)` / `addLayerAbove` para fijar el orden. **`beforeId` no existe** en el SDK Android.

- [ ] **Step 2: Los marcadores comparten fuente**

Un solo `SRC_MARKERS` con una propiedad `"tipo"` (`destino`, `peer`, `hueco`, `radar`, `yo`) y una `SymbolLayer` que resuelve ícono, anclaje y opacidad por expresión. Menos capas, un solo `setGeoJson` por cambio.

- [ ] **Step 3: Verificar en el dispositivo**

Long-press fija destino y aparecen la línea cian y el pin. Los huecos se ven centrados y translúcidos. En rodada en grupo, los peers aparecen.

- [ ] **Step 4: Commit**

```bash
git add feature/map
git commit -m "refactor: huecos, peers, destino y ruta planeada sobre MapLibre"
```

---

## Task 9: Cámara — follow, rumbo arriba, zoom y long-press

**Files:**
- Modify: `feature/map/src/main/kotlin/com/revscope/feature/map/LiveMapScreen.kt`

Contrato de paridad, incluidos los quirks actuales que hay que decidir a propósito:

- Zoom inicial 16 con viaje activo, 13 sin viaje, aplicado **una sola vez**. El follow re-centra pero **no** re-zoomea: el zoom que deja el usuario con pinch se respeta.
- Cualquier contacto con el mapa apaga el follow — incluido el long-press que fija destino. Recuperarlo requiere el FAB.
- Apagar rumbo-arriba des-rota de inmediato; encenderlo no rota hasta la próxima revisión de ruta, y solo si el follow está activo.

- [ ] **Step 1: Cámara**

```kotlin
map.animateCamera(
    CameraUpdateFactory.newCameraPosition(
        CameraPosition.Builder()
            .target(LatLng(last.lat, last.lon))
            .bearing(if (headingUp) currentBearingDegrees(route) else 0.0)
            .build()
    )
)
```

`CameraPosition.Builder` sin `.zoom(...)` conserva el zoom actual — que es justo el contrato.

- [ ] **Step 2: Apagar el follow al interactuar**

Reemplazar el `setOnTouchListener` por `map.addOnMoveListener(object : MapLibreMap.OnMoveListener { … })`, poniendo `followEnabled = false` en `onMoveBegin`.

- [ ] **Step 3: Long-press**

```kotlin
map.addOnMapLongClickListener { latLng ->
    viewModel.setDestination(latLng.latitude, latLng.longitude)
    true
}
```

- [ ] **Step 4: Verificar en el dispositivo**

Follow sigue la posición; panear lo apaga; el FAB lo reactiva; rumbo-arriba rota y norte-arriba devuelve a cero; long-press fija destino.

- [ ] **Step 5: Commit**

```bash
git add feature/map
git commit -m "refactor: cámara, follow, rumbo arriba y long-press sobre MapLibre"
```

---

## Task 10: Modo nocturno por cambio de estilo

**Files:**
- Modify: `feature/map/src/main/kotlin/com/revscope/feature/map/LiveMapScreen.kt`

El mecanismo cambia: `TilesOverlay.INVERT_COLORS` no tiene equivalente. Pasa a ser un estilo oscuro real.

**Esta es la tarea con el bug más difícil de la migración.** Al cambiar de estilo, todas las fuentes y capas agregadas a mano quedan `detached`, y `GeoJsonSource.setGeoJson()` **retorna en silencio** — sin excepción, sin log. La ruta simplemente deja de actualizarse.

- [ ] **Step 1: Recrear todo tras el cambio de estilo**

El callback de `setStyle` debe volver a: registrar las imágenes (`Bitmap` nuevos, los anteriores fueron reciclados), crear las fuentes vacías, agregar las capas en orden, y re-escribir los datos actuales. Extraer eso a una única función `instalarCapas(style)` que se llame tanto en la carga inicial como después de cada cambio.

- [ ] **Step 2: Verificar en el dispositivo**

Alternar nocturno/claro **cinco veces seguidas** con un viaje activo y confirmar que después del quinto cambio la ruta viva **sigue creciendo** y los radares siguen apareciendo. Si la ruta se congela, las fuentes quedaron detached.

- [ ] **Step 3: Commit**

```bash
git add feature/map
git commit -m "refactor: modo nocturno como estilo oscuro real

Cambiar de estilo deja detached las fuentes agregadas a mano y setGeoJson retorna
en silencio, así que las capas se reinstalan enteras en cada cambio."
```

---

## Task 11: Quitar osmdroid y cerrar la fase

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `docs/manual-usuario.md`, `docs/configuracion.md`

- [ ] **Step 1: Verificar que no queda ninguna referencia**

Run: `grep -rn "osmdroid" /c/personal/OBD2 --include=*.kt --include=*.kts --include=*.toml --include=*.xml | grep -v "/build/"`
Expected: sin resultados fuera de documentación histórica.

- [ ] **Step 2: Quitar la dependencia**

Borrar `osmdroid = "6.1.20"` de `[versions]` y el alias `osmdroid` de `[libraries]`.

- [ ] **Step 3: Build completo y suite de tests**

Run: `set -o pipefail; gradle.bat -p /c/personal/OBD2 test :app:assembleDebug --console=plain 2>&1 | tail -10`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Correr el contrato de paridad completo**

Ejecutar en el S25 la lista del spec §7, incluidas las pruebas de autonomía: **modo avión con el mapa abierto** (debe seguir dibujando ruta y radares sobre fondo vacío, sin crash), 20 ciclos de apagar/encender pantalla, 30 navegaciones entre tabs, y una sesión de 60 minutos con GPS.

- [ ] **Step 5: Actualizar la documentación**

En `docs/manual-usuario.md`, sección Mapa: el modo nocturno ahora es un estilo oscuro. En `docs/configuracion.md`: el mapa usa tiles vectoriales servidos por `revscope-server`, con degradado a las zonas ya vistas cuando no hay servidor.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "chore: eliminar osmdroid del proyecto

Cierra la Fase 1: las dos pantallas de mapa corren sobre MapLibre y la dependencia
archivada desde 2024 sale del grafo."
```

---

## Notas para quien ejecute

- **Las tareas 5 a 10 requieren el S25 desbloqueado.** El render no se puede verificar sin dispositivo y el teléfono suele estar con PIN. Coordinar con el usuario antes de empezar la Task 5.
- **El riesgo de rendimiento vive en la Task 6.** Si al llegar a varios miles de puntos la app se pone a tirones, la mitigación es partir la ruta en un tramo histórico congelado más una cola corta que se actualiza. Medir antes de optimizar.
- **Si algo de la lista de paridad queda peor, la fase no está terminada.** El único cambio visible aceptado es el modo nocturno.
