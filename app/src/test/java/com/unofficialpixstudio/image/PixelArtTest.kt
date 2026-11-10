package com.unofficialpixstudio.image

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelArtTest {

    private val opaque = 0xFF000000.toInt()

    @Test
    fun downscaleAveragesCoveredPixels() {
        // 2×2 → 1×1: average of red, green, blue, white.
        val src = intArrayOf(0xFF0000, 0x00FF00, 0x0000FF, 0xFFFFFF).map { it or opaque }.toIntArray()
        val out = PixelArt.downscale(src, 2, 2, CropRect(0f, 0f, 2f, 2f), 1, 1)
        assertArrayEquals(intArrayOf(0x808080), out) // 127.5 rounds to 128
    }

    @Test
    fun downscaleWeightsPartialPixels() {
        // 1×1 output covering [0, 1.5) of a black|white row: 1 black + 0.5 white → 85.
        val src = intArrayOf(0x000000 or opaque, 0xFFFFFF or opaque)
        val out = PixelArt.downscale(src, 2, 1, CropRect(0f, 0f, 1.5f, 1f), 1, 1)
        assertArrayEquals(intArrayOf(0x555555), out)
    }

    @Test
    fun downscaleTreatsTransparentAsBlack() {
        val out = PixelArt.downscale(intArrayOf(0x00FFFFFF), 1, 1, CropRect(0f, 0f, 1f, 1f), 1, 1)
        assertArrayEquals(intArrayOf(0), out)
    }

    @Test
    fun quantizeLeavesSmallPalettesAlone() {
        val pixels = intArrayOf(0xFF112233.toInt(), 0x445566, 0x112233)
        assertArrayEquals(intArrayOf(0x112233, 0x445566, 0x112233), PixelArt.quantize(pixels))
    }

    @Test
    fun quantizeReducesToMaxColors() {
        val pixels = IntArray(320) { (it * 0x010203) and 0xFFFFFF } // 320 distinct colours
        val result = PixelArt.quantize(pixels, maxColors = 256)
        assertTrue(PixelArt.distinctColors(result) <= 256)
        assertEquals(pixels.size, result.size)
    }

    @Test
    fun quantizeMergesSimilarColorsFirst() {
        // Two near-reds and a blue down to 2 colours: the reds merge, blue survives.
        val pixels = intArrayOf(0xFF0000, 0xFE0000, 0x0000FF)
        val result = PixelArt.quantize(pixels, maxColors = 2)
        assertEquals(result[0], result[1])
        assertEquals(0x0000FF, result[2])
    }

    @Test
    fun relativeLoadRange() {
        assertEquals(0.0, PixelArt.relativeLoad(intArrayOf(0, 0)), 1e-9)
        assertEquals(1.0, PixelArt.relativeLoad(intArrayOf(0xFFFFFF)), 1e-9)
        assertEquals(1.0 / 3, PixelArt.relativeLoad(intArrayOf(0xFF0000)), 1e-9)
    }
}
