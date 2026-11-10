package com.unofficialpixstudio.ble

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Connects to [device] while shown, lists its GATT services and characteristics, reads every
 * readable value and shows notifications as they arrive.
 */
@Composable
fun BackpackScreen(device: BleDevice, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val client = remember(device.address) { PixGattClient(context) }
    val backpack = remember(client) { PixBackpack(client) }
    val state by client.state.collectAsState()
    val values by client.values.collectAsState()
    val notifications by client.notifications.collectAsState()
    val subscriptions by client.subscriptions.collectAsState()
    val scope = rememberCoroutineScope()
    var readingAll by remember { mutableStateOf(false) }

    DisposableEffect(client) {
        client.connect(device.address)
        onDispose { client.disconnect() }
    }
    BackHandler(onBack = onBack)

    val isReady = state is GattState.Ready
    // Once connected, read everything and subscribe to the PIX notify characteristics.
    LaunchedEffect(isReady) {
        if (!isReady) return@LaunchedEffect
        readingAll = true
        try {
            client.readAllAndSubscribe()
        } finally {
            readingAll = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        TextButton(onClick = onBack) { Text("← Back to scan") }
        Text(device.name ?: "Unknown device", style = MaterialTheme.typography.headlineSmall)
        Text(
            device.address,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state) {
                GattState.Connecting, GattState.DiscoveringServices ->
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)

                is GattState.Ready -> {
                    OutlinedButton(onClick = client::disconnect) { Text("Disconnect") }
                    Button(
                        onClick = {
                            scope.launch {
                                readingAll = true
                                try {
                                    client.readAllAndSubscribe()
                                } finally {
                                    readingAll = false
                                }
                            }
                        },
                        enabled = !readingAll,
                    ) { Text(if (readingAll) "Reading…" else "Read all") }
                }

                GattState.Disconnected, is GattState.Failed ->
                    Button(onClick = { client.connect(device.address) }) { Text("Connect") }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(state.label(), style = MaterialTheme.typography.bodyMedium)

        val ready = state as? GattState.Ready ?: return@Column

        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = {
            context.copyToClipboard(buildReport(device, ready, values, subscriptions, notifications, readingAll))
        }) { Text("Copy report") }

        Spacer(Modifier.height(16.dp))
        Text(
            "Services (${ready.services.size}) · MTU ${ready.mtu}",
            style = MaterialTheme.typography.titleMedium,
        )
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            item(key = "controls") {
                ControlsSection(values = values, backpack = backpack)
                HorizontalDivider()
            }
            item(key = "notifications") {
                NotificationLog(notifications)
                HorizontalDivider()
            }
            items(ready.services, key = { it.uuid }) { service ->
                ServiceItem(
                    service = service,
                    values = values,
                    subscriptions = subscriptions,
                    onReadCharacteristic = { uuid ->
                        scope.launch { runCatching { client.read(uuid) } }
                    },
                )
                HorizontalDivider()
            }
        }
    }
}

private const val TEST_IMAGE_FRAME_MS = 1000

/** Brightness slider and test-image upload. Disabled until the relevant values are read. */
@Composable
private fun ControlsSection(values: Map<UUID, CharacteristicReading>, backpack: PixBackpack) {
    val scope = rememberCoroutineScope()
    fun byteValue(uuid: UUID): Int? =
        (values[uuid] as? CharacteristicReading.Value)?.bytes?.firstOrNull()?.toInt()?.and(0xFF)

    val rawBrightness = byteValue(PixUuids.BRIGHTNESS)
    // Fall back to the documented 16×20 if the size hasn't been read.
    val width = byteValue(PixUuids.WIDTH)?.takeIf { it > 0 } ?: 16
    val height = byteValue(PixUuids.HEIGHT)?.takeIf { it > 0 } ?: 20

    var draggingPercent by remember { mutableStateOf<Float?>(null) }
    var busy by remember { mutableStateOf(false) }
    // A static image doesn't need to loop. Looping a 1-frame animation restarts it every
    // frame duration, which may be what causes the periodic flicker/dim on firmware 2.4.0.
    var playOnce by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    val shownPercent = draggingPercent ?: rawBrightness?.let { PixBrightness.rawToPercent(it).toFloat() }

    Column(Modifier.padding(vertical = 12.dp)) {
        Text("Controls", style = MaterialTheme.typography.titleSmall)

        val maxPercent = PixBrightness.MAX_SAFE_PERCENT
        val overMax = shownPercent != null && shownPercent > maxPercent + 0.5f
        Text(
            "Brightness: " + (shownPercent?.let { "${it.roundToInt()}%" } ?: "not read yet") +
                if (overMax) " (above the $maxPercent% limit; may flicker)" else "",
            style = MaterialTheme.typography.bodyMedium,
            color = if (overMax) MaterialTheme.colorScheme.error else Color.Unspecified,
        )
        Slider(
            value = (shownPercent ?: 0f).coerceAtMost(maxPercent.toFloat()),
            onValueChange = { draggingPercent = it },
            onValueChangeFinished = {
                draggingPercent?.roundToInt()?.let { target ->
                    scope.launch {
                        busy = true
                        message = "Setting brightness to $target%…"
                        message = try {
                            backpack.setBrightness(target)
                            null
                        } catch (e: GattException) {
                            "Brightness failed: ${e.message}"
                        } finally {
                            draggingPercent = null
                            busy = false
                        }
                    }
                }
            },
            valueRange = 0f..maxPercent.toFloat(),
            enabled = rawBrightness != null && !busy,
        )

        Button(
            onClick = {
                scope.launch {
                    busy = true
                    message = try {
                        val animation = PixAnimation(
                            width = width,
                            height = height,
                            frames = listOf(pixTestPattern(width, height)),
                            frameDurationsMs = listOf(TEST_IMAGE_FRAME_MS),
                            direction = if (playOnce) PixDirection.NORMAL_STOP else PixDirection.NORMAL,
                        )
                        backpack.showAnimation(animation) { sent, total ->
                            message = "Sending test image… $sent/$total"
                        }
                        if (playOnce) {
                            "Test image sent (play once)."
                        } else {
                            "Test image sent (looping every ${TEST_IMAGE_FRAME_MS} ms)."
                        }
                    } catch (e: GattException) {
                        "Test image failed: ${e.message}"
                    } catch (e: IllegalArgumentException) {
                        "Test image failed: ${e.message}"
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = !busy,
        ) { Text("Send test image (${width}×$height)") }

        FilterChip(
            selected = playOnce,
            onClick = { playOnce = !playOnce },
            label = { Text("Play once (don't loop)") },
            enabled = !busy,
        )

        message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun NotificationLog(notifications: List<NotificationEntry>) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }
    Column(Modifier.padding(vertical = 12.dp)) {
        Text("Notifications (${notifications.size})", style = MaterialTheme.typography.titleSmall)
        if (notifications.isEmpty()) {
            Text(
                "None yet. Press a button or change something on the backpack.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SelectionContainer {
            Column {
                for (entry in notifications.take(10)) {
                    Text(
                        "${timeFormat.format(Date(entry.timeMillis))}  " +
                            "${characteristicLabel(entry.characteristic)}  ${entry.bytes.toHex()}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
private fun ServiceItem(
    service: GattServiceInfo,
    values: Map<UUID, CharacteristicReading>,
    subscriptions: Map<UUID, String>,
    onReadCharacteristic: (UUID) -> Unit,
) {
    Column(Modifier.padding(vertical = 12.dp)) {
        Text(serviceLabel(service.uuid), style = MaterialTheme.typography.titleSmall)
        Text(
            service.uuid.toString(),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
        for (characteristic in service.characteristics) {
            CharacteristicItem(
                characteristic = characteristic,
                reading = values[characteristic.uuid],
                subscription = subscriptions[characteristic.uuid],
                onClick = { onReadCharacteristic(characteristic.uuid) },
            )
        }
    }
}

@Composable
private fun CharacteristicItem(
    characteristic: GattCharacteristicInfo,
    reading: CharacteristicReading?,
    subscription: String?,
    onClick: () -> Unit,
) {
    // Tap to re-read; long-press text to copy.
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = characteristic.isReadable, onClick = onClick)
            .padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
    ) {
        Text(characteristicLabel(characteristic.uuid), style = MaterialTheme.typography.bodyMedium)
        SelectionContainer {
            Column {
                Text(
                    characteristic.uuid.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    characteristic.propertyNames.joinToString(" · ").ifEmpty { "no properties" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (subscription != null) {
                    Text(
                        "notifications: $subscription",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (subscription.startsWith("failed")) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                when (reading) {
                    is CharacteristicReading.Value -> for (line in describeValue(reading.bytes)) {
                        Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }

                    is CharacteristicReading.Error -> Text(
                        "Read failed: ${reading.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )

                    CharacteristicReading.Pending -> Text(
                        "Reading…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    null -> Unit
                }
            }
        }
    }
}

/** Plain-text dump of everything on this screen, for pasting into a chat or bug report. */
private fun buildReport(
    device: BleDevice,
    ready: GattState.Ready,
    values: Map<UUID, CharacteristicReading>,
    subscriptions: Map<UUID, String>,
    notifications: List<NotificationEntry>,
    readingAll: Boolean,
): String = buildString {
    val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    appendLine("PIX backpack report")
    appendLine("Device: ${device.name ?: "Unknown device"} (${device.address})")
    appendLine("Generated: ${timeFormat.format(Date())}")
    appendLine("MTU: ${ready.mtu}")
    appendLine("Read all: ${if (readingAll) "STILL RUNNING (report may be incomplete)" else "finished"}")

    for (service in ready.services) {
        appendLine()
        appendLine("== ${serviceLabel(service.uuid)} ${service.uuid}")
        for (c in service.characteristics) {
            appendLine("-- ${characteristicLabel(c.uuid)} ${c.uuid}")
            appendLine("   ${c.propertyNames.joinToString(" · ").ifEmpty { "no properties" }}")
            subscriptions[c.uuid]?.let { appendLine("   notifications: $it") }
            when (val reading = values[c.uuid]) {
                is CharacteristicReading.Value ->
                    describeValue(reading.bytes).forEach { appendLine("   $it") }

                is CharacteristicReading.Error -> appendLine("   read failed: ${reading.message}")
                CharacteristicReading.Pending -> appendLine("   (reading… no response yet)")
                null -> appendLine("   (not read yet)")
            }
        }
    }

    appendLine()
    appendLine("== Notifications (${notifications.size}, oldest first)")
    for (entry in notifications.asReversed()) {
        appendLine(
            "${timeFormat.format(Date(entry.timeMillis))}  " +
                "${characteristicLabel(entry.characteristic)}  ${entry.bytes.toHex()}"
        )
    }
}

private fun Context.copyToClipboard(text: String) {
    val clipboard = getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("PIX backpack report", text))
    // Android 13+ shows its own confirmation when something is copied.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(this, "Report copied", Toast.LENGTH_SHORT).show()
    }
}

/** Hex, plus text and little-endian integer interpretations where they make sense. */
private fun describeValue(bytes: ByteArray): List<String> {
    if (bytes.isEmpty()) return listOf("(empty)")
    val lines = mutableListOf("hex:  ${bytes.toHex()}")
    // Show text when it's mostly printable ASCII; stray control bytes appear as \xNN.
    val printable = bytes.count { it in 0x20..0x7E }
    if (bytes.size >= 3 && printable * 10 >= bytes.size * 8) {
        val text = bytes.joinToString("") {
            if (it in 0x20..0x7E) it.toInt().toChar().toString() else "\\x%02X".format(it)
        }
        lines += "text: \"$text\""
    }
    if (bytes.size in setOf(1, 2, 4)) {
        val value = bytes.foldIndexed(0L) { i, acc, b -> acc or ((b.toLong() and 0xFF) shl (8 * i)) }
        lines += "int:  $value"
    }
    return lines
}

private fun GattState.label(): String = when (this) {
    GattState.Disconnected -> "Disconnected"
    GattState.Connecting -> "Connecting…"
    GattState.DiscoveringServices -> "Discovering services…"
    is GattState.Ready -> "Connected"
    is GattState.Failed -> message
}

private const val SIG_BASE_UUID_SUFFIX = "-0000-1000-8000-00805f9b34fb"
private const val PIX_BASE_UUID_SUFFIX = "-e984-11e7-b78e-ffd6fcc34510"

/** The 4-hex-digit short ID inside a UUID built on [suffix], or null if it isn't one. */
private fun UUID.shortId(suffix: String): String? =
    toString().takeIf { it.endsWith(suffix) && it.startsWith("0000") }?.substring(4, 8)

private fun serviceLabel(uuid: UUID): String {
    if (uuid == PIX_SERVICE_UUID) return "PIX service"
    return when (uuid.shortId(SIG_BASE_UUID_SUFFIX)) {
        "1800" -> "Generic Access"
        "1801" -> "Generic Attribute"
        "180a" -> "Device Information"
        "180f" -> "Battery"
        else -> "Unknown service"
    }
}

private fun characteristicLabel(uuid: UUID): String {
    uuid.shortId(PIX_BASE_UUID_SUFFIX)?.let { return "PIX $it" }
    return when (uuid.shortId(SIG_BASE_UUID_SUFFIX)) {
        "2a00" -> "Device Name"
        "2a01" -> "Appearance"
        "2a05" -> "Service Changed"
        "2aa6" -> "Central Address Resolution"
        "2a19" -> "Battery Level"
        "2a24" -> "Model Number"
        "2a25" -> "Serial Number"
        "2a26" -> "Firmware Revision"
        "2a27" -> "Hardware Revision"
        "2a29" -> "Manufacturer Name"
        else -> "Unknown characteristic"
    }
}
