package com.unofficialpixstudio.draw

import kotlin.math.abs

/**
 * The drawing behind the Draw tab: strokes, flood fill, mirroring and undo/redo. Pure Kotlin
 * (no Android types), so it's unit-testable. Not thread-safe; use from one thread.
 *
 * Pixels are 0xRRGGBB, row-major. Black (0) is an LED switched off, so erasing paints black.
 */
class PixelEditor(width: Int, height: Int, pixels: IntArray = IntArray(width * height)) {

    private class Snapshot(val width: Int, val height: Int, val pixels: IntArray)

    var width: Int = width
        private set
    var height: Int = height
        private set
    private var data: IntArray = checked(width, height, pixels)

    private val undoStack = ArrayDeque<Snapshot>()
    private val redoStack = ArrayDeque<Snapshot>()

    /** State before the stroke in progress, or null when not stroking. */
    private var strokeStart: Snapshot? = null
    /** Last point of the stroke, so fast drags draw connected lines; -1 when the pen is up. */
    private var penX = -1
    private var penY = -1

    /** A copy of the current pixels. */
    val pixels: IntArray get() = data.copyOf()
    val isBlank: Boolean get() = data.all { it == 0 }
    val isStroking: Boolean get() = strokeStart != null
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun pixelAt(x: Int, y: Int): Int {
        require(x in 0 until width && y in 0 until height) { "($x, $y) is outside the drawing" }
        return data[y * width + x]
    }

    /** Starts a stroke; everything drawn until [endStroke] is undone in one step. */
    fun beginStroke() {
        if (strokeStart == null) strokeStart = snapshot()
        liftPen()
    }

    /**
     * Draws to ([x], [y]): a single pixel if the pen was up, otherwise a line from the previous
     * point, so no gaps appear when the finger moves faster than one cell per touch event.
     * With [mirror], also draws the left–right reflection.
     */
    fun strokeTo(x: Int, y: Int, color: Int, mirror: Boolean) {
        if (strokeStart == null) beginStroke()
        if (penX < 0) {
            plot(x, y, color, mirror)
        } else {
            line(penX, penY, x, y) { px, py -> plot(px, py, color, mirror) }
        }
        penX = x
        penY = y
    }

    /** The finger left the drawing; the next [strokeTo] starts a new line in the same stroke. */
    fun liftPen() {
        penX = -1
        penY = -1
    }

    fun endStroke() {
        val start = strokeStart ?: return
        strokeStart = null
        liftPen()
        commit(start)
    }

    /** Fills the area of same-coloured pixels around ([x], [y]) (4-connected). */
    fun fill(x: Int, y: Int, color: Int, mirror: Boolean) {
        endStroke()
        val before = snapshot()
        floodFill(x, y, color and RGB)
        if (mirror) floodFill(width - 1 - x, y, color and RGB)
        commit(before)
    }

    fun clear() {
        endStroke()
        val before = snapshot()
        data.fill(0)
        commit(before)
    }

    /** Replaces the whole drawing, possibly with a different size. Undoable. */
    fun replace(width: Int, height: Int, pixels: IntArray) {
        endStroke()
        val before = snapshot()
        set(width, height, pixels)
        commit(before)
    }

    /** Changes the size, keeping the drawing centred (cropping or adding blank edges). Undoable. */
    fun resize(width: Int, height: Int) {
        if (width == this.width && height == this.height) return
        replace(width, height, centred(data, this.width, this.height, width, height))
    }

    /** Starts over with [pixels] (blank by default) and no undo history. */
    fun reset(width: Int, height: Int, pixels: IntArray = IntArray(width * height)) {
        set(width, height, pixels)
        undoStack.clear()
        redoStack.clear()
        strokeStart = null
        liftPen()
    }

    fun undo(): Boolean {
        endStroke()
        val previous = undoStack.removeLastOrNull() ?: return false
        redoStack.addLast(snapshot())
        restore(previous)
        return true
    }

    fun redo(): Boolean {
        endStroke()
        val next = redoStack.removeLastOrNull() ?: return false
        undoStack.addLast(snapshot())
        restore(next)
        return true
    }

    private fun snapshot() = Snapshot(width, height, data.copyOf())

    private fun restore(snapshot: Snapshot) = set(snapshot.width, snapshot.height, snapshot.pixels)

    private fun set(width: Int, height: Int, pixels: IntArray) {
        data = checked(width, height, pixels)
        this.width = width
        this.height = height
    }

    /** Records [before] as an undo step if anything changed since. */
    private fun commit(before: Snapshot) {
        val unchanged = before.width == width && before.height == height && before.pixels.contentEquals(data)
        if (unchanged) return
        undoStack.addLast(before)
        if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()
    }

    private fun plot(x: Int, y: Int, color: Int, mirror: Boolean) {
        setPixel(x, y, color and RGB)
        if (mirror) setPixel(width - 1 - x, y, color and RGB)
    }

    private fun setPixel(x: Int, y: Int, color: Int) {
        if (x in 0 until width && y in 0 until height) data[y * width + x] = color
    }

    private fun floodFill(x: Int, y: Int, color: Int) {
        if (x !in 0 until width || y !in 0 until height) return
        val start = y * width + x
        val target = data[start]
        if (target == color) return
        // Pixels are recoloured as they're queued, so each is visited once.
        val stack = ArrayDeque<Int>()
        fun visit(i: Int) {
            if (data[i] == target) {
                data[i] = color
                stack.addLast(i)
            }
        }
        visit(start)
        while (stack.isNotEmpty()) {
            val i = stack.removeLast()
            val px = i % width
            val py = i / width
            if (px > 0) visit(i - 1)
            if (px < width - 1) visit(i + 1)
            if (py > 0) visit(i - width)
            if (py < height - 1) visit(i + width)
        }
    }

    companion object {
        /** Undo steps kept; each is a copy of the drawing (~1 KB at 16×20). */
        const val MAX_HISTORY = 100

        private const val RGB = 0xFFFFFF

        private fun checked(width: Int, height: Int, pixels: IntArray): IntArray {
            require(width > 0 && height > 0 && pixels.size == width * height) { "Pixels don't match size" }
            return IntArray(pixels.size) { pixels[it] and RGB }
        }

        /** Calls [visit] for each cell of the line from (x0, y0) to (x1, y1), ends included (Bresenham). */
        fun line(x0: Int, y0: Int, x1: Int, y1: Int, visit: (Int, Int) -> Unit) {
            val dx = abs(x1 - x0)
            val dy = -abs(y1 - y0)
            val sx = if (x0 < x1) 1 else -1
            val sy = if (y0 < y1) 1 else -1
            var err = dx + dy
            var x = x0
            var y = y0
            while (true) {
                visit(x, y)
                if (x == x1 && y == y1) return
                val e2 = 2 * err
                if (e2 >= dy) {
                    err += dy
                    x += sx
                }
                if (e2 <= dx) {
                    err += dx
                    y += sy
                }
            }
        }

        /** [src] placed in the middle of a blank [dstWidth] × [dstHeight] image, cropped to fit. */
        fun centred(src: IntArray, srcWidth: Int, srcHeight: Int, dstWidth: Int, dstHeight: Int): IntArray {
            val out = IntArray(dstWidth * dstHeight)
            val offsetX = (dstWidth - srcWidth) / 2
            val offsetY = (dstHeight - srcHeight) / 2
            for (y in 0 until srcHeight) {
                val ty = y + offsetY
                if (ty !in 0 until dstHeight) continue
                for (x in 0 until srcWidth) {
                    val tx = x + offsetX
                    if (tx in 0 until dstWidth) out[ty * dstWidth + tx] = src[y * srcWidth + x]
                }
            }
            return out
        }
    }
}
