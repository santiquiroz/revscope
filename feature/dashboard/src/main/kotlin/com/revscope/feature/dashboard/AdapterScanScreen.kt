package com.revscope.feature.dashboard

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
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
    val connectionState by connectionVm.connectionState.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions granted — UI will refresh */ }

    val hasBluetoothPermission = remember(context) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
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
                    onRetry = { connectionVm.disconnect() },
                )
            }
        }
    }
}

@Composable
private fun DisconnectedContent(
    bondedDevices: List<BluetoothDevice>,
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
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(bondedDevices) { device ->
                DeviceItem(device = device, onClick = { onConnectDevice(device.address) })
            }
        }
    }
}

@Composable
private fun DeviceItem(device: BluetoothDevice, onClick: () -> Unit) {
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
        Column {
            Text(name, color = RevScopeColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(device.address, color = RevScopeColors.TextMuted, fontSize = 11.sp)
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
private fun ErrorContent(message: String, onRetry: () -> Unit) {
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
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = RevScopeColors.Accent),
        ) {
            Text("Retry", color = RevScopeColors.Background)
        }
    }
}
