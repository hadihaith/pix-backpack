package com.unofficialpixstudio.animation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/**
 * Where the Animate tab is, kept here so it's still there after switching tabs or rotating:
 * the animation whose page is open, and the frame time the user set for each one.
 */
class AnimationViewModel : ViewModel() {

    /** The animation whose page is open, or null while browsing all of them. */
    var open: AnimationPreset? by mutableStateOf(null)
        private set

    private val chosenFrameMs = mutableStateMapOf<String, Int>()

    fun open(preset: AnimationPreset) {
        open = preset
    }

    fun close() {
        open = null
    }

    /** Milliseconds between frames for [preset]: the user's choice, or its default. */
    fun frameMs(preset: AnimationPreset): Int = chosenFrameMs[preset.id] ?: preset.defaultFrameMs

    fun setFrameMs(preset: AnimationPreset, frameMs: Int) {
        chosenFrameMs[preset.id] = frameMs.coerceIn(AnimationPresets.MIN_FRAME_MS, AnimationPresets.MAX_FRAME_MS)
    }
}
