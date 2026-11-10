package com.unofficialpixstudio.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.unofficialpixstudio.ble.GattState
import com.unofficialpixstudio.ui.theme.LedOff
import com.unofficialpixstudio.ui.theme.LedPanel
import com.unofficialpixstudio.ui.theme.StatusBusy
import com.unofficialpixstudio.ui.theme.StatusConnected
import kotlin.math.max

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
