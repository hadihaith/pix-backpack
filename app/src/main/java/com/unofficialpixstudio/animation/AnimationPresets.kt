package com.unofficialpixstudio.animation

import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A ready-made animation, drawn for whatever size the display is. Pure Kotlin (no Android
 * types), so presets are unit-testable.
 */
class AnimationPreset(
    val id: String,
    val name: String,
    val description: String,
    /** Milliseconds between frames the preset was designed for; users can change it. */
    val defaultFrameMs: Int,
    private val draw: (width: Int, height: Int) -> List<IntArray>,
) {
    /** Every frame, 0xRRGGBB row-major. Frames loop: the last one leads back into the first. */
    fun frames(width: Int, height: Int): List<IntArray> {
        require(width > 0 && height > 0) { "Display size must be positive" }
        return draw(width, height)
    }
}

/*
 * What keeps these smooth on a display this small:
 *  - many frames with a small change each, rather than a few big jumps;
 *  - motion in fractions of a pixel, drawn by fading LEDs in and out (anti-aliasing);
 *  - every time-dependent term goes through a whole number of cycles per loop, so the last
 *    frame flows into the first with no visible jump.
 *
 * The display has one 256-colour palette shared by all frames, so each preset picks its
 * colours from a small fixed ramp instead of computing free RGB values.
 */
object AnimationPresets {

    /** Slowest and fastest frame time the app offers, in milliseconds. */
    const val MIN_FRAME_MS = 20
    const val MAX_FRAME_MS = 500

    val all: List<AnimationPreset> = listOf(
        AnimationPreset("rainbow", "Rainbow", "Colours flowing across the display", 60, ::rainbow),
        AnimationPreset("plasma", "Plasma", "Slow swirling blues and pinks", 70, ::plasma),
        AnimationPreset("heart", "Heart", "A glowing, beating heart", 35, ::heart),
        AnimationPreset("rain", "Rain", "Drops streaking down", 50, ::rain),
        AnimationPreset("fire", "Fire", "Flickering flames", 60, ::fire),
        AnimationPreset("aurora", "Aurora", "Northern lights rippling overhead", 80, MorePresets::aurora),
        AnimationPreset("ocean", "Ocean", "Waves rolling past", 60, MorePresets::ocean),
        AnimationPreset("lava", "Lava", "Blobs drifting in a lava lamp", 80, MorePresets::lava),
        AnimationPreset("ripples", "Ripples", "Rings spreading across a pond", 60, MorePresets::ripples),
        AnimationPreset("spiral", "Spiral", "A rainbow pinwheel turning", 50, MorePresets::spiral),
        AnimationPreset("twinkle", "Twinkle", "Stars glittering in the night sky", 80, MorePresets::twinkle),
        AnimationPreset("warp", "Warp", "Flying through the stars", 45, MorePresets::warp),
        AnimationPreset("snow", "Snow", "Snowflakes drifting down", 70, MorePresets::snow),
        AnimationPreset("comet", "Comet", "A comet tracing a figure of eight", 40, MorePresets::comet),
        AnimationPreset("fireworks", "Fireworks", "Rockets bursting in three colours", 55, MorePresets::fireworks),
        AnimationPreset("equalizer", "Equalizer", "Music bars bouncing to a beat", 45, MorePresets::equalizer),
        AnimationPreset("dna", "DNA", "A double helix slowly turning", 60, MorePresets::dna),
        AnimationPreset("eyes", "Eyes", "A pair of eyes looking around", 70, MorePresets::eyes),
        AnimationPreset("radar", "Radar", "A sweeping beam picking out blips", 40, MorePresets::radar),
        AnimationPreset("disco", "Disco", "Dance-floor tiles changing colour", 60, MorePresets::disco),
        AnimationPreset("tunnel", "Tunnel", "Rainbow frames rushing outwards", 50, MorePresets::tunnel),
        AnimationPreset("kaleidoscope", "Kaleidoscope", "Mirrored patterns in every colour", 70, MorePresets::kaleidoscope),
        AnimationPreset("galaxy", "Galaxy", "Spiral arms wheeling round a bright core", 80, MorePresets::galaxy),
        AnimationPreset("clouds", "Clouds", "Clouds drifting across a blue sky", 90, MorePresets::clouds),
        AnimationPreset("bubbles", "Bubbles", "Bubbles wobbling up through water", 70, MorePresets::bubbles),
        AnimationPreset("fireflies", "Fireflies", "Glowing dots wandering in the dark", 80, MorePresets::fireflies),
        AnimationPreset("confetti", "Confetti", "Coloured pieces fluttering down", 60, MorePresets::confetti),
        AnimationPreset("bounce", "Bounce", "Three balls bouncing around", 40, MorePresets::bounce),
        AnimationPreset("chase", "Chase", "Lights running round the edge", 40, MorePresets::chase),
        AnimationPreset("pulse", "Pulse", "A heartbeat on a monitor", 35, MorePresets::pulse),
    )

    fun byId(id: String): AnimationPreset? = all.firstOrNull { it.id == id }

    // ---- Rainbow: diagonal bands of hue, shifting one palette step per frame. ----

    private fun rainbow(width: Int, height: Int): List<IntArray> {
        val steps = 60 // hues in the palette = frames in the loop
        val palette = IntArray(steps) { hsv(360f * it / steps, 1f, 1f) }
        return List(steps) { f ->
            IntArray(width * height) { i ->
                val x = i % width
                val y = i / width
                palette[Math.floorMod(x + y - f, steps)]
            }
        }
    }

    // ---- Plasma: overlapping sine waves mapped onto a looping cool-colour ramp. ----

    private fun plasma(width: Int, height: Int): List<IntArray> {
        val frameCount = 64
        val colours = 128
        // Cyan → blue → purple → pink and back, so the ramp has no seam where it wraps.
        val palette = IntArray(colours) { hsv(255f - 70f * cos(TAU * it / colours), 1f, 1f) }
        return List(frameCount) { f ->
            val t = TAU * f / frameCount
            // A point the ripples spread from, itself circling the middle of the display.
            val cx = width / 2f + width / 3f * cos(t)
            val cy = height / 2f + height / 3f * sin(t)
            IntArray(width * height) { i ->
                val x = (i % width).toFloat()
                val y = (i / width).toFloat()
                val distance = sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy))
                val v = sin(x * 0.27f + t) +
                    sin(y * 0.21f - t) +
                    sin((x + y) * 0.16f + 2 * t) +
                    sin(distance * 0.33f - t)
                // v is within -4..4 but mostly near 0, which lands on the ramp's blue; lower
                // values run towards cyan and higher ones towards pink.
                val position = 0.25f + v / 8f * 1.3f
                palette[Math.floorMod((position * colours).roundToInt(), colours)]
            }
        }
    }

    // ---- Heart: an anti-aliased heart that swells twice per loop ("lub-dub"). ----

    private fun heart(width: Int, height: Int): List<IntArray> {
        val frameCount = 30
        val levels = 48
        val palette = IntArray(levels) { scale(0xFF1040, it / (levels - 1f)) }
        val cx = width / 2f
        val cy = height / 2f
        val radius = 0.33f * minOf(width.toFloat(), height * 0.9f)
        return List(frameCount) { f ->
            val u = f.toFloat() / frameCount
            val pulse = bump(u, 0.18f, 0.06f) + 0.6f * bump(u, 0.42f, 0.07f)
            val size = radius * (1f + 0.17f * pulse)
            val glow = 0.72f + 0.28f * pulse.coerceAtMost(1f)
            IntArray(width * height) { i ->
                val x = i % width
                val y = i / width
                val solid = heartCoverage(x, y, cx, cy, size)
                val halo = heartCoverage(x, y, cx, cy, size * 1.28f)
                val light = (solid + (1f - solid) * 0.2f * halo) * glow
                palette[(light * (levels - 1)).roundToInt().coerceIn(0, levels - 1)]
            }
        }
    }

    /** How much of pixel ([px], [py]) a heart of [size] covers, 0–1, by 4×4 supersampling. */
    private fun heartCoverage(px: Int, py: Int, cx: Float, cy: Float, size: Float): Float {
        var inside = 0
        for (sy in 0 until 4) {
            for (sx in 0 until 4) {
                val a = (px + (sx + 0.5f) / 4f - cx) / size
                // Flipped so +b is up; the curve sits a little above its own origin.
                val b = -(py + (sy + 0.5f) / 4f - cy) / size + 0.12f
                val q = a * a + b * b - 1f
                if (q * q * q - a * a * b * b * b <= 0f) inside++
            }
        }
        return inside / 16f
    }

    // ---- Rain: one drop per column, each with a fading tail, at staggered times. ----

    private fun rain(width: Int, height: Int): List<IntArray> {
        val tail = 7f
        val track = height + tail.toInt() + 1 // rows a drop travels before it comes round again
        val frameCount = (2 * track).coerceAtMost(120)
        val levels = 40
        val palette = IntArray(levels) {
            gradient(it / (levels - 1f), 0x000000, 0x001060, 0x0050E0, 0x30C0FF, 0xE8FFFF)
        }
        // Golden-ratio spacing keeps neighbouring columns well apart in time.
        val phase = FloatArray(width) { (it * 0.618034f + 0.11f) % 1f }
        // Every third column falls twice as fast, for a sense of depth.
        val laps = IntArray(width) { if (it % 3 == 1) 2 else 1 }
        return List(frameCount) { f ->
            val u = f.toFloat() / frameCount
            IntArray(width * height) { i ->
                val x = i % width
                val y = i / width
                val head = (u * laps[x] + phase[x]) % 1f * track - 1f
                val behind = head - y
                val light = when {
                    behind <= -1f || behind >= tail -> 0f
                    behind < 0f -> 1f + behind // the head fading in on the next LED down
                    else -> (1f - behind / tail).pow(1.7f)
                }
                palette[(light * (levels - 1)).roundToInt()]
            }
        }
    }

    // ---- Fire: two layers of looping noise rising at different speeds, hottest at the base. ----

    private fun fire(width: Int, height: Int): List<IntArray> {
        val frameCount = 48
        val levels = 64
        val palette = IntArray(levels) {
            gradient(it / (levels - 1f), 0x000000, 0x500000, 0xD01000, 0xFF6000, 0xFFC000, 0xFFF090)
        }
        val centre = (width - 1) / 2f
        return List(frameCount) { f ->
            val u = f.toFloat() / frameCount
            IntArray(width * height) { i ->
                val x = i % width
                val y = i / width
                val up = (height - 1 - y) / (height - 1f).coerceAtLeast(1f) // 0 at the base, 1 at the top
                val big = loopNoise(x / 3.2f, y / 4.5f + u * 6f, period = 6, seed = 1)
                val small = loopNoise(x / 1.7f, y / 2.2f + u * 24f, period = 24, seed = 2)
                val flicker = 0.62f * big + 0.38f * small
                // Flames reach a little higher in the middle than at the sides.
                val side = (x - centre) / (width / 2f)
                val reach = 1f - 0.3f * side * side
                val heat = (0.92f - 2f * up / reach) + (flicker - 0.5f) * (1.1f + 1.5f * up)
                palette[(heat.coerceIn(0f, 1f) * (levels - 1)).roundToInt()]
            }
        }
    }
}
