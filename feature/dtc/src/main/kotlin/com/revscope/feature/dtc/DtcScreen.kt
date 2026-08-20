package com.revscope.feature.dtc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.revscope.core.obd.viewmodel.ConnectionViewModel

private val BgColor = Color(0xFF0A0A0F)
private val SurfaceColor = Color(0xFF12121A)
private val SurfaceHighColor = Color(0xFF1C1C28)
private val AccentColor = Color(0xFFE8FF00)
private val SuccessColor = Color(0xFF00E676)
private val DangerColor = Color(0xFFFF3040)
private val TextPrimaryColor = Color(0xFFF0F0F8)
private val TextMutedColor = Color(0xFF6B7089)

@Composable
private fun FreezeFrameCard(items: List<FreezeFrameItem>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceHighColor, RoundedCornerShape(8.dp))
            .padding(14.dp),
    ) {
        Text(
            "📸 Freeze frame — el motor al momento de la falla",
            color = AccentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        items.forEach { item ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(item.label, color = TextMutedColor, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(item.value, color = TextPrimaryColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DtcScreen(
    connectionVm: ConnectionViewModel = hiltViewModel(),
    onOpenAiValue: () -> Unit = {},
    vm: DtcViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val freezeFrame by vm.freezeFrame.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor),
    ) {
        TopAppBar(
            title = { Text("Códigos DTC", color = TextPrimaryColor, fontWeight = FontWeight.SemiBold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceColor),
        )

        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { vm.readDtcCodes(connectionVm) },
                    enabled = state !is DtcUiState.Reading && state !is DtcUiState.Clearing,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Leer DTCs", color = BgColor, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = { vm.clearDtcCodes(connectionVm) },
                    enabled = state is DtcUiState.HasCodes,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Borrar DTCs", color = DangerColor)
                }
            }

            Spacer(Modifier.height(16.dp))

            when (val s = state) {
                DtcUiState.Idle -> IdleContent()
                DtcUiState.Reading -> LoadingContent("Leyendo códigos de falla…")
                DtcUiState.Clearing -> LoadingContent("Borrando códigos…")
                DtcUiState.Cleared -> StatusContent("Códigos borrados ✓", AccentColor)
                is DtcUiState.HasCodes -> {
                    if (s.codes.isEmpty()) {
                        StatusContent("Sin códigos de falla activos ✓", SuccessColor)
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (freezeFrame.isNotEmpty()) {
                                item(key = "freeze_frame") { FreezeFrameCard(freezeFrame) }
                            }
                            items(s.codes) { item -> DtcItem(item, onOpenAiValue) }
                        }
                    }
                }
                is DtcUiState.Error -> {
                    StatusContent(s.message, DangerColor)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { vm.reset() },
                        colors = ButtonDefaults.buttonColors(containerColor = SurfaceHighColor),
                    ) {
                        Text("Reintentar", color = TextPrimaryColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun IdleContent() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Presiona «Leer DTCs» para conectarte al ECU",
            color = TextMutedColor,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun LoadingContent(label: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = AccentColor)
        Spacer(Modifier.height(12.dp))
        Text(label, color = TextMutedColor, fontSize = 13.sp)
    }
}

@Composable
private fun StatusContent(message: String, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, color = color, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DtcItem(item: DtcCodeUi, onOpenAiValue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceColor, RoundedCornerShape(8.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = DangerColor,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = item.dtc.code,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = DangerColor,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = item.dtc.mode.javaClass.simpleName,
                fontSize = 11.sp,
                color = TextMutedColor,
            )
        }
        Spacer(Modifier.height(8.dp))
        when {
            item.isLoadingExplanation -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = AccentColor,
                    )
                    Text("Consultando explicación…", color = TextMutedColor, fontSize = 12.sp)
                }
            }
            item.explanation != null -> {
                Text(item.explanation, color = TextPrimaryColor, fontSize = 13.sp, lineHeight = 20.sp)
            }
            else -> {
                Text("Sin explicación disponible.", color = TextMutedColor, fontSize = 12.sp)
                TextButton(onClick = onOpenAiValue, contentPadding = PaddingValues(0.dp)) {
                    Text("Configurar IA", color = AccentColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
