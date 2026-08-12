# Fase 2 — Búsqueda de direcciones

Fecha: 2026-08-12. Segunda de seis fases hacia navegación turn-by-turn propia. Depende de la Fase 1 (mapa sobre MapLibre), ya completa.

## Objetivo

Buscar una dirección o un lugar y fijarlo como destino. El cálculo de ruta y el chip de distancia/ETA ya existen desde antes: esta fase solo agrega **cómo llegar a un destino sin tener que long-pressear el punto exacto en el mapa**.

## Alcance

Dentro: campo de búsqueda en el mapa, resultados con nombre y contexto, tocar un resultado fija el destino.

Fuera: índice local con FTS (queda para la Fase 4, junto con el resto de la autonomía), historial de búsquedas, favoritos, resolución de destinos en lenguaje natural por IA (Fase 6).

## Proveedor: Photon público

`https://photon.komoot.io/api` — geocoder open source de Komoot sobre datos de OpenStreetMap, **sin API key**, pensado para autocompletar y tolerante a errores de tipeo.

Contrato verificado contra el servicio real, no de memoria:

- Devuelve un `FeatureCollection` de GeoJSON. Cada feature trae `geometry.coordinates` como `[lon, lat]` — **en ese orden**, no al revés.
- `properties` incluye, según el tipo de lugar: `name`, `street`, `housenumber`, `city`, `county`, `state`, `country`, `countrycode`, `postcode`, `osm_key`, `osm_value`, `type`. Ninguno está garantizado salvo `name` en la práctica, así que el parser trata todos como opcionales.

### El sesgo por ubicación no es opcional

Buscar `medellin` sin sesgo devuelve **Medellín de Filipinas** como primer resultado. Con `lat`/`lon` de la posición actual, los tres primeros son Medellín, Antioquia.

Para alguien en la moto buscando una calle de su ciudad, un resultado en otro continente no es un detalle de ranking: es la diferencia entre que la función sirva o no. **Toda consulta va sesgada** por la última posición conocida — el último punto de la ruta viva, o el centro inicial del mapa si no hay viaje.

Si no hay ninguna posición conocida, se consulta sin sesgo y los resultados muestran país, que es la única forma honesta de desambiguar.

## Arquitectura

Sigue el patrón que ya existe en `feature/map/routing/` para OSRM:

| Pieza | Responsabilidad |
|---|---|
| `search/PhotonParser.kt` | Puro: JSON → `List<PlaceResult>`. Sin red, sin Android. Con tests |
| `search/PhotonGeocoder.kt` | HTTP y armado de URL. Igual que `OsrmRouteFetcher`: `HttpURLConnection`, timeouts, `runCatching` |
| `LiveMapViewModel` | Estado de la búsqueda, debounce, sesgo por ubicación |
| `LiveMapScreen` | Campo de búsqueda y lista de resultados |

`PlaceResult(name, subtitle, lat, lon)`. El `subtitle` se arma con calle, ciudad, departamento y país, sin repetir el nombre y sin campos vacíos — un resultado que dice "Carrera 52 · Medellín, Antioquia, Colombia" es útil; uno que dice "Carrera 52 · Carrera 52" no.

## Comportamiento

- Se consulta con **debounce de 350 ms** desde la última tecla, y solo con 3 o más caracteres: Photon es un servicio público y gratuito, y una consulta por pulsación es abusar de él.
- Tocar un resultado llama al `setDestination` que ya existe, que dispara el cálculo de ruta OSRM y el chip de distancia/ETA. Cero código nuevo de routing.
- Si la búsqueda falla (sin red, servicio caído), la lista queda vacía con un mensaje: nunca un crash, nunca un spinner eterno.
- El campo se limpia y se cierra al elegir un resultado. El long-press en el mapa sigue funcionando igual.

## Testing

`PhotonParser` con JSON real capturado del servicio, cubriendo: respuesta vacía, feature sin `name`, feature con solo país, coordenadas en orden `[lon, lat]`, y armado del subtítulo sin duplicados. El cliente HTTP y la UI se verifican en el dispositivo.
