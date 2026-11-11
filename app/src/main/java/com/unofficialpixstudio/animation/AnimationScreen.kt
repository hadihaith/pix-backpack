package com.unofficialpixstudio.animation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unofficialpixstudio.ble.BackpackViewModel
import com.unofficialpixstudio.ble.GattState
import com.unofficialpixstudio.ui.components.AnimatedLedDisplay
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Animate tab: pick a ready-made animation, watch it play exactly as the LEDs will show it,
 * set how many milliseconds each frame lasts, and send it to the backpack.
 */
@Composable
fun AnimationScreen(
    backpack: BackpackViewModel,
    modifier: Modifier = Modifier,
    animations: AnimationViewModel = viewModel(),
) {
    val connection by backpack.connectionState.collectAsState()
    val displaySize by backpack.displaySize.collectAsState()
    val busy by backpack.busy.collectAsState()
    val message by backpack.message.collectAsState()
    val width = displaySize.width
    val height = displaySize.height

    val preset = animations.selected
    val frameMs = animations.frameMs(preset)
    val frames = animations.frames(preset, width, height)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text("Animations", style = MaterialTheme.typography.headlineLarge)

        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            AnimatedLedDisplay(frames, width, height, frameMs)
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
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (option in AnimationPresets.all) {
                PresetTile(
                    name = option.name,
                    frames = animations.frames(option, width, height),
                    width = width,
                    height = height,
                    frameMs = animations.frameMs(option),
                    selected = option.id == preset.id,
                    onClick = { animations.select(option) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        FrameTimeCard(
            frameMs = frameMs,
            defaultMs = preset.defaultFrameMs,
            frameCount = frames.size,
            onChange = { animations.setFrameMs(preset, it) },
        )

        Spacer(Modifier.height(16.dp))
        val ready = connection is GattState.Ready
        Button(
            onClick = { backpack.sendAnimation(frames, width, height, frameMs) },
            enabled = ready && !busy,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) { Text(if (busy) "Sending…" else "Send to backpack") }
        Text(
            when {
                !ready -> "Backpack not connected."
                message != null -> message.orEmpty()
                else -> "${preset.name} · ${frames.size} frames"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (!ready) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A small playing preview with the preset's name under it; outlined when it's the chosen one. */
@Composable
private fun PresetTile(
    name: String,
    frames: List<IntArray>,
    width: Int,
    height: Int,
    frameMs: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, shape)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedLedDisplay(frames, width, height, frameMs)
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
        )
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
            "Shorter is faster · one loop takes ${seconds(frameMs * frameCount)}",
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
