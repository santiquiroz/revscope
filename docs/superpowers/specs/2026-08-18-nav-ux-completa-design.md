# UX de navegación completa — sub-proyecto B (2026-08-18)

Segundo de cuatro sub-proyectos (A mapa base → B UX navegación → C tipo de vehículo → D onboarding). Depende de A (GPS vivo en mapa). Meta: experiencia de navegación al nivel de Google Maps sobre el stack existente (OSRM + Ferrostar core + Photon + TTS).

## Estado actual

Fases 1-3 ya entregaron: routing OSRM (long-press → destino), búsqueda Photon en overlay, turn-by-turn con voz, banner de maniobra, barra de progreso, chip de ruta con ETA pre-inicio, detección de desvío y llegada, handoff a Google Maps externo. Faltantes frente a Google Maps: navegar exige iniciar viaje a mano, no hay recálculo automático, la cámara no acompaña maniobras, la búsqueda está escondida, no hay lugares guardados, no hay ETA viva durante la nav, nocturno solo manual.

## Diseño

### B1. Navegación de un tap

"Iniciar navegación" auto-arranca viaje GPS si no hay sesión activa: `ConnectionViewModel.startGpsTrip()` (o equivalente inyectado) antes de `startNavigation()`. El guardrail "Inicia un viaje para que la navegación reciba el GPS" desaparece. La sesión graba telemetría como cualquier viaje; la nav sobrevive pantalla apagada vía `ObdForegroundService`.

### B2. Auto-reroute

- Fuente: estado de desvío que Ferrostar ya expone en `NavigationSession`/`NavigationController`.
- Al desvío sostenido: re-fetch OSRM desde posición + bearing actual, swap de ruta en la sesión viva, anuncio TTS "Recalculando ruta".
- Throttle: mínimo 10 s entre recálculos y no recalcular si ya hay un fetch en vuelo (OSRM público `router.project-osrm.org`).
- Fallo del re-fetch: mantener ruta vieja, reintentar en el próximo fix respetando throttle, sin spam de avisos.

### B3. Cámara de navegación

Modo cámara dedicado mientras hay nav activa:

- Course-up + pitch ~50° (vista inclinada tipo Google Maps).
- Zoom dinámico como **función pura** `navZoom(speedKmh, distToManeuverM)`: velocidad alta → alejar; a <300 m de la maniobra → acercar gradualmente.
- Pan del usuario pausa el follow (semántica existente); FAB MyLocation re-arma.
- Al terminar/cancelar nav: vuelve a la cámara normal del mapa (zoom del usuario preservado).

### B4. Búsqueda prominente + lugares guardados

- Barra de búsqueda fija arriba del mapa (reemplaza el overlay escondido como punto de entrada; `SearchOverlay` se convierte en pantalla/estado de búsqueda).
- Al tocar: chips **Casa / Trabajo**, **recientes** (automáticos, cap 20, LRU), **favoritos** (estrella en resultados), resultados Photon debajo.
- Tap en resultado → ruta directa (fetch OSRM) + chip "Iniciar". Long-press en mapa sigue funcionando.
- Persistencia: nueva tabla Room `saved_places` — `id, type (HOME|WORK|FAVORITE|RECENT), name, lat, lon, lastUsedAt`. Migración de schema. HOME/WORK únicos (upsert por type).

### B5. Bottom bar con ETA viva

Durante nav, barra inferior estilo Google Maps: hora de llegada + minutos restantes + km restantes + botón salir. Se actualiza con cada fix a partir del progreso de Ferrostar (distancia restante / velocidad estimada). El `RouteInfoChip` pre-inicio se mantiene.

### B6. Banner de maniobra mejorado

- Countdown de distancia grande y prominente.
- Maniobra encadenada: "luego gira a la derecha" cuando la siguiente está a <150 m de la actual.
- Sin lane guidance: OSRM demo no entrega datos de carril confiables (YAGNI).

### B7. Nocturno automático

- Util `SunTimes` (lat/lon + fecha → sunrise/sunset, cálculo solar simple sin dependencias).
- Preferencia `auto | on | off` (default auto). En auto: tiles nocturnos tras el atardecer.
- El toggle manual existente pasa a ser el estado `on/off`.

## Errores

- OSRM caído o rate-limit en fetch inicial: aviso discreto + opción reintentar; la búsqueda sigue funcionando.
- Sin red durante nav: la guía sigue (Ferrostar procesa local con la ruta cargada); reroute deshabilitado hasta recuperar red.
- ETA sin velocidad válida (parado): mostrar última ETA conocida, no NaN.

## Archivos

| Archivo | Cambio |
|---|---|
| `feature/map/.../LiveMapViewModel.kt` | auto-start sesión, reroute, estado nav-camera, lugares |
| `feature/map/.../LiveMapScreen.kt` | barra búsqueda, bottom bar, cámara nav |
| `feature/map/.../SearchOverlay.kt` | → pantalla búsqueda con chips/recientes/favoritos |
| `feature/map/.../navigation/NavigationBanner.kt` | countdown + maniobra encadenada |
| `feature/map/.../navigation/NavBottomBar.kt` | nuevo |
| `feature/map/.../routing/OsrmRouteFetcher.kt` | fetch desde posición+bearing (reroute) |
| `core/navigation/.../NavigationController.kt` | swap de ruta en vivo, exposición de desvío/progreso |
| `core/data/.../SavedPlaceEntity.kt` + `SavedPlaceDao.kt` | nuevos + migración |
| `core/maps/.../MapStyleProvider.kt` | modo nocturno auto |
| `core/common` o `core/maps` | `SunTimes.kt` nuevo |

## Tests

- Unit: decisión de reroute (desvío sostenido → refetch; throttle 10 s; fetch en vuelo no duplica), `navZoom()` pura, `SunTimes` contra valores conocidos, LRU de recientes, upsert HOME/WORK, cálculo ETA.
- Manual: ruta con desvío deliberado → recalcula y anuncia; nav con pantalla apagada sigue hablando; búsqueda → casa → iniciar en 3 taps.

## Fuera de alcance

Lane guidance, rutas alternativas en paralelo, tráfico en vivo, waypoints múltiples, servidor OSRM propio.
