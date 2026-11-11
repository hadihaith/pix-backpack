package com.unofficialpixstudio.extras

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unofficialpixstudio.animation.AnimationPreset
import com.unofficialpixstudio.animation.FrameCache
import com.unofficialpixstudio.animation.framesOf
import com.unofficialpixstudio.ble.BackpackViewModel
import com.unofficialpixstudio.ui.components.AnimatedLedDisplay

/** The things the Extras tab offers. Add to this to add another. */
private enum class Extra(val title: String, val summary: String, val preview: AnimationPreset) {
    Signs("Bike signs", "Turn arrows and road signs for when you ride", BikeSigns.all.first()),
    Flag("Flags", "Country flags, hung the way a banner hangs", Flags.byId(Flags.idOf("Kuwait")) ?: Flags.all.first()),
    Clock(
        "Clock",
        "The time, kept by the backpack itself",
        AnimationPreset("extra-clock", "Clock", "", 1000) { w, h ->
            listOf(TimeFace.draw(w, h, 12, 34, TimeLook.Clock.top, TimeLook.Clock.bottom))
        },
    ),
    Stopwatch(
        "Stopwatch",
        "Counts up from zero",
        AnimationPreset("extra-stopwatch", "Stopwatch", "", 1000) { w, h ->
            List(10) { TimeFace.draw(w, h, 0, it, TimeLook.Stopwatch.top, TimeLook.Stopwatch.bottom) }
        },
    ),
    Countdown(
        "Countdown",
        "Counts down to zero, then flashes",
        AnimationPreset("extra-countdown", "Countdown", "", 1000) { w, h ->
            List(10) { TimeFace.draw(w, h, 0, 10 - it, TimeLook.Countdown.top, TimeLook.Countdown.bottom) }
        },
    ),
}

/**
 * Extras tab: more things to do with the backpack than pictures and animations. It opens on
 * a list of them; each has its own page.
 */
@Composable
fun ExtrasScreen(
    backpack: BackpackViewModel,
    modifier: Modifier = Modifier,
    cache: FrameCache = viewModel(),
) {
    var open by rememberSaveable { mutableStateOf<Extra?>(null) }
    val displaySize by backpack.displaySize.collectAsState()

    when (open) {
        null -> ExtrasList(displaySize.width, displaySize.height, cache, onOpen = { open = it }, modifier = modifier)
        Extra.Signs -> {
            BackHandler { open = null }
            BikeSignsScreen(backpack, cache, onBack = { open = null }, modifier = modifier)
        }
        Extra.Flag -> {
            BackHandler { open = null }
            FlagsScreen(backpack, cache, onBack = { open = null }, modifier = modifier)
        }
        Extra.Clock -> {
            BackHandler { open = null }
            ClockScreen(backpack, onBack = { open = null }, modifier = modifier)
        }
        Extra.Stopwatch -> {
            BackHandler { open = null }
            StopwatchScreen(backpack, onBack = { open = null }, modifier = modifier)
        }
        Extra.Countdown -> {
            BackHandler { open = null }
            CountdownScreen(backpack, onBack = { open = null }, modifier = modifier)
        }
    }
}

@Composable
private fun ExtrasList(
    width: Int,
    height: Int,
    cache: FrameCache,
    onOpen: (Extra) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Text("Extras", style = MaterialTheme.typography.headlineLarge)
            Text(
                "More things your backpack can do.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        for (extra in Extra.entries) {
            Card(
                onClick = { onOpen(extra) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    AnimatedLedDisplay(
                        frames = cache.framesOf(extra.preview, width, height).orEmpty(),
                        width = width,
                        height = height,
                        frameMs = extra.preview.defaultFrameMs,
                        modifier = Modifier.width(64.dp),
                    )
                    Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                        Text(extra.title, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            extra.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
