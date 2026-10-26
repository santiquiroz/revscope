# Plan G — Mapa offline Colombia (PMTiles) + estilo protomaps completo (2026-08-20)

Spec: docs/superpowers/specs/2026-08-20-pmtiles-colombia-design.md
Branch: feature/backlog-e-f-g (continúa tras F). Release conjunta E+F+G al final (v1.16.0, versionCode 20).

## Global constraints

- **Asset URL fija**: `https://github.com/santiquiroz/revscope/releases/download/tiles-v1/colombia.pmtiles` (release tag `tiles-v1`, lo sube el orquestador — NO es task de subagente). Tamaño real: ~911 MB (extract 20260819, bbox -79.1,-4.3,-66.8,13.6).
- **Ruta local**: `context.filesDir/maps/colombia.pmtiles`; descarga a `colombia.pmtiles.part` + `renameTo` atómico. NUNCA exponer un archivo parcial con el nombre final.
- **MapStyleProvider sigue puro** (object sin Context): las capas entran como String ya leído (`layersJson: String?`); leer assets es del caller. Tests con JSON fixture mínimo.
- **Assets de capas**: `core/maps/src/main/assets/map-layers-light.json` / `map-layers-dark.json` — SOLO el array JSON de layers, source name `protomaps`, generados por `scripts/gen-map-layers.mjs` con `@protomaps/basemaps` (npm) `layers("protomaps", theme, { lang: "es" })`, commiteados. La app no depende de Node.
- **Sprites**: `"sprite": "https://protomaps.github.io/basemaps-assets/sprites/v4/light|dark"` según tema (remoto, degradación sin red = POIs sin icono, aceptada).
- **Capas propias en runtime**: LiveMapLayers agrega sus capas SOBRE el estilo cargado (como hoy) — deben quedar por encima de las de protomaps; verificar que los ids propios no colisionen con los generados (prefijos `src-`/`icono-`/`peer-` vs ids protomaps).
- **Fallbacks intactos**: ráster OSM (con su fake-dark) y fondo-solo NO se tocan. El fake-dark ráster se conserva.
- **Descarga**: OkHttp streaming (client compartido si existe singleton; si no, uno propio con timeouts largos), progreso por Content-Length, chequeo `usableSpace > size*1.2` antes, WiFi-only por defecto con toggle "permitir datos móviles", cancelable. Sin resume por ranges (v1). `.part` huérfano se borra al reintentar.
- **Corrupción**: si MapLibre falla cargando estilo con tier 1 activo → borrar archivo + recargar estilo (cascada sigue) + snackbar "Mapa offline dañado — re-descargalo en Ajustes".
- **Gradle**: `GRADLE="$HOME/.gradle/wrapper/dists/gradle-8.11.1-bin/bpt9gzteqjrbo1mjrsomdt32c/gradle-8.11.1/bin/gradle"` desde /c/personal/OBD2; exit codes reales (sin pipes).
- Commits en español `tipo: descripción`, sin Co-Authored-By.

## Tasks

### T1 — Generador de capas + assets commiteados

Files: scripts/gen-map-layers.mjs (nuevo), core/maps/src/main/assets/map-layers-{light,dark}.json (generados), package.json efímero NO commiteado (usar `npm exec`/install temporal en scratch).
Steps: script Node que importa `@protomaps/basemaps`, emite `JSON.stringify(layers("protomaps", theme, { lang: "es" }))` por tema a los dos assets. Correrlo UNA vez (implementer lo ejecuta con node real; si npm falla en este rig, documentar y fallback: descargar style JSON oficial de https://github.com/protomaps/basemaps y extraer layers). Sanity: ambos assets parsean, >40 capas, todas con `"source":"protomaps"` (background no lleva source — excluirlo del check), ninguna colisiona con ids propios (`grep` de LiveMapLayers ids).
Verify: node script re-ejecutable idempotente; `git diff --stat` solo los 3 archivos esperados.

### T2 — MapStyleProvider estilo vectorial completo

Files: core/maps/MapStyleProvider.kt, MapStyleProviderTest.kt.
Steps: `styleJson(tilesUrl, dark, rasterFallback, layersJson: String? = null)`. Con tilesUrl+layersJson → estilo completo: glyphs + sprite por tema + source protomaps + `[fondo] + capas del layersJson`. Sin layersJson → vectorStyle actual (fondo solo, compat). Fondo: usar el background del tema protomaps si viene en las capas (la capa background generada REEMPLAZA la capa "fondo" propia — no duplicar backgrounds; si el array trae background, omitir "fondo").
Tests: fixture layers mínimo (background + 1 line layer) — estilo resultante contiene ambas, sprite correcto por tema, sin doble background, compat layersJson=null intacta (tests existentes no se tocan salvo firma).
Verify: `"$GRADLE" :core:maps:test`.

### T3 — Servicio de descarga del mapa

Files: core/maps (o feature/settings si el DI queda más limpio): MapDownloadService.kt (nuevo), MapDownloadState.kt, lógica pura extraída MapDownloadDecider.kt (decisiones: espacio suficiente, wifi-gate, promote/cleanup de .part) + tests.
Steps: estados `Idle(exists: Boolean, sizeBytes, downloadedAtMs)` / `Downloading(progress0a1, bytesDownloaded, totalBytes)` / `Error(msg)` en StateFlow. API: `download(allowCellular)`, `cancel()`, `delete()`. OkHttp asíncrono en scope propio (SupervisorJob + IO), escritura streaming 8KB chunks, progreso cada ≥1%. Al completar: rename atómico; si rename falla → Error + borrar .part. Cancel → borrar .part.
Tests (JVM, sin red): Decider puro (espacio, wifi, promote); state machine con server fake (MockWebServer si ya está en deps de test; si no, abstraer la fuente de bytes con interface y fake).
Verify: `"$GRADLE" :core:maps:test` (o módulo elegido).

### T4 — Ajustes → Mapa: sección "Mapa offline"

Files: pantalla de Ajustes existente (feature/settings — ubicar sección Mapa actual), ViewModel correspondiente, strings es.
Steps: card "Mapa offline de Colombia (~911 MB)": estado desde MapDownloadService.state; botones contextuales — Descargar (con confirm de tamaño + toggle datos móviles si no hay WiFi), Cancelar (descargando, con progreso lineal + %), Borrar (descargado, con confirm). Al completar → toast/snackbar "Mapa offline listo". Errores del state → texto inline rojo.
Verify: `"$GRADLE" :feature:settings:test test` según módulo + assembleDebug.

### T5 — Wiring del tier local + dark real en los mapas

Files: LiveMapScreen.kt (~línea 176), LiveMapViewModel si el File/estilo pasa por ahí, RealTrackMap.kt (feature/session), lector de assets (context.assets → strings, remember/cache).
Steps: resolver `localFile = File(context.filesDir, "maps/colombia.pmtiles")`, `tilesUrl = MapStyleProvider.tilesUrl(localFile, null)` (tier server sigue sin wirear: el server hoy no hospeda tiles — dejar null). Leer asset de capas según darkTiles y pasar `layersJson`. Recomponer estilo cuando el archivo aparece/desaparece (observar MapDownloadState). RealTrackMap: mismo wiring (dark fijo false hoy — mantener). Manejo corrupción per Global Constraints (callback de error de estilo de MapLibre → delete + reload + snackbar).
Verify: assembleDebug + test; smoke manual si hay device.

### T6 — Verificación integral G

Steps: `"$GRADLE" test :app:assembleDebug` exit real; APK fresco; adb devices (si hay → installDebug + smoke visual del mapa vectorial con el pmtiles real pusheado por adb a filesDir vía run-as o descarga real). Checklist manual pendiente de device en el reporte.
Verify: reporte en .superpowers/sdd/2026-08-20-pmtiles-colombia/task-6-report.md.

## Orquestador (fuera de SDD)

- [HECHO 2026-08-20] Extract colombia.pmtiles: 913.398.631 bytes, mvt z0-15, bbox verificado, `pmtiles verify` OK.
- [HECHO 2026-08-20] Release `tiles-v1` publicado (prerelease); URL pública verificada: 200, Content-Length correcto, `Accept-Ranges: bytes`.
- Tras T6: fix wave final G (si hay), re-review, luego release conjunta v1.16.0.
