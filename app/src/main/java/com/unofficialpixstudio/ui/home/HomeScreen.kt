package com.unofficialpixstudio.ui.home

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.unofficialpixstudio.ble.BackpackViewModel
import com.unofficialpixstudio.ble.GattState
import com.unofficialpixstudio.ble.PixBrightness
import com.unofficialpixstudio.ui.components.ConnectionLook
import com.unofficialpixstudio.ui.components.ConnectionPill
import com.unofficialpixstudio.ui.components.LedDisplay
import com.unofficialpixstudio.ui.components.PixCard
import com.unofficialpixstudio.ui.components.PixIcons
import com.unofficialpixstudio.ui.components.look
import kotlin.math.roundToInt

/** Landing tab: connection status, what's on the backpack, brightness and quick actions. */
@Composable
fun HomeScreen(viewModel: BackpackViewModel, onOpenPicture: () -> Unit, modifier: Modifier = Modifier) {
    val device by viewModel.device.collectAsState()
    val state by viewModel.connectionState.collectAsState()
    val displaySize by viewModel.displaySize.collectAsState()
    val nowShowing by viewModel.nowShowing.collectAsState()
    val brightness by viewModel.brightnessPercent.collectAsState()
    val brightnessLimit by viewModel.brightnessLimit.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()
    val connected = state is GattState.Ready

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Pix Studio", style = MaterialTheme.typography.headlineLarge)
                Text(
                    device?.name ?: "PIX Backpack",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ConnectionPill(
                state = state,
                onClick = if (state.look() == ConnectionLook.Offline) viewModel::connect else null,
            )
        }

        if (state.look() != ConnectionLook.Connected) OfflineCard(state, onReconnect = viewModel::connect)

        PixCard(title = "On your backpack") {
            val picture = nowShowing
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                LedDisplay(
                    pixels = picture?.pixels,
                    width = picture?.width ?: displaySize.width,
                    height = picture?.height ?: displaySize.height,
                    modifier = Modifier.fillMaxWidth(0.62f),
                )
                Spacer(Modifier.height(12.dp))
                if (picture == null) {
                    Text(
                        "Nothing sent from this phone yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "Last sent from this phone · " +
                            DateUtils.getRelativeTimeSpanString(picture.sentAtMillis),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "The backpack can't report its screen, so this may have changed if it " +
                            "was switched off or another app sent something.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        BrightnessCard(
            percent = brightness,
            limit = brightnessLimit,
            enabled = connected && brightness != null && !busy,
            onChange = viewModel::setBrightness,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionCard(
                title = "Send a picture",
                subtitle = "Any photo, as pixel art",
                icon = PixIcons.Heart,
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = onOpenPicture,
                modifier = Modifier.weight(1f),
            )
            ActionCard(
                title = "Animated GIF",
                subtitle = "Coming soon",
                icon = PixIcons.Film,
                container = MaterialTheme.colorScheme.surfaceContainerHigh,
                content = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = null,
                modifier = Modifier.weight(1f),
            )
        }

        message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun OfflineCard(state: GattState, onReconnect: () -> Unit) {
    PixCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                val busy = state.look() == ConnectionLook.Busy
                Text(
                    if (busy) "Connecting to your backpack…" else "Backpack not connected",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    (state as? GattState.Failed)?.message ?: "Make sure it's switched on and nearby.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.look() == ConnectionLook.Busy) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
            } else {
                Button(onClick = onReconnect) { Text("Reconnect") }
            }
        }
    }
}

@Composable
private fun BrightnessCard(percent: Int?, limit: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    // Only held while dragging; afterwards the slider follows the value read back.
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging ?: percent?.toFloat()

    PixCard(title = "Brightness") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(PixIcons.Sun, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            Text(
                shown?.let { "${it.roundToInt()}%" } ?: "–",
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        Slider(
            value = (shown ?: 0f).coerceAtMost(limit.toFloat()),
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.roundToInt()?.let(onChange)
                dragging = null
            },
            valueRange = 0f..limit.toFloat(),
            enabled = enabled,
        )
        val note = when {
            limit < PixBrightness.MAX_SAFE_PERCENT ->
                "Limited to $limit% for this picture so the LEDs don't flicker."
            else -> "Capped at $limit% so the LEDs don't flicker."
        }
        Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    container: Color,
    content: Color,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = CardDefaults.cardColors(containerColor = container, contentColor = content)
    val body: @Composable () -> Unit = {
        Column(Modifier.padding(20.dp)) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.CenterStart) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.large, colors = colors) { body() }
    } else {
        Card(modifier = modifier, shape = MaterialTheme.shapes.large, colors = colors) { body() }
    }
}
