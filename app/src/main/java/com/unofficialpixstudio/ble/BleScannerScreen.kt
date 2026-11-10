package com.unofficialpixstudio.ble

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.location.LocationManagerCompat
import com.unofficialpixstudio.bluetoothPermissions
import com.unofficialpixstudio.hasBluetoothPermissions
import com.unofficialpixstudio.ui.components.PixIcons
import kotlinx.coroutines.withTimeoutOrNull

private const val SCAN_DURATION_MS = 12_000L

/** First-run screen: finds nearby PIX backpacks and lets the user pick theirs. */
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
    var status by remember { mutableStateOf("Switch your PIX backpack on and keep it close.") }
    var scanning by remember { mutableStateOf(false) }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (scanner.isBluetoothEnabled) scanning = true else status = "Bluetooth is off. Turn it on to find your backpack."
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
        else status = "The app needs Bluetooth permission to find your backpack."
    }

    LaunchedEffect(scanning) {
        if (!scanning) return@LaunchedEffect
        devices.clear()
        status = "Looking for backpacks nearby…"
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
                0 -> "No backpack found. Make sure it's switched on and close by."
                1 -> "Found your backpack. Tap it to connect."
                else -> "Found $pixCount backpacks. Tap yours to connect."
            }
        } catch (e: BleScanException) {
            status = e.message ?: "Scan failed"
        } catch (e: SecurityException) {
            status = "Bluetooth permission error"
        } finally {
            scanning = false
        }
    }

    // Start looking straight away when we already have permission; otherwise wait for a tap
    // so the permission prompt has context.
    LaunchedEffect(Unit) {
        if (context.hasBluetoothPermissions()) startScanOrPrompt()
    }

    fun onScanClick() {
        if (context.hasBluetoothPermissions()) startScanOrPrompt()
        else permissionLauncher.launch(bluetoothPermissions)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ScanRadar(scanning = scanning, modifier = Modifier.size(220.dp))
                Spacer(Modifier.height(8.dp))
                Text(
                    "Find your backpack",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                if (scanning) {
                    OutlinedButton(onClick = {
                        scanning = false
                        status = "Stopped looking."
                    }) { Text("Stop") }
                } else {
                    Button(
                        onClick = ::onScanClick,
                        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp),
                    ) { Text(if (devices.isEmpty()) "Find my backpack" else "Search again") }
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        items(visibleDevices, key = { it.address }) { device ->
            DeviceCard(device, showDetails = !pixOnly, onClick = { onDeviceClick(device) })
        }

        item(key = "advanced") {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(8.dp))
                // Developer aid: list every Bluetooth device, not just PIX backpacks. The scan
                // filter is fixed when a scan starts, so it can't change mid-scan.
                TextButton(onClick = { pixOnly = !pixOnly }, enabled = !scanning) {
                    Text(if (pixOnly) "Show all Bluetooth devices" else "Show PIX backpacks only")
                }
                if (!pixOnly) {
                    FilterChip(
                        selected = namedOnly,
                        onClick = { namedOnly = !namedOnly },
                        label = { Text("Named only") },
                    )
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Filter by name or address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "Showing ${visibleDevices.size} of ${devices.size} devices",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

/** Pulsing rings around a pixel heart while scanning; a calm single ring otherwise. */
@Composable
private fun ScanRadar(scanning: Boolean, modifier: Modifier = Modifier) {
    val ringColor = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "radar")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 2400, easing = LinearEasing)),
        label = "radar phase",
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val maxRadius = size.minDimension / 2
            if (scanning) {
                for (i in 0 until 3) {
                    val t = (phase + i / 3f) % 1f
                    drawCircle(
                        color = ringColor.copy(alpha = (1f - t) * 0.5f),
                        radius = maxRadius * (0.3f + 0.7f * t),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            } else {
                drawCircle(
                    color = ringColor.copy(alpha = 0.25f),
                    radius = maxRadius * 0.6f,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                PixIcons.Heart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(44.dp),
            )
        }
    }
}

@Composable
private fun DeviceCard(device: BleDevice, showDetails: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SignalBars(device.rssi)
            Column(Modifier.weight(1f)) {
                Text(device.name ?: "Unknown device", style = MaterialTheme.typography.titleMedium)
                Text(
                    device.address,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (showDetails && device.manufacturerIds.isNotEmpty()) {
                    Text(
                        "Maker: " + device.manufacturerIds.joinToString { manufacturerLabel(it) },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (showDetails && device.serviceUuids.isNotEmpty()) {
                    Text(
                        "Services: " + device.serviceUuids.joinToString(),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Connect",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Four bars, filled according to signal strength (closer = more bars). */
@Composable
private fun SignalBars(rssi: Int) {
    val filled = when {
        rssi >= -60 -> 4
        rssi >= -70 -> 3
        rssi >= -80 -> 2
        rssi >= -90 -> 1
        else -> 0
    }
    val on = MaterialTheme.colorScheme.primary
    val off = MaterialTheme.colorScheme.outline
    Canvas(Modifier.size(width = 22.dp, height = 18.dp)) {
        val barWidth = size.width / 7
        for (i in 0 until 4) {
            val barHeight = size.height * (i + 1) / 4
            drawRoundRect(
                color = if (i < filled) on else off,
                topLeft = Offset(i * barWidth * 2, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 3),
            )
        }
    }
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
