package com.revscope.app.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.revscope.core.data.db.entities.VehicleProfileEntity

private val SurfaceColor = Color(0xFF12121A)
private val TextPrimaryColor = Color(0xFFE6E8F0)
private val AccentColor = Color(0xFFE8FF00)
private val TextMutedColor = Color(0xFF6B7089)

/** Startup vehicle picker — shown once per process when there are saved profiles. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehiclePickerSheet(
    vm: VehiclePickerViewModel = hiltViewModel(),
    onDismiss: () -> Unit,
    onManageVehicles: () -> Unit,
) {
    val profiles by vm.profiles.collectAsState()
    val activeProfile by vm.activeProfile.collectAsState()
    var dontAskAgain by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceColor,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(
                "¿Qué vehículo vas a usar?",
                color = TextPrimaryColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))

            profiles.forEach { profile ->
                VehiclePickerRow(
                    profile = profile,
                    isActive = activeProfile?.id == profile.id,
                    onClick = {
                        vm.select(profile)
                        if (dontAskAgain) vm.disableAsking()
                        onDismiss()
                    },
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { dontAskAgain = !dontAskAgain },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = dontAskAgain,
                    onCheckedChange = { dontAskAgain = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = AccentColor,
                        checkmarkColor = SurfaceColor,
                    ),
                )
                Text("No volver a preguntar al inicio", color = TextMutedColor, fontSize = 13.sp)
            }

            TextButton(onClick = onManageVehicles) {
                Text("Administrar vehículos", color = AccentColor)
            }
        }
    }
}

@Composable
private fun VehiclePickerRow(
    profile: VehicleProfileEntity,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (profile.type == "MOTORCYCLE") Icons.Default.TwoWheeler else Icons.Default.DirectionsCar,
            contentDescription = null,
            tint = AccentColor,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            profile.name,
            color = TextPrimaryColor,
            fontSize = 15.sp,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (isActive) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Activo",
                tint = AccentColor,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
