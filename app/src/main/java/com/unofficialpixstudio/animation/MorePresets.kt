package com.unofficialpixstudio.animation

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The rest of the preset animations. They follow the same rules as the ones in
 * [AnimationPresets]: small steps, sub-pixel motion, whole cycles per loop, and colours taken
 * from a fixed ramp so all frames fit the display's one 256-colour palette.
 */
internal object MorePresets {

    // ---- Aurora: curtains of light, green at the hem and violet higher up, folding sideways. ----

    fun aurora(width: Int, height: Int): List<IntArray> {
        val frameCount = 64
        val bands = 10 // colours up the curtain
        val levels = 25 // brightnesses of each
        val palette = IntArray(bands * levels) {
            val tint = gradient((it / levels) / (bands - 1f), 0x30FF70, 0x10E0B0, 0x3080FF, 0xA040FF)
            scale(tint, (it % levels) / (levels - 1f))
        }
        return List(frameCount) { f ->
            val t = TAU * f / frameCount
            IntArray(width * height) { i ->
                val x = (i % width).toFloat()
                val y = (i / width).toFloat()
                val fold = 0.5f + 0.5f * sin(x * 0.55f + t + 1.3f * sin(x * 0.21f - t))
                val drift = 0.5f + 0.5f * sin(x * 0.31f - 2 * t + 2f)
                val strength = (0.65f * fold + 0.35f * drift).pow(1.4f)
                // The hem is the bright lower edge; light trails off slowly above it and quickly below.
                val hem = height * (0.76f + 0.1f * sin(x * 0.4f + t))
                val above = hem - y
                val profile = if (above < 0f) exp(above * 1.4f) else exp(-above / (0.62f * height))
                val light = (0.22f + 0.95f * strength) * profile
                val band = (above / (0.7f * height) * (bands - 1)).roundToInt().coerceIn(0, bands - 1)
                palette[band * levels + (light.coerceIn(0f, 1f) * (levels - 1)).roundToInt()]
            }
        }
    }

    // ---- Ocean: a rolling surface with a line of foam, darker the deeper it goes. ----

    fun ocean(width: Int, height: Int): List<IntArray> {
        val frameCount = 60
        val deepest = height * 0.75f
        val foam = 0xE8FFFF
        // Position along the ramp is depth: one row for the foam to fade in, then down into the dark.
        val palette = ramp(200) { position ->
            val depth = position * (deepest + 1f) - 1f
            if (depth < 0f) scale(foam, 1f + depth)
            else gradient(sqrt(depth / deepest), foam, 0x30C0FF, 0x0068E0, 0x0020A0, 0x000C50)
        }
        return List(frameCount) { f ->
            val t = TAU * f / frameCount
            IntArray(width * height) { i ->
                val x = (i % width).toFloat()
                val y = (i / width).toFloat()
                val surface = height * 0.38f +
                    height * 0.085f * sin(x * 0.5f - t) +
                    height * 0.035f * sin(x * 0.85f + 2 * t + 1f)
                var depth = y - surface
                // Light rippling through the water, which only shows once clear of the foam.
                if (depth > 1f) depth += 0.9f * ((depth - 1f) / 3f).coerceAtMost(1f) * sin(x * 0.7f - y * 0.9f + 2 * t)
                palette.at((depth + 1f) / (deepest + 1f))
            }
        }
    }

    // ---- Lava: blobs drifting up and down, merging where they meet. ----

    fun lava(width: Int, height: Int): List<IntArray> {
        val frameCount = 64
        val blobs = 5
        val palette = ramp(120) { gradient(it, 0x0C0018, 0x500050, 0xD01048, 0xFF7010, 0xFFE060) }
        val size = minOf(width, height).toFloat()
        val radius = FloatArray(blobs) { size * (0.17f + 0.09f * hash(it, 0, 41)) }
        return List(frameCount) { f ->
            val u = f.toFloat() / frameCount
            val bx = FloatArray(blobs) { (width - 1) * (0.5f + 0.34f * sin(TAU * ((1 + it % 2) * u + hash(it, 1, 41)))) }
            val by = FloatArray(blobs) { (height - 1) * (0.5f + 0.47f * sin(TAU * (u + it / blobs.toFloat()))) }
            IntArray(width * height) { i ->
                val x = i % width
                val y = i / width
                var field = 0f
                for (b in 0 until blobs) {
                    val d2 = (x - bx[b]) * (x - bx[b]) + (y - by[b]) * (y - by[b])
                    // 2 in the middle of a blob, falling away past its edge; nearby blobs add up and join.
                    field += radius[b] * radius[b] / (d2 + 0.5f * radius[b] * radius[b])
                }
                palette.at((field - 0.45f) / 1.7f)
            }
        }
    }

    // ---- Ripples: rings spreading from two drops and crossing each other. ----

    fun ripples(width: Int, height: Int): List<IntArray> {
        val frameCount = 48
        val palette = ramp(96) { gradient(it, 0x000418, 0x0030B0, 0x20A8FF, 0xE8FFFF) }
        val dropX = floatArrayOf(width * 0.27f, width * 0.74f)
        val dropY = floatArrayOf(height * 0.3f, height * 0.72f)
        return List(frameCount) { f ->
            val t = TAU * f / frameCount
            IntArray(width * height) { i ->
                val x = i % width
                val y = i / width
                var wave = 0f
                for (d in dropX.indices) {
                    val distance = sqrt((x - dropX[d]) * (x - dropX[d]) + (y - dropY[d]) * (y - dropY[d]))
                    wave += sin(distance * 1.05f - 2 * t) / (1f + distance * 0.06f)
                }
                palette.at((0.5f + 0.42f * wave).coerceIn(0f, 1f).pow(1.5f))
            }
        }
    }

    // ---- Spiral: a two-armed rainbow pinwheel turning about the middle. ----

    fun spiral(width: Int, height: Int): List<IntArray> {
        val frameCount = 60
        val hues = 120
        val palette = IntArray(hues) { hsv(360f * it / hues, 1f, 1f) }
        val cx = (width - 1) / 2f
        val cy = (height - 1) / 2f
        return List(frameCount) { f ->
            IntArray(width * height) { i ->
                val dx = i % width - cx
                val dy = i / width - cy
                val turn = atan2(dy, dx) / TAU
                val distance = sqrt(dx * dx + dy * dy)
                // Two steps a frame is one whole trip round the hues per loop.
                palette[Math.floorMod((hues * (2 * turn + distance * 0.07f)).roundToInt() - 2 * f, hues)]
            }
        }
    }

    // ---- Twinkle: a still night sky whose stars each brighten and dim in their own time. ----

    fun twinkle(width: Int, height: Int): List<IntArray> {
        val frameCount = 64
        val levels = 80
        val tints = intArrayOf(0xFFFFFF, 0xFFDC98, 0x98C4FF)
        val palette = tintedLevels(tints, levels) { tint, light -> scale(tint, light) }
        return List(frameCount) { f ->
            val u = f.toFloat() / frameCount
            IntArray(width * height) { i ->
                val x = i % width
                val y = i / width
                if (hash(x, y, 11) > 0.2f) {
                    palette[0]
                } else {
                    val cycles = 1 + (hash(x, y, 12) * 3).toInt().coerceAtMost(2)
                    val peak = 0.5f + 0.5f * hash(x, y, 13)
                    val wave = 0.5f + 0.5f * sin(TAU * (cycles * u + hash(x, y, 14)))
                    val light = peak * (0.12f + 0.88f * wave * wave)
                    val tint = (hash(x, y, 15) * tints.size).toInt().coerceAtMost(tints.size - 1)
                    palette[tint * levels + (light * (levels - 1)).roundToInt()]
                }
            }
        }
    }

    // ---- Warp: flying through a field of stars, which speed up and brighten as they pass. ----

    fun warp(width: Int, height: Int): List<IntArray> {
        val frameCount = 48
        val palette = ramp(64) { gradient(it, 0x000000, 0x2838D0, 0x90B0FF, 0xFFFFFF) }
        val cx = (width - 1) / 2f
        val cy = (height - 1) / 2f
        val half = maxOf(cx, cy)
        val nearest = 0.08f // how close a star gets, as a fraction of where it starts
        val stars = (width * height / 8).coerceIn(16, 80)
        return List(frameCount) { f ->
            val u = f.toFloat() / frameCount
            val field = LightField(width, height)
            for (s in 0 until stars) {
                // Where the star sits across the view. None are dead ahead, so by the time a star
                // is at its nearest it's off the display, and can start again far away unseen.
                val angle = TAU * (s + hash(s, 0, 21)) / stars
                val aside = half * (0.15f + 0.85f * hash(s, 1, 21))
                val progress = (u * (if (s % 4 == 0) 2 else 1) + hash(s, 2, 21)) % 1f
                val brightness = smooth((progress * 5f).coerceAtMost(1f)) * (0.6f + 0.9f * progress)
                // A short streak: the star, and fainter copies of where it just was.
                for (k in 0 until 3) {
                    val depth = 1f - (progress - 0.02f * k).coerceAtLeast(0f) * (1f - nearest)
                    field.dot(cx + aside / depth * cos(angle), cy + aside / depth * sin(angle), brightness * (1f - k / 3f))
                }
            }
            IntArray(width * height) { palette.at(field.light[it]) }
        }
    }

    // ---- Snow: flakes swaying as they fall, the nearer ones quicker and brighter. ----

    fun snow(width: Int, height: Int): List<IntArray> {
        val frameCount = 64
        val palette = ramp(64) { gradient(it, 0x000000, 0x7080B8, 0xD0DCFF, 0xFFFFFF) }
        val flakes = (width * 1.2f).roundToInt()
        return List(frameCount) { f ->
            val u = f.toFloat() / frameCount
            val field = LightField(width, height)
            for (s in 0 until flakes) {
                val near = s % 4 == 0
                val fall = (u * (if (near) 2 else 1) + hash(s, 0, 31)) % 1f
                val sway = (0.4f + 0.6f * hash(s, 1, 31)) * sin(TAU * ((1 + s % 2) * u + hash(s, 2, 31)))
                val x = (s + hash(s, 3, 31)) * width / flakes - 0.5f + sway
                field.dot(x, fall * (height + 2) - 1f, if (near) 1.5f else 0.6f + 0.5f * hash(s, 4, 31))
            }
            IntArray(width * height) { palette.at(field.light[it]) }
        }
    }

    // ---- Comet: a bright head tracing a figure of eight, with its tail fading behind. ----

    fun comet(width: Int, height: Int): List<IntArray> {
        val frameCount = 64
        val palette = ramp(96) { gradient(it, 0x000000, 0x3010A8, 0x2090FF, 0xD8FFFF) }
        val cx = (width - 1) / 2f
        val cy = (height - 1) / 2f
        // The eight stands upright on a tall display and lies on its side on a wide one.
        val (lapsX, lapsY) = if (height >= width) 2 to 1 else 1 to 2
        val tail = 80 // places along the tail that get drawn
        return List(frameCount) { f ->
            val u = f.toFloat() / frameCount
            val field = LightField(width, height)
            for (k in tail - 1 downTo 0) {
                val s = u - k * 0.34f / tail
                val fade = 1f - k.toFloat() / tail
                field.spot(
                    x = cx + (width - 1) * 0.4f * sin(TAU * lapsX * s),
                    y = cy + (height - 1) * 0.42f * sin(TAU * lapsY * s),
                    radius = 1.1f + 0.8f * fade,
                    amount = fade * fade,
                )
            }
            IntArray(width * height) { palette.at(field.light[it]) }
        }
    }

    // ---- Fireworks: three rockets, each climbing, bursting and drifting down as it fades. ----

    fun fireworks(width: Int, height: Int): List<IntArray> {
        val frameCount = 64
        val levels = 80
        val tints = intArrayOf(0xFFB020, 0xFF3090, 0x30D0FF)
        val palette = tintedLevels(tints, levels) { tint, light ->
            gradient(light, 0x000000, scale(tint, 0.6f), tint, gradient(0.65f, tint, 0xFFFFFF))
        }
        val size = minOf(width, height).toFloat()
        val burstX = floatArrayOf(0.3f, 0.7f, 0.48f)
        val burstY = floatArrayOf(0.34f, 0.44f, 0.26f)
        val burstAt = floatArrayOf(0.14f, 0.47f, 0.8f) // when in the loop each one goes off
        val climb = 0.13f // how much of the loop the rocket takes to get up there
        val life = 0.42f // and how long its sparks last
        val sparks = 18
        return List(frameCount) { f ->
            val u = f.toFloat() / frameCount
            val fields = List(tints.size) { LightField(width, height) }
            for (b in tints.indices) {
                val field = fields[b]
                val bx = (width - 1) * burstX[b]
                val by = (height - 1) * burstY[b]
                val since = (u - burstAt[b] + 1f) % 1f
                if (since > 1f - climb) {
                    // Still climbing: quick off the ground, slowing as it reaches the top.
                    val toGo = (1f - since) / climb
                    for (k in 0 until 3) {
                        val behind = (toGo + 0.07f * k).pow(1.6f)
                        field.dot(bx, by + (height - by) * behind, 0.75f * (1f - k / 3f))
                    }
                } else if (since < life) {
                    val age = since / life
                    val fade = (1f - age).pow(1.3f)
                    field.spot(bx, by, 2.4f, 1f - age * 5f)
                    for (p in 0 until sparks) {
                        val angle = TAU * (p + 0.6f * hash(p, b, 51)) / sparks
                        val speed = size * (0.2f + 0.24f * hash(p, b, 52))
                        for (k in 0 until 3) {
                            val a = (age - 0.05f * k).coerceAtLeast(0f)
                            val out = speed * (1f - (1f - a).pow(2.5f))
                            // Sparks sag as they slow, as if falling.
                            field.dot(bx + out * cos(angle), by + out * sin(angle) + size * 0.22f * a * a, fade * (1f - k / 3f))
                        }
                    }
                }
            }
            // An LED shows one colour, so the brightest rocket there wins.
            IntArray(width * height) { brightest(fields, it, palette, levels) }
        }
    }

    // ---- Equalizer: bars rising and falling, green at the bottom through to red at the top. ----

    fun equalizer(width: Int, height: Int): List<IntArray> {
        val frameCount = 64
        val bands = minOf(height, 24) // colours up a bar
        val shades = 255 / bands // brightnesses of each, for the half-lit LED at the top of a bar
        val barWidth = if (width >= 12) 2 else 1
        val stride = barWidth + 1
        val bars = (width + 1) / stride
        val left = (width - (bars * stride - 1)) / 2
        return List(frameCount) { f ->
            val u = f.toFloat() / frameCount
            val level = FloatArray(bars) { b ->
                val beat = 0.5f +
                    0.27f * sin(TAU * (u + hash(b, 0, 61))) +
                    0.17f * sin(TAU * (2 * u + hash(b, 1, 61))) +
                    0.1f * sin(TAU * (3 * u + hash(b, 2, 61)))
                beat.coerceIn(0.06f, 1f) * height
            }
            IntArray(width * height) { i ->
                val inBars = i % width - left
                if (inBars < 0 || inBars / stride >= bars || inBars % stride >= barWidth) {
                    0
                } else {
                    val fromBottom = height - 1 - i / width
                    val lit = (level[inBars / stride] - fromBottom).coerceIn(0f, 1f)
                    val band = fromBottom * bands / height
                    val colour = gradient(band / (bands - 1f).coerceAtLeast(1f), 0x00E040, 0xB0F000, 0xFFC000, 0xFF2010)
                    scale(colour, (lit * shades).roundToInt() / shades.toFloat())
                }
            }
        }
    }

    // ---- DNA: two strands winding round each other, joined by rungs. ----

    fun dna(width: Int, height: Int): List<IntArray> {
        val frameCount = 48
        val levels = 80
        val tints = intArrayOf(0x20D0FF, 0xFF40A0, 0xA8A8D0)
        val palette = tintedLevels(tints, levels) { tint, light -> scale(tint, light) }
        val cx = (width - 1) / 2f
        val reach = (width - 1) * 0.36f
        val twist = TAU / (height * 0.9f) // radians per row: just over one full turn on show
        return List(frameCount) { f ->
            val t = TAU * f / frameCount
            IntArray(width * height) { i ->
                val x = i % width
                val y = i / width
                val phase = y * twist + t
                val offset = reach * sin(phase)
                val front = cos(phase) // above 0 when the first strand is the nearer one
                fun strand(centre: Float, nearness: Float) =
                    (1f - abs(x - centre) / 1.15f).coerceAtLeast(0f) * (0.6f + 0.4f * nearness)
                val lights = floatArrayOf(
                    strand(cx + offset, front),
                    strand(cx - offset, -front),
                    if (y % 3 == 1 && abs(x - cx) < abs(offset) - 0.6f) 0.3f else 0f,
                )
                // Where the strands cross, the nearer one is the brighter, so it's the one drawn.
                var best = 0
                for (c in 1 until lights.size) if (lights[c] > lights[best]) best = c
                palette[best * levels + (lights[best] * (levels - 1)).roundToInt()]
            }
        }
    }

    // ---- Eyes: a pair of eyes that look around and blink. ----

    fun eyes(width: Int, height: Int): List<IntArray> {
        val frameCount = 64
        val samples = 4 // per LED each way, to soften the curved edges
        val white = 0xFFFFFF
        val iris = 0x2090FF
        val eyeX = floatArrayOf(width * 0.27f, width * 0.73f)
        val eyeY = height * 0.47f
        val rx = width * 0.2f
        val ry = height * 0.21f
        val irisRadius = rx * 0.56f
        return List(frameCount) { f ->
            val u = f.toFloat() / frameCount
            val open = (1f - bump(u, 0.55f, 0.035f)).coerceAtLeast(0.1f)
            val lookX = rx * 0.4f * sin(TAU * u)
            val lookY = ry * 0.3f * sin(TAU * 2 * u + 0.8f)
            IntArray(width * height) { i ->
                var whites = 0
                var irises = 0
                for (sy in 0 until samples) {
                    for (sx in 0 until samples) {
                        val px = i % width + (sx + 0.5f) / samples
                        val py = i / width + (sy + 0.5f) / samples
                        for (centre in eyeX) {
                            val ex = (px - centre) / rx
                            val ey = (py - eyeY) / (ry * open)
                            if (ex * ex + ey * ey <= 1f) {
                                val ix = (px - centre - lookX) / irisRadius
                                val iy = (py - eyeY - lookY) / irisRadius
                                val d2 = ix * ix + iy * iy
                                if (d2 > 1f) whites++ else if (d2 > 0.2f) irises++ // inside that is the pupil
                            }
                        }
                    }
                }
                val total = (samples * samples).toFloat()
                fun channel(shift: Int) =
                    (((white shr shift) and 0xFF) * whites + ((iris shr shift) and 0xFF) * irises) / total / 255f
                rgb(channel(16), channel(8), channel(0))
            }
        }
    }

    // ---- Radar: a beam sweeping round, leaving a fading trail and lighting up blips. ----

    fun radar(width: Int, height: Int): List<IntArray> {
        val frameCount = 60
        val palette = ramp(96) { gradient(it, 0x000000, 0x003810, 0x00B038, 0x40FF70, 0xD0FFD8) }
        val cx = (width - 1) / 2f
        val cy = (height - 1) / 2f
        val range = minOf(cx, cy)
        val blips = 3
        val blipAngle = FloatArray(blips) { TAU * (it + 0.8f * hash(it, 0, 71)) / blips }
        val blipX = FloatArray(blips) { cx + range * (0.45f + 0.4f * hash(it, 1, 71)) * cos(blipAngle[it]) }
        val blipY = FloatArray(blips) { cy + range * (0.45f + 0.4f * hash(it, 1, 71)) * sin(blipAngle[it]) }
        /** How far round the beam has gone since it passed [angle], 0 to a full turn. */
        fun since(beam: Float, angle: Float) = ((beam - angle) % TAU + TAU) % TAU
        return List(frameCount) { f ->
            val beam = TAU * f / frameCount
            IntArray(width * height) { i ->
                val dx = i % width - cx
                val dy = i / width - cy
                val distance = sqrt(dx * dx + dy * dy)
                val passed = since(beam, atan2(dy, dx))
                var light = 0.8f * exp(-passed * 1.25f)
                // The beam's front edge, faded in over about one LED so it doesn't stair-step.
                val edge = (1f / distance.coerceAtLeast(0.8f))
                light = maxOf(light, 0.8f * (1f - (TAU - passed) / edge))
                for (ring in 1..2) light = maxOf(light, 0.13f * (1f - abs(distance - range * ring / 2f) / 0.7f))
                for (b in 0 until blips) {
                    val d2 = ((i % width - blipX[b]) * (i % width - blipX[b]) + (i / width - blipY[b]) * (i / width - blipY[b])) / 2.2f
                    if (d2 < 1f) light = maxOf(light, exp(-since(beam, blipAngle[b]) * 0.8f) * (1f - d2))
                }
                palette.at(light)
            }
        }
    }

    // ---- Disco: dance-floor tiles, each drifting through the colours at its own pace. ----

    fun disco(width: Int, height: Int): List<IntArray> {
        val frameCount = 60
        val hues = 120
        val palette = IntArray(hues) { hsv(360f * it / hues, 1f, 1f) }
        val tile = if (minOf(width, height) >= 12) 4 else 3 // lit LEDs plus a one-LED gap
        val across = (width + 1) / tile
        val down = (height + 1) / tile
        val left = (width - (across * tile - 1)) / 2
        val top = (height - (down * tile - 1)) / 2
        val paces = intArrayOf(1, 2, -1) // trips round the hues per loop
        return List(frameCount) { f ->
            IntArray(width * height) { i ->
                val x = i % width - left
                val y = i / width - top
                if (x < 0 || y < 0 || x / tile >= across || y / tile >= down || x % tile == tile - 1 || y % tile == tile - 1) {
                    0
                } else {
                    val tx = x / tile
                    val ty = y / tile
                    val pace = paces[(hash(tx, ty, 82) * paces.size).toInt().coerceAtMost(paces.size - 1)]
                    val start = (hash(tx, ty, 81) * hues).toInt()
                    palette[Math.floorMod(start + pace * f * hues / frameCount, hues)]
                }
            }
        }
    }

    // ---- Tunnel: rainbow frames the shape of the display, growing out from the middle. ----

    fun tunnel(width: Int, height: Int): List<IntArray> {
        val frameCount = 60
        val hues = 120
        val palette = IntArray(hues) { hsv(360f * it / hues, 1f, 1f) }
        val cx = (width - 1) / 2f
        val cy = (height - 1) / 2f
        return List(frameCount) { f ->
            IntArray(width * height) { i ->
                // 0 in the middle, 1 at the edge, the same along each frame.
                val out = maxOf(abs(i % width - cx) / (cx + 0.5f), abs(i / width - cy) / (cy + 0.5f))
                palette[Math.floorMod((hues * 1.2f * out).roundToInt() - 2 * f, hues)]
            }
        }
    }

    // ---- Kaleidoscope: plasma in every colour, mirrored left to right and top to bottom. ----

    fun kaleidoscope(width: Int, height: Int): List<IntArray> {
        val frameCount = 64
        val hues = 128
        val palette = IntArray(hues) { hsv(360f * it / hues, 1f, 1f) }
        val cx = (width - 1) / 2f
        val cy = (height - 1) / 2f
        return List(frameCount) { f ->
            val t = TAU * f / frameCount
            IntArray(width * height) { i ->
                val across = abs(i % width - cx)
                val down = abs(i / width - cy)
                // Using the nearer and farther of the two folds it along the diagonals as well.
                val near = minOf(across, down)
                val far = maxOf(across, down)
                val v = sin(near * 0.55f + t) +
                    sin(far * 0.42f - t) +
                    sin((near + far) * 0.3f + 2 * t) +
                    sin(sqrt(near * near + far * far) * 0.6f - 2 * t)
                palette[Math.floorMod((hues * (0.5f + v * 0.19f)).roundToInt() + 2 * f, hues)]
            }
        }
    }

    // ---- Galaxy: two spiral arms wheeling round a bright core, with a few stars beyond. ----

    fun galaxy(width: Int, height: Int): List<IntArray> {
        val frameCount = 64
        val palette = ramp(110) { gradient(it, 0x000000, 0x301078, 0x9040D8, 0xFF90C8, 0xFFF4D8) }
        val cx = (width - 1) / 2f
        val cy = (height - 1) / 2f
        val size = maxOf(cx, cy)
        return List(frameCount) { f ->
            val u = f.toFloat() / frameCount
            IntArray(width * height) { i ->
                val x = i % width
                val y = i / width
                val distance = sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy)) / size
                // Two arms, so half a turn per loop brings it back to where it started.
                val arm = 0.5f + 0.5f * sin(2 * atan2(y - cy, x - cx) + 5.5f * sqrt(distance) - TAU * u)
                var light = exp(-distance * distance * 16f) + 0.85f * arm.pow(2.2f) * exp(-distance * 1.5f)
                if (hash(x, y, 101) > 0.9f) {
                    val wave = 0.5f + 0.5f * sin(TAU * ((1 + (x + y) % 2) * u + hash(x, y, 102)))
                    light += 0.3f * wave * wave
                }
                palette.at(light)
            }
        }
    }

    // ---- Clouds: soft white clouds drifting across a blue sky. ----

    fun clouds(width: Int, height: Int): List<IntArray> {
        val frameCount = 64
        val palette = ramp(96) { gradient(it, 0x0848C0, 0x2C78E8, 0xC0D8FF, 0xFFFFFF) }
        return List(frameCount) { f ->
            val u = f.toFloat() / frameCount
            IntArray(width * height) { i ->
                val x = i % width
                val y = i / width
                // The noise repeats along its second direction, which here runs across the display.
                val big = loopNoise(y / 4f, x / 5.5f + u * 6f, period = 6, seed = 5)
                val small = loopNoise(y / 2f, x / 2.6f + u * 12f, period = 12, seed = 6)
                palette.at(smooth(((0.65f * big + 0.35f * small - 0.4f) / 0.3f).coerceIn(0f, 1f)))
            }
        }
    }

    // ---- Bubbles: rings wobbling up through deep water. ----

    fun bubbles(width: Int, height: Int): List<IntArray> {
        val frameCount = 64
        val palette = ramp(96) { gradient(it, 0x00081C, 0x1060C0, 0x60C8FF, 0xE8FFFF) }
        val bubbles = (width * height / 45).coerceIn(5, 24)
        return List(frameCount) { f ->
            val u = f.toFloat() / frameCount
            val field = LightField(width, height)
            for (b in 0 until bubbles) {
                val big = b % 3 == 0
                val radius = if (big) 2.6f else 1.1f + 0.7f * hash(b, 0, 111)
                // Big bubbles rise twice as fast. Each starts and ends just out of sight.
                val rise = (u * (if (big) 2 else 1) + hash(b, 1, 111)) % 1f
                val x = (b + hash(b, 2, 111)) * width / bubbles - 0.5f + 0.7f * sin(TAU * (2 * u + hash(b, 3, 111)))
                val y = height + radius + 1f - rise * (height + 2 * radius + 2f)
                field.ring(x, y, radius, if (big) 1f else 0.75f)
                if (big) field.dot(x - 1f, y - 1f, 0.5f) // a glint
            }
            IntArray(width * height) { palette.at(field.light[it]) }
        }
    }

    // ---- Fireflies: glowing dots wandering in the dark, each pulsing in its own time. ----

    fun fireflies(width: Int, height: Int): List<IntArray> {
        val frameCount = 64
        val palette = ramp(96) { gradient(it, 0x000000, 0x304400, 0xA0E020, 0xF8FFA8) }
        val flies = (width * height / 30).coerceIn(5, 30)
        return List(frameCount) { f ->
            val u = f.toFloat() / frameCount
            val field = LightField(width, height)
            for (s in 0 until flies) {
                val x = (width - 1) * (0.1f + 0.8f * hash(s, 0, 121)) +
                    width * 0.16f * sin(TAU * ((1 + s % 2) * u + hash(s, 1, 121)))
                val y = (height - 1) * (0.1f + 0.8f * hash(s, 2, 121)) +
                    height * 0.14f * sin(TAU * ((1 + (s + 1) % 2) * u + hash(s, 3, 121)))
                val glow = 0.5f + 0.5f * sin(TAU * ((2 + s % 2) * u + hash(s, 4, 121)))
                field.spot(x, y, 1.7f, 0.12f + 0.88f * glow * glow)
            }
            IntArray(width * height) { palette.at(field.light[it]) }
        }
    }

    // ---- Confetti: coloured pieces fluttering down, catching the light as they tumble. ----

    fun confetti(width: Int, height: Int): List<IntArray> {
        val frameCount = 64
        val levels = 48
        val tints = intArrayOf(0xFF3040, 0xFFD020, 0x40E060, 0x30C0FF, 0xE050FF)
        val palette = tintedLevels(tints, levels) { tint, light -> scale(tint, light) }
        val pieces = (width * 1.5f).roundToInt()
        return List(frameCount) { f ->
            val u = f.toFloat() / frameCount
            val fields = List(tints.size) { LightField(width, height) }
            for (s in 0 until pieces) {
                val fall = (u * (1 + s % 2) + hash(s, 0, 131)) % 1f
                val sway = (0.5f + hash(s, 1, 131)) * sin(TAU * (2 * u + hash(s, 2, 131)))
                val x = (s + hash(s, 3, 131)) * width / pieces - 0.5f + sway
                val tumble = 0.5f + 0.5f * sin(TAU * (3 * u + hash(s, 4, 131)))
                fields[s % tints.size].dot(x, fall * (height + 2) - 1f, 0.7f + 0.9f * tumble)
            }
            IntArray(width * height) { brightest(fields, it, palette, levels) }
        }
    }

    // ---- Bounce: three balls bouncing across the display at their own heights and speeds. ----

    fun bounce(width: Int, height: Int): List<IntArray> {
        val frameCount = 64
        val levels = 80
        val tints = intArrayOf(0xFF5030, 0x70FF40, 0x40A8FF)
        val palette = tintedLevels(tints, levels) { tint, light ->
            gradient(light, 0x000000, tint, gradient(0.45f, tint, 0xFFFFFF))
        }
        val radius = (minOf(width, height) * 0.15f).coerceAtLeast(1.6f)
        val floor = height - 1 - radius * 0.6f
        return List(frameCount) { f ->
            val u = f.toFloat() / frameCount
            val fields = List(tints.size) { LightField(width, height) }
            for (b in tints.indices) {
                // Side to side and back is one loop (or half of one), so it joins up.
                val sweep = (u * (1 + b % 2) + hash(b, 0, 91)) % 1f
                val x = radius * 0.6f + (width - 1 - 1.2f * radius) * (1f - abs(2 * sweep - 1f))
                val hop = (u * (1 + b) + hash(b, 1, 91)) % 1f
                val y = floor - (height * (0.85f - 0.17f * b) - radius) * 4 * hop * (1f - hop)
                fields[b].spot(x, y, radius, 1f)
            }
            IntArray(width * height) { brightest(fields, it, palette, levels) }
        }
    }

    // ---- Chase: lights running round the edge, and the other way round the frames inside it. ----

    fun chase(width: Int, height: Int): List<IntArray> {
        val frameCount = 60
        val levels = 80
        val tints = intArrayOf(0x30E0FF, 0xFF40C0, 0xFFD020)
        val palette = tintedLevels(tints, levels) { tint, light -> scale(tint, light) }
        return List(frameCount) { f ->
            val u = f.toFloat() / frameCount
            IntArray(width * height) { i ->
                val x = i % width
                val y = i / width
                // Every other LED in from the edge belongs to a frame; the ones between stay dark.
                val inset = minOf(minOf(x, y), minOf(width - 1 - x, height - 1 - y))
                val w = width - 2 * inset
                val h = height - 2 * inset
                if (inset % 2 != 0 || w < 3 || h < 3) {
                    0
                } else {
                    val around = 2 * (w + h) - 4
                    val lx = x - inset
                    val ly = y - inset
                    // How far round the frame this LED is, clockwise from its top-left corner.
                    val place = when {
                        ly == 0 -> lx
                        lx == w - 1 -> w - 1 + ly
                        ly == h - 1 -> 2 * (w - 1) + h - 1 - lx
                        else -> 2 * (w - 1) + 2 * (h - 1) - ly
                    }
                    val turn = if (inset % 4 == 0) u else -u
                    // Three lights share each frame, a third of the way round apart.
                    val third = around / 3f
                    val behind = ((turn * around - place) % around + around) % around
                    val which = (behind / third).toInt().coerceAtMost(2)
                    val along = behind - which * third
                    if (along > third - 1f) {
                        // The LED just ahead of the next light, fading in as it arrives.
                        palette[(which + 1) % 3 * levels + ((along - (third - 1f)) * (levels - 1)).roundToInt()]
                    } else {
                        val tail = 1f - along / (third - 1f)
                        palette[which * levels + (tail * tail * (levels - 1)).roundToInt()]
                    }
                }
            }
        }
    }

    // ---- Pulse: a heartbeat trace swept across the display, fading behind the sweep. ----

    fun pulse(width: Int, height: Int): List<IntArray> {
        val frameCount = 64
        val palette = ramp(80) { gradient(it, 0x000000, 0x006820, 0x20FF60, 0xD8FFE0) }
        fun hump(p: Float, centre: Float, wide: Float) = exp(-((p - centre) / wide) * ((p - centre) / wide))
        /** The row the trace is on at [x]: flat, then one beat, across the display. */
        fun trace(x: Float): Float {
            val p = x / width
            val beat = 0.12f * hump(p, 0.2f, 0.05f) -
                0.16f * hump(p, 0.39f, 0.03f) +
                hump(p, 0.46f, 0.04f) -
                0.34f * hump(p, 0.53f, 0.03f) +
                0.22f * hump(p, 0.74f, 0.07f)
            return height * 0.6f - height * 0.45f * beat
        }
        // The rows the trace passes through in each column, so the steep spike has no gaps.
        val top = FloatArray(width) { x -> minOf(trace(x - 0.5f), trace(x.toFloat()), trace(x + 0.5f)) }
        val bottom = FloatArray(width) { x -> maxOf(trace(x - 0.5f), trace(x.toFloat()), trace(x + 0.5f)) }
        return List(frameCount) { f ->
            val head = width * f.toFloat() / frameCount
            IntArray(width * height) { i ->
                val x = i % width
                val y = i / width
                val cover = (minOf(y + 0.5f, bottom[x] + 0.5f) - maxOf(y - 0.5f, top[x] - 0.5f)).coerceIn(0f, 1f)
                // Columns behind the sweep: the newest fades in, then it dims until the sweep returns.
                val behind = ((head - x) % width + width) % width
                val fade = behind.coerceAtMost(1f) * (1f - behind / width).pow(2.2f)
                palette.at(cover * fade)
            }
        }
    }

    /** The colour of LED [i] when each tint has its own [fields]: the brightest one there wins. */
    private fun brightest(fields: List<LightField>, i: Int, palette: IntArray, levels: Int): Int {
        var best = 0
        for (b in 1 until fields.size) if (fields[b].light[i] > fields[best].light[i]) best = b
        return palette[best * levels + (fields[best].light[i].coerceIn(0f, 1f) * (levels - 1)).roundToInt()]
    }

    /** [levels] colours for each of [tints], one tint after another; level 0 is the darkest. */
    private fun tintedLevels(tints: IntArray, levels: Int, colour: (tint: Int, light: Float) -> Int): IntArray =
        IntArray(tints.size * levels) { colour(tints[it / levels], (it % levels) / (levels - 1f)) }
}
