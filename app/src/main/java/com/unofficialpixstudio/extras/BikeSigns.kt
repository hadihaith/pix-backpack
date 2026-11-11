package com.unofficialpixstudio.extras

import com.unofficialpixstudio.animation.AnimationPreset
import com.unofficialpixstudio.animation.TAU
import com.unofficialpixstudio.animation.at
import com.unofficialpixstudio.animation.ramp
import com.unofficialpixstudio.animation.scale
import com.unofficialpixstudio.animation.smooth
import kotlin.math.abs
import kotlin.math.cos

/**
 * Signs for riding with the backpack on: turn arrows and a few road signs, drawn for whatever
 * size the display is. Each is a short loop in one or two colours, big and plain enough to
 * read at a glance from behind. Pure Kotlin, like the animation presets.
 */
object BikeSigns {

    val all: List<AnimationPreset> = listOf(
        AnimationPreset("turn-left", "Turn left", "An arrow sliding to the left", 35) { w, h -> arrow(w, h, pointsRight = false) },
        AnimationPreset("turn-right", "Turn right", "An arrow sliding to the right", 35) { w, h -> arrow(w, h, pointsRight = true) },
        AnimationPreset("stop", "Stop", "A red stop sign", 45, ::stop),
        AnimationPreset("caution", "Caution", "A warning triangle", 45, ::caution),
        AnimationPreset("slow", "Slow", "Asks whoever is behind to slow down", 45, ::slow),
    )

    fun byId(id: String): AnimationPreset? = all.firstOrNull { it.id == id }

    private const val AMBER = 0xFFA000
    private const val RED = 0xFF1010
    private const val WHITE = 0xFFFFFF

    /** Samples per LED each way, to soften slanted edges. */
    private const val SAMPLES = 4

    // ---- Turn arrows: an arrow that appears, slides the way it points, and fades out. ----

    private fun arrow(width: Int, height: Int, pointsRight: Boolean): List<IntArray> {
        val frameCount = 32
        val palette = ramp(96) { scale(AMBER, it) }
        val length = minOf(width * 0.72f, height * 1.1f)
        val way = if (pointsRight) 1f else -1f
        return List(frameCount) { f ->
            val u = f.toFloat() / frameCount
            // Quick to appear, slower to fade; dark at the very start, so the loop has no jump.
            val light = smooth((u / 0.2f).coerceAtMost(1f)) * smooth(((1f - u) / 0.5f).coerceAtMost(1f))
            // Far enough to see it move, not so far that the tip leaves the display.
            val slide = way * (width - length) * (u - 0.5f)
            IntArray(width * height) { i ->
                val cover = coverage(i % width, i / width) { px, py ->
                    // Along the arrow from tail (-0.5) to tip (+0.5), and how far off its centre line.
                    val along = way * (px - width / 2f - slide) / length
                    val off = abs(py - height / 2f) / length
                    (along in -0.5f..0.08f && off <= 0.15f) || (along in 0f..0.5f && off <= (0.5f - along) * 0.9f)
                }
                palette.at(cover * light)
            }
        }
    }

    // ---- Stop: a red octagon, STOP across it where there's room, pulsing gently. ----

    private fun stop(width: Int, height: Int): List<IntArray> {
        val frameCount = 24
        val palette = ramp(96) { scale(RED, it) }
        val half = minOf(width, height) / 2f
        val words = lettering("STOP", width, height)
        return List(frameCount) { f ->
            val pulse = 0.62f + 0.38f * (0.5f + 0.5f * cos(TAU * f / frameCount))
            IntArray(width * height) { i ->
                if (words != null && words[i]) {
                    WHITE
                } else {
                    val cover = coverage(i % width, i / width) { px, py ->
                        val dx = abs(px - width / 2f)
                        val dy = abs(py - height / 2f)
                        dx <= half && dy <= half && dx + dy <= half * 1.5f
                    }
                    palette.at(cover * pulse)
                }
            }
        }
    }

    // ---- Caution: an amber warning triangle with an exclamation mark, pulsing. ----

    private fun caution(width: Int, height: Int): List<IntArray> {
        val frameCount = 24
        val palette = ramp(96) { scale(AMBER, it) }
        val base = minOf(width.toFloat(), height * 1.1f)
        val tall = base * 0.9f
        val top = (height - tall) / 2f
        val stroke = (base / 16f).coerceAtLeast(1f) // half the width of the exclamation mark
        return List(frameCount) { f ->
            val pulse = 0.5f + 0.5f * (0.5f + 0.5f * cos(TAU * f / frameCount))
            IntArray(width * height) { i ->
                val cover = coverage(i % width, i / width) { px, py ->
                    val down = (py - top) / tall // 0 at the point, 1 at the base
                    val dx = abs(px - width / 2f)
                    val inTriangle = down in 0f..1f && dx <= down * base / 2f
                    val inMark = dx <= stroke && (down in 0.36f..0.72f || down in 0.8f..0.92f)
                    inTriangle && !inMark
                }
                palette.at(cover * pulse)
            }
        }
    }

    // ---- Slow: SLOW between two bars, pulsing. ----

    private fun slow(width: Int, height: Int): List<IntArray> {
        val frameCount = 24
        val palette = ramp(96) { scale(AMBER, it) }
        val words = lettering("SLOW", width, height)
        return List(frameCount) { f ->
            val pulse = 0.45f + 0.55f * (0.5f + 0.5f * cos(TAU * f / frameCount))
            IntArray(width * height) { i ->
                val y = i / width
                val onBar = y == 0 || y == height - 1
                when {
                    words != null && words[i] -> palette.at(pulse)
                    // The bars pulse the other way, so something is always bright.
                    onBar -> palette.at(1.45f - pulse)
                    else -> 0
                }
            }
        }
    }

    /** How much of LED ([x], [y]) a shape covers, 0–1. [inside] is asked about points within it. */
    private inline fun coverage(x: Int, y: Int, inside: (px: Float, py: Float) -> Boolean): Float {
        var hits = 0
        for (sy in 0 until SAMPLES) {
            for (sx in 0 until SAMPLES) {
                if (inside(x + (sx + 0.5f) / SAMPLES, y + (sy + 0.5f) / SAMPLES)) hits++
            }
        }
        return hits / (SAMPLES * SAMPLES).toFloat()
    }

    /** 3×5 capitals, only the ones the signs use. */
    private val glyphs = mapOf(
        'S' to arrayOf("XXX", "X..", "XXX", "..X", "XXX"),
        'T' to arrayOf("XXX", ".X.", ".X.", ".X.", ".X."),
        'O' to arrayOf("XXX", "X.X", "X.X", "X.X", "XXX"),
        'P' to arrayOf("XXX", "X.X", "XXX", "X..", "X.."),
        'L' to arrayOf("X..", "X..", "X..", "X..", "XXX"),
        'W' to arrayOf("X.X", "X.X", "X.X", "XXX", "X.X"),
    )

    /**
     * Which LEDs spell [text] across the middle of the display, as large as fits and twice as
     * tall as wide so it reads from a distance. Null if the display is too narrow for it.
     */
    private fun lettering(text: String, width: Int, height: Int): BooleanArray? {
        val columns = text.length * 4 - 1 // three per letter and a gap between
        val wide = minOf(width / columns, height / 10)
        if (wide < 1) return null
        val tall = 2 * wide
        val left = (width - columns * wide) / 2
        val top = (height - 5 * tall) / 2
        val lit = BooleanArray(width * height)
        text.forEachIndexed { n, letter ->
            val rows = glyphs.getValue(letter)
            for (row in 0 until 5) {
                for (column in 0 until 3) {
                    if (rows[row][column] != 'X') continue
                    for (dy in 0 until tall) {
                        for (dx in 0 until wide) {
                            lit[(top + row * tall + dy) * width + left + (n * 4 + column) * wide + dx] = true
                        }
                    }
                }
            }
        }
        return lit
    }
}
