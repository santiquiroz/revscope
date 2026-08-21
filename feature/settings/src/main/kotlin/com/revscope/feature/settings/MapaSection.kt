package com.revscope.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
internal fun MapaCard(vm: SettingsViewModel, snackbarHostState: SnackbarHostState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Mapa")
        OfflineMapSection(vm, snackbarHostState)
    }
}
