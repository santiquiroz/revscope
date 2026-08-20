package com.revscope.feature.gear

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.revscope.core.intelligence.IntelligenceOrchestrator
import com.revscope.core.intelligence.gear.AdaptiveGearLearner
import com.revscope.core.intelligence.gear.GearCluster
import javax.inject.Inject

private val BgColor = Color(0xFF0A0A0F)
private val SurfaceColor = Color(0xFF12121A)
private val SurfaceHighColor = Color(0xFF1C1C28)
private val AccentColor = Color(0xFFE8FF00)
private val SuccessColor = Color(0xFF00E676)
private val WarningColor = Color(0xFFFF8C00)
private val TextPrimaryColor = Color(0xFFF0F0F8)
private val TextMutedColor = Color(0xFF6B7089)

/** Thirds-based gear color logic matching GearDisplay */
private fun gearColorByThirds(gear: Int, gearCount: Int): Color = when {
    gear <= 0 -> TextMutedColor
    gear <= gearCount / 3 -> SuccessColor
    gear <= gearCount * 2 / 3 -> AccentColor
    gear <= gearCount -> WarningColor
    else -> TextMutedColor
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GearAnalyzerScreen(
    orchestrator: IntelligenceOrchestrator = hiltViewModel<GearAnalyzerViewModel>().orchestrator,
) {
    val gearTable by orchestrator.gearLearner.gearTable.collectAsState()
    val isCalibrated = gearTable.all { it.observationCount >= AdaptiveGearLearner.MIN_OBSERVATIONS_PER_GEAR }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor),
    ) {
        TopAppBar(
            title = { Text("Marchas", color = TextPrimaryColor, fontWeight = FontWeight.SemiBold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceColor),
        )

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            // Calibration status banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceHighColor, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isCalibrated) "Tabla calibrada ✓" else "Calibrando…",
                    color = if (isCalibrated) SuccessColor else AccentColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
                val totalObs = gearTable.sumOf { it.observationCount }
                val needed = gearTable.size * AdaptiveGearLearner.MIN_OBSERVATIONS_PER_GEAR
                Text(
                    text = "$totalObs / $needed obs",
                    color = TextMutedColor,
                    fontSize = 12.sp,
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "Tabla de ratios velocidad/RPM",
                color = TextMutedColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(8.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(gearTable) { cluster ->
                    GearClusterRow(cluster = cluster, gearColor = gearColorByThirds(cluster.gear, gearTable.size))
                }
            }
        }
    }
}

@Composable
private fun GearClusterRow(cluster: GearCluster, gearColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceColor, RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Gear number badge
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(SurfaceHighColor, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = cluster.gear.toString(),
                color = gearColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Ratio: %.2f".format(cluster.centerRatio),
                    color = TextPrimaryColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "${cluster.observationCount} obs",
                    color = TextMutedColor,
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.height(4.dp))
            val progress = (cluster.observationCount / AdaptiveGearLearner.MIN_OBSERVATIONS_PER_GEAR.toFloat()).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = gearColor,
                trackColor = SurfaceHighColor,
            )
        }
    }
}
