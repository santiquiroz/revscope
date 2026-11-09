package com.revscope.feature.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.captureRoboImage
import com.revscope.core.common.i18n.Region
import com.revscope.core.data.db.entities.SessionEntity
import com.revscope.core.designsystem.RevScopeColors
import com.revscope.core.designsystem.RevScopeTheme
import com.revscope.core.designsystem.RevScopeType
import com.revscope.core.designsystem.rememberUnitFormatter
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

/**
 * Captura en inglés del reporte de viaje para el README (docs/screenshots/en/trip-report.png).
 *
 * ReportContent es privado y depende de SessionDetailViewModel (ObdSessionManager, DAOs), así que
 * el test arma el reporte con las piezas internas/públicas del mismo archivo y módulo:
 * SpeedTempRow, FuelCostCard, SprintRow, TrackMap, FrictionCircle y ThrottleGScatter, en el orden
 * de ReportContent, con la barra superior (exportar CSV) de SessionDetailScreen. El viaje es el de
 * CapturasViajesTest.sessionItem con costo, eco-score y arrancadas.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CapturasReadmeTripReportTest {

    private val sesion = SessionEntity(
        vehicleProfileId = 1,
        startedAt = 1_790_000_000_000,
        endedAt = 1_790_000_000_000 + 47 * 60_000 + 12_000,
        adapterName = "Android-Vlink",
        maxRpm = 9948,
        maxSpeed = 112,
        distanceKm = 38.4f,
        best0to60Ms = 4_380,
        best0to100Ms = 9_120,
        fuelCostCop = 18_450.0,
        ecoScore = 74,
    )

    // Circuito cerrado alrededor de Medellín, con velocidades que suben y bajan por tramos.
    private val track: List<Pair<Double, Double>> = (0 until 240).map { i ->
        val t = i / 240.0 * 2 * Math.PI
        val r = 0.010 + 0.003 * sin(3 * t)
        6.2442 + r * sin(t) to -75.5812 + 1.4 * r * cos(t)
    }
    private val velocidades: List<Float> = track.indices.map { 35f + 55f * ((sin(it / 18.0) + 1) / 2).toFloat() }
    private val frenadas: List<Boolean> = track.indices.map { it % 40 in 36..38 }

    private val aleatorio = Random(30)
    private val friccion: List<Pair<Float, Float>> = (0 until 320).map {
        val angulo = aleatorio.nextDouble() * 2 * Math.PI
        val g = 0.15 + 0.55 * aleatorio.nextDouble()
        (g * cos(angulo)).toFloat() to (g * sin(angulo) * 0.8).toFloat()
    }
    private val aceleradorG: List<Pair<Float, Float>> = (0 until 160).map {
        val acelerador = aleatorio.nextFloat() * 100f
        acelerador to (acelerador / 100f * 0.45f - 0.05f + (aleatorio.nextFloat() - 0.5f) * 0.12f)
    }

    @Test
    fun tripReport() {
        RuntimeEnvironment.setQualifiers("en-w412dp-h1400dp-xxhdpi")
        RuntimeEnvironment.setFontScale(1.0f)
        captureRoboImage(filePath = "../../docs/screenshots/en/trip-report.png") {
            RevScopeTheme { Reporte() }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Reporte() {
        Column(Modifier.fillMaxSize().background(RevScopeColors.Background)) {
            TopAppBar(
                title = { Text(stringResource(R.string.trips_trip_report), color = RevScopeColors.TextPrimary, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = {}) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.trips_back), tint = RevScopeColors.TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.DirectionsCar, stringResource(R.string.trips_assign_vehicle), tint = RevScopeColors.Accent)
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Outlined.PhotoCamera, stringResource(R.string.trips_trip_image_share_chooser), tint = RevScopeColors.TextPrimary)
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Download, stringResource(R.string.trips_export), tint = RevScopeColors.Accent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = RevScopeColors.Surface),
            )
            Cuerpo()
        }
    }

    @Composable
    private fun Cuerpo() {
        val units = rememberUnitFormatter()
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Formato "dd MMM yyyy  HH:mm" de ReportContent, fijado a la hora de Bogotá.
            Text("21 Sep 2026  09:13", color = RevScopeColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("Benelli TNT 150i", color = RevScopeColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(sesion.adapterName, color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
            SpeedTempRow(sesion.maxSpeed, avgSpeedKmh = 48.8f, maxCoolantC = 97, units = units)
            FuelCostCard(sesion.fuelCostCop, Region.CO, Locale.US, Locale.US)
            SprintRow(sesion, units)
            Titulo(routeSummary(38.4, 112.0, units))
            TrackMap(
                track = track,
                speeds = velocidades,
                brakingMask = frenadas,
                modifier = Modifier.fillMaxWidth().height(200.dp),
            )
            Leyenda(stringResource(R.string.trips_route_racing))
            Titulo(stringResource(R.string.trips_friction_circle))
            FrictionCircle(points = friccion, modifier = Modifier.fillMaxWidth().height(260.dp))
            Leyenda(stringResource(R.string.trips_friction_legend))
            Titulo(stringResource(R.string.trips_throttle_g))
            ThrottleGScatter(points = aceleradorG, modifier = Modifier.fillMaxWidth().height(220.dp))
            Leyenda(stringResource(R.string.trips_throttle_g_legend))
        }
    }

    @Composable
    private fun Titulo(texto: String) {
        Text(texto, color = RevScopeColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }

    @Composable
    private fun Leyenda(texto: String) {
        Text(texto, color = RevScopeColors.TextSecondary, style = RevScopeType.bodySmall)
    }
}
