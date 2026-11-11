package com.unofficialpixstudio.extras

import com.unofficialpixstudio.animation.AnimationPresets
import com.unofficialpixstudio.ble.PixAnimation
import com.unofficialpixstudio.ble.PixCommandEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class BikeSignsTest {

    // The last is too small for lettering, which the signs must cope with.
    private val sizes = listOf(16 to 20, 22 to 23, 30 to 16, 12 to 12)

    @Test
    fun signsHaveUniqueIdsAndSensibleSpeeds() {
        assertEquals(BikeSigns.all.size, BikeSigns.all.map { it.id }.toSet().size)
        for (sign in BikeSigns.all) {
            assertTrue(sign.name, sign.defaultFrameMs in AnimationPresets.MIN_FRAME_MS..AnimationPresets.MAX_FRAME_MS)
            assertEquals(sign, BikeSigns.byId(sign.id))
        }
    }

    @Test
    fun everySignFitsTheDisplaysLimits() {
        for (sign in BikeSigns.all) {
            for ((width, height) in sizes) {
                val label = "${sign.name} ${width}x$height"
                val frames = sign.frames(width, height)
                assertTrue(label, frames.size in 2..PixAnimation.MAX_FRAMES)
                assertTrue(label, frames.all { it.size == width * height })
                val colours = frames.flatMap { it.asIterable() }.toSet()
                assertTrue("$label uses ${colours.size} colours", colours.size <= 256)
                assertTrue("$label never lights up", colours.any { it != 0 })

                val animation = PixAnimation(width, height, frames, List(frames.size) { sign.defaultFrameMs })
                assertTrue(label, PixCommandEncoder().uploadAnimation(animation).size >= frames.size + 5)
            }
        }
    }

    @Test
    fun loopsWithoutAJump() {
        for (sign in BikeSigns.all) {
            val frames = sign.frames(16, 20)
            val steps = (0 until frames.size - 1).map { difference(frames[it], frames[it + 1]) }
            val wrap = difference(frames.last(), frames.first())
            assertTrue("${sign.name}: wrap $wrap vs largest step ${steps.max()}", wrap <= steps.max() * 1.05)
        }
    }

    @Test
    fun arrowsPointOppositeWays() {
        // Half-way through, each arrow is the other's mirror image, with its tip on its own side.
        val (width, height) = 16 to 20
        val left = BikeSigns.byId("turn-left")!!.frames(width, height).let { it[it.size / 2] }
        val right = BikeSigns.byId("turn-right")!!.frames(width, height).let { it[it.size / 2] }
        for (i in left.indices) {
            assertEquals(left[i], right[i / width * width + (width - 1 - i % width)])
        }
        // The tip is the narrow end: fewer LEDs lit in the outermost lit column than at the tail.
        fun litIn(frame: IntArray, x: Int) = (0 until height).count { frame[it * width + x] != 0 }
        val rightColumns = (0 until width).filter { litIn(right, it) > 0 }
        assertTrue(litIn(right, rightColumns.last()) < litIn(right, rightColumns.first()))
    }

    private fun difference(a: IntArray, b: IntArray): Double {
        var sum = 0L
        for (i in a.indices) {
            for (shift in intArrayOf(16, 8, 0)) sum += abs(((a[i] shr shift) and 0xFF) - ((b[i] shr shift) and 0xFF))
        }
        return sum / (a.size * 3.0)
    }
}
