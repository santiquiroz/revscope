package com.revscope.feature.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.captureRoboImage
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeTheme
import com.revscope.core.designsystem.SelectorVehiculo
import com.revscope.core.designsystem.VehiculoEnEncabezado
import com.revscope.core.designsystem.rememberUnitFormatter
import com.revscope.core.obd.cameras.SpeedCameraAlerter
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captura en inglés de la pestaña Mapa para el README (docs/screenshots/en/map.png).
 *
 * MapLibre dibuja con OpenGL nativo y no corre en Robolectric, así que el fondo (calles, ruta viva
 * en #E8FF00 y radares con su anillo en #FF1744, los colores de LiveMapLayers) lo pinta este test
 * en un Canvas. Encima van los overlays reales de LiveMapScreen en la misma disposición: atribución,
 * selector de vehículo, búsqueda y banner del radar al que se acerca; la velocidad y la columna de
 * botones replican los composables privados de la pantalla con sus mismos colores.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CapturasReadmeMapTest {

    private val selector = VehiculoEnEncabezado(
        "Benelli TNT 150i",
        esMoto = true,
        estadoEnlace = "Android-Vlink",
        colorEstado = RevScopeColors.Success,
    )

    private val radar = SpeedCameraAlerter.ApproachingCamera(
        osmId = 4_211_907_311,
        latitude = 6.2442,
        longitude = -75.5812,
        distanceM = 420,
        maxSpeedKmh = 60,
    )

    @Test
    fun map() {
        RuntimeEnvironment.setQualifiers("en-w412dp-h915dp-xxhdpi")
        RuntimeEnvironment.setFontScale(1.0f)
        captureRoboImage(filePath = "../../docs/screenshots/en/map.png") {
            RevScopeTheme { PantallaMapa() }
        }
    }

    @Composable
    private fun PantallaMapa() {
        Box(Modifier.fillMaxSize().background(Color(0xFF15161E))) {
            Canvas(Modifier.fillMaxSize()) { fondoMapa() }
            Text(
                stringResource(R.string.map_osm_attribution),
                color = Color(0xFF6B7089),
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            )
            Box(Modifier.align(Alignment.TopCenter).padding(top = 20.dp, start = 12.dp, end = 12.dp)) {
                SelectorVehiculo(selector, onClick = {})
            }
            SearchOverlay(
                query = "",
                results = emptyList(),
                searching = false,
                savedPlaces = emptyList(),
                onQueryChange = {},
                onClear = {},
                onSelect = {},
                onSelectSaved = {},
                onSaveFavorite = {},
                onRemoveSaved = {},
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 116.dp, start = 12.dp, end = 12.dp),
            )
            SecondaryBannerContent(
                banner = SecondaryBanner.Radar(radar),
                onDismissMapCorrupted = {},
                onDismissNavError = {},
                onAcceptSharedDest = {},
                onDismissSharedDest = {},
                // BAJO_SELECTOR (76 dp), igual que el Column de banners de LiveMapScreen.
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 76.dp, start = 12.dp, end = 12.dp),
            )
            Surface(color = Color(0xCC12121A), modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                Text(
                    rememberUnitFormatter().formatSpeed(54.0),
                    color = Color(0xFFE8FF00),
                    fontSize = 28.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            ColumnaBotones(Modifier.align(Alignment.BottomEnd).padding(16.dp))
        }
    }

    @Composable
    private fun ColumnaBotones(modifier: Modifier) {
        val activo = Color(0xFFE8FF00)
        val inactivo = Color(0xFF1C1C28)
        val iconoActivo = Color(0xFF0A0A0F)
        val iconoInactivo = Color(0xFFF0F0F8)
        Column(modifier, horizontalAlignment = Alignment.End) {
            SmallFloatingActionButton(onClick = {}, containerColor = activo) {
                Icon(Icons.Default.DarkMode, stringResource(R.string.map_night_mode_on), tint = iconoActivo)
            }
            Spacer(Modifier.height(8.dp))
            SmallFloatingActionButton(onClick = {}, containerColor = inactivo) {
                Icon(Icons.Default.Explore, stringResource(R.string.map_heading_up), tint = iconoInactivo)
            }
            Spacer(Modifier.height(8.dp))
            SmallFloatingActionButton(onClick = {}, containerColor = activo) {
                Icon(Icons.Default.MyLocation, stringResource(R.string.map_follow_me), tint = iconoActivo)
            }
            Spacer(Modifier.height(12.dp))
            FloatingActionButton(onClick = {}, containerColor = inactivo) {
                Icon(Icons.Default.Group, stringResource(R.string.map_group_ride), tint = iconoInactivo)
            }
            Spacer(Modifier.height(12.dp))
            FloatingActionButton(onClick = {}, containerColor = inactivo) {
                Icon(Icons.Default.Navigation, stringResource(R.string.map_open_in_maps), tint = iconoInactivo)
            }
        }
    }
}

// Coordenadas en fracciones del ancho/alto de la pantalla.
private val CALLES = listOf(
    listOf(0.00f to 0.30f, 1.00f to 0.36f),
    listOf(0.00f to 0.62f, 1.00f to 0.55f),
    listOf(0.00f to 0.86f, 1.00f to 0.80f),
    listOf(0.18f to 0.00f, 0.26f to 1.00f),
    listOf(0.55f to 0.00f, 0.48f to 1.00f),
    listOf(0.84f to 0.00f, 0.90f to 1.00f),
    listOf(0.00f to 0.12f, 0.62f to 0.48f, 1.00f to 0.70f),
)

private val RUTA_VIVA = listOf(
    0.22f to 0.98f, 0.24f to 0.86f, 0.25f to 0.74f, 0.31f to 0.68f, 0.40f to 0.64f,
    0.50f to 0.60f, 0.54f to 0.52f, 0.56f to 0.44f, 0.62f to 0.40f, 0.70f to 0.38f,
)

private val RADARES = listOf(0.74f to 0.37f, 0.86f to 0.62f, 0.24f to 0.33f)

private fun DrawScope.punto(p: Pair<Float, Float>) = Offset(p.first * size.width, p.second * size.height)

private fun DrawScope.linea(puntos: List<Pair<Float, Float>>, color: Color, ancho: Float) {
    val path = Path().apply {
        moveTo(punto(puntos.first()).x, punto(puntos.first()).y)
        puntos.drop(1).forEach { lineTo(punto(it).x, punto(it).y) }
    }
    drawPath(path, color, style = Stroke(width = ancho, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.fondoMapa() {
    CALLES.forEach { linea(it, Color(0xFF262838), 9.dp.toPx()) }
    CALLES.forEach { linea(it, Color(0xFF30334A), 2.dp.toPx()) }
    linea(RUTA_VIVA, Color(0xFFE8FF00), 6.dp.toPx())
    RADARES.forEachIndexed { i, radar ->
        val objetivo = i == 0
        val centro = punto(radar)
        val radio = 54.dp.toPx()
        drawCircle(Color(0xFFFF1744).copy(alpha = if (objetivo) 0.17f else 0.08f), radio, centro)
        drawCircle(
            Color(if (objetivo) 0xFFFF1744 else 0xFFFF5252).copy(alpha = if (objetivo) 1f else 0.4f),
            radio,
            centro,
            style = Stroke(width = (if (objetivo) 3f else 1.5f).dp.toPx()),
        )
        drawCircle(Color(0xFFFF1744), 9.dp.toPx(), centro)
        drawCircle(Color.White, 3.5.dp.toPx(), centro)
    }
    val puck = punto(RUTA_VIVA.last())
    drawCircle(Color(0xFF00E5FF), 11.dp.toPx(), puck)
    drawCircle(Color.White, 11.dp.toPx(), puck, style = Stroke(width = 2.5.dp.toPx()))
}
