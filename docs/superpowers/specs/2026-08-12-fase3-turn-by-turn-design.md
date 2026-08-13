# Fase 3 — Navegación turn-by-turn

Fecha: 2026-08-12. Depende de las Fases 1 (MapLibre) y 2 (búsqueda), ambas completas.

## Restricciones verificadas

Todo lo de abajo salió de un pase de investigación con verificación adversarial contra POMs y AAR reales de Maven Central, el código fuente de Ferrostar 0.53.0 y llamadas HTTP en vivo. Lo que no se pudo verificar está marcado.

### Ferrostar exige subir el toolchain

| Requisito | Evidencia | Estado |
|---|---|---|
| `compileSdk 36` | `minCompileSdk=36` en la metadata de **todos** los AAR desde 0.48.1 | **Hecho** (AGP 8.10.1) |
| Core library desugaring | declarado en la metadata del AAR; `desugar_jdk_libs:2.1.5` | **Hecho** |
| Kotlin 2.3.x | **Refutado**: la metadata 2.3.0 de Ferrostar la lee un compilador 2.2.21 | No hace falta |
| AGP ≥ 8.10 | 8.9.x tope en compileSdk 35 | **Hecho** |

### Solo `core`, sin `ui-compose` ni `ui-maplibre`

Dos razones concretas, no de gusto:

1. **`ui-maplibre` colisiona con nuestro mapa.** Arrastra `maplibre-compose-android:0.13.0` → `org.maplibre.gl:android-sdk:13.0.2`, la variante **Vulkan**. El proyecto usa `android-sdk-opengl:13.4.1`. Se verificó descomprimiendo ambos AAR: mismos paquetes y mismas clases (`org/maplibre/android/maps/MapView.class`, `NativeMapView`, …). Gradle los ve como módulos distintos → clases duplicadas y `.so` duplicados.
2. **`ui-compose` arrastra Compose BOM 2026.02.01.** El proyecto está en 2025.05.00.

Como la app ya tiene su propio mapa y su propia voz, la UI de navegación se dibuja con lo propio y de Ferrostar se toma solo el motor: seguimiento de ruta, snapping, progreso y detección de desvío. Aun así `core` fuerza OkHttp 5.3.2, lifecycle 2.10.0 y coroutines 1.11.0 por resolución "gana el mayor".

### El backend de rutas es el problema abierto

| Opción | Instrucciones de voz | Costo | Veredicto |
|---|---|---|---|
| OSRM público (el que ya usa la app) | **No** — `voiceInstructions` y `bannerInstructions` son extensiones de Mapbox, verificado contra el servidor real | Gratis | Sirve para maniobras, no para voz |
| FOSSGIS Valhalla | **Sí**, en español, con `costing: motorcycle` | Gratis | **Solo desarrollo**: su política no admite apps distribuidas, y hay tope de 1 req/usuario/s |
| Stadia Maps | Sí | **Confirmado que no es gratis** para routing | Descartado |
| Valhalla propio | Sí | Infra propia | Destino final (Fase 4/5) |

La app le pide hoy al OSRM público `overview=full&geometries=polyline` y ya recibe `steps` con `maneuver` (`type`, `modifier`, `bearing_*`, `location`) y el nombre de la vía. Alcanza para navegar; lo que falta es el texto hablado, y ese lo genera la app.

### La precisión de polyline es una trampa real

OSRM devuelve precisión **5** con `geometries=polyline` y **6** con `polyline6`. Los adaptadores de Ferrostar para proveedores conocidos usan 6. El verificador reprodujo el error decodificando geometría de precisión 5 como 6: las coordenadas quedan desplazadas por un factor de 10. **Toda construcción de ruta debe pasar la precisión que corresponde al parámetro que se pidió**, y hay un test que lo fija.

## Lo entregado en esta sesión

Módulo nuevo `:core:navigation` con Ferrostar `core:0.53.0` resolviendo y compilando, más la pieza que hacía falta pase lo que pase:

**`ManeuverSpeech`** — puro y determinístico, 16 tests. Convierte `(type, modifier, nombre de vía, distancia)` en la frase en español que va a hablar `AlertsEngine`:

- `"En 250 metros, gire a la izquierda"` — los metros se redondean a 50, porque "en 237 metros" no es una instrucción útil.
- `"Ahora, haga un giro en U"` — por debajo de 30 m ya no tiene sentido anunciar distancia.
- `"En 1,2 kilómetros, gire a la derecha"` — coma decimal a propósito: lo lee un TTS en español.
- Cubre glorietas, bifurcaciones, rampas, fin de vía, incorporaciones, y un tipo desconocido cae a "continúe" en vez de quedarse mudo.

La voz sale por `AlertsEngine`, no por el TTS de Ferrostar: ya está resuelto que llegue al intercomunicador del casco por el stream de media, y la lección del falso positivo de caída fue que una alerta que no se oye rodando es una alerta que no existe. No se introduce un segundo motor de voz con otras reglas de ruteo de audio.

### Qué hace Ferrostar de verdad con el OSRM público

Medido en el dispositivo, contra la respuesta real guardada en `core/navigation/src/androidTest/assets/`:

| Pregunta | Respuesta medida |
|---|---|
| ¿Parsea la respuesta completa? | **No** — `createRouteFromOsrm` la rechaza con `missing field duration`. La vía que sirve es `createRouteFromOsrmRoute` con `routes[0]` y los waypoints como objetos. |
| ¿Ve los mismos pasos que nuestro parser? | **Sí** — mismo conteo, mismo orden, mismos nombres de vía y distancias. Eso habilita cruzarlos por índice, y hay un test que lo fija. |
| ¿Puede decir qué maniobra es? | **No** — su texto de instrucción es literalmente `"TODO: OSRM instruction synthesis"`. |

De ahí sale el reparto definitivo: Ferrostar hace lo difícil de hacer bien —enganche a la ruta, avance de paso, desvío y progreso— y las maniobras salen de nuestro parser.

## Fase 3 completa

1. **Rutas con pasos** — `steps=true`, `OsrmRouteParser` puro, precisión de polyline atada al parámetro pedido.
2. **Motor** — `NavigationSession` sobre Ferrostar; `StepCursor` aparte y puro para la aritmética de índices.
3. **Voz** — `ManeuverAnnouncer` decide cuándo, `ManeuverSpeech` decide qué, `AlertsEngine` lo dice.
4. **UI** — banner de maniobra arriba, distancia/tiempo/ETA al pie.
5. **Continuidad** — `NavigationController` es singleton y se alimenta del GPS del servicio en primer plano, así que la guía sigue con la pantalla apagada.

Bug encontrado por el test de recorrido simulado: con la misma condición de avance para el último paso que para los demás, **la llegada nunca se dispara**. El paso final no se puede dar por cumplido "saliendo" de él porque no hay siguiente. La condición de llegada es ahora por distancia al final del paso.

## Deuda anotada

- La navegación necesita un viaje activo, porque el GPS lo entrega el servicio en primer plano. Cuando no lo hay, la app lo dice en vez de quedarse muda; falta el modo "solo navegar" que levante el servicio por su cuenta.
- El chip de ruta se queda en `"Sin ruta — ¿hay internet?"` indefinidamente cuando OSRM falla.
- No hay rerouteo automático: al salirse de la ruta se avisa, pero la ruta no se recalcula sola.
- Sigue pendiente decidir el backend definitivo: seguir con OSRM + instrucciones propias, o self-hostear Valhalla (Fase 4/5).
