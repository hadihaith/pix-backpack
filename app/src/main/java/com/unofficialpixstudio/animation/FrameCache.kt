package com.unofficialpixstudio.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Draws animations' frames away from the main thread and keeps them, so a page full of
 * previews opens straight away and each animation is only ever drawn once per display size.
 */
class FrameCache : ViewModel() {

    private val drawn = ConcurrentHashMap<String, List<IntArray>>()

    /** [animation]'s frames if they've been drawn already. */
    fun ready(animation: AnimationPreset, width: Int, height: Int): List<IntArray>? =
        drawn[key(animation, width, height)]

    /** [animation]'s frames, drawing them first if need be. */
    suspend fun frames(animation: AnimationPreset, width: Int, height: Int): List<IntArray> =
        ready(animation, width, height) ?: withContext(Dispatchers.Default) {
            animation.frames(width, height).also { drawn[key(animation, width, height)] = it }
        }

    private fun key(animation: AnimationPreset, width: Int, height: Int) = "${animation.id}/$width/$height"
}

/** [animation]'s frames for a display this size, or null for the moment it takes to draw them. */
@Composable
fun FrameCache.framesOf(animation: AnimationPreset, width: Int, height: Int): List<IntArray>? {
    var frames by remember(animation.id, width, height) { mutableStateOf(ready(animation, width, height)) }
    LaunchedEffect(animation.id, width, height) {
        if (frames == null) frames = frames(animation, width, height)
    }
    return frames
}
