package com.unofficialpixstudio.animation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/**
 * What the Animate tab has selected, kept here so it's still there after switching tabs or
 * rotating: the chosen preset, and the frame time the user set for each one.
 */
class AnimationViewModel : ViewModel() {

    var selected: AnimationPreset by mutableStateOf(AnimationPresets.all.first())
        private set

    private val chosenFrameMs = mutableStateMapOf<String, Int>()

    /** Drawn frames by preset and display size; drawing them again every recomposition would be wasteful. */
    private val drawn = HashMap<String, List<IntArray>>()

    fun select(preset: AnimationPreset) {
        selected = preset
    }

    /** Milliseconds between frames for [preset]: the user's choice, or its default. */
    fun frameMs(preset: AnimationPreset): Int = chosenFrameMs[preset.id] ?: preset.defaultFrameMs

    fun setFrameMs(preset: AnimationPreset, frameMs: Int) {
        chosenFrameMs[preset.id] = frameMs.coerceIn(AnimationPresets.MIN_FRAME_MS, AnimationPresets.MAX_FRAME_MS)
    }

    fun frames(preset: AnimationPreset, width: Int, height: Int): List<IntArray> =
        drawn.getOrPut("${preset.id}/$width/$height") { preset.frames(width, height) }
}
