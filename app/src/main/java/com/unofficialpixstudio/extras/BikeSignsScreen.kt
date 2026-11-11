package com.unofficialpixstudio.extras

import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.unofficialpixstudio.animation.AnimationTile
import com.unofficialpixstudio.animation.FrameCache
import com.unofficialpixstudio.animation.framesOf
import com.unofficialpixstudio.ble.BackpackViewModel

/**
 * Bike signs: every sign playing as the LEDs will show it, two to a row. One tap sends a
 * sign to the backpack, with nothing to confirm, since it's meant to be used mid-ride.
 */
@Composable
fun BikeSignsScreen(
    backpack: BackpackViewModel,
    cache: FrameCache,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TapToSendPage(
        title = "Bike signs",
        hint = "Tap a sign to show it on your backpack.",
        columns = 2,
        backpack = backpack,
        onBack = onBack,
        modifier = modifier,
    ) { width, height, canSend ->
        items(BikeSigns.all, key = { it.id }) { sign ->
            val frames = cache.framesOf(sign, width, height)
            AnimationTile(
                title = sign.name,
                subtitle = sign.description,
                frames = frames,
                width = width,
                height = height,
                frameMs = sign.defaultFrameMs,
                onClick = { if (frames != null) backpack.sendAnimation(frames, width, height, sign.defaultFrameMs) },
                enabled = canSend && frames != null,
            )
        }
    }
}
