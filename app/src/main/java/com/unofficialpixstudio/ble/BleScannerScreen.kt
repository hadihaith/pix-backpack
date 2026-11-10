package com.unofficialpixstudio.ble

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.location.LocationManagerCompat
import com.unofficialpixstudio.bluetoothPermissions
import com.unofficialpixstudio.hasBluetoothPermissions
import kotlinx.coroutines.withTimeoutOrNull

private const val SCAN_DURATION_MS = 12_000L

@Composable
fun BleScannerScreen(onDeviceClick: (BleDevice) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scanner = remember { BleScanner(context.applicationContext) }

    // Keyed by address so repeat advertisements update a device instead of duplicating it.
    val devices = remember { mutableStateMapOf<String, BleDevice>() }
    // By default only PIX backpacks are listed. Turning that off shows everything nearby, with
    // a name filter and a named-only toggle for picking through the noise.
    var pixOnly by remember { mutableStateOf(true) }
    var namedOnly by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    val visibleDevices by remember {
        derivedStateOf {
            devices.values
                .filter { !pixOnly || it.isPixBackpack }
                .filter { pixOnly || !namedOnly || it.name != null }
                .filter {
                    pixOnly || query.isBlank() ||
                        it.name?.contains(query.trim(), ignoreCase = true) == true ||
                        it.address.contains(query.trim(), ignoreCase = true)
                }
                .sortedByDescending { it.rssi }
        }
    }
    var status by remember { mutableStateOf("Ready to scan") }
    var scanning by remember { mutableStateOf(false) }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (scanner.isBluetoothEnabled) scanning = true else status = "Bluetooth is off"
    }

    // Runs once permissions are granted: makes sure Bluetooth (and location, pre-Android 12)
    // is on, then starts the scan.
    fun startScanOrPrompt() {
        when {
            !scanner.isBluetoothSupported ->
                status = "This device doesn't support Bluetooth"

            !scanner.isBluetoothEnabled ->
                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))

            Build.VERSION.SDK_INT < Build.VERSION_CODES.S && !context.isLocationEnabled() ->
                status = "Turn on Location in system settings — Android 11 and below " +
                    "need it to find Bluetooth devices"

            else -> scanning = true
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) startScanOrPrompt()
        else status = "Bluetooth permission was denied"
    }

    LaunchedEffect(scanning) {
        if (!scanning) return@LaunchedEffect
        devices.clear()
        status = "Scanning for nearby devices…"
        try {
            withTimeoutOrNull(SCAN_DURATION_MS) {
                // In PIX-only mode the Bluetooth controller filters by the PIX service UUID;
                // otherwise everything nearby is reported.
                val serviceFilter = if (pixOnly) PIX_SERVICE_UUID else null
                scanner.scan(serviceFilter).collect { device ->
                    // Not every packet carries the name or service list (they may only be in
                    // the scan response), so keep what was seen before instead of losing it.
                    val known = devices[device.address]
                    devices[device.address] = device.copy(
                        name = device.name ?: known?.name,
                        serviceUuids = device.serviceUuids.ifEmpty { known?.serviceUuids.orEmpty() },
                    )
                }
            }
            val pixCount = devices.values.count { it.isPixBackpack }
            status = when (pixCount) {
                0 -> "Scan finished: no PIX backpack found. Make sure it's on and nearby."
                1 -> "Scan finished: found 1 PIX backpack"
                else -> "Scan finished: found $pixCount PIX backpacks"
            }
        } catch (e: BleScanException) {
            status = e.message ?: "Scan failed"
        } catch (e: SecurityException) {
            status = "Bluetooth permission error"
        } finally {
            scanning = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text("Unofficial Pix Studio", style = MaterialTheme.typography.headlineMedium)
        Text(
            "PIX Backpack Bluetooth scanner",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(20.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (scanning) {
                OutlinedButton(onClick = {
                    scanning = false
                    status = "Scan stopped"
                }) { Text("Stop") }
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Button(onClick = {
                    if (context.hasBluetoothPermissions()) startScanOrPrompt()
                    else permissionLauncher.launch(bluetoothPermissions)
                }) { Text("Scan for devices") }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(status, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // The scan filter is fixed when a scan starts, so the mode can't change mid-scan.
            FilterChip(
                selected = pixOnly,
                onClick = { pixOnly = !pixOnly },
                label = { Text("PIX backpacks only") },
                enabled = !scanning,
            )
            if (!pixOnly) {
                FilterChip(
                    selected = namedOnly,
                    onClick = { namedOnly = !namedOnly },
                    label = { Text("Named only") },
                )
            }
        }
        if (!pixOnly) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Filter by name or address") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(8.dp))

        Text(
            if (pixOnly) "PIX backpacks (${visibleDevices.size})"
            else "Showing ${visibleDevices.size} of ${devices.size} devices",
            style = MaterialTheme.typography.titleMedium,
        )

        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            items(visibleDevices, key = { it.address }) { device ->
                BleDeviceRow(device, onClick = { onDeviceClick(device) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun BleDeviceRow(device: BleDevice, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(device.name ?: "Unknown device") },
        supportingContent = {
            Column {
                Text(device.address)
                if (device.manufacturerIds.isNotEmpty()) {
                    Text(
                        "Maker: " + device.manufacturerIds.joinToString { manufacturerLabel(it) },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (device.serviceUuids.isNotEmpty()) {
                    Text(
                        "Services: " + device.serviceUuids.joinToString(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        trailingContent = { Text("${device.rssi} dBm") },
    )
}

/** Names for the company IDs that make up most of the noise in a typical scan. */
private fun manufacturerLabel(id: Int): String {
    val name = when (id) {
        0x004C -> "Apple"
        0x0075 -> "Samsung"
        0x0006 -> "Microsoft"
        0x00E0 -> "Google"
        else -> null
    }
    val hex = "0x%04X".format(id)
    return if (name != null) "$name ($hex)" else hex
}

private fun Context.isLocationEnabled(): Boolean {
    val locationManager = getSystemService(LocationManager::class.java) ?: return false
    return LocationManagerCompat.isLocationEnabled(locationManager)
}
