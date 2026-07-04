package com.revscope.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

private val BgColor = Color(0xFF0A0A0F)
private val SurfaceColor = Color(0xFF12121A)
private val SurfaceHighColor = Color(0xFF1C1C28)
private val AccentColor = Color(0xFFE8FF00)
private val TextPrimaryColor = Color(0xFFF0F0F8)
private val TextMutedColor = Color(0xFF6B7089)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToScanner: () -> Unit = {},
    onNavigateToGearAnalyzer: () -> Unit = {},
    onNavigateToVehicleProfiles: () -> Unit = {},
    vm: SettingsViewModel = hiltViewModel(),
) {
    val apiKey by vm.apiKey.collectAsState()
    val customPidsJson by vm.customPidsJson.collectAsState()
    val saveResult by vm.lastSaveResult.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(saveResult) {
        saveResult?.let {
            snackbarHostState.showSnackbar(it.message)
            vm.dismissSaveResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes", color = TextPrimaryColor, fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceColor),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = BgColor,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Herramientas")
            NavRow("Escáner Modo 22 (descubrir PIDs del fabricante)", onNavigateToScanner)
            NavRow("Analizador de marchas", onNavigateToGearAnalyzer)
            NavRow("Perfiles de vehículo", onNavigateToVehicleProfiles)

            Spacer(Modifier.height(8.dp))
            SectionTitle("IA — Explicación de códigos DTC")
            Text(
                "API key de Anthropic (opcional). Sin ella, los DTC se muestran sin explicación de IA.",
                color = TextMutedColor,
                fontSize = 12.sp,
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = vm::updateApiKey,
                label = { Text("Claude API key", fontSize = 12.sp) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = settingsFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = vm::saveApiKey,
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
            ) { Text("Guardar API key", color = BgColor) }

            Spacer(Modifier.height(8.dp))
            SectionTitle("PIDs personalizados")
            Text(
                "Definiciones extra en JSON (mismo esquema que pids_mode01.json). " +
                    "Para parámetros del fabricante, p. ej. modo de manejo vía Modo 22.",
                color = TextMutedColor,
                fontSize = 12.sp,
            )
            OutlinedTextField(
                value = customPidsJson,
                onValueChange = vm::updateCustomPidsJson,
                label = { Text("JSON de PIDs custom", fontSize = 12.sp) },
                minLines = 4,
                maxLines = 10,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                ),
                colors = settingsFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = vm::saveCustomPids,
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
            ) { Text("Validar y aplicar", color = BgColor) }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = AccentColor,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun NavRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(label, color = TextPrimaryColor, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text("›", color = TextMutedColor, fontSize = 16.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun settingsFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimaryColor,
    unfocusedTextColor = TextPrimaryColor,
    focusedBorderColor = AccentColor,
    unfocusedBorderColor = SurfaceHighColor,
    focusedLabelColor = AccentColor,
    unfocusedLabelColor = TextMutedColor,
    cursorColor = AccentColor,
)
