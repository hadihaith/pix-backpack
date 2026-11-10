package com.unofficialpixstudio.image

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure-Kotlin pixel helpers (no Android types, so they're unit-testable).
 * Pixels are ints, row-major: 0xAARRGGBB as input, 0xRRGGBB as output.
 */
object PixelArt {

    /**
     * Shrinks the [crop] region of [src] to [outWidth] × [outHeight] by area averaging: each
     * output pixel is the average of every source pixel it covers, weighted by how much of it
     * is covered. Transparent pixels are composited onto black (the LEDs can't be "clear").
     */
    fun downscale(
        src: IntArray,
        srcWidth: Int,
        srcHeight: Int,
        crop: CropRect,
        outWidth: Int,
        outHeight: Int,
    ): IntArray {
        require(src.size == srcWidth * srcHeight) { "Pixel count doesn't match size" }
        val out = IntArray(outWidth * outHeight)
        val cellWidth = crop.width / outWidth
        val cellHeight = crop.height / outHeight

        for (oy in 0 until outHeight) {
            val y0 = crop.top + oy * cellHeight
            val y1 = y0 + cellHeight
            for (ox in 0 until outWidth) {
                val x0 = crop.left + ox * cellWidth
                val x1 = x0 + cellWidth
                var r = 0.0
                var g = 0.0
                var b = 0.0
                var weightSum = 0.0

                for (sy in max(floor(y0).toInt(), 0) until min(ceil(y1).toInt(), srcHeight)) {
                    val wy = min(y1, sy + 1f) - max(y0, sy.toFloat())
                    if (wy <= 0f) continue
                    for (sx in max(floor(x0).toInt(), 0) until min(ceil(x1).toInt(), srcWidth)) {
                        val wx = min(x1, sx + 1f) - max(x0, sx.toFloat())
                        if (wx <= 0f) continue
                        val weight = (wx * wy).toDouble()
                        val p = src[sy * srcWidth + sx]
                        val alpha = ((p ushr 24) and 0xFF) / 255.0
                        r += ((p shr 16) and 0xFF) * alpha * weight
                        g += ((p shr 8) and 0xFF) * alpha * weight
                        b += (p and 0xFF) * alpha * weight
                        weightSum += weight
                    }
                }
                out[oy * outWidth + ox] = if (weightSum > 0) {
                    rgb(r / weightSum, g / weightSum, b / weightSum)
                } else {
                    0
                }
            }
        }
        return out
    }

    /**
     * Reduces [pixels] to at most [maxColors] distinct colours (a median-cut variant):
     * repeatedly split the group of colours with the widest channel range at its pixel-weighted
     * mean, then replace each group by its weighted average. Returns the input (alpha
     * stripped) unchanged if it already fits.
     */
    fun quantize(pixels: IntArray, maxColors: Int = 256): IntArray {
        require(maxColors >= 1)
        val counts = HashMap<Int, Int>()
        for (p in pixels) counts.merge(p and 0xFFFFFF, 1, Int::plus)
        if (counts.size <= maxColors) return IntArray(pixels.size) { pixels[it] and 0xFFFFFF }

        val boxes = mutableListOf(ColorBox(counts.keys.toList()))
        while (boxes.size < maxColors) {
            val widest = boxes.withIndex()
                .filter { it.value.colors.size > 1 }
                .maxByOrNull { it.value.range } ?: break
            val (first, second) = widest.value.split(counts)
            boxes[widest.index] = first
            boxes += second
        }

        val replacement = HashMap<Int, Int>(counts.size)
        for (box in boxes) {
            val average = box.weightedAverage(counts)
            for (color in box.colors) replacement[color] = average
        }
        return IntArray(pixels.size) { replacement.getValue(pixels[it] and 0xFFFFFF) }
    }

    fun distinctColors(pixels: IntArray): Int {
        val seen = HashSet<Int>()
        for (p in pixels) seen += p and 0xFFFFFF
        return seen.size
    }

    /**
     * How hard an image drives the LEDs, from 0 (all black) to 1 (all white): the mean of
     * every R, G and B value. LED current is roughly proportional to this × brightness.
     */
    fun relativeLoad(pixels: IntArray): Double {
        if (pixels.isEmpty()) return 0.0
        var sum = 0L
        for (p in pixels) sum += ((p shr 16) and 0xFF) + ((p shr 8) and 0xFF) + (p and 0xFF)
        return sum / (pixels.size * 765.0)
    }

    private fun rgb(r: Double, g: Double, b: Double): Int =
        (channel(r) shl 16) or (channel(g) shl 8) or channel(b)

    private fun channel(value: Double): Int = value.roundToInt().coerceIn(0, 255)

    /** A group of distinct colours, tracking which channel varies most within it. */
    private class ColorBox(val colors: List<Int>) {
        val shift: Int
        val range: Int

        init {
            var best = 16
            var bestRange = -1
            for (s in intArrayOf(16, 8, 0)) {
                var lo = 255
                var hi = 0
                for (c in colors) {
                    val v = (c shr s) and 0xFF
                    if (v < lo) lo = v
                    if (v > hi) hi = v
                }
                if (hi - lo > bestRange) {
                    best = s
                    bestRange = hi - lo
                }
            }
            shift = best
            range = bestRange
        }

        /** Splits at the pixel-weighted mean of the widest channel, so near colours stay together. */
        fun split(counts: Map<Int, Int>): Pair<ColorBox, ColorBox> {
            val sorted = colors.sortedBy { (it shr shift) and 0xFF }
            var weightedSum = 0.0
            var total = 0.0
            for (c in sorted) {
                val w = counts.getValue(c).toDouble()
                weightedSum += ((c shr shift) and 0xFF) * w
                total += w
            }
            val mean = weightedSum / total
            var at = sorted.indexOfFirst { ((it shr shift) and 0xFF) > mean }
            if (at == -1) at = sorted.size / 2
            at = at.coerceIn(1, sorted.size - 1)
            return ColorBox(sorted.subList(0, at)) to ColorBox(sorted.subList(at, sorted.size))
        }

        fun weightedAverage(counts: Map<Int, Int>): Int {
            var r = 0.0
            var g = 0.0
            var b = 0.0
            var n = 0.0
            for (c in colors) {
                val w = counts.getValue(c).toDouble()
                r += ((c shr 16) and 0xFF) * w
                g += ((c shr 8) and 0xFF) * w
                b += (c and 0xFF) * w
                n += w
            }
            return rgb(r / n, g / n, b / n)
        }
    }
}
