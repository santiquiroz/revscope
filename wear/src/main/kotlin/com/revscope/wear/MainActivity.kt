package com.revscope.wear

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText

private val AccentColor = Color(0xFFE8FF00)
private val MutedColor = Color(0xFF6B7089)

/**
 * RevScope for Wear OS: streams the rider's heart rate to the phone, where it is
 * recorded alongside OBD/GPS/IMU telemetry and shown in trip reports per lap.
 */
class MainActivity : ComponentActivity() {

    private lateinit var streamer: HrStreamer

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) streamer.start()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        streamer = HrStreamer(applicationContext)

        setContent {
            MaterialTheme {
                WearApp(
                    streamer = streamer,
                    onToggle = {
                        if (streamer.isStreaming.value) {
                            streamer.stop()
                        } else {
                            permissionLauncher.launch(Manifest.permission.BODY_SENSORS)
                        }
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        streamer.stop()
        super.onDestroy()
    }
}

@Composable
private fun WearApp(streamer: HrStreamer, onToggle: () -> Unit) {
    val bpm by streamer.latestBpm.collectAsState()
    val streaming by streamer.isStreaming.collectAsState()
    val sent by streamer.sentCount.collectAsState()

    Scaffold(timeText = { TimeText() }) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = bpm?.let { "${it.toInt()}" } ?: "—",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = if (streaming) AccentColor else MutedColor,
            )
            Text("♥ bpm", fontSize = 12.sp, color = MutedColor)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onToggle,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (streaming) MutedColor else AccentColor,
                ),
            ) {
                Text(
                    if (streaming) "■" else "▶",
                    color = Color.Black,
                    fontSize = 18.sp,
                )
            }
            if (streaming) {
                Spacer(Modifier.height(4.dp))
                Text("$sent enviados", fontSize = 10.sp, color = MutedColor)
            }
        }
    }
}
