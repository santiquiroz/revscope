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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import com.revscope.core.data.db.entities.VehicleProfileEntity

private val SurfaceColor = Color(0xFF12121A)
private val TextPrimaryColor = Color(0xFFE6E8F0)
private val AccentColor = Color(0xFFE8FF00)
private val TextMutedColor = Color(0xFF6B7089)

/**
 * Vehicle picker sheet — R5-style "Selecciona". Opened either as the once-per-process
 * startup prompt ([isStartupPrompt] = true, shows the "no volver a preguntar" checkbox)
 * or as a hot-swap from the [VehicleSwitcherPill] ([isStartupPrompt] = false).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehiclePickerSheet(
    vm: VehiclePickerViewModel,
    isStartupPrompt: Boolean,
    onDismiss: () -> Unit,
    onAddVehicle: () -> Unit,
    onManageAdapter: () -> Unit,
) {
    val profiles by vm.profiles.collectAsState()
    val activeProfile by vm.activeProfile.collectAsState()
    val connectionState by vm.connectionState.collectAsState()
    var dontAskAgain by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceColor,
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(
                "Selecciona",
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
                        if (isStartupPrompt && dontAskAgain) vm.disableAsking()
                        onDismiss()
                    },
                )
            }

            if (isStartupPrompt) {
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
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onAddVehicle,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentColor,
                    contentColor = SurfaceColor,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Agregar otro vehículo", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onManageAdapter)
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    "Adaptador: ${connectionStatusLabel(connectionState)} · Administrar",
                    color = TextMutedColor,
                    fontSize = 13.sp,
                )
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                profile.name,
                color = TextPrimaryColor,
                fontSize = 15.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            )
            profile.plate?.takeIf { it.isNotBlank() }?.let { plate ->
                Text(plate, color = TextMutedColor, fontSize = 12.sp)
            }
        }
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
