package com.unofficialpixstudio.extras

import android.os.SystemClock
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unofficialpixstudio.ble.BackpackViewModel
import com.unofficialpixstudio.ble.GattState
import com.unofficialpixstudio.ui.components.SendProgress
import kotlinx.coroutines.delay

/** The frame every Extras page shares: a back arrow to the list, [title], then [content]. */
@Composable
internal fun ExtraPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = if (bottomPadding) 16.dp else 0.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Extras")
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
        }
        content()
    }
}

/**
 * The one line that says how sending is going: a progress bar while something is on its way,
 * otherwise that the backpack isn't connected, or the result of the last send, or [idle].
 */
@Composable
internal fun SendStatus(backpack: BackpackViewModel, idle: String, modifier: Modifier = Modifier) {
    val connection by backpack.connectionState.collectAsState()
    val busy by backpack.busy.collectAsState()
    val message by backpack.message.collectAsState()
    val progress by backpack.sendProgress.collectAsState()
    val ready = connection is GattState.Ready

    val sent = progress
    if (ready && sent != null) {
        SendProgress(sent, modifier)
    } else {
        Text(
            when {
                !ready -> "Backpack not connected."
                busy || message != null -> message ?: "Sending…"
                else -> idle
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (!ready) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier.fillMaxWidth(),
        )
    }
}

/** Whether a tap can send something right now: connected, and nothing else on its way. */
@Composable
internal fun canSend(backpack: BackpackViewModel): Boolean {
    val connection by backpack.connectionState.collectAsState()
    val busy by backpack.busy.collectAsState()
    return connection is GattState.Ready && !busy
}

/**
 * [SystemClock.elapsedRealtime], read again every [everyMs] for as long as [ticking], so
 * whatever shows a running time is redrawn as it changes.
 */
@Composable
internal fun elapsedRealtime(ticking: Boolean, everyMs: Long): Long {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(ticking, everyMs) {
        now = SystemClock.elapsedRealtime()
        while (ticking) {
            delay(everyMs)
            now = SystemClock.elapsedRealtime()
        }
    }
    return now
}
