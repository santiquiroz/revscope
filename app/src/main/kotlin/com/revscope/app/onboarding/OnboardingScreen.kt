package com.revscope.app.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.revscope.app.R

private val BgColor = Color(0xFF0A0A0F)
private val SurfaceColor = Color(0xFF12121A)
private val AccentColor = Color(0xFFE8FF00)
private val TextPrimaryColor = Color(0xFFF0F0F8)
private val TextMutedColor = Color(0xFF6B7089)
private val GrantedColor = Color(0xFF4CD964)
private val DeniedColor = Color(0xFFFF4D4D)

@Composable
fun OnboardingScreen(
    onFinished: (goToAdapterScan: Boolean) -> Unit,
    vm: OnboardingViewModel = hiltViewModel(),
) {
    val step by vm.step.collectAsState()

    Scaffold(containerColor = BgColor) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
        ) {
            StepIndicator(step)
            Box(Modifier.weight(1f)) {
                when (step) {
                    0 -> Step0Permissions()
                    1 -> Step1Vehicle(vm)
                    2 -> Step2Adapter(vm, onFinished)
                    3 -> Step3Ai(vm)
                    else -> Step4Done()
                }
            }
            WizardBar(
                step = step,
                onBack = vm::back,
                onSkip = vm::next,
                onNext = {
                    if (step < OnboardingViewModel.TOTAL_STEPS - 1) {
                        vm.next()
                    } else {
                        vm.markDone()
                        onFinished(false)
                    }
                },
                showAdvance = step != 3,
            )
        }
    }
}

@Composable
private fun StepIndicator(step: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(OnboardingViewModel.TOTAL_STEPS) { index ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(if (index == step) AccentColor else TextMutedColor, CircleShape),
            )
        }
    }
}

@Composable
private fun WizardBar(
    step: Int,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
    showAdvance: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (step > 0) {
            TextButton(onClick = onBack) {
                Text(stringResource(R.string.app_onboarding_back), color = TextMutedColor)
            }
        }
        Spacer(Modifier.weight(1f))
        if (showAdvance) {
            if (step < OnboardingViewModel.TOTAL_STEPS - 1) {
                TextButton(onClick = onSkip) {
                    Text(stringResource(R.string.app_onboarding_skip), color = TextMutedColor)
                }
                Spacer(Modifier.width(8.dp))
            }
            Button(
                onClick = onNext,
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(
                    stringResource(
                        if (step == OnboardingViewModel.TOTAL_STEPS - 1) R.string.app_onboarding_start else R.string.app_onboarding_next,
                    ),
                    color = BgColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun Step0Permissions() {
    val context = LocalContext.current

    var locationGranted by remember { mutableStateOf(isLocationGranted(context)) }
    var notificationsGranted by remember { mutableStateOf(isNotificationsGranted(context)) }
    var bluetoothGranted by remember { mutableStateOf(isBluetoothGranted(context)) }

    // Android 12+ ignora un request de FINE sin COARSE en el mismo diálogo.
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results -> locationGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true }

    val notificationsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> notificationsGranted = granted }

    val bluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results -> bluetoothGranted = results.values.all { it } }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.app_onboarding_welcome), color = TextPrimaryColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.app_onboarding_permissions_intro),
            color = TextMutedColor,
            fontSize = 14.sp,
        )

        Spacer(Modifier.height(8.dp))

        PermissionCard(
            icon = Icons.Default.LocationOn,
            title = stringResource(R.string.app_onboarding_location),
            rationale = stringResource(R.string.app_onboarding_location_rationale),
            granted = locationGranted,
            onRequest = { locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) },
        )

        PermissionCard(
            icon = Icons.Default.Notifications,
            title = stringResource(R.string.app_onboarding_notifications),
            rationale = stringResource(R.string.app_onboarding_notifications_rationale),
            granted = notificationsGranted,
            onRequest = { notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
        )

        PermissionCard(
            icon = Icons.Default.Bluetooth,
            title = stringResource(R.string.app_onboarding_bluetooth),
            rationale = stringResource(R.string.app_onboarding_bluetooth_rationale),
            granted = bluetoothGranted,
            onRequest = { bluetoothLauncher.launch(bluetoothPermissions()) },
        )
    }
}

@Composable
private fun Step1Vehicle(vm: OnboardingViewModel) {
    var name by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf("CAR") }
    var plate by rememberSaveable { mutableStateOf("") }
    val profileCreated by vm.profileCreated.collectAsState()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.app_onboarding_vehicle_title), color = TextPrimaryColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.app_onboarding_vehicle_intro),
            color = TextMutedColor,
            fontSize = 14.sp,
        )

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.app_onboarding_vehicle_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = onboardingTextFieldColors(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VehicleTypeChip(stringResource(R.string.app_onboarding_vehicle_car), active = type == "CAR", onClick = { type = "CAR" })
            VehicleTypeChip(stringResource(R.string.app_onboarding_vehicle_motorcycle), active = type == "MOTORCYCLE", onClick = { type = "MOTORCYCLE" })
        }

        OutlinedTextField(
            value = plate,
            onValueChange = { plate = it },
            label = { Text(stringResource(R.string.app_onboarding_vehicle_plate)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = onboardingTextFieldColors(),
        )

        Button(
            onClick = { vm.createFirstProfile(name, type, plate) },
            enabled = !profileCreated,
            colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            if (profileCreated) {
                Icon(Icons.Default.Check, contentDescription = null, tint = BgColor)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.app_onboarding_vehicle_created), color = BgColor, fontWeight = FontWeight.SemiBold)
            } else {
                Text(stringResource(R.string.app_onboarding_vehicle_create), color = BgColor, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun VehicleTypeChip(label: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (active) AccentColor else SurfaceColor,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                color = if (active) BgColor else TextPrimaryColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun Step2Adapter(vm: OnboardingViewModel, onFinished: (goToAdapterScan: Boolean) -> Unit) {
    var gpsOnlySelected by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.app_onboarding_adapter_title), color = TextPrimaryColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.app_onboarding_adapter_intro),
            color = TextMutedColor,
            fontSize = 14.sp,
        )

        AdapterOptionCard(
            title = stringResource(R.string.app_onboarding_adapter_now),
            subtitle = stringResource(R.string.app_onboarding_adapter_now_subtitle),
            onClick = {
                vm.setGpsOnlyMode(false)
                vm.markDone()
                onFinished(true)
            },
        )

        AdapterOptionCard(
            title = stringResource(R.string.app_onboarding_adapter_later),
            subtitle = stringResource(R.string.app_onboarding_adapter_later_subtitle),
            onClick = {
                vm.setGpsOnlyMode(false)
                vm.next()
            },
        )

        AdapterOptionCard(
            title = stringResource(R.string.app_onboarding_adapter_none),
            subtitle = stringResource(R.string.app_onboarding_adapter_none_subtitle),
            selected = gpsOnlySelected,
            onClick = {
                gpsOnlySelected = true
                vm.setGpsOnlyMode(true)
            },
        )

        if (gpsOnlySelected) {
            GpsOnlyFeatureList()
            Button(
                onClick = vm::next,
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.app_onboarding_continue), color = BgColor, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun AdapterOptionCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    selected: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceColor, RoundedCornerShape(12.dp))
            .then(
                if (selected) {
                    Modifier.border(1.dp, AccentColor, RoundedCornerShape(12.dp))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, color = TextPrimaryColor, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = TextMutedColor, fontSize = 13.sp)
    }
}

@Composable
private fun GpsOnlyFeatureList() {
    val features = listOf(
        R.string.app_onboarding_gps_feature_radars,
        R.string.app_onboarding_gps_feature_trips,
        R.string.app_onboarding_gps_feature_crash,
        R.string.app_onboarding_gps_feature_pico_placa,
        R.string.app_onboarding_gps_feature_history,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceColor, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        features.forEach { feature ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, contentDescription = null, tint = AccentColor)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(feature), color = TextPrimaryColor, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun Step3Ai(vm: OnboardingViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.app_onboarding_ai_title), color = TextPrimaryColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.app_onboarding_ai_intro),
            color = TextMutedColor,
            fontSize = 14.sp,
        )
        AiValueContent(onDone = vm::next)
    }
}

@Composable
private fun onboardingTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimaryColor,
    unfocusedTextColor = TextPrimaryColor,
    focusedContainerColor = SurfaceColor,
    unfocusedContainerColor = SurfaceColor,
    focusedBorderColor = AccentColor,
    unfocusedBorderColor = TextMutedColor,
    focusedLabelColor = AccentColor,
    unfocusedLabelColor = TextMutedColor,
    cursorColor = AccentColor,
)

@Composable
private fun Step4Done() {
    Column {
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.app_onboarding_done_title), color = TextPrimaryColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.app_onboarding_done_body),
            color = TextMutedColor,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    rationale: String,
    granted: Boolean,
    onRequest: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceColor, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = AccentColor)
            Text(
                title,
                color = TextPrimaryColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .weight(1f),
            )
            PermissionStatusIcon(granted)
        }
        Text(rationale, color = TextMutedColor, fontSize = 13.sp)
        if (!granted) {
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(containerColor = AccentColor),
            ) {
                Text(stringResource(R.string.app_onboarding_allow), color = BgColor)
            }
        }
    }
}

@Composable
private fun PermissionStatusIcon(granted: Boolean) {
    Icon(
        imageVector = if (granted) Icons.Default.Check else Icons.Default.Close,
        contentDescription = stringResource(
            if (granted) R.string.app_onboarding_granted else R.string.app_onboarding_not_granted,
        ),
        tint = if (granted) GrantedColor else DeniedColor,
    )
}

private fun isLocationGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun isNotificationsGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

private fun isBluetoothGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        bluetoothPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }

private fun bluetoothPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
    } else {
        emptyArray()
    }
