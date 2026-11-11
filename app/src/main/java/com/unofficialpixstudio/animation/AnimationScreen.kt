package com.unofficialpixstudio.animation

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unofficialpixstudio.ble.BackpackViewModel
import com.unofficialpixstudio.ble.GattState
import com.unofficialpixstudio.ui.components.AnimatedLedDisplay
import com.unofficialpixstudio.ui.components.SendProgress
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Animate tab. It opens on every ready-made animation, two to a row, each playing as the LEDs
 * will show it. Tapping one opens its own page, to set how long each frame lasts and send it
 * to the backpack.
 */
@Composable
fun AnimationScreen(
    backpack: BackpackViewModel,
    modifier: Modifier = Modifier,
    animations: AnimationViewModel = viewModel(),
    cache: FrameCache = viewModel(),
) {
    val displaySize by backpack.displaySize.collectAsState()
    // Kept out here so coming back from an animation's page returns to the same place in the list.
    val gallery = rememberLazyGridState()

    val open = animations.open
    if (open == null) {
        AnimationGallery(displaySize.width, displaySize.height, animations, cache, gallery, modifier)
    } else {
        BackHandler { animations.close() }
        AnimationPage(open, backpack, displaySize.width, displaySize.height, animations, cache, modifier)
    }
}

/** Every animation, two to a row. */
@Composable
private fun AnimationGallery(
    width: Int,
    height: Int,
    animations: AnimationViewModel,
    cache: FrameCache,
    state: LazyGridState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp)
    ) {
        Text("Animations", style = MaterialTheme.typography.headlineLarge)
        Text(
            "${AnimationPresets.all.size} to pick from. Tap one to set its speed and send it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = state,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(AnimationPresets.all, key = { it.id }) { preset ->
                AnimationTile(
                    title = preset.name,
                    subtitle = preset.description,
                    frames = cache.framesOf(preset, width, height),
                    width = width,
                    height = height,
                    frameMs = animations.frameMs(preset),
                    onClick = { animations.open(preset) },
                )
            }
        }
    }
}

/**
 * A card with an animation playing on top and its name and a line about it below. The LEDs
 * stay off while [frames] is null, which is the moment it takes to draw them.
 */
@Composable
fun AnimationTile(
    title: String,
    subtitle: String,
    frames: List<IntArray>?,
    width: Int,
    height: Int,
    frameMs: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val container = MaterialTheme.colorScheme.surfaceContainerLow
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        // Looks the same when it can't be tapped; whatever disables it says why elsewhere.
        colors = CardDefaults.cardColors(containerColor = container, disabledContainerColor = container),
    ) {
        Column(Modifier.padding(12.dp)) {
            AnimatedLedDisplay(frames.orEmpty(), width, height, frameMs, Modifier.fillMaxWidth())
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 2, // so tiles in a row are the same height whether or not the text wraps
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One animation's own page: a large preview, its speed, and the button to send it. */
@Composable
private fun AnimationPage(
    preset: AnimationPreset,
    backpack: BackpackViewModel,
    width: Int,
    height: Int,
    animations: AnimationViewModel,
    cache: FrameCache,
    modifier: Modifier = Modifier,
) {
    val connection by backpack.connectionState.collectAsState()
    val busy by backpack.busy.collectAsState()
    val message by backpack.message.collectAsState()
    val sendProgress by backpack.sendProgress.collectAsState()
    val frameMs = animations.frameMs(preset)
    val frames = cache.framesOf(preset, width, height)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = animations::close) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "All animations")
            }
            Text(
                preset.name,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            AnimatedLedDisplay(frames.orEmpty(), width, height, frameMs)
        }

        Spacer(Modifier.height(10.dp))
        Text(
            preset.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))
        FrameTimeCard(
            frameMs = frameMs,
            defaultMs = preset.defaultFrameMs,
            frameCount = frames?.size ?: 0,
            onChange = { animations.setFrameMs(preset, it) },
        )

        Spacer(Modifier.height(16.dp))
        val ready = connection is GattState.Ready
        Button(
            onClick = { if (frames != null) backpack.sendAnimation(frames, width, height, frameMs) },
            enabled = ready && !busy && frames != null,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) { Text(if (busy) "Sending…" else "Send to backpack") }
        val progress = sendProgress
        if (ready && progress != null) {
            SendProgress(progress, Modifier.padding(top = 6.dp))
        } else {
            Text(
                when {
                    !ready -> "Backpack not connected."
                    message != null -> message.orEmpty()
                    frames != null -> "${preset.name} · ${frames.size} frames"
                    else -> ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (!ready) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Sets the milliseconds between frames: a slider for big changes and − / + for exact ones.
 * The slider is logarithmic, so the short times most animations use aren't squeezed into its
 * first few millimetres.
 */
@Composable
private fun FrameTimeCard(frameMs: Int, defaultMs: Int, frameCount: Int, onChange: (Int) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Time between frames",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("$frameMs ms", style = MaterialTheme.typography.titleLarge)
            }
            TextButton(onClick = { onChange(defaultMs) }, enabled = frameMs != defaultMs) { Text("Reset") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StepButton("−", "Shorter, $STEP_MS milliseconds less", frameMs > AnimationPresets.MIN_FRAME_MS) {
                onChange(frameMs - STEP_MS)
            }
            Slider(
                value = sliderPosition(frameMs),
                onValueChange = { onChange(frameMsAt(it)) },
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Time between frames, $frameMs milliseconds" },
            )
            StepButton("+", "Longer, $STEP_MS milliseconds more", frameMs < AnimationPresets.MAX_FRAME_MS) {
                onChange(frameMs + STEP_MS)
            }
        }
        Text(
            if (frameCount > 0) "Shorter is faster · one loop takes ${seconds(frameMs * frameCount)}" else "Shorter is faster",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StepButton(symbol: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Text(symbol, style = MaterialTheme.typography.titleLarge)
    }
}

private const val STEP_MS = 5

private val FRAME_MS_SPAN = ln(AnimationPresets.MAX_FRAME_MS.toFloat() / AnimationPresets.MIN_FRAME_MS)

/** Where [frameMs] sits on the slider, 0–1. */
private fun sliderPosition(frameMs: Int): Float =
    (ln(frameMs.toFloat() / AnimationPresets.MIN_FRAME_MS) / FRAME_MS_SPAN).coerceIn(0f, 1f)

/** The frame time at slider [position] (0–1), to the nearest [STEP_MS]. */
private fun frameMsAt(position: Float): Int {
    val exact = AnimationPresets.MIN_FRAME_MS * (AnimationPresets.MAX_FRAME_MS.toFloat() / AnimationPresets.MIN_FRAME_MS).pow(position)
    val stepped = (exact / STEP_MS).roundToInt() * STEP_MS
    return stepped.coerceIn(AnimationPresets.MIN_FRAME_MS, AnimationPresets.MAX_FRAME_MS)
}

/** "3.6 s" from milliseconds. */
private fun seconds(millis: Int): String {
    val tenths = (millis + 50) / 100
    return "${tenths / 10}.${tenths % 10} s"
}
