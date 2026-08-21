package com.revscope.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay

private val BgColor = Color(0xFF0A0A0F)
private val SurfaceColor = Color(0xFF12121A)
private val AccentColor = Color(0xFFE8FF00)
private val TextPrimaryColor = Color(0xFFF0F0F8)
private val TextMutedColor = Color(0xFF6B7089)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToVehicleProfiles: () -> Unit = {},
    onOpenAiValue: () -> Unit = {},
    onRerunOnboarding: () -> Unit = {},
    // Deep-link opcional (nav arg `expand`, ver Screen.Settings.withExpand): nombre de un
    // SettingsSectionId a expandir al abrir — ej. "mapa" desde el diálogo de descarga de mapa.
    initialExpandedSection: String? = null,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val saveResult by vm.lastSaveResult.collectAsState()
    val backupState by vm.backupState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(saveResult) {
        saveResult?.let {
            snackbarHostState.showSnackbar(it.message)
            vm.dismissSaveResult()
        }
    }

    LaunchedEffect(backupState) {
        if (backupState == SettingsViewModel.BackupState.RESTARTING_AFTER_IMPORT) {
            delay(900)
            restartApp(context)
        }
    }

    pendingImportUri?.let { uri ->
        BackupRestoreConfirmDialog(
            onConfirm = {
                pendingImportUri = null
                vm.importBackup(uri)
            },
            onDismiss = { pendingImportUri = null },
        )
    }

    val entries = buildSettingsEntries(
        vm = vm,
        onNavigateToVehicleProfiles = onNavigateToVehicleProfiles,
        onOpenAiValue = onOpenAiValue,
        onRerunOnboarding = onRerunOnboarding,
        snackbarHostState = snackbarHostState,
        onImportRequested = { uri -> pendingImportUri = uri },
    )

    val deepLinkSection = remember(initialExpandedSection) {
        settingsSectionFromDeepLinkKey(initialExpandedSection)
    }
    val sectionExpansion = SettingsSectionId.entries.associateWith { section ->
        rememberSaveable(key = section.name) {
            mutableStateOf(
                section == deepLinkSection ||
                    (section == SettingsSectionId.AVANZADO_DIAGNOSTICO && vm.guardAlarmActive.value),
            )
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Buscar en ajustes", fontSize = 12.sp) },
                singleLine = true,
                colors = settingsFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )

            val isSearching = SettingsSearch.isActive(searchQuery)
            val matchedIds = if (isSearching) {
                SettingsSearch.filter(searchQuery, entries.map { it.meta }).map { it.id }.toSet()
            } else {
                null
            }

            SettingsSectionId.entries.forEach { section ->
                val sectionEntries = entries.filter { it.meta.section == section }
                val visibleEntries = matchedIds?.let { ids -> sectionEntries.filter { it.meta.id in ids } }
                    ?: sectionEntries
                if (isSearching && visibleEntries.isEmpty()) return@forEach

                val expandedState = sectionExpansion.getValue(section)
                val expanded = isSearching || expandedState.value
                SettingsSectionHeader(
                    title = section.title,
                    itemCount = visibleEntries.size,
                    expanded = expanded,
                    onToggle = {
                        if (!isSearching) expandedState.value = !expandedState.value
                    },
                )
                AnimatedVisibility(visible = expanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        visibleEntries.forEach { entry -> entry.content() }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private data class SettingsEntry(
    val meta: SettingsEntryMeta,
    val content: @Composable () -> Unit,
)

private fun buildSettingsEntries(
    vm: SettingsViewModel,
    onNavigateToVehicleProfiles: () -> Unit,
    onOpenAiValue: () -> Unit,
    onRerunOnboarding: () -> Unit,
    snackbarHostState: SnackbarHostState,
    onImportRequested: (Uri) -> Unit,
): List<SettingsEntry> = listOf(
    SettingsEntry(
        SettingsEntryMeta(
            id = "vehiculo_activo",
            section = SettingsSectionId.VEHICULO_GARAGE,
            title = "Vehículo",
            subtitle = "Vehículo activo y arranque",
            keywords = listOf("vehiculo", "garage", "perfil activo", "auto", "moto", "carro"),
        ),
    ) { VehiculoActivoCard(vm, onNavigateToVehicleProfiles) },
    SettingsEntry(
        SettingsEntryMeta(
            id = "herramientas",
            section = SettingsSectionId.VEHICULO_GARAGE,
            title = "Herramientas",
            subtitle = "Perfiles de vehículo y pantalla encendida",
            keywords = listOf("perfiles", "pantalla", "screen", "herramientas"),
        ),
    ) { HerramientasCard(vm, onNavigateToVehicleProfiles) },
    SettingsEntry(
        SettingsEntryMeta(
            id = "combustible",
            section = SettingsSectionId.VEHICULO_GARAGE,
            title = "Combustible",
            subtitle = "Precios de gasolina, extra y diésel",
            keywords = listOf("gasolina", "combustible", "precio", "diesel", "acpm", "galon"),
        ),
    ) { CombustibleCard(vm) },
    SettingsEntry(
        SettingsEntryMeta(
            id = "alertas_audio",
            section = SettingsSectionId.RADARES_ALERTAS,
            title = "Alertas de audio y vibración",
            subtitle = "Voz, TTS y umbrales de temperatura/voltaje/RPM",
            keywords = listOf(
                "voz", "tts", "alerta", "temperatura", "voltaje", "rpm", "zona roja",
                "pico y placa", "atardecer", "huecos", "lluvia", "fatiga",
            ),
        ),
    ) { AlertasAudioCard(vm) },
    SettingsEntry(
        SettingsEntryMeta(
            id = "sonido_motor",
            section = SettingsSectionId.RADARES_ALERTAS,
            title = "Sonido de motor",
            subtitle = "Simulación de sonido de motor deportivo",
            keywords = listOf("sonido", "motor", "soundracer", "pack"),
        ),
    ) { SonidoMotorCard(vm) },
    SettingsEntry(
        SettingsEntryMeta(
            id = "radares_velocidad",
            section = SettingsSectionId.RADARES_ALERTAS,
            title = "Radares de velocidad",
            subtitle = "Descarga y radio de aviso de radares",
            keywords = listOf("radar", "camara", "cámara", "fotomulta", "velocidad", "ansv"),
        ),
    ) { RadaresVelocidadCard(vm) },
    SettingsEntry(
        SettingsEntryMeta(
            id = "pico_placa_personalizado",
            section = SettingsSectionId.RADARES_ALERTAS,
            title = "Pico y placa personalizado",
            subtitle = "Reglas propias por ciudad",
            keywords = listOf("pico y placa", "restriccion", "rotacion", "ciudad"),
        ),
    ) { PicoPlacaPersonalizadoCard(vm) },
    SettingsEntry(
        SettingsEntryMeta(
            id = "mapa",
            section = SettingsSectionId.MAPA,
            title = "Mapa",
            subtitle = "Descarga de mapas offline",
            keywords = listOf("mapa", "offline", "descarga"),
        ),
    ) { MapaCard(vm, snackbarHostState) },
    SettingsEntry(
        SettingsEntryMeta(
            id = "ia",
            section = SettingsSectionId.INTELIGENCIA_ARTIFICIAL,
            title = "Inteligencia artificial",
            subtitle = "Proveedor, API key y modelo",
            keywords = listOf("ia", "ai", "claude", "openai", "gemini", "nodo", "api key", "modelo"),
        ),
    ) { InteligenciaArtificialCard(vm, onOpenAiValue) },
    SettingsEntry(
        SettingsEntryMeta(
            id = "servidor_colaborativo",
            section = SettingsSectionId.SALA_SOCIAL,
            title = "Servidor colaborativo",
            subtitle = "Servidor RevScope para rodadas y fantasmas",
            keywords = listOf("servidor", "rodadas", "fantasmas", "huecos", "social"),
        ),
    ) { ServidorColaborativoCard(vm) },
    SettingsEntry(
        SettingsEntryMeta(
            id = "companero_viaje",
            section = SettingsSectionId.SALA_SOCIAL,
            title = "Compañero de viaje",
            subtitle = "Brief al llegar a una zona nueva",
            keywords = listOf("viaje", "brief", "peajes", "zona", "companero"),
        ),
    ) { CompaneroViajeCard(vm) },
    SettingsEntry(
        SettingsEntryMeta(
            id = "avisos_segundo_plano",
            section = SettingsSectionId.NOTIFICACIONES_SEGUNDO_PLANO,
            title = "Avisos en segundo plano",
            subtitle = "Alarmas exactas y optimización de batería",
            keywords = listOf("notificacion", "alarma", "bateria", "doze", "segundo plano"),
        ),
    ) { AvisosSegundoPlanoCard() },
    SettingsEntry(
        SettingsEntryMeta(
            id = "deteccion_caida",
            section = SettingsSectionId.AVANZADO_DIAGNOSTICO,
            title = "Detección de caída",
            subtitle = "Alarma y SMS de emergencia",
            keywords = listOf("caida", "choque", "accidente", "sms", "emergencia"),
        ),
    ) { DeteccionCaidaCard(vm) },
    SettingsEntry(
        SettingsEntryMeta(
            id = "modo_guardia",
            section = SettingsSectionId.AVANZADO_DIAGNOSTICO,
            title = "Modo guardia antirrobo",
            subtitle = "Alarma si el adaptador OBD despierta",
            keywords = listOf("guardia", "antirrobo", "robo", "alarma"),
        ),
    ) { ModoGuardiaCard(vm) },
    SettingsEntry(
        SettingsEntryMeta(
            id = "pids_personalizados",
            section = SettingsSectionId.AVANZADO_DIAGNOSTICO,
            title = "PIDs personalizados",
            subtitle = "JSON de PIDs custom (Modo 22)",
            keywords = listOf("pid", "modo 22", "json", "custom"),
        ),
    ) { CustomPidsCard(vm) },
    SettingsEntry(
        SettingsEntryMeta(
            id = "alertas_pid_personalizadas",
            section = SettingsSectionId.AVANZADO_DIAGNOSTICO,
            title = "Alertas personalizadas por PID",
            subtitle = "Umbrales por PID en JSON",
            keywords = listOf("alerta", "pid", "umbral", "tpms"),
        ),
    ) { CustomPidAlertsCard(vm) },
    SettingsEntry(
        SettingsEntryMeta(
            id = "copia_seguridad",
            section = SettingsSectionId.AVANZADO_DIAGNOSTICO,
            title = "Copia de seguridad",
            subtitle = "Exportar e importar backup",
            keywords = listOf("backup", "copia", "exportar", "importar", "restaurar"),
        ),
    ) { CopiaSeguridadCard(vm, onImportRequested) },
    SettingsEntry(
        SettingsEntryMeta(
            id = "servidor_mcp",
            section = SettingsSectionId.AVANZADO_DIAGNOSTICO,
            title = "Servidor MCP (red local)",
            subtitle = "Expone el vehículo a asistentes de IA en WiFi",
            keywords = listOf("mcp", "claude desktop", "lm studio", "token"),
        ),
    ) { ServidorMcpCard(vm) },
    SettingsEntry(
        SettingsEntryMeta(
            id = "acerca_de",
            section = SettingsSectionId.AVANZADO_DIAGNOSTICO,
            title = "Acerca de",
            subtitle = "Versión y actualizaciones",
            keywords = listOf("version", "actualizacion", "onboarding", "acerca"),
        ),
    ) { AcercaDeCard(vm, onRerunOnboarding) },
)

@Composable
private fun BackupRestoreConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceColor,
        title = { Text("Restaurar copia de seguridad", color = TextPrimaryColor, fontWeight = FontWeight.SemiBold) },
        text = {
            Text(
                "Reemplaza TODOS los datos actuales (viajes, perfiles, informes y ajustes) por los " +
                    "de la copia elegida. Esta acción no se puede deshacer.",
                color = TextMutedColor,
                fontSize = 13.sp,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Reemplazar", color = AccentColor) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", color = TextMutedColor) }
        },
    )
}
