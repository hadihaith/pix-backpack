package com.unofficialpixstudio.draw

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import android.graphics.Color as AndroidColor

/** Mix any colour: saturation/brightness square, hue bar and a hex field, all kept in sync. */
@Composable
fun ColorPickerDialog(initial: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val start = remember { FloatArray(3).also { AndroidColor.colorToHSV(initial or OPAQUE, it) } }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var saturation by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    var hex by remember { mutableStateOf(toHex(initial)) }
    val rgb = hsvToRgb(hue, saturation, value)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom colour") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SaturationValuePanel(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    onChange = { s, v ->
                        saturation = s
                        value = v
                        hex = toHex(hsvToRgb(hue, s, v))
                    },
                    modifier = Modifier.fillMaxWidth().aspectRatio(1.5f),
                )
                HueBar(
                    hue = hue,
                    onChange = { h ->
                        hue = h
                        hex = toHex(hsvToRgb(h, saturation, value))
                    },
                    modifier = Modifier.fillMaxWidth().height(28.dp),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(OPAQUE or rgb))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    )
                    OutlinedTextField(
                        value = hex,
                        onValueChange = { text ->
                            val cleaned = text.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }.take(6)
                            hex = cleaned
                            if (cleaned.length == 6) {
                                val hsv = FloatArray(3)
                                AndroidColor.colorToHSV(cleaned.toInt(16) or OPAQUE, hsv)
                                hue = hsv[0]
                                saturation = hsv[1]
                                value = hsv[2]
                            }
                        },
                        label = { Text("Hex") },
                        prefix = { Text("#") },
                        singleLine = true,
                        isError = hex.length != 6,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    "Dark colours look dim on the LEDs, and black is switched off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onPick(rgb) }) { Text("Use colour") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Saturation left→right, brightness bottom→top, for the current [hue]. Tap or drag to choose. */
@Composable
private fun SaturationValuePanel(
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (saturation: Float, value: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnChange by rememberUpdatedState(onChange)
    // Not clipped, so the thumb can overhang the corners; the gradients get rounded corners instead.
    Canvas(
        modifier
            .semantics { contentDescription = "Saturation and brightness" }
            .pointerInput(Unit) {
                awaitEachGesture {
                    fun update(p: Offset) = currentOnChange(
                        (p.x / size.width).coerceIn(0f, 1f),
                        1f - (p.y / size.height).coerceIn(0f, 1f),
                    )
                    val down = awaitFirstDown()
                    down.consume()
                    update(down.position)
                    drag(down.id) { change ->
                        change.consume()
                        update(change.position)
                    }
                }
            }
    ) {
        val corner = CornerRadius(12.dp.toPx())
        drawRoundRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))), cornerRadius = corner)
        drawRoundRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)), cornerRadius = corner)
        val thumb = Offset(saturation * size.width, (1f - value) * size.height)
        drawCircle(Color.Black.copy(alpha = 0.5f), radius = 12.dp.toPx(), center = thumb, style = Stroke(2.dp.toPx()))
        drawCircle(Color.White, radius = 10.dp.toPx(), center = thumb, style = Stroke(3.dp.toPx()))
    }
}

/** The colour wheel unrolled into a bar. Tap or drag to choose a hue. */
@Composable
private fun HueBar(hue: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val currentOnChange by rememberUpdatedState(onChange)
    Canvas(
        modifier
            .clip(RoundedCornerShape(50))
            .semantics { contentDescription = "Hue" }
            .pointerInput(Unit) {
                awaitEachGesture {
                    fun update(p: Offset) = currentOnChange((p.x / size.width).coerceIn(0f, 1f) * 360f)
                    val down = awaitFirstDown()
                    down.consume()
                    update(down.position)
                    drag(down.id) { change ->
                        change.consume()
                        update(change.position)
                    }
                }
            }
    ) {
        drawRect(Brush.horizontalGradient(HUE_STOPS.map { Color.hsv(it, 1f, 1f) }))
        val x = (hue / 360f * size.width).coerceIn(size.height / 2, size.width - size.height / 2)
        val center = Offset(x, size.height / 2)
        drawCircle(Color.Black.copy(alpha = 0.5f), radius = size.height / 2 - 1.dp.toPx(), center = center, style = Stroke(2.dp.toPx()))
        drawCircle(Color.White, radius = size.height / 2 - 3.dp.toPx(), center = center, style = Stroke(3.dp.toPx()))
    }
}

private const val OPAQUE = 0xFF000000.toInt()
private val HUE_STOPS = listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f)

private fun hsvToRgb(hue: Float, saturation: Float, value: Float): Int =
    AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value)) and 0xFFFFFF

private fun toHex(rgb: Int): String = "%06X".format(rgb and 0xFFFFFF)
