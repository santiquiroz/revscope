# Fase 1 — Cambio de motor del mapa: osmdroid → MapLibre

Fecha: 2026-08-12. Primera de seis fases hacia navegación turn-by-turn propia (ver §9).

## 1. Por qué

osmdroid fue **archivado por su autor el 2024-11-20** (repo read-only: "the effort to maintain this project has become untenable"). Es una dependencia muerta bajo las dos pantallas de mapa de la app.

Además es un bloqueo duro: Ferrostar — el SDK FOSS de navegación turn-by-turn elegido para la Fase 3 — exige MapLibre. No hay navegación propia sin esta migración.

## 2. Alcance

**Reemplazar el motor de render. Ninguna feature nueva.** El criterio de aceptación es la lista de paridad de §7, no una demo.

Fuera de alcance: búsqueda de direcciones (Fase 2), turn-by-turn (Fase 3), mapa offline en el dispositivo (Fase 4), costeo propio (Fase 5), IA de destinos (Fase 6).

Único cambio visible aceptado: el **modo nocturno** cambia de mecanismo (§5.3), porque el actual no tiene equivalente en MapLibre.

## 3. Superficie a migrar

Solo **dos archivos de código** usan osmdroid:

| Archivo | Rol |
|---|---|
| `feature/map/.../LiveMapScreen.kt` (610 líneas) | Mapa vivo: ruta, radares, huecos, peers, destino, cámara |
| `feature/session/.../RealTrackMap.kt` (123 líneas) | Replay del recorrido, polilínea graduada por velocidad |

Más: `gradle/libs.versions.toml:29,77` (versión y alias), `feature/map/build.gradle.kts:32`, `feature/session/build.gradle.kts:35`.

Nada más toca osmdroid: ningún permiso, ninguna regla ProGuard, ningún recurso propio, ninguna preferencia persistida por la app. `:app` la recibe solo transitivamente como `implementation`, así que sus clases no están en su classpath.

Detalle heredado: los `Marker` sin ícono propio usan `marker_default` del AAR de osmdroid. Al quitar la dependencia esos drawables desaparecen — hay que aportar íconos propios (§5.4).

## 4. Arquitectura

Hoy el código de mapa está partido entre dos módulos que no se hablan, cada uno con su copia de la mezcla Compose + motor. Migrar duplicando eso sería heredar el problema.

### Módulo nuevo `:core:maps`

| Pieza | Responsabilidad |
|---|---|
| `MapLibreMapView` | El `AndroidView` con la cadena de ciclo de vida completa, resuelta **una sola vez** |
| `MapStyleProvider` | Estilo claro/oscuro y origen de tiles. **Único punto que la Fase 4 toca** para pasar a offline |
| `MapLayers` | Alta y actualización de líneas, símbolos y círculos, para que las pantallas no hablen GeoJSON crudo |
| `MapIcons` | Registro de íconos en el estilo, con su ciclo de vida |

`:core:common` recibe solo matemática pura sin dependencia de Android ni MapLibre (bounding box), con tests.

Ambos features dependen de `:core:maps`. Ninguno depende del otro.

## 5. Decisiones técnicas

Todas verificadas contra fuente primaria (Maven Central, CHANGELOG oficial, código fuente del SDK, style spec) por un pase de investigación con verificación adversarial. Lo no verificado está marcado como tal.

### 5.1 Artefacto y versión

**`org.maplibre.gl:android-sdk-opengl:13.4.1`** — la variante OpenGL, no la default.

Desde **13.0.0 el backend por defecto es Vulkan** (CHANGELOG verbatim: "💥 Breaking: Use Vulkan as rendering backend for the `org.maplibre.gl:android-sdk` package. You can still use OpenGL ES with the `org.maplibre.gl:android-sdk-opengl` package"). Se elige OpenGL por dos razones concretas:

1. El paquete Vulkan declara `android.hardware.vulkan.version` con `required="true"`, lo que filtra dispositivos sin Vulkan 1.0.
2. Hay un fix de `VK_ERROR_DEVICE_LOST` en GPUs Adreno serie 600 con backend Vulkan **usando el indicador de ubicación especializado** (PR #4442, mergeado 2026-08-12) que todavía no salió en release. Una app de navegación usa exactamente ese indicador.

Se puede reevaluar Vulkan cuando ese fix esté publicado. `minSdk 23` desde 12.0.0 — la app está en 26, no hay conflicto.

### 5.2 Conflicto de versión de Kotlin — resolver ANTES de escribir UI

El proyecto está en **Kotlin 2.0.21** (`libs.versions.toml:3`). El POM de `android-sdk:13.4.1` arrastra **`kotlin-stdlib:2.2.10`**, y la ruta de la Fase 3 (`ferrostar:ui-maplibre` → `maplibre-compose-android:0.13.0`) arrastra **2.3.21**.

Este proyecto ya fue mordido dos veces por esta clase de problema: blessed 0.5.0 (compilada con Kotlin 2.2) y Hilt 2.51.1 (kapt sin poder leer metadata Kotlin 2.x).

**Tarea 1 de la implementación**, antes de tocar UI: compilar con la dependencia agregada y resolver el conflicto, sea forzando `kotlin-stdlib` a la versión del proyecto o subiendo Kotlin. Si hace falta subir Kotlin, es un cambio transversal que va en su propio commit, separado de la migración.

### 5.3 Basemap: PMTiles propio — **corrección respecto al diseño presentado**

Estilos: los [cartográficos abiertos de Protomaps](https://github.com/protomaps/basemaps) (BSD-3-Clause), que traen variantes clara y oscura. Eso convierte el modo nocturno de un truco (`ColorMatrix` invirtiendo tiles) en un estilo oscuro real. El `INVERT_COLORS` de osmdroid no tiene equivalente, así que este cambio no era opcional.

Sintaxis verificada del consumo:

- Remoto: `pmtiles://https://host/ruta/colombia.pmtiles`
- Local: `pmtiles://file:///ruta/absoluta/colombia.pmtiles`
- La URL dentro de `pmtiles://` debe estar **completamente especificada**; MapLibre Native no resuelve relativas.

Soporte de PMTiles: la doc dice 11.7.0, el CHANGELOG lo lista en 11.8.0 — con 13.4.1 el punto es discutible, y además desde 13.3.0 hay caché ambiente para sources PMTiles (PR #4290).

**Dónde se hospeda — corregido.** En el diseño presentado propuse arrancar con un asset de release de GitHub. **La verificación empírica lo descarta como origen en runtime:**

- Los range requests **sí funcionan** (verificado byte a byte en tres offsets: `206 Partial Content`, `Content-Range` correcto).
- Pero cada request paga un **302** a una URL prefirmada que expira y no se puede cachear: **0,40–0,55 s por request** contra 0,10–0,14 s yendo directo. PMTiles hace decenas de range requests por viewport. Serializado, son segundos de primer pintado.
- Y la postura de ToS de GitHub sobre servir tiles range-heavy desde ese endpoint **no está verificada**. No es para lo que existe.

Decisión: **`revscope-server` sirve el archivo**, con soporte de range requests (`206 Partial Content`) y URL estable.

**El servidor no puede volverse un requisito.** La app tiene que seguir funcionando en lo básico sin él, así que `MapStyleProvider` resuelve el origen en cascada:

1. **Archivo local** `.pmtiles` en el almacenamiento del dispositivo, si existe → `pmtiles://file:///…`
2. **`revscope-server`**, si responde → `pmtiles://https://…`
3. **Degradado**: sin basemap, pero la pantalla **sigue dibujando sus capas propias** — ruta, radares, huecos, peers — sobre fondo vacío, sin crash.

El paso 1 se implementa desde esta fase aunque la descarga del archivo sea Fase 4: es una rama de resolución de URL, no un subsistema, y dejarlo para después obligaría a rehacer el proveedor. Además, desde 13.3.0 MapLibre tiene caché ambiente para sources PMTiles (PR #4290), así que las zonas ya vistas siguen disponibles sin red.

Nada del resto de la app depende del mapa: telemetría, alertas de radar por voz, detección de caída y pico y placa ya funcionan offline contra Room. Esta fase no puede introducir la primera dependencia dura de red.

El asset de release de GitHub **sí** sirve para la Fase 4: ahí la descarga es una sola secuencial, no decenas de rangos, y el límite de 2 GiB por archivo entra sobrado — Colombia a z15 fue **medido en 906 MB** (1,0 GB incluyendo islas).

Atribución: el basemap es Produced Work de ODbL y **exige atribución visible de OpenStreetMap**. La app ya la muestra; se mantiene.

### 5.4 Los seis puntos donde esta migración se rompe

No son detalles de implementación: son las razones por las que una migración así falla en la calle y no en el emulador.

**1. Círculos de radar en metros.** `circle-radius` del style spec es, verbatim, "Units in pixels". Ni `circle-pitch-scale` ni `circle-pitch-alignment` convierten a metros. Un port literal haría que el círculo cambie de tamaño físico con el zoom, mostrando un radio de alerta que no es el real.

Solución verificada, sin escribir matemática propia: **`org.maplibre.turf.TurfTransformation.circle(center, radius, steps, units)`** devuelve un `Polygon`, y `android-sdk-turf:6.0.1` **ya viene como dependencia transitiva** del SDK. Es lo que usa el ejemplo oficial de Android para dibujar un círculo de 150 m. Se dibuja con `FillLayer` + `LineLayer`.

**2. Anchos de trazo: píxeles físicos vs dp.** `outlinePaint.strokeWidth` de osmdroid se aplica directo al `Canvas`: son **píxeles físicos, no dp**. Una ruta de `8f` mide 8 px ≈ 2,67 dp en pantalla 3x. Si el motor nuevo interpreta el ancho en dp, **las líneas salen 3-4× más gruesas**. Hay que convertir explícitamente, no copiar los números.

**3. Ciclo de vida.** osmdroid se conformaba con `onDetach()`. El `MapView` de MapLibre exige la cadena completa (`onStart`/`onResume`/`onPause`/`onStop`/`onDestroy`/`onLowMemory`/`onSaveInstanceState`). Hacerlo mal no falla en el emulador: falla al volver de la pantalla apagada, que es el caso de uso real en la moto. Va resuelto una sola vez en `:core:maps`.

Nota: **`RealTrackMap` hoy no llama a ningún método de ciclo de vida** — ni siquiera `onDetach()`. Es un bug latente actual; la migración lo cierra.

**4. Estado del marcador de radar objetivo.** El plan intuitivo (cambiar el ícono con `feature-state`) **no funciona**: `icon-image` es propiedad *layout*, y `feature-state` solo opera sobre propiedades *paint* data-driven. Además los ids de Feature deben ser enteros o strings casteables a entero. Alternativas válidas: `iconColor` sobre íconos SDF, `iconOpacity`, o capas separadas con filtro.

**5. Orden de capas.** `beforeId` **no existe** en el SDK Android (0 ocurrencias en el código de la plataforma). La API real es `style.addLayerBelow(layer, "id")` / `addLayerAbove(layer, "id")`. Y `style.isFullyLoaded()` es **método**, no propiedad — hay que llamarlo antes de tocar capas.

**6. Ciclo de vida de Source/Layer.** No se deben guardar referencias a `Source`/`Layer`/`Style` fuera del scope del estilo: reusar un wrapper Java cuyo peer nativo ya murió es crash nativo (#3269). **Guardar solo los ids** y recuperar desde el estilo.

### 5.5 Marcadores: capas, no plugin

El plugin de anotaciones existe y sí soporta MapLibre 11+ (`org.maplibre.gl:android-plugin-annotation-v9:3.0.2`), pero su última publicación es de **2024-10-17** — mantenimiento desalineado del SDK. Se usa `GeoJsonSource` + `SymbolLayer`, que además es lo que la Fase 3 va a necesitar.

## 6. Rendimiento: el riesgo real de esta fase

La ruta viva crece hasta **18.000 puntos** y hoy se actualiza con `polyline.addPoint()` — incremental. En MapLibre no existe equivalente: actualizar un `GeoJsonSource` implica **reemplazar la FeatureCollection completa**.

A 1 Hz, en sesiones de horas, con GPS activo. Esta es la regresión de rendimiento más probable de toda la fase.

Mitigaciones, en orden: mantener la lógica de throttling que ya existe (repintado completo solo ante cambios estructurales), evaluar partir la ruta en un segmento "histórico" congelado más una "cola" corta que se actualiza, y medir antes de optimizar. La app ya tiene `LiveRouteHolder` con snapshot cada 2 s y tope de puntos — hay dónde apoyarse.

`MapLibreMap.setTileCacheEnabled(Boolean)` es API estable desde 11.2.0 (default `true`) y cambia memoria por fluidez de zoom: queda como palanca si la memoria aprieta, no como decisión previa.

## 7. Criterio de aceptación — paridad

La fase no cierra hasta que todo esto se comporte igual que antes, verificado en el S25:

**Mapa vivo**
- [ ] Ruta viva amarilla `0xFFE8FF00`, ancho equivalente a 8 px físicos
- [ ] Marcador "Tú" anclado al centro, siguiendo la última posición
- [ ] Círculo de radar del **radio configurado en metros** (250 m default), con los tres estados: objetivo `4f`, normal `2f`, atenuado `1f`, y sus seis colores ARGB exactos
- [ ] Marcadores de radar con resalte del objetivo y atenuación del resto
- [ ] Marcadores de huecos con su alpha
- [ ] Peers de rodada en grupo
- [ ] Ruta planeada OSRM cian `0xFF00E5FF`, ancho equivalente a 10 px físicos, y marcador de destino
- [ ] Long-press fija destino
- [ ] Follow: se apaga al panear, lo reactiva el FAB
- [ ] Rumbo arriba rota el mapa; norte arriba lo devuelve a 0
- [ ] Modo nocturno (ahora estilo oscuro)
- [ ] Zoom inicial 16 con viaje activo, 13 sin viaje, aplicado una sola vez
- [ ] Banner de radar y chip de distancia/ETA intactos

**Replay de viaje**
- [ ] Polilínea graduada por velocidad, segmentos de 8 puntos, gradiente `#3D8BFF → #E8FF00 → #FF3D5A`
- [ ] Casing blanco `0xCCFFFFFF` debajo
- [ ] Zoom al bounding box con escala 1,25
- [ ] Scroll de la pantalla no secuestrado por el mapa

**Autonomía (el servidor no es requisito)**
- [ ] En modo avión y sin servidor, la pantalla de mapa **no crashea** y sigue dibujando ruta, radares y huecos sobre fondo vacío
- [ ] Con el servidor caído pero con red, mismo resultado — y sin bloquear la UI esperando timeout
- [ ] Las zonas ya vistas se siguen renderizando sin red (caché ambiente de PMTiles)
- [ ] El resto de la app (telemetría, avisos de radar, caída, pico y placa) funciona igual sin servidor

**No funcional**
- [ ] Sin crash al apagar y encender la pantalla 20 veces con el mapa abierto
- [ ] Sin crash navegando entre tabs 30 veces
- [ ] Sesión de 60+ minutos con GPS sin crecimiento sostenido de memoria
- [ ] Primer pintado del mapa en tiempo comparable al actual

## 8. Testing

- Unit tests para lo puro: conversión px físicos → dp, bounding box, envoltura del círculo de Turf, mapeo de estados de radar a propiedades de capa.
- El render no se puede testear sin dispositivo: la fase se cierra contra §7 ejecutado en el S25. Requiere el teléfono desbloqueado.
- Sin bandera de feature ni convivencia de los dos motores. Rollback = `git revert` del commit de la fase.

## 9. Fases siguientes

| Fase | Entregable |
|---|---|
| 2 | Búsqueda de direcciones (Photon público online, FTS local después) |
| 3 | Turn-by-turn con Ferrostar, voz en español vía `AlertsEngine` |
| 4 | Autonomía: PMTiles de Colombia en el dispositivo + routing local |
| 5 | Costeo propio: evitar huecos mapeados, radares y zonas de pico y placa |
| 6 | IA para resolver destinos en lenguaje natural |

Restricciones ya verificadas que condicionan la Fase 3: Ferrostar **0.53.0** es la versión actual; sus artefactos Android se **renombraron en 0.49.0** (`composeui`/`maplibreui` → `ui-compose`/`ui-maplibre`/…) sin anuncio en el changelog; **0.48.0 no existe** para Android en Maven Central; arrastra **OkHttp** como dependencia no negociable; y su doc solo promete keys gratuitas de **evaluación** para Stadia y GraphHopper — lo que confirma que la única vía verificada de costo cero en producción es self-hosting o proveedor propio.
