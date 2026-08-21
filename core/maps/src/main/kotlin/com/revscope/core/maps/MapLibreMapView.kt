package com.revscope.core.maps

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * `MapView` de MapLibre con la cadena de ciclo de vida completa.
 *
 * osmdroid se conformaba con `onDetach()`. MapLibre exige onCreate/onStart/onResume/
 * onPause/onStop/onDestroy: hacerlo mal no falla en el emulador, falla al volver de la
 * pantalla apagada — el caso de uso real en la moto.
 *
 * [onStyleInstalled] corre en la carga inicial Y en cada cambio de [styleJson]. El
 * consumidor DEBE reinstalar ahí sus fuentes y capas: al recargar el estilo las viejas
 * quedan `detached` y `setGeoJson()` retorna en silencio, sin excepción ni log.
 *
 * [onDidFailLoadingMap] expone `MapView.OnDidFailLoadingMapListener` (único callback de fallo
 * de carga que ofrece el SDK — cubre estilo mal formado, fuente inválida, etc.). Registrado una
 * sola vez por [mapView], no por cambio de estilo: el caller decide qué hacer con cada mensaje
 * de error a través de un lambda siempre actualizado ([rememberUpdatedState]).
 *
 * [onDidFailLoadingMap] recibe también [styleTag] — el tag que tenía [styleJson] EN EL MOMENTO
 * en que este composable llamó a `setStyle` con él, no el que el caller tiene "ahora" (fix W1
 * CRITICAL). El listener del SDK no dice qué intento de carga falló, y `rememberUpdatedState`
 * siempre reenvía a la lambda MÁS RECIENTE del caller — sin esta correlación, un fallo tardío de
 * un estilo viejo (p. ej. el tier remoto, mientras su red compite con una descarga en curso) se
 * clasifica con el estado YA ACTUALIZADO del caller (p. ej. tier local, si esa descarga terminó
 * mientras tanto), no con el estilo que realmente falló. [appliedStyleTag] se pisa en el MISMO
 * punto que [appliedStyle] (solo cuando `setStyle` se llama de verdad), así que durante la
 * ventana entre "el caller ya recompuso con un tier nuevo" y "este composable todavía no volvió
 * a llamar `setStyle`", sigue reportando el tag del estilo REALMENTE en vuelo.
 */
@Composable
fun MapLibreMapView(
    modifier: Modifier = Modifier,
    styleJson: String,
    styleTag: String? = null,
    onDidFailLoadingMap: ((error: String, styleTag: String?) -> Unit)? = null,
    onStyleInstalled: (MapLibreMap, Style) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnStyleInstalled by rememberUpdatedState(onStyleInstalled)
    val currentOnDidFailLoadingMap by rememberUpdatedState(onDidFailLoadingMap)

    // El bloque `update` corre en CADA recomposición. Sin este centinela se llamaría
    // setStyle todo el tiempo, y cada recarga deja detached las fuentes agregadas a mano.
    val appliedStyle = remember { mutableStateOf<String?>(null) }
    // Tag correlacionado con appliedStyle — ver el fix W1 CRITICAL documentado arriba.
    val appliedStyleTag = remember { mutableStateOf<String?>(null) }

    // getInstance DEBE correr antes de construir el MapView, o el constructor lanza
    // MapLibreConfigurationException.
    val mapView = remember {
        MapLibre.getInstance(context)
        // textureMode: por defecto MapLibre renderiza en un GLSurfaceView, que vive en una
        // capa aparte del árbol de vistas. Al navegar a otra pestaña, Compose destruye el
        // composable pero la superficie tarda en liberarse y el mapa se queda unos segundos
        // encima de la pantalla nueva. Con TextureView el mapa es una vista común y
        // desaparece con ella. Cuesta algo de rendimiento y batería; el artefacto visual
        // cuesta más. Requiere el backend OpenGL, que es el que usa este proyecto.
        val options = MapLibreMapOptions.createFromAttributes(context).textureMode(true)
        MapView(context, options).apply { onCreate(Bundle()) }
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

    DisposableEffect(mapView) {
        val listener = MapView.OnDidFailLoadingMapListener { error ->
            currentOnDidFailLoadingMap?.invoke(error, appliedStyleTag.value)
        }
        mapView.addOnDidFailLoadingMapListener(listener)
        onDispose { mapView.removeOnDidFailLoadingMapListener(listener) }
    }

    AndroidView(
        modifier = modifier,
        factory = { mapView },
        update = { view ->
            if (appliedStyle.value != styleJson) {
                appliedStyle.value = styleJson
                appliedStyleTag.value = styleTag
                view.getMapAsync { map ->
                    // MapLibre dibuja su logo y un botón de info abajo a la izquierda, justo
                    // donde va el badge de velocidad. La app ya muestra su propia atribución
                    // de OpenStreetMap, que es la que la licencia exige.
                    map.uiSettings.isLogoEnabled = false
                    map.uiSettings.isAttributionEnabled = false
                    map.setStyle(Style.Builder().fromJson(styleJson)) { style ->
                        currentOnStyleInstalled(map, style)
                    }
                }
            }
        },
    )
}
