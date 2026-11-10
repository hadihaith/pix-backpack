package com.unofficialpixstudio.image

import org.junit.Assert.assertEquals
import org.junit.Test

class CropGeometryTest {

    // 16×20 display (aspect 0.8) over a 1000×1000 image: full crop is 800×1000.
    private val geometry = CropGeometry(srcWidth = 1000, srcHeight = 1000, outWidth = 16, outHeight = 20)

    @Test
    fun initialCropIsLargestCenteredWindow() {
        assertEquals(CropRect(100f, 0f, 800f, 1000f), geometry.rect(geometry.initial()))
    }

    @Test
    fun maxZoomKeepsOneSourcePixelPerLed() {
        assertEquals(50f, geometry.maxZoom) // 800 px wide / 16 LEDs
    }

    @Test
    fun clampKeepsWindowInsideImage() {
        val clamped = geometry.clamp(CropState(centerX = 0f, centerY = 5000f, zoom = 2f))
        val rect = geometry.rect(clamped)
        assertEquals(0f, rect.left)
        assertEquals(1000f, rect.top + rect.height, 0.01f)
    }

    @Test
    fun zoomKeepsPointUnderCentroid() {
        val start = geometry.initial()
        // Box 400×500 screen px shows the 800×1000 window: 0.5 screen px per source px.
        // Pinch 2× around the box centre: the centre stays put.
        val zoomed = geometry.transform(start, 400f, 500f, 200f, 250f, 0f, 0f, 2f)
        assertEquals(500f, zoomed.centerX, 0.01f)
        assertEquals(500f, zoomed.centerY, 0.01f)
        assertEquals(2f, zoomed.zoom)
    }

    @Test
    fun panMovesOppositeToDrag() {
        val zoomed = CropState(500f, 500f, 2f) // window 400×500 source px
        // At 1 screen px per source px, dragging right by 50 moves the window left by 50.
        val panned = geometry.transform(zoomed, 400f, 500f, 200f, 250f, 50f, 0f, 1f)
        assertEquals(450f, panned.centerX, 0.01f)
    }
}
