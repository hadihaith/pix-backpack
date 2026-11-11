package com.unofficialpixstudio.draw

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class PixelEditorTest {

    private val red = 0xFF0000
    private val blue = 0x0000FF

    /** Rows of the drawing as strings: '.' for off, 'R'/'B' for red/blue, '?' otherwise. */
    private fun PixelEditor.rows(): List<String> = (0 until height).map { y ->
        (0 until width).joinToString("") { x ->
            when (pixelAt(x, y)) {
                0 -> "."
                red -> "R"
                blue -> "B"
                else -> "?"
            }
        }
    }

    @Test
    fun strokeJoinsPointsWithoutGaps() {
        val editor = PixelEditor(5, 3)
        editor.beginStroke()
        editor.strokeTo(0, 0, red, mirror = false)
        editor.strokeTo(4, 2, red, mirror = false) // one big jump, as from a fast drag
        editor.endStroke()
        assertEquals(listOf("R....", ".RR..", "...RR"), editor.rows())
    }

    @Test
    fun liftingThePenStartsANewLineInTheSameStroke() {
        val editor = PixelEditor(5, 1)
        editor.beginStroke()
        editor.strokeTo(0, 0, red, mirror = false)
        editor.liftPen()
        editor.strokeTo(4, 0, red, mirror = false)
        editor.endStroke()
        assertEquals(listOf("R...R"), editor.rows())

        editor.undo()
        assertTrue(editor.isBlank) // both points went in one undo step
    }

    @Test
    fun mirrorDrawsTheReflectionToo() {
        val editor = PixelEditor(5, 2)
        editor.beginStroke()
        editor.strokeTo(0, 1, red, mirror = true)
        editor.endStroke()
        assertEquals(listOf(".....", "R...R"), editor.rows())
    }

    @Test
    fun fillStopsAtOtherColours() {
        val editor = PixelEditor(5, 3)
        editor.beginStroke()
        editor.strokeTo(2, 0, red, mirror = false)
        editor.strokeTo(2, 2, red, mirror = false)
        editor.endStroke()

        editor.fill(0, 1, blue, mirror = false)
        assertEquals(listOf("BBR..", "BBR..", "BBR.."), editor.rows())
    }

    @Test
    fun mirroredFillFillsBothSides() {
        val editor = PixelEditor(5, 1, intArrayOf(0, red, 0, red, 0))
        editor.fill(0, 0, blue, mirror = true)
        assertEquals(listOf("BR.RB"), editor.rows())
    }

    @Test
    fun noChangeAddsNoUndoStep() {
        val editor = PixelEditor(3, 1)
        editor.fill(0, 0, 0, mirror = false) // already off
        editor.beginStroke()
        editor.strokeTo(1, 0, 0, mirror = false) // erasing nothing
        editor.endStroke()
        assertFalse(editor.canUndo)
    }

    @Test
    fun undoAndRedo() {
        val editor = PixelEditor(3, 1)
        editor.beginStroke()
        editor.strokeTo(0, 0, red, mirror = false)
        editor.endStroke()
        editor.clear()

        assertTrue(editor.undo())
        assertEquals(listOf("R.."), editor.rows())
        assertTrue(editor.undo())
        assertEquals(listOf("..."), editor.rows())
        assertFalse(editor.undo())

        assertTrue(editor.redo())
        assertEquals(listOf("R.."), editor.rows())

        // A new change drops what could be redone.
        editor.fill(2, 0, blue, mirror = false)
        assertFalse(editor.canRedo)
    }

    @Test
    fun historyIsCapped() {
        val editor = PixelEditor(1, 1)
        repeat(PixelEditor.MAX_HISTORY + 10) { i ->
            editor.fill(0, 0, i + 1, mirror = false)
        }
        var undone = 0
        while (editor.undo()) undone++
        assertEquals(PixelEditor.MAX_HISTORY, undone)
    }

    @Test
    fun resizeKeepsTheDrawingCentredAndIsUndoable() {
        val editor = PixelEditor(2, 2, intArrayOf(red, red, red, red))
        editor.resize(4, 3)
        assertEquals(4, editor.width)
        assertEquals(listOf(".RR.", ".RR.", "...."), editor.rows())

        editor.undo()
        assertEquals(2, editor.width)
        assertEquals(listOf("RR", "RR"), editor.rows())
    }

    @Test
    fun coloursLoseAlpha() {
        val editor = PixelEditor(1, 1, intArrayOf(0xFF00FF00.toInt()))
        assertEquals(0x00FF00, editor.pixelAt(0, 0))
    }

    @Test
    fun drawingCodecRoundTrip() {
        val pixels = IntArray(16 * 20) { (it * 0x030507) and 0xFFFFFF }
        val bytes = ByteArrayOutputStream().also { DrawingCodec.write(Drawing(16, 20, pixels), it) }.toByteArray()
        val restored = DrawingCodec.read(ByteArrayInputStream(bytes))
        assertEquals(16, restored.width)
        assertEquals(20, restored.height)
        assertArrayEquals(pixels, restored.pixels)
    }

    @Test(expected = IOException::class)
    fun drawingCodecRejectsOtherData() {
        DrawingCodec.read(ByteArrayInputStream("not a drawing".toByteArray()))
    }
}
