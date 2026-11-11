package com.unofficialpixstudio.extras

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unofficialpixstudio.ble.BackpackViewModel
import com.unofficialpixstudio.ui.components.LedDisplay

/**
 * Countdown: set a time, and Start sends the backpack the whole countdown to play by itself,
 * minutes over seconds, flashing when it reaches zero. It starts the moment it has arrived.
 */
@Composable
fun CountdownScreen(
    backpack: BackpackViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    timers: TimersViewModel = viewModel(),
) {
    val displaySize by backpack.displaySize.collectAsState()
    val width = displaySize.width
    val height = displaySize.height
    val canSend = canSend(backpack)

    val startedAt = timers.countdownStartedAt
    val now = elapsedRealtime(ticking = startedAt != null, everyMs = 100)
    val leftMs = timers.countdownLeftMs(now)
    val set = timers.countdownSeconds
    val shown = if (startedAt != null) {
        TimeSchedules.showing(timers.countdownSteps, (now - startedAt).coerceAtLeast(0))
    } else {
        TimeStep(set, 0)
    }
    val preview = remember(shown, width, height) { TimeFace.frame(width, height, shown, TimeLook.Countdown) }

    ExtraPage("Countdown", onBack, modifier) {
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            LedDisplay(preview, width, height)
        }

        Spacer(Modifier.height(8.dp))
        if (leftMs == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stepper("Minutes", set / 60, Modifier.weight(1f)) { timers.setCountdown(it.coerceIn(0, 99) * 60 + set % 60) }
                // In fives, and back onto a multiple of five if a quick pick left it between them.
                Stepper("Seconds", set % 60, Modifier.weight(1f), step = 5) {
                    timers.setCountdown(set / 60 * 60 + (it.coerceIn(0, 55) / 5) * 5)
                }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (minutes in QUICK_MINUTES) {
                    FilterChip(
                        selected = set == minutes * 60,
                        onClick = { timers.setCountdown(minutes * 60) },
                        label = { Text("$minutes min") },
                    )
                }
            }
        } else {
            // Rounded up, so it reads 0:05 for the whole of the fifth-last second, as the backpack does.
            val left = ((leftMs + 999) / 1000).toInt()
            Text(
                if (left == 0) "Time's up" else "%02d:%02d".format(left / 60, left % 60),
                style = MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(12.dp))
        if (leftMs == null) {
            Button(
                onClick = {
                    val steps = TimeSchedules.countdown(set)
                    backpack.sendSequence(
                        label = "countdown",
                        frames = TimeFace.frames(width, height, steps, TimeLook.Countdown),
                        width = width,
                        height = height,
                        durationsMs = steps.map { it.durationMs },
                        onShown = { timers.countdownStarted(steps) },
                    )
                },
                enabled = canSend,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) { Text("Start") }
        } else {
            OutlinedButton(
                onClick = {
                    val finished = leftMs == 0L
                    timers.clearCountdown()
                    // Cancelled part-way: put the backpack back on the time it was set to.
                    if (!finished) {
                        backpack.sendSequence(
                            label = "time",
                            frames = listOf(TimeFace.frame(width, height, TimeStep(set, 0), TimeLook.Countdown)),
                            width = width,
                            height = height,
                            durationsMs = listOf(1000),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) { Text(if (leftMs == 0L) "Done" else "Cancel") }
        }
        SendStatus(
            backpack,
            idle = if (leftMs != null) "The backpack is counting by itself." else "Starts once it has reached the backpack.",
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** A labelled number with − and + either side of it. */
@Composable
private fun Stepper(label: String, value: Int, modifier: Modifier = Modifier, step: Int = 1, onChange: (Int) -> Unit) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalIconButton(
                onClick = { onChange(value - step) },
                modifier = Modifier.semantics { contentDescription = "Fewer ${label.lowercase()}" },
            ) { Text("−", style = MaterialTheme.typography.titleLarge) }
            Text("%02d".format(value), style = MaterialTheme.typography.headlineMedium)
            FilledTonalIconButton(
                onClick = { onChange(value + step) },
                modifier = Modifier.semantics { contentDescription = "More ${label.lowercase()}" },
            ) { Text("+", style = MaterialTheme.typography.titleLarge) }
        }
    }
}

private val QUICK_MINUTES = intArrayOf(1, 3, 5, 10, 15, 30)
