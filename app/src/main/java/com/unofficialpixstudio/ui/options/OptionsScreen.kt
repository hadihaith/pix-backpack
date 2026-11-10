package com.unofficialpixstudio.ui.options

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.unofficialpixstudio.ble.BackpackViewModel
import com.unofficialpixstudio.ble.GattState
import com.unofficialpixstudio.ui.components.ConnectionLook
import com.unofficialpixstudio.ui.components.ConnectionPill
import com.unofficialpixstudio.ui.components.PixCard
import com.unofficialpixstudio.ui.components.look

/** Backpack details and management, developer tools and about. */
@Composable
fun OptionsScreen(viewModel: BackpackViewModel, onOpenDeveloper: () -> Unit, modifier: Modifier = Modifier) {
    val device by viewModel.device.collectAsState()
    val state by viewModel.connectionState.collectAsState()
    val info by viewModel.deviceInfo.collectAsState()
    val displaySize by viewModel.displaySize.collectAsState()
    var confirmForget by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Options", style = MaterialTheme.typography.headlineLarge)

        PixCard(title = "Your backpack") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    device?.name ?: "PIX Backpack",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                ConnectionPill(state)
            }
            Spacer(Modifier.height(12.dp))
            InfoRow("Address", device?.address ?: "–")
            InfoRow("Firmware", info.firmware ?: "–")
            InfoRow("Hardware", info.hardware ?: "–")
            InfoRow("Display", "${displaySize.width} × ${displaySize.height} LEDs")
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state.look()) {
                    ConnectionLook.Connected ->
                        OutlinedButton(onClick = viewModel::disconnect) { Text("Disconnect") }
                    ConnectionLook.Busy ->
                        OutlinedButton(onClick = viewModel::disconnect) { Text("Cancel") }
                    ConnectionLook.Offline ->
                        OutlinedButton(onClick = viewModel::connect) { Text("Reconnect") }
                }
                TextButton(
                    onClick = { confirmForget = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Forget backpack") }
            }
        }

        PixCard(title = "Developer") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenDeveloper)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(Icons.Filled.Build, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f)) {
                    Text("Developer tools", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Bluetooth services, raw values, test image and copyable reports",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }

        PixCard(title = "About") {
            val context = LocalContext.current
            val version = remember {
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
                    .getOrNull() ?: "?"
            }
            Text("Unofficial Pix Studio $version", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "A fan-made app, not affiliated with PIX. The backpack's Bluetooth protocol is " +
                    "based on the open-source pix-backpack-go project.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("Forget this backpack?") },
            text = { Text("The app will disconnect and you'll need to search for it again.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmForget = false
                    viewModel.forgetDevice()
                }) { Text("Forget") }
            },
            dismissButton = { TextButton(onClick = { confirmForget = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.labelSmall)
    }
}
