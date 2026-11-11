package com.unofficialpixstudio.extras

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unofficialpixstudio.animation.FrameCache
import com.unofficialpixstudio.animation.framesOf
import com.unofficialpixstudio.ble.BackpackViewModel
import com.unofficialpixstudio.ui.components.LedDisplay

/**
 * Flags: every flag in [Flags], in alphabetical order, three to a row, each shown as it will
 * hang on the backpack. One tap sends a flag as a still picture.
 */
@Composable
fun FlagsScreen(
    backpack: BackpackViewModel,
    cache: FrameCache,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TapToSendPage(
        title = "Flags",
        hint = "Tap a flag to show it on your backpack.",
        columns = 3,
        backpack = backpack,
        onBack = onBack,
        modifier = modifier,
    ) { width, height, canSend ->
        items(Flags.all, key = { it.id }) { flag ->
            val pixels = cache.framesOf(flag, width, height)?.firstOrNull()
            val container = MaterialTheme.colorScheme.surfaceContainerLow
            Card(
                onClick = { if (pixels != null) backpack.sendImage(pixels, width, height) },
                enabled = canSend && pixels != null,
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = container, disabledContainerColor = container),
            ) {
                Column(Modifier.padding(8.dp)) {
                    LedDisplay(pixels, width, height, Modifier.fillMaxWidth())
                    Text(
                        flag.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
                    )
                }
            }
        }
    }
}
