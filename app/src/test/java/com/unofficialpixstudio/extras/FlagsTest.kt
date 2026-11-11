package com.unofficialpixstudio.extras

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlagsTest {

    private val width = 16
    private val height = 20

    private fun flag(name: String): IntArray = Flags.byId(Flags.idOf(name))!!.frames(width, height).single()

    private fun IntArray.at(x: Int, y: Int) = this[y * width + x]

    @Test
    fun flagsAreUniqueAndInAlphabeticalOrder() {
        assertEquals(Flags.all.size, Flags.all.map { it.id }.toSet().size)
        assertEquals(Flags.all.map { it.name }.sorted(), Flags.all.map { it.name })
    }

    @Test
    fun everyFlagIsOneStillPictureOfTheRightSize() {
        for (flag in Flags.all) {
            for ((w, h) in listOf(16 to 20, 22 to 23, 12 to 12, 15 to 19)) {
                val frames = flag.frames(w, h)
                assertEquals(flag.name, 1, frames.size)
                assertEquals(flag.name, w * h, frames[0].size)
                assertTrue(flag.name, frames[0].all { it in 0..0xFFFFFF })
                assertTrue("${flag.name} is blank", frames[0].any { it != 0 })
            }
        }
    }

    @Test
    fun stripesDownTheFlagHangSideBySideWithTheTopOnTheLeft() {
        val germany = flag("Germany")
        for (y in 0 until height) {
            assertEquals(0x000000, germany.at(0, y))
            assertEquals(0xDD0000, germany.at(width / 2, y))
            assertEquals(0xFFCE00, germany.at(width - 1, y))
        }
        // Whole LEDs only: no blurred column between two stripes.
        assertEquals(setOf(0x000000, 0xDD0000, 0xFFCE00), germany.toSet())
    }

    @Test
    fun stripesAcrossTheFlagHangOneAboveAnotherWithTheHoistOnTop() {
        val france = flag("France")
        for (x in 0 until width) {
            assertEquals(0x0055A4, france.at(x, 0))
            assertEquals(0xFFFFFF, france.at(x, height / 2))
            assertEquals(0xEF4135, france.at(x, height - 1))
        }
        assertEquals(setOf(0x0055A4, 0xFFFFFF, 0xEF4135), france.toSet())
    }

    @Test
    fun kuwaitHasItsBlackShapeAtTheTop() {
        val kuwait = flag("Kuwait")
        // Black along the hoist, which is the top row; the slant starts at the very corners.
        for (x in 1 until width - 1) assertEquals(0x000000, kuwait.at(x, 0))
        // Below the shape: green, white, red from the left.
        assertEquals(0x007A3D, kuwait.at(0, height - 1))
        assertEquals(0xFFFFFF, kuwait.at(width / 2, height - 1))
        assertEquals(0xCE1126, kuwait.at(width - 1, height - 1))
    }

    @Test
    fun crossesAreTheSameFromBothSides() {
        for (name in listOf("Denmark", "Sweden", "Norway", "Finland", "Iceland", "England", "Switzerland")) {
            val pixels = flag(name)
            for (i in pixels.indices) {
                assertEquals(name, pixels[i], pixels.at(width - 1 - i % width, i / width))
            }
        }
    }

    @Test
    fun switzerlandIsTheSameUpsideDown() {
        val pixels = flag("Switzerland")
        for (i in pixels.indices) assertEquals(pixels[i], pixels.at(i % width, height - 1 - i / width))
    }
}
