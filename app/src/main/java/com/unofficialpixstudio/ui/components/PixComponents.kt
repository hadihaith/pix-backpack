package com.unofficialpixstudio.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.unofficialpixstudio.ble.GattState
import com.unofficialpixstudio.ui.theme.LedOff
import com.unofficialpixstudio.ui.theme.LedPanel
import com.unofficialpixstudio.ui.theme.StatusBusy
import com.unofficialpixstudio.ui.theme.StatusConnected
import kotlin.math.max
import kotlin.math.roundToInt

/** Pixel-art icons, in keeping with the LED display. Drawn from simple 'X' grids. */
object PixIcons {
    val Heart: ImageVector by lazy {
        pixelIcon(
            "PixHeart",
            ".XX.XX.",
            "XXXXXXX",
            "XXXXXXX",
            ".XXXXX.",
            "..XXX..",
            "...X...",
        )
    }

    val Sun: ImageVector by lazy {
        pixelIcon(
            "PixSun",
            "X..X..X",
            ".......",
            "..XXX..",
            "X.XXX.X",
            "..XXX..",
            ".......",
            "X..X..X",
        )
    }

    val Film: ImageVector by lazy {
        pixelIcon(
            "PixFilm",
            "X.XXX.X",
            "XXXXXXX",
            "X.....X",
            "XX...XX",
            "X.....X",
            "XXXXXXX",
            "X.XXX.X",
        )
    }

    val Star: ImageVector by lazy {
        pixelIcon(
            "PixStar",
            "...X...",
            "..XXX..",
            "XXXXXXX",
            ".XXXXX.",
            ".XXXXX.",
            "XXX.XXX",
            "X.....X",
        )
    }

    val Pencil: ImageVector by lazy {
        pixelIcon(
            "PixPencil",
            ".....XX",
            "....XXX",
            "...XXX.",
            "..XXX..",
            ".XXX...",
            "X.X....",
            "XX.....",
        )
    }

    val Eraser: ImageVector by lazy {
        pixelIcon(
            "PixEraser",
            "...XXXX",
            "..X.XXX",
            ".X...XX",
            "X...XX.",
            ".X.XX..",
            "..XX...",
            ".......",
        )
    }

    val Bucket: ImageVector by lazy {
        pixelIcon(
            "PixBucket",
            ".X.....",
            "X.X....",
            "X..X...",
            "X...X..",
            "XXXXXX.",
            ".XXXX.X",
            "..XX..X",
        )
    }

    val Dropper: ImageVector by lazy {
        pixelIcon(
            "PixDropper",
            ".....XX",
            "....XXX",
            "...XXXX",
            "..X.X..",
            ".X.X...",
            "X.X....",
            "X......",
        )
    }

    val Mirror: ImageVector by lazy {
        pixelIcon(
            "PixMirror",
            "...X...",
            "X.....X",
            "XX.X.XX",
            "XXX.XXX",
            "XX.X.XX",
            "X.....X",
            "...X...",
        )
    }

    private val undoRows = arrayOf(
        ".X.....",
        "XXXXXX.",
        ".X....X",
        "......X",
        ".....X.",
        "..XXX..",
    )

    val Undo: ImageVector by lazy { pixelIcon("PixUndo", *undoRows) }

    val Redo: ImageVector by lazy { pixelIcon("PixRedo", *Array(undoRows.size) { undoRows[it].reversed() }) }

    private fun pixelIcon(name: String, vararg rows: String): ImageVector {
        val columns = rows.maxOf { it.length }
        val cell = 20f / max(columns, rows.size)
        val left = (24f - columns * cell) / 2
        val top = (24f - rows.size * cell) / 2
        return ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                rows.forEachIndexed { y, row ->
                    row.forEachIndexed { x, c ->
                        if (c == 'X') {
                            moveTo(left + x * cell, top + y * cell)
                            horizontalLineToRelative(cell)
                            verticalLineToRelative(cell)
                            horizontalLineToRelative(-cell)
                            close()
                        }
                    }
                }
            }
        }.build()
    }
}

/**
 * Renders [pixels] (0xRRGGBB, row-major) like the backpack's LED panel: round LEDs with a soft
 * glow on a black board. With null [pixels], shows the panel switched off.
 */
@Composable
fun LedDisplay(pixels: IntArray?, width: Int, height: Int, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .aspectRatio(width.toFloat() / height)
            .clip(RoundedCornerShape(12.dp))
            .background(LedPanel)
            .padding(6.dp)
    ) {
        val cell = minOf(size.width / width, size.height / height)
        val originX = (size.width - cell * width) / 2
        val originY = (size.height - cell * height) / 2
        for (y in 0 until height) {
            for (x in 0 until width) {
                val rgb = pixels?.getOrNull(y * width + x) ?: 0
                val center = Offset(originX + (x + 0.5f) * cell, originY + (y + 0.5f) * cell)
                if (rgb and 0xFFFFFF == 0) {
                    drawCircle(LedOff, radius = cell * 0.36f, center = center)
                } else {
                    val color = Color(0xFF000000.toInt() or rgb)
                    drawCircle(color.copy(alpha = 0.22f), radius = cell * 0.62f, center = center)
                    drawCircle(color, radius = cell * 0.4f, center = center)
                }
            }
        }
    }
}

/**
 * Which frame to show right now when [frameCount] frames loop with [frameMs] milliseconds
 * each. Changing [frameMs] carries on from the current frame instead of starting over, so the
 * preview doesn't jump while a speed slider is dragged.
 */
@Composable
fun rememberLoopFrame(frameCount: Int, frameMs: Int): Int {
    val currentFrameMs by rememberUpdatedState(frameMs)
    val frame by produceState(0, frameCount) {
        value = 0
        if (frameCount <= 1) return@produceState
        var position = 0.0
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                position = (position + (now - last) / 1e6 / currentFrameMs.coerceAtLeast(1)) % frameCount
                last = now
                value = position.toInt().coerceIn(0, frameCount - 1)
            }
        }
    }
    return frame.coerceIn(0, (frameCount - 1).coerceAtLeast(0))
}

/** [LedDisplay] playing [frames] in a loop, [frameMs] milliseconds each. */
@Composable
fun AnimatedLedDisplay(frames: List<IntArray>, width: Int, height: Int, frameMs: Int, modifier: Modifier = Modifier) {
    val frame = rememberLoopFrame(frames.size, frameMs)
    LedDisplay(frames.getOrNull(frame), width, height, modifier)
}

enum class ConnectionLook { Connected, Busy, Offline }

fun GattState.look(): ConnectionLook = when (this) {
    is GattState.Ready -> ConnectionLook.Connected
    GattState.Connecting, GattState.DiscoveringServices -> ConnectionLook.Busy
    GattState.Disconnected, is GattState.Failed -> ConnectionLook.Offline
}

fun GattState.shortLabel(): String = when (this) {
    is GattState.Ready -> "Connected"
    GattState.Connecting -> "Connecting…"
    GattState.DiscoveringServices -> "Connecting…"
    GattState.Disconnected -> "Offline"
    is GattState.Failed -> "Offline"
}

/** Status dot + label. Tappable (e.g. to reconnect) when [onClick] is given. */
@Composable
fun ConnectionPill(state: GattState, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val look = state.look()
    val dot by animateColorAsState(
        when (look) {
            ConnectionLook.Connected -> StatusConnected
            ConnectionLook.Busy -> StatusBusy
            ConnectionLook.Offline -> MaterialTheme.colorScheme.error
        },
        label = "connection dot",
    )
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Text(state.shortLabel(), style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * How far a picture or animation has got on its way to the backpack: a bar that fills and the
 * percentage beside it. [progress] is 0–1 and arrives in steps, one per packet; the bar and
 * the number glide between them rather than jumping.
 */
@Composable
fun SendProgress(progress: Float, modifier: Modifier = Modifier) {
    val shown by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 250, easing = LinearEasing),
        label = "send progress",
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LinearProgressIndicator(
            progress = { shown },
            modifier = Modifier.weight(1f).height(6.dp),
            drawStopIndicator = {},
        )
        Text(
            "${(shown * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            // Wide enough for "100%", so the bar doesn't change length as digits are added;
            // the bar already announces the same number to screen readers.
            modifier = Modifier.widthIn(min = 40.dp).clearAndSetSemantics {},
        )
    }
}

/** Rounded surface card with an optional heading, used for every section. */
@Composable
fun PixCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(Modifier.padding(20.dp)) {
            if (title != null) {
                Text(
                    title.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            content()
        }
    }
}
