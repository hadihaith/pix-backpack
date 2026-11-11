package com.unofficialpixstudio.animation

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/* Drawing tools shared by the preset animations. Pure Kotlin, no Android types. */

internal const val TAU = (2 * PI).toFloat()

// ---- Looping shapes and noise ----

/** A smooth bump of height 1 centred on [centre], measured around the loop so it wraps. */
internal fun bump(u: Float, centre: Float, width: Float): Float {
    val d = (u - centre + 1.5f) % 1f - 0.5f
    return exp(-(d / width) * (d / width))
}

/**
 * Smooth 2-D value noise in 0–1 that repeats every [period] units of [y], so scrolling it
 * by exactly one period brings the animation back to where it started.
 */
internal fun loopNoise(x: Float, y: Float, period: Int, seed: Int): Float {
    val x0 = floor(x).toInt()
    val y0 = floor(y).toInt()
    val fx = smooth(x - x0)
    val fy = smooth(y - y0)
    fun at(ix: Int, iy: Int) = hash(ix, Math.floorMod(iy, period), seed)
    val top = at(x0, y0) + (at(x0 + 1, y0) - at(x0, y0)) * fx
    val bottom = at(x0, y0 + 1) + (at(x0 + 1, y0 + 1) - at(x0, y0 + 1)) * fx
    return top + (bottom - top) * fy
}

internal fun smooth(t: Float): Float = t * t * (3f - 2f * t)

/** Repeatable pseudo-random value in 0–1 for a lattice point. */
internal fun hash(x: Int, y: Int, seed: Int): Float {
    var h = x * 374761393 + y * 668265263 + seed * 1442695041
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h and 0xFFFF) / 65535f
}

// ---- Light ----

/**
 * Brightness per LED (0 is off, 1 is full), built up from dots and spots at fractional
 * positions. LED centres sit on whole numbers, so (0, 0) is the middle of the top-left LED.
 */
internal class LightField(private val width: Int, private val height: Int) {
    val light = FloatArray(width * height)

    /** Adds a one-LED dot, shared between the four LEDs around it so it can sit between them. */
    fun dot(x: Float, y: Float, amount: Float) {
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val fx = x - x0
        val fy = y - y0
        add(x0, y0, amount * (1f - fx) * (1f - fy))
        add(x0 + 1, y0, amount * fx * (1f - fy))
        add(x0, y0 + 1, amount * (1f - fx) * fy)
        add(x0 + 1, y0 + 1, amount * fx * fy)
    }

    /** A soft round spot [radius] LEDs across; where spots overlap, the brighter one shows. */
    fun spot(x: Float, y: Float, radius: Float, amount: Float) {
        for (py in floor(y - radius).toInt().coerceAtLeast(0)..ceil(y + radius).toInt().coerceAtMost(height - 1)) {
            for (px in floor(x - radius).toInt().coerceAtLeast(0)..ceil(x + radius).toInt().coerceAtMost(width - 1)) {
                val d2 = ((px - x) * (px - x) + (py - y) * (py - y)) / (radius * radius)
                if (d2 < 1f) {
                    val i = py * width + px
                    light[i] = maxOf(light[i], amount * (1f - d2) * (1f - d2))
                }
            }
        }
    }

    /** The outline of a circle, about one LED thick; overlaps show the brighter, as with [spot]. */
    fun ring(x: Float, y: Float, radius: Float, amount: Float) {
        val reach = radius + 1f
        for (py in floor(y - reach).toInt().coerceAtLeast(0)..ceil(y + reach).toInt().coerceAtMost(height - 1)) {
            for (px in floor(x - reach).toInt().coerceAtLeast(0)..ceil(x + reach).toInt().coerceAtMost(width - 1)) {
                val off = abs(sqrt((px - x) * (px - x) + (py - y) * (py - y)) - radius)
                if (off < 1f) {
                    val i = py * width + px
                    light[i] = maxOf(light[i], amount * (1f - off))
                }
            }
        }
    }

    private fun add(x: Int, y: Int, amount: Float) {
        if (x in 0 until width && y in 0 until height) light[y * width + x] += amount
    }
}

// ---- Colour ----

/** A palette of [levels] colours: [colour] is asked for each position along it, 0–1. */
internal fun ramp(levels: Int, colour: (Float) -> Int): IntArray = IntArray(levels) { colour(it / (levels - 1f)) }

/** The colour [position] (0–1, clamped) of the way along a palette made by [ramp]. */
internal fun IntArray.at(position: Float): Int = this[(position.coerceIn(0f, 1f) * (size - 1)).roundToInt()]

/** [hue] in degrees, [saturation] and [value] 0–1, to 0xRRGGBB. */
internal fun hsv(hue: Float, saturation: Float, value: Float): Int {
    val h = ((hue % 360f) + 360f) % 360f / 60f
    val c = value * saturation
    val x = c * (1f - abs(h % 2f - 1f))
    val m = value - c
    val (r, g, b) = when (h.toInt()) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return rgb(r + m, g + m, b + m)
}

/** [colour] dimmed to [amount] (0–1) of its brightness. */
internal fun scale(colour: Int, amount: Float): Int = rgb(
    ((colour shr 16) and 0xFF) / 255f * amount,
    ((colour shr 8) and 0xFF) / 255f * amount,
    (colour and 0xFF) / 255f * amount,
)

/** The colour [t] (0–1) of the way along evenly spaced [stops]. */
internal fun gradient(t: Float, vararg stops: Int): Int {
    val position = t.coerceIn(0f, 1f) * (stops.size - 1)
    val index = position.toInt().coerceAtMost(stops.size - 2)
    val mix = position - index
    val from = stops[index]
    val to = stops[index + 1]
    fun channel(shift: Int): Float {
        val a = (from shr shift) and 0xFF
        val b = (to shr shift) and 0xFF
        return (a + (b - a) * mix) / 255f
    }
    return rgb(channel(16), channel(8), channel(0))
}

internal fun rgb(r: Float, g: Float, b: Float): Int =
    (channel(r) shl 16) or (channel(g) shl 8) or channel(b)

private fun channel(value: Float): Int = (value * 255f).roundToInt().coerceIn(0, 255)
