package com.revscope.feature.dashboard

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.revscope.core.obd.connection.ConnectionState
import com.revscope.core.obd.viewmodel.ConnectionViewModel
import com.revscope.feature.dashboard.ui.RevScopeColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdapterScanScreen(
    onNavigateBack: () -> Unit = {},
    connectionVm: ConnectionViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val connectionState by connectionVm.connectionState.collectAsState()
    val lastAdapterAddress by connectionVm.lastAdapterAddress.collectAsState()

    var hasBluetoothPermission by remember { mutableStateOf(hasBtConnectPermission(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { hasBluetoothPermission = hasBtConnectPermission(context) }

    // Covers grants made outside the launcher (e.g. from system Settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasBluetoothPermission = hasBtConnectPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val bondedDevices: List<BluetoothDevice> = remember(hasBluetoothPermission) {
        if (!hasBluetoothPermission) emptyList()
        else {
            try {
                val adapter = BluetoothAdapter.getDefaultAdapter()
                adapter?.bondedDevices?.toList() ?: emptyList()
            } catch (_: SecurityException) {
                emptyList()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Adapter",
                        color = RevScopeColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = RevScopeColors.TextPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = RevScopeColors.Surface),
            )
        },
        containerColor = RevScopeColors.Background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
        ) {
            when (val state = connectionState) {
                ConnectionState.Disconnected -> DisconnectedContent(
                    bondedDevices = bondedDevices,
                    lastAdapterAddress = lastAdapterAddress,
                    hasPermission = hasBluetoothPermission,
                    onRequestPermission = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.BLUETOOTH_CONNECT,
                                Manifest.permission.BLUETOOTH_SCAN,
                            )
                        )
                    },
                    onConnectDevice = { address -> connectionVm.connectToDevice(address) },
                )

                ConnectionState.Connecting -> ConnectingContent()

                is ConnectionState.Connected -> ConnectedContent(
                    deviceName = state.deviceName,
                    onDisconnect = { connectionVm.disconnect() },
                )

                is ConnectionState.Error -> ErrorContent(
                    message = state.message,
                    onRetry = { connectionVm.reconnectToLast() },
                    onChooseAnother = { connectionVm.disconnect() },
                )
            }
        }
    }
}

// BLUETOOTH_CONNECT is a runtime permission only from API 31; below that it is
// granted at install time via the legacy BLUETOOTH manifest permission.
private fun hasBtConnectPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED

@Composable
private fun DisconnectedContent(
    bondedDevices: List<BluetoothDevice>,
    lastAdapterAddress: String?,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onConnectDevice: (String) -> Unit,
) {
    if (!hasPermission) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Default.BluetoothDisabled,
                contentDescription = null,
                tint = RevScopeColors.TextMuted,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Bluetooth permission required",
                color = RevScopeColors.TextMuted,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(containerColor = RevScopeColors.Accent),
            ) {
                Text("Grant Permission", color = RevScopeColors.Background)
            }
        }
        return
    }

    Text(
        "Paired devices",
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = RevScopeColors.TextMuted,
        modifier = Modifier.padding(bottom = 8.dp),
    )

    if (bondedDevices.isEmpty()) {
        Text(
            "No paired Bluetooth devices found.\nPair your OBD2 adapter in Android Settings first.",
            color = RevScopeColors.TextMuted,
            fontSize = 13.sp,
        )
    } else {
        val sortedDevices = bondedDevices.sortedByDescending { it.address == lastAdapterAddress }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sortedDevices, key = { it.address }) { device ->
                DeviceItem(
                    device = device,
                    isLastUsed = device.address == lastAdapterAddress,
                    onClick = { onConnectDevice(device.address) },
                )
            }
        }
    }
}

@Composable
private fun DeviceItem(device: BluetoothDevice, isLastUsed: Boolean, onClick: () -> Unit) {
    val name = try { device.name ?: device.address } catch (_: SecurityException) { device.address }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(RevScopeColors.Surface, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Default.Bluetooth, contentDescription = null, tint = RevScopeColors.Accent)
        Column(modifier = Modifier.weight(1f)) {
            Text(name, color = RevScopeColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(device.address, color = RevScopeColors.TextMuted, fontSize = 11.sp)
        }
        if (isLastUsed) {
            Text(
                "Último usado",
                color = RevScopeColors.Accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .background(RevScopeColors.SurfaceHigh, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun ConnectingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = RevScopeColors.Accent)
        Spacer(Modifier.height(16.dp))
        Text("Connecting…", color = RevScopeColors.TextPrimary, fontSize = 16.sp)
    }
}

@Composable
private fun ConnectedContent(deviceName: String, onDisconnect: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.Bluetooth,
            contentDescription = null,
            tint = RevScopeColors.Success,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text("Connected ●", color = RevScopeColors.Success, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(deviceName, color = RevScopeColors.TextPrimary, fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onDisconnect,
            colors = ButtonDefaults.buttonColors(containerColor = RevScopeColors.SurfaceHigh),
        ) {
            Text("Disconnect", color = RevScopeColors.TextPrimary)
        }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit, onChooseAnother: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.Error,
            contentDescription = null,
            tint = RevScopeColors.Danger,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(message, color = RevScopeColors.Danger, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "Reintentando en segundo plano…",
            color = RevScopeColors.TextMuted,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = RevScopeColors.Accent),
        ) {
            Text("Reintentar ahora", color = RevScopeColors.Background)
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onChooseAnother,
            colors = ButtonDefaults.buttonColors(containerColor = RevScopeColors.SurfaceHigh),
        ) {
            Text("Elegir otro dispositivo", color = RevScopeColors.TextPrimary)
        }
    }
}
