package com.unofficialpixstudio.animation

import com.unofficialpixstudio.ble.PixAnimation
import com.unofficialpixstudio.ble.PixCommandEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AnimationPresetsTest {

    private val sizes = listOf(16 to 20, 12 to 12, 22 to 23)

    @Test
    fun presetsHaveUniqueIdsAndSensibleSpeeds() {
        assertEquals(5, AnimationPresets.all.size)
        assertEquals(AnimationPresets.all.size, AnimationPresets.all.map { it.id }.toSet().size)
        for (preset in AnimationPresets.all) {
            assertTrue(preset.name, preset.defaultFrameMs in AnimationPresets.MIN_FRAME_MS..AnimationPresets.MAX_FRAME_MS)
            assertEquals(preset, AnimationPresets.byId(preset.id))
        }
    }

    @Test
    fun everyPresetFitsTheDisplaysLimits() {
        for (preset in AnimationPresets.all) {
            for ((width, height) in sizes) {
                val label = "${preset.name} ${width}x$height"
                val frames = preset.frames(width, height)
                assertTrue(label, frames.size in 2..PixAnimation.MAX_FRAMES)
                assertTrue(label, frames.all { it.size == width * height })
                val colours = frames.flatMap { it.asIterable() }.toSet()
                assertTrue("$label uses ${colours.size} colours", colours.size <= 256)
                assertTrue(label, colours.all { it in 0..0xFFFFFF })

                // The real upload accepts it: one packet per frame plus the fixed commands.
                val animation = PixAnimation(width, height, frames, List(frames.size) { preset.defaultFrameMs })
                val packets = PixCommandEncoder().uploadAnimation(animation)
                assertTrue(label, packets.size >= frames.size + 5)
            }
        }
    }

    @Test
    fun keepsMoving() {
        // A pause is fine (the heart rests between beats), but at least half the steps should change something.
        for (preset in AnimationPresets.all) {
            val frames = preset.frames(16, 20)
            val moving = frames.indices.count { !frames[it].contentEquals(frames[(it + 1) % frames.size]) }
            assertTrue("${preset.name}: only $moving of ${frames.size} steps move", moving >= frames.size / 2)
        }
    }

    @Test
    fun loopsWithoutAJump() {
        // Going from the last frame back to the first changes no more than an ordinary step.
        for (preset in AnimationPresets.all) {
            val frames = preset.frames(16, 20)
            val steps = (0 until frames.size - 1).map { difference(frames[it], frames[it + 1]) }
            val wrap = difference(frames.last(), frames.first())
            assertTrue("${preset.name}: wrap $wrap vs largest step ${steps.max()}", wrap <= steps.max() * 1.05)
        }
    }

    @Test
    fun sameFramesEveryTime() {
        for (preset in AnimationPresets.all) {
            val first = preset.frames(16, 20)
            val second = preset.frames(16, 20)
            assertTrue(preset.name, first.indices.all { first[it].contentEquals(second[it]) })
        }
    }

    /** Mean per-channel difference between two frames. */
    private fun difference(a: IntArray, b: IntArray): Double {
        var sum = 0L
        for (i in a.indices) {
            for (shift in intArrayOf(16, 8, 0)) sum += abs(((a[i] shr shift) and 0xFF) - ((b[i] shr shift) and 0xFF))
        }
        return sum / (a.size * 3.0)
    }
}
