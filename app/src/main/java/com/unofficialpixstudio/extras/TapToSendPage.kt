package com.unofficialpixstudio.extras

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.unofficialpixstudio.ble.BackpackViewModel

/**
 * The shape of an Extras page whose things are sent with one tap: a back arrow and [title],
 * a line that says what to do (or how the sending is going), and a grid of [tiles].
 * [tiles] is told the display's size and whether a tap can send right now.
 */
@Composable
internal fun TapToSendPage(
    title: String,
    hint: String,
    columns: Int,
    backpack: BackpackViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    tiles: LazyGridScope.(width: Int, height: Int, canSend: Boolean) -> Unit,
) {
    val displaySize by backpack.displaySize.collectAsState()
    val canSend = canSend(backpack)

    ExtraPage(title, onBack, modifier, bottomPadding = false) {
        SendStatus(backpack, idle = hint, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            tiles(displaySize.width, displaySize.height, canSend)
        }
    }
}
