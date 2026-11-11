package com.unofficialpixstudio.extras

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unofficialpixstudio.ble.BackpackViewModel
import com.unofficialpixstudio.ui.components.LedDisplay
import java.util.Calendar

/**
 * Clock: the time, hours over minutes. Sending it gives the backpack every minute of the
 * next two hours to play through by itself, so it keeps time with the phone put away.
 */
@Composable
fun ClockScreen(
    backpack: BackpackViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    timers: TimersViewModel = viewModel(),
) {
    val displaySize by backpack.displaySize.collectAsState()
    val width = displaySize.width
    val height = displaySize.height
    val canSend = canSend(backpack)

    // Redrawn every second, so the preview turns over with the minute.
    val tick = elapsedRealtime(ticking = true, everyMs = 1000)
    val minuteOfDay = remember(tick) { secondOfDay(Calendar.getInstance()) / 60 }
    val preview = remember(minuteOfDay, width, height, timers.twelveHour) {
        TimeFace.frame(width, height, TimeStep(minuteOfDay, 0), TimeLook.Clock, timers.twelveHour)
    }

    ExtraPage("Clock", onBack, modifier) {
        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            LedDisplay(preview, width, height)
        }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(timers.twelveHour, role = Role.Switch) { timers.twelveHour = it }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("12-hour clock", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = timers.twelveHour, onCheckedChange = null)
        }
        Text(
            "The backpack keeps the time by itself for ${TimeSchedules.CLOCK_MINUTES / 60} hours, " +
                "then shows dashes. Send it again to carry on.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                // The clock appears when the sending finishes, so it's made for the time it will be then.
                val frameCount = TimeSchedules.CLOCK_MINUTES + 1
                val then = Calendar.getInstance().apply {
                    add(Calendar.MILLISECOND, backpack.estimatedSendMs(frameCount).toInt())
                }
                val steps = TimeSchedules.clock(secondOfDay(then), then.get(Calendar.MILLISECOND))
                backpack.sendSequence(
                    label = "clock",
                    frames = TimeFace.frames(width, height, steps, TimeLook.Clock, timers.twelveHour),
                    width = width,
                    height = height,
                    durationsMs = steps.map { it.durationMs },
                )
            },
            enabled = canSend,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) { Text("Show on backpack") }
        SendStatus(backpack, idle = "", modifier = Modifier.padding(top = 6.dp))
    }
}

private fun secondOfDay(calendar: Calendar): Int =
    calendar.get(Calendar.HOUR_OF_DAY) * 3600 + calendar.get(Calendar.MINUTE) * 60 + calendar.get(Calendar.SECOND)
