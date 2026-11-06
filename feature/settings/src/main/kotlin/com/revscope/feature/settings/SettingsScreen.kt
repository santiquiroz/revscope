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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.revscope.core.common.i18n.AppLanguage
import com.revscope.core.common.units.AppUnits
import com.revscope.core.designsystem.FranjaSelectorVehiculo
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
    selectorVehiculo: @Composable () -> Unit = {},
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

    val tagActual = remember { AppLanguage.savedTag(context) }
    val unidadesActual = remember { AppUnits.savedTag(context) }
    val idiomaSectionEntry = SettingsEntry(
        SettingsEntryMeta(
            id = "idioma",
            section = SettingsSectionId.IDIOMA_UNIDADES,
            title = stringResource(R.string.settings_entry_language),
            subtitle = "",
            keywords = stringResource(R.string.settings_search_keywords_language).split('|'),
        ),
    ) {
        IdiomaSection(tagActual = tagActual, onElegir = { tag ->
            AppLanguage.persist(context, tag)
            restartApp(context)
        })
        SectionTitle(stringResource(R.string.settings_units_title))
        UnidadesSection(tagActual = unidadesActual, onElegir = { tag ->
            AppUnits.persist(context, tag)
            restartApp(context)
        })
    }

    val deepLinkSection = remember(initialExpandedSection) {
        settingsSectionFromDeepLinkKey(initialExpandedSection)
    }
    val entriesWithLanguage = listOf(idiomaSectionEntry) + entries
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
            Column {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.settings_screen_title),
                            color = TextPrimaryColor,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceColor),
                )
                FranjaSelectorVehiculo(selector = selectorVehiculo)
            }
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
                label = { Text(stringResource(R.string.settings_search_label), fontSize = 12.sp) },
                singleLine = true,
                colors = settingsFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )

            val isSearching = SettingsSearch.isActive(searchQuery)
            val matchedIds = if (isSearching) {
                SettingsSearch.filter(searchQuery, entriesWithLanguage.map { it.meta }).map { it.id }.toSet()
            } else {
                null
            }

            SettingsSectionId.entries.forEach { section ->
                val sectionEntries = entriesWithLanguage.filter { it.meta.section == section }
                val visibleEntries = matchedIds?.let { ids -> sectionEntries.filter { it.meta.id in ids } }
                    ?: sectionEntries
                if (isSearching && visibleEntries.isEmpty()) return@forEach

                val expandedState = sectionExpansion.getValue(section)
                val expanded = isSearching || expandedState.value
                SettingsSectionHeader(
                    title = stringResource(section.titleRes),
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

@Composable
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
            title = stringResource(R.string.settings_entry_vehicle_title),
            subtitle = stringResource(R.string.settings_entry_vehicle_subtitle),
            keywords = stringResource(R.string.settings_search_keywords_vehicle).split('|'),
        ),
    ) { VehiculoActivoCard(vm, onNavigateToVehicleProfiles) },
    SettingsEntry(
        SettingsEntryMeta(
            id = "herramientas",
            section = SettingsSectionId.VEHICULO_GARAGE,
            title = stringResource(R.string.settings_entry_tools_title),
            subtitle = stringResource(R.string.settings_entry_tools_subtitle),
            keywords = stringResource(R.string.settings_search_keywords_tools).split('|'),
        ),
    ) { HerramientasCard(vm, onNavigateToVehicleProfiles) },
    SettingsEntry(
        SettingsEntryMeta(
            id = "combustible",
            section = SettingsSectionId.VEHICULO_GARAGE,
            title = stringResource(R.string.settings_entry_fuel_title),
            subtitle = stringResource(R.string.settings_entry_fuel_subtitle),
            keywords = stringResource(R.string.settings_search_keywords_fuel).split('|'),
        ),
    ) { CombustibleCard(vm) },
    SettingsEntry(
        SettingsEntryMeta(
            id = "alertas_audio",
            section = SettingsSectionId.RADARES_ALERTAS,
            title = stringResource(R.string.settings_entry_audio_alerts_title),
            subtitle = stringResource(R.string.settings_entry_audio_alerts_subtitle),
            keywords = stringResource(R.string.settings_search_keywords_audio_alerts).split('|'),
        ),
    ) { AlertasAudioCard(vm) },
    SettingsEntry(
        SettingsEntryMeta(
            id = "sonido_motor",
            section = SettingsSectionId.RADARES_ALERTAS,
            title = stringResource(R.string.settings_entry_engine_sound_title),
            subtitle = stringResource(R.string.settings_entry_engine_sound_subtitle),
            keywords = stringResource(R.string.settings_search_keywords_engine_sound).split('|'),
        ),
    ) { SonidoMotorCard(vm) },
    SettingsEntry(
        SettingsEntryMeta(
            id = "radares_velocidad",
            section = SettingsSectionId.RADARES_ALERTAS,
            title = stringResource(R.string.settings_entry_speed_cameras_title),
            subtitle = stringResource(R.string.settings_entry_speed_cameras_subtitle),
            keywords = stringResource(R.string.settings_search_keywords_speed_cameras).split('|'),
        ),
    ) { RadaresVelocidadCard(vm) },
    SettingsEntry(
        SettingsEntryMeta(
            id = "pico_placa_personalizado",
            section = SettingsSectionId.RADARES_ALERTAS,
            title = stringResource(R.string.settings_entry_custom_driving_restrictions_title),
            subtitle = stringResource(R.string.settings_entry_custom_driving_restrictions_subtitle),
            keywords = stringResource(R.string.settings_search_keywords_custom_driving_restrictions).split('|'),
        ),
    ) { PicoPlacaPersonalizadoCard(vm) },
    SettingsEntry(
        SettingsEntryMeta(
            id = "mapa",
            section = SettingsSectionId.MAPA,
            title = stringResource(R.string.settings_entry_map_title),
            subtitle = stringResource(R.string.settings_entry_map_subtitle),
            keywords = stringResource(R.string.settings_search_keywords_map).split('|'),
        ),
    ) { MapaCard(vm, snackbarHostState) },
    SettingsEntry(
        SettingsEntryMeta(
            id = "ia",
            section = SettingsSectionId.INTELIGENCIA_ARTIFICIAL,
            title = stringResource(R.string.settings_entry_ai_title),
            subtitle = stringResource(R.string.settings_entry_ai_subtitle),
            keywords = stringResource(R.string.settings_search_keywords_ai).split('|'),
        ),
    ) { InteligenciaArtificialCard(vm, onOpenAiValue) },
    SettingsEntry(
        SettingsEntryMeta(
            id = "servidor_colaborativo",
            section = SettingsSectionId.SALA_SOCIAL,
            title = stringResource(R.string.settings_entry_collaborative_server_title),
            subtitle = stringResource(R.string.settings_entry_collaborative_server_subtitle),
            keywords = stringResource(R.string.settings_search_keywords_collaborative_server).split('|'),
        ),
    ) { ServidorColaborativoCard(vm) },
    SettingsEntry(
        SettingsEntryMeta(
            id = "companero_viaje",
            section = SettingsSectionId.SALA_SOCIAL,
            title = stringResource(R.string.settings_entry_travel_companion_title),
            subtitle = stringResource(R.string.settings_entry_travel_companion_subtitle),
            keywords = stringResource(R.string.settings_search_keywords_travel_companion).split('|'),
        ),
    ) { CompaneroViajeCard(vm) },
    SettingsEntry(
        SettingsEntryMeta(
            id = "avisos_segundo_plano",
            section = SettingsSectionId.NOTIFICACIONES_SEGUNDO_PLANO,
            title = stringResource(R.string.settings_entry_background_alerts_title),
            subtitle = stringResource(R.string.settings_entry_background_alerts_subtitle),
            keywords = stringResource(R.string.settings_search_keywords_background_alerts).split('|'),
        ),
    ) { AvisosSegundoPlanoCard() },
    SettingsEntry(
        SettingsEntryMeta(
            id = "deteccion_caida",
            section = SettingsSectionId.AVANZADO_DIAGNOSTICO,
            title = stringResource(R.string.settings_entry_crash_detection_title),
            subtitle = stringResource(R.string.settings_entry_crash_detection_subtitle),
            keywords = stringResource(R.string.settings_search_keywords_crash_detection).split('|'),
        ),
    ) { DeteccionCaidaCard(vm) },
    SettingsEntry(
        SettingsEntryMeta(
            id = "modo_guardia",
            section = SettingsSectionId.AVANZADO_DIAGNOSTICO,
            title = stringResource(R.string.settings_entry_anti_theft_guard_title),
            subtitle = stringResource(R.string.settings_entry_anti_theft_guard_subtitle),
            keywords = stringResource(R.string.settings_search_keywords_anti_theft_guard).split('|'),
        ),
    ) { ModoGuardiaCard(vm) },
    SettingsEntry(
        SettingsEntryMeta(
            id = "muestreo_obd",
            section = SettingsSectionId.AVANZADO_DIAGNOSTICO,
            title = stringResource(R.string.settings_entry_obd_sampling_title),
            subtitle = stringResource(R.string.settings_entry_obd_sampling_subtitle),
            keywords = stringResource(R.string.settings_search_keywords_obd_sampling).split('|'),
        ),
    ) { MuestreoObdCard() },
    SettingsEntry(
        SettingsEntryMeta(
            id = "pids_personalizados",
            section = SettingsSectionId.AVANZADO_DIAGNOSTICO,
            title = stringResource(R.string.settings_entry_custom_pids_title),
            subtitle = stringResource(R.string.settings_entry_custom_pids_subtitle),
            keywords = stringResource(R.string.settings_search_keywords_custom_pids).split('|'),
        ),
    ) { CustomPidsCard(vm) },
    SettingsEntry(
        SettingsEntryMeta(
            id = "alertas_pid_personalizadas",
            section = SettingsSectionId.AVANZADO_DIAGNOSTICO,
            title = stringResource(R.string.settings_entry_custom_pid_alerts_title),
            subtitle = stringResource(R.string.settings_entry_custom_pid_alerts_subtitle),
            keywords = stringResource(R.string.settings_search_keywords_custom_pid_alerts).split('|'),
        ),
    ) { CustomPidAlertsCard(vm) },
    SettingsEntry(
        SettingsEntryMeta(
            id = "copia_seguridad",
            section = SettingsSectionId.AVANZADO_DIAGNOSTICO,
            title = stringResource(R.string.settings_entry_backup_title),
            subtitle = stringResource(R.string.settings_entry_backup_subtitle),
            keywords = stringResource(R.string.settings_search_keywords_backup).split('|'),
        ),
    ) { CopiaSeguridadCard(vm, onImportRequested) },
    SettingsEntry(
        SettingsEntryMeta(
            id = "servidor_mcp",
            section = SettingsSectionId.AVANZADO_DIAGNOSTICO,
            title = stringResource(R.string.settings_entry_mcp_server_title),
            subtitle = stringResource(R.string.settings_entry_mcp_server_subtitle),
            keywords = stringResource(R.string.settings_search_keywords_mcp_server).split('|'),
        ),
    ) { ServidorMcpCard(vm) },
    SettingsEntry(
        SettingsEntryMeta(
            id = "acerca_de",
            section = SettingsSectionId.AVANZADO_DIAGNOSTICO,
            title = stringResource(R.string.settings_entry_about_title),
            subtitle = stringResource(R.string.settings_entry_about_subtitle),
            keywords = stringResource(R.string.settings_search_keywords_about).split('|'),
        ),
    ) { AcercaDeCard(vm, onRerunOnboarding) },
)

@Composable
private fun BackupRestoreConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceColor,
        title = {
            Text(
                stringResource(R.string.settings_backup_restore_title),
                color = TextPrimaryColor,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Text(
                stringResource(R.string.settings_backup_restore_warning),
                color = TextMutedColor,
                fontSize = 13.sp,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.settings_backup_restore_confirm), color = AccentColor)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel), color = TextMutedColor)
            }
        },
    )
}
