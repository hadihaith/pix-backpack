package com.unofficialpixstudio.extras

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unofficialpixstudio.ble.BackpackViewModel
import com.unofficialpixstudio.ui.components.LedDisplay

/**
 * Stopwatch: minutes over seconds, counting up. Start sends the backpack the count from
 * here on, which it plays by itself; the stopwatch starts the moment that has arrived. Stop
 * freezes the backpack on the time the phone measured.
 */
@Composable
fun StopwatchScreen(
    backpack: BackpackViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    timers: TimersViewModel = viewModel(),
) {
    val displaySize by backpack.displaySize.collectAsState()
    val width = displaySize.width
    val height = displaySize.height
    val canSend = canSend(backpack)

    val runningSince = timers.stopwatchRunningSince
    val now = elapsedRealtime(ticking = runningSince != null, everyMs = 50)
    val elapsedMs = timers.stopwatchElapsedMs(now)
    // The preview shows what the backpack does, which after a few minutes ticks less often than the phone.
    val shown = if (runningSince != null) {
        TimeSchedules.showing(timers.stopwatchSteps, (now - runningSince).coerceAtLeast(0))
    } else {
        TimeStep(wholeSeconds(elapsedMs), 0)
    }
    val preview = remember(shown, width, height) { TimeFace.frame(width, height, shown, TimeLook.Stopwatch) }

    fun showStill(ms: Long) = backpack.sendSequence(
        label = "time",
        frames = listOf(TimeFace.frame(width, height, TimeStep(wholeSeconds(ms), 0), TimeLook.Stopwatch)),
        width = width,
        height = height,
        durationsMs = listOf(1000),
    )

    ExtraPage("Stopwatch", onBack, modifier) {
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            LedDisplay(preview, width, height)
        }

        Spacer(Modifier.height(8.dp))
        Text(
            stopwatchText(elapsedMs),
            style = MaterialTheme.typography.displayMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = {
                    timers.resetStopwatch()
                    showStill(0)
                },
                enabled = runningSince == null && elapsedMs > 0 && canSend,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) { Text("Reset") }
            if (runningSince == null) {
                Button(
                    onClick = {
                        val steps = TimeSchedules.stopwatch(timers.stopwatchBankedMs)
                        backpack.sendSequence(
                            label = "stopwatch",
                            frames = TimeFace.frames(width, height, steps, TimeLook.Stopwatch),
                            width = width,
                            height = height,
                            durationsMs = steps.map { it.durationMs },
                            onShown = { timers.stopwatchStarted(steps) },
                        )
                    },
                    enabled = canSend && elapsedMs < TimeSchedules.MAX_SECONDS * 1000L,
                    modifier = Modifier.weight(1.6f),
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) { Text(if (elapsedMs > 0) "Resume" else "Start") }
            } else {
                Button(
                    onClick = {
                        // Stopped on the phone at once, whether or not the backpack can be told.
                        timers.stopStopwatch()
                        showStill(timers.stopwatchBankedMs)
                    },
                    modifier = Modifier.weight(1.6f),
                    contentPadding = PaddingValues(vertical = 12.dp),
                ) { Text("Stop") }
            }
        }
        SendStatus(
            backpack,
            idle = if (runningSince != null) "The backpack is counting by itself." else "Starts once it has reached the backpack.",
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

private fun wholeSeconds(ms: Long): Int = (ms / 1000).toInt().coerceAtMost(TimeSchedules.MAX_SECONDS)

/** "03:07.4" from milliseconds. */
private fun stopwatchText(ms: Long): String {
    val seconds = ms / 1000
    return "%02d:%02d.%d".format(seconds / 60, seconds % 60, ms % 1000 / 100)
}
