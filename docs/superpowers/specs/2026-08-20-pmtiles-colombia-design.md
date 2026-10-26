# Mapa offline Colombia (PMTiles) + look oscuro premium — sub-proyecto G (2026-08-20)

Cierre del backlog del mapa: extracto vectorial de Colombia hospedado y descargable in-app, estilo cartográfico protomaps completo (light/dark) y modo oscuro de verdad. Autorización total del usuario ("mejorá la app y el mapa… toda mi autorización"); el look "oscuro premium" quedó prometido en la spec de F.

## Base existente (explorada)

- `MapStyleProvider` ya resuelve tiles en cascada: `.pmtiles` local → revscope-server → ráster OSM → fondo vacío. El tier 1 (`pmtiles://file://…`) ya existe pero nunca se ejerció: el archivo no se generó jamás.
- `vectorStyle()` es un placeholder: fondo + source, CERO capas cartográficas. Con un `.pmtiles` real hoy se vería un lienzo vacío.
- El modo oscuro ráster es una aproximación (brightness/saturation); el comentario del código promete "el estilo oscuro de verdad llega con el vectorial".
- Glyphs URL ya presente en los 3 estilos (F-T3).

## Diseño

### G1. Generación y hosting del extracto (fuera de la app)

- `pmtiles extract https://build.protomaps.com/20260819.pmtiles colombia.pmtiles --bbox=-79.1,-4.3,-66.8,13.6` (go-pmtiles 1.31.2). Bbox cubre Colombia continental + San Andrés queda fuera (aceptado v1; isla ~(-81.7,12.5) — anotar como known gap en release notes).
- Si el archivo supera 1.9 GB (límite asset GitHub 2 GB): re-extraer con `--maxzoom=14` y documentar.
- Hosting: release de GitHub en el repo revscope, tag `tiles-v1`, asset `colombia.pmtiles` (prerelease, título "Tiles Colombia v1"). URL estable: `https://github.com/santiquiroz/revscope/releases/download/tiles-v1/colombia.pmtiles`.

### G2. Estilo cartográfico completo (light/dark)

- Capas generadas con `@protomaps/basemaps` (npm, función `layers("protomaps", theme, {lang:"es"})`) para temas `light` y `dark`, guardadas como assets del módulo core/maps: `assets/map-layers-light.json` y `assets/map-layers-dark.json` (solo el array de layers). Generación one-shot vía Node en build de desarrollo, archivos commiteados — la app NO depende de Node.
- `MapStyleProvider.vectorStyle` pasa a ensamblar: fondo + capas del asset según `dark` + (encima) las capas propias existentes que agrega LiveMapLayers en runtime (sin cambio: se agregan sobre el estilo cargado).
- `styleJson` gana el parámetro de acceso a assets (lambda `readAsset: (String) -> String` o los JSON ya leídos) — MapStyleProvider sigue puro/testeable sin Context.
- Labels en español donde el tile lo traiga (`name:es` fallback `name`), como hace protomaps con `lang`.
- Sprites de protomaps (iconos POI): `"sprite": "https://protomaps.github.io/basemaps-assets/sprites/v4/{theme}"` — remoto como los glyphs, degradación sin red aceptada (los POI pierden icono, calles siguen).

### G3. Descarga in-app (Ajustes → Mapa)

- Sección nueva "Mapa offline" en Ajustes → Mapa: estado (No descargado / Descargando X% / Descargado · N GB · fecha), botón Descargar/Cancelar/Borrar.
- Descarga con OkHttp streaming a `filesDir/maps/colombia.pmtiles.part` → rename atómico al terminar (nunca un .pmtiles truncado visible para MapLibre). Progreso por Content-Length; reanudación v1 = no (re-descarga completa; known gap, el server de GitHub soporta ranges — deuda anotada).
- Solo con WiFi por defecto (checkbox "permitir datos móviles"); aviso de tamaño antes de bajar.
- Al completar: el próximo LiveMap load usa tier 1 automáticamente (cascada existente). Toast "Mapa offline listo".
- Borrar: elimina el archivo, la cascada vuelve a server/ráster.

### G4. Modo oscuro real

- Con vectorial: `dark=true` usa las capas `map-layers-dark.json` — se elimina la aproximación ráster SOLO cuando hay vectorial (el tier ráster conserva su fake-dark actual como fallback).
- La selección light/dark sigue la fuente actual (auto nocturno de B + ajuste manual) — sin cambios de UX fuera del render.

## Errores

- Descarga interrumpida (app muerta, red caída): `.part` huérfano se borra al reintentar; nunca se promueve.
- Sin espacio: chequear `usableSpace` > tamaño*1.2 antes de arrancar; error claro.
- Archivo corrupto (magic bytes PMTiles inválidos al abrir): MapLibre falla el load → catch → borrar archivo + volver a cascada + hint "re-descargá el mapa".
- Sin red y sin archivo: cascada actual (fondo vacío con capas propias) — sin cambios.

## Fuera de alcance

Actualización periódica del extracto (tiles-v2 manual), San Andrés/Providencia, reanudación de descarga por ranges, extractos por-departamento, selector de región.

## Archivos

App: core/maps/MapStyleProvider.kt (+test), core/maps assets nuevos, servicio de descarga nuevo (core/maps o feature/settings), pantalla Ajustes → Mapa, LiveMapViewModel/Screen (wiring del File local — hoy pasa null). Fuera: scripts/gen-map-layers.mjs (generador one-shot), release tiles-v1.
