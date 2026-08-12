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
    // setStyle todo el tiempo, y cada recarga deja detached las fuentes agregadas a mano.
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
