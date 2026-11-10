package com.unofficialpixstudio.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PixProtocolTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun frameMatchesDocumentedExample() {
        // pix-backpack-go example: call 3, opcode 00, frame 0, offset 0, indices 0,1,2,0.
        val encoder = PixCommandEncoder()
        repeat(3) { encoder.direction(0) }
        assertArrayEquals(
            bytes(0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x02, 0x00),
            encoder.frame(index = 0, offset = 0, indices = bytes(0, 1, 2, 0)),
        )
    }

    @Test
    fun durationsAreLittleEndianMillis() {
        val packet = PixCommandEncoder().durations(offset = 0, durationsMs = listOf(300, 1000))
        assertArrayEquals(bytes(0x00, 0x03, 0x00, 0x00, 0x2C, 0x01, 0xE8, 0x03), packet)
    }

    @Test
    fun setModeNone() {
        assertArrayEquals(bytes(0x00, 0x07, 0x00, 0x00, 0x00), PixCommandEncoder().setMode(PixMode.NONE))
    }

    @Test
    fun paletteIsRgbTriplets() {
        val packet = PixCommandEncoder().palette(start = 0, colors = listOf(0xFF0000, 0x00FF80))
        assertArrayEquals(bytes(0x00, 0x01, 0x00, 0xFF, 0x00, 0x00, 0x00, 0xFF, 0x80), packet)
    }

    @Test
    fun callIdWrapsAt256() {
        val encoder = PixCommandEncoder()
        repeat(256) { encoder.direction(0) }
        assertEquals(0, encoder.direction(0)[0].toInt())
    }

    @Test
    fun brightnessConversionMatchesObservedValue() {
        // The backpack read back 0x4C (76) after being set to 30 %.
        assertEquals(76, PixBrightness.percentToRaw(30))
        assertEquals(255, PixBrightness.percentToRaw(100))
        assertEquals(30, PixBrightness.rawToPercent(76))
    }

    @Test
    fun brightnessPercentSurvivesRoundTrip() {
        for (percent in 0..100) {
            assertEquals(percent, PixBrightness.rawToPercent(PixBrightness.percentToRaw(percent)))
        }
    }

    @Test
    fun brightnessLimitScalesWithImageLoad() {
        val max = PixBrightness.MAX_SAFE_PERCENT
        assertEquals(max, PixBrightness.maxSafeFor(pixTestPattern(16, 20)))
        assertEquals(max, PixBrightness.maxSafeFor(IntArray(320))) // all black
        val white = PixBrightness.maxSafeFor(IntArray(320) { 0xFFFFFF })
        // All-white draws more than the rainbow test pattern, so it gets a lower limit.
        assert(white in 1 until max) { "white limit was $white" }
    }

    @Test
    fun uploadSequenceForSingleFrame() {
        val frame = IntArray(4) { if (it % 2 == 0) 0xFF0000 else 0x0000FF }
        val packets = PixCommandEncoder().uploadAnimation(
            PixAnimation(width = 2, height = 2, frames = listOf(frame), frameDurationsMs = listOf(1000))
        )
        val expected = listOf(
            bytes(0x00, 0x07, 0x00, 0x00, 0x00), // mode NONE
            bytes(0x01, 0x02, 0x00), // frame count 0
            bytes(0x02, 0x01, 0x00, 0xFF, 0x00, 0x00, 0x00, 0x00, 0xFF), // palette red, blue
            bytes(0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01), // frame 0
            bytes(0x04, 0x03, 0x00, 0x00, 0xE8, 0x03), // durations
            bytes(0x05, 0x04, 0x00), // direction normal
            bytes(0x06, 0x02, 0x01), // frame count 1
        )
        assertEquals(expected.size, packets.size)
        expected.zip(packets).forEach { (e, a) -> assertArrayEquals(e, a) }
    }

    @Test
    fun largePaletteSplitsAt160Colours() {
        val frame = IntArray(200) { it }
        val packets = PixCommandEncoder().uploadAnimation(
            PixAnimation(width = 20, height = 10, frames = listOf(frame), frameDurationsMs = listOf(100))
        )
        val palettes = packets.filter { it[1].toInt() == 0x01 }
        assertEquals(2, palettes.size)
        assertEquals(3 + 160 * 3, palettes[0].size) // 483 bytes, as documented
        assertEquals(160, palettes[1][2].toInt() and 0xFF) // second chunk starts at slot 160
    }

    @Test
    fun testPatternFitsPaletteAndWrite() {
        val pattern = pixTestPattern(16, 20)
        val packets = PixCommandEncoder().uploadAnimation(
            PixAnimation(16, 20, listOf(pattern), listOf(1000))
        )
        val framePacket = packets.single { it[1].toInt() == 0x00 }
        assertEquals(326, framePacket.size) // 6-byte header + 320 pixels, as documented
        assertEquals(0xFFFFFF, pattern[0])
    }
}
