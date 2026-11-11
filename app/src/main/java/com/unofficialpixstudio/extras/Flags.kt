package com.unofficialpixstudio.extras

import com.unofficialpixstudio.animation.AnimationPreset
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Flags for the display, hung the way a banner or a cape hangs: the hoist (the edge that
 * would be at the pole) is at the top, and the flag's upper edge is on the viewer's left. So
 * Germany's stripes run down the display with black on the left, and France's run across it
 * with blue at the top.
 *
 * Only flags that survive being a few hundred LEDs are here: stripes, crosses, discs,
 * triangles and diagonals. Anything with a coat of arms, lettering or small stars is left out
 * rather than drawn badly.
 *
 * Each flag is a still picture: an [AnimationPreset] with a single frame, so it can share the
 * animation previews and their cache. Pure Kotlin.
 */
object Flags {

    val all: List<AnimationPreset> = listOf(
        flag("Armenia", stripes(0xD90012, 0x0033A0, 0xF2A800)),
        flag("Austria", stripes(0xED2939, WHITE, 0xED2939)),
        flag("Bahamas", stripes(0x00778B, 0xFFC72C, 0x00778B), hoistTriangle(BLACK, 0.433f)),
        flag(
            "Bahrain",
            plain(0xDA291C),
            // White at the hoist, meeting the red in five points.
            Smooth { u, v, _ -> WHITE.takeIf { u < 0.25f + 0.15f * (1f - abs(2f * (v * 5f % 1f) - 1f)) } },
        ),
        flag("Bangladesh", plain(0x006A4E), disc(0xF42A41, u = 0.45f, radius = 0.333f)),
        flag("Belgium", pales(BLACK, 0xFDDA24, 0xEF3340)),
        flag("Benin", stripes(0xFCD116, 0xE8112D), hoistBand(0x008751, 0.4f)),
        flag("Bolivia", stripes(0xDA291C, 0xF4E400, 0x007A33)),
        flag("Botswana", stripes(0x6DA9D2, WHITE, BLACK, WHITE, 0x6DA9D2, weights = intArrayOf(9, 1, 4, 1, 9))),
        flag("Bulgaria", stripes(WHITE, 0x00966E, 0xD62612)),
        flag("Chad", pales(0x002664, 0xFECB00, 0xC60C30)),
        flag("Colombia", stripes(0xFFCD00, 0x003087, 0xC8102E, weights = intArrayOf(2, 1, 1))),
        flag(
            "Congo",
            // Green by the hoist, red by the fly, and a yellow band rising between them.
            Smooth { u, v, _ ->
                val along = u - 0.667f * (1f - v)
                if (along < 0f) 0x009543 else if (along < 0.333f) 0xFBDE4A else 0xDC241F
            },
        ),
        flag("Costa Rica", stripes(0x001489, WHITE, 0xDA291C, WHITE, 0x001489, weights = intArrayOf(1, 1, 2, 1, 1))),
        flag("Czechia", stripes(WHITE, 0xD7141A), hoistTriangle(0x11457E, 0.5f)),
        flag("Denmark", plain(0xC8102E), Cross(WHITE, thickness = 0.143f, centre = 0.378f)),
        flag("England", plain(WHITE), Cross(0xCE1124, thickness = 0.2f)),
        flag("Estonia", stripes(0x0072CE, BLACK, WHITE)),
        flag("Finland", plain(WHITE), Cross(0x003580, thickness = 0.273f, centre = 0.361f)),
        flag("France", pales(0x0055A4, WHITE, 0xEF4135)),
        flag("Gabon", stripes(0x009E60, 0xFCD116, 0x3A75C4)),
        flag("Gambia", stripes(0xCE1126, WHITE, 0x0C1C8C, WHITE, 0x3A7728, weights = intArrayOf(6, 1, 4, 1, 6))),
        flag("Germany", stripes(BLACK, 0xDD0000, 0xFFCE00)),
        flag(
            "Greenland",
            stripes(WHITE, 0xD00C33),
            // A disc towards the hoist, coloured the other way round from the halves it sits on.
            Smooth { u, v, stretch ->
                val across = v - 0.5f
                val down = (u - 0.389f) * stretch
                if (across * across + down * down > 0.333f * 0.333f) null else if (v < 0.5f) 0xD00C33 else WHITE
            },
        ),
        flag("Guinea", pales(0xCE1126, 0xFCD116, 0x009460)),
        flag("Hungary", stripes(0xCE2939, WHITE, 0x477050)),
        flag(
            "Iceland",
            plain(0x02529C),
            Cross(WHITE, thickness = 0.222f, centre = 0.36f),
            Cross(0xDC1E35, thickness = 0.111f, centre = 0.36f),
        ),
        flag("Indonesia", stripes(0xFF0000, WHITE)),
        flag("Ireland", pales(0x169B62, WHITE, 0xFF883E)),
        flag("Italy", pales(0x009246, WHITE, 0xCE2B37)),
        flag("Ivory Coast", pales(0xF77F00, WHITE, 0x009E60)),
        flag(
            "Jamaica",
            // Green above and below, black at the hoist and fly, and a gold cross from corner to corner.
            Smooth { u, v, _ ->
                if (abs(abs(u - 0.5f) - abs(v - 0.5f)) < 0.09f) 0xFFB81C
                else if (abs(v - 0.5f) > abs(u - 0.5f)) 0x007749
                else BLACK
            },
        ),
        flag("Japan", plain(WHITE), disc(0xBC002D, u = 0.5f, radius = 0.3f)),
        flag(
            "Kuwait",
            stripes(0x007A3D, WHITE, 0xCE1126),
            // The black shape at the hoist: full height at the edge, a third of it a quarter of the way along.
            Smooth { u, v, _ -> BLACK.takeIf { u < 0.25f && abs(v - 0.5f) < 0.5f - u / 0.25f / 3f } },
        ),
        flag("Laos", stripes(0xCE1126, 0x002868, 0xCE1126, weights = intArrayOf(1, 2, 1)), disc(WHITE, u = 0.5f, radius = 0.2f)),
        flag("Latvia", stripes(0x9E3039, WHITE, 0x9E3039, weights = intArrayOf(2, 1, 2))),
        flag("Lithuania", stripes(0xFDB913, 0x006A44, 0xC1272D)),
        flag("Luxembourg", stripes(0xED2939, WHITE, 0x00A1DE)),
        flag("Madagascar", stripes(0xFC3D32, 0x007E3A), hoistBand(WHITE, 0.333f)),
        flag("Mali", pales(0x14B53A, 0xFCD116, 0xCE1126)),
        flag("Mauritius", stripes(0xEA2839, 0x1A206D, 0xFFD500, 0x00A551)),
        flag("Monaco", stripes(0xCE1126, WHITE)),
        flag("Netherlands", stripes(0xAE1C28, WHITE, 0x21468B)),
        flag("Niger", stripes(0xE05206, WHITE, 0x0DB02B), disc(0xE05206, u = 0.5f, radius = 0.14f)),
        flag("Nigeria", pales(0x008751, WHITE, 0x008751)),
        flag(
            "Norway",
            plain(0xBA0C2F),
            Cross(WHITE, thickness = 0.25f, centre = 0.364f),
            Cross(0x00205B, thickness = 0.125f, centre = 0.364f),
        ),
        flag("Palau", plain(0x4AADD6), disc(0xFFDE00, u = 0.4375f, radius = 0.3f)),
        flag("Palestine", stripes(BLACK, WHITE, 0x007A3D), hoistTriangle(0xCE1126, 0.333f)),
        flag("Peru", pales(0xD91023, WHITE, 0xD91023)),
        flag("Poland", stripes(WHITE, 0xDC143C)),
        flag(
            "Qatar",
            plain(0x8A1538),
            // As Bahrain, but maroon, and nine points.
            Smooth { u, v, _ -> WHITE.takeIf { u < 0.26f + 0.12f * (1f - abs(2f * (v * 9f % 1f) - 1f)) } },
        ),
        flag("Romania", pales(0x002B7F, 0xFCD116, 0xCE1126)),
        flag("Russia", stripes(WHITE, 0x0039A6, 0xD52B1E)),
        flag(
            "Scotland",
            plain(0x005EB8),
            Smooth { u, v, _ -> WHITE.takeIf { abs(abs(u - 0.5f) - abs(v - 0.5f)) < 0.1f } },
        ),
        flag(
            "Seychelles",
            // Five rays fanning out from the bottom corner at the hoist.
            Smooth { u, v, _ ->
                val rise = (1f - v) / u.coerceAtLeast(0.0001f)
                when {
                    rise > 3f -> 0x003F87
                    rise > 1.5f -> 0xFCD856
                    rise > 0.667f -> 0xD62828
                    rise > 0.333f -> WHITE
                    else -> 0x007A3D
                }
            },
        ),
        flag("Sierra Leone", stripes(0x1EB53A, WHITE, 0x0072C6)),
        flag("Sudan", stripes(0xD21034, WHITE, BLACK), hoistTriangle(0x007229, 0.333f)),
        flag("Sweden", plain(0x006AA7), Cross(0xFECC02, thickness = 0.2f, centre = 0.375f)),
        flag("Switzerland", plain(0xDA291C), Cross(WHITE, thickness = 0.1875f, length = 0.72f)),
        flag(
            "Tanzania",
            // Green by the hoist, blue by the fly, and between them a black band edged with yellow.
            Smooth { u, v, _ ->
                val off = u + v - 1f
                when {
                    abs(off) < 0.17f -> BLACK
                    abs(off) < 0.25f -> 0xFCD116
                    off < 0f -> 0x1EB53A
                    else -> 0x00A3DD
                }
            },
        ),
        flag("Thailand", stripes(0xA51931, WHITE, 0x2D2A4A, WHITE, 0xA51931, weights = intArrayOf(1, 1, 2, 1, 1))),
        flag(
            "Trinidad and Tobago",
            plain(0xDA1A35),
            // A black band from the top of the hoist to the bottom of the fly, edged with white.
            Smooth { u, v, _ ->
                val off = abs(u - v)
                if (off < 0.17f) BLACK else if (off < 0.24f) WHITE else null
            },
        ),
        flag("UAE", stripes(0x00732F, WHITE, BLACK), hoistBand(0xFF0000, 0.25f)),
        flag("Ukraine", stripes(0x0057B7, 0xFFD700)),
        flag("Yemen", stripes(0xCE1126, WHITE, BLACK)),
    ).sortedBy { it.name }

    fun byId(id: String): AnimationPreset? = all.firstOrNull { it.id == id }

    /** The id of the flag called [name], as [byId] wants it. */
    fun idOf(name: String): String = "flag-" + name.lowercase().replace(' ', '-')

    private const val WHITE = 0xFFFFFF
    private const val BLACK = 0x000000

    /** Samples per LED each way, for edges that aren't straight up or across. */
    private const val SAMPLES = 4

    private fun flag(name: String, vararg layers: Layer) =
        AnimationPreset(idOf(name), name, "", STILL_MS) { width, height -> listOf(draw(width, height, layers)) }

    /** Unused for a single frame, but an [AnimationPreset] needs one. */
    private const val STILL_MS = 500

    /*
     * A flag is painted in layers, each described on the flag as it's normally drawn: u runs
     * from the hoist (0) to the fly (1), v from the top edge (0) to the bottom (1). Hanging it
     * puts u down the display and v across it.
     */
    private sealed interface Layer

    /**
     * Asked once per LED, at its centre, so straight stripes get clean edges on whole LEDs
     * rather than a blurred row between two colours. Null leaves what's underneath.
     */
    private class Crisp(val colour: (u: Float, v: Float) -> Int?) : Layer

    /**
     * Asked several times per LED and averaged, for slanted and curved edges. [stretch] is how
     * many times longer the display is than wide: multiply a distance along u by it to compare
     * with one along v, which is how a disc stays round.
     */
    private class Smooth(val colour: (u: Float, v: Float, stretch: Float) -> Int?) : Layer

    /**
     * A cross with arms the same number of LEDs thick both ways, centred across the flag and
     * crossing [centre] of the way from hoist to fly. [thickness] is a fraction of the flag's
     * height. The arms reach the edges unless [length] (also of the height) says otherwise.
     */
    private class Cross(
        val colour: Int,
        val thickness: Float,
        val centre: Float = 0.5f,
        val length: Float? = null,
    ) : Layer

    /** Stripes from the top of the flag to the bottom, equal unless [weights] gives their shares. */
    private fun stripes(vararg colours: Int, weights: IntArray? = null) = Crisp { _, v -> pick(v, colours, weights) }

    /** Stripes from the hoist to the fly. */
    private fun pales(vararg colours: Int) = Crisp { u, _ -> pick(u, colours, null) }

    private fun plain(colour: Int) = Crisp { _, _ -> colour }

    /** A band the full height of the flag at the hoist, [width] of the way along. */
    private fun hoistBand(colour: Int, width: Float) = Crisp { u, _ -> colour.takeIf { u < width } }

    /** A triangle on the hoist with its point [depth] of the way along the flag. */
    private fun hoistTriangle(colour: Int, depth: Float) =
        Smooth { u, v, _ -> colour.takeIf { u < depth * (1f - abs(2f * v - 1f)) } }

    /** A disc centred [u] of the way along and half-way down; [radius] is a fraction of the flag's height. */
    private fun disc(colour: Int, u: Float, radius: Float) = Smooth { pu, pv, stretch ->
        val across = pv - 0.5f
        val down = (pu - u) * stretch
        colour.takeIf { across * across + down * down <= radius * radius }
    }

    /** Which of [colours] is [t] (0–1) of the way through them. */
    private fun pick(t: Float, colours: IntArray, weights: IntArray?): Int {
        if (weights == null) return colours[(t * colours.size).toInt().coerceIn(0, colours.size - 1)]
        var passed = 0
        for (i in colours.indices) {
            passed += weights[i]
            if (t * weights.sum() < passed) return colours[i]
        }
        return colours.last()
    }

    private fun draw(width: Int, height: Int, layers: Array<out Layer>): IntArray {
        val pixels = IntArray(width * height)
        val stretch = height.toFloat() / width
        for (layer in layers) {
            when (layer) {
                is Crisp -> for (i in pixels.indices) {
                    layer.colour((i / width + 0.5f) / height, (i % width + 0.5f) / width)?.let { pixels[i] = it }
                }
                is Smooth -> for (i in pixels.indices) {
                    var red = 0
                    var green = 0
                    var blue = 0
                    for (sy in 0 until SAMPLES) {
                        for (sx in 0 until SAMPLES) {
                            val u = (i / width + (sy + 0.5f) / SAMPLES) / height
                            val v = (i % width + (sx + 0.5f) / SAMPLES) / width
                            val colour = layer.colour(u, v, stretch) ?: pixels[i]
                            red += (colour shr 16) and 0xFF
                            green += (colour shr 8) and 0xFF
                            blue += colour and 0xFF
                        }
                    }
                    val samples = SAMPLES * SAMPLES
                    pixels[i] = ((red + samples / 2) / samples shl 16) or
                        ((green + samples / 2) / samples shl 8) or
                        ((blue + samples / 2) / samples)
                }
                is Cross -> {
                    val thick = fitted(layer.thickness * width, width)
                    val long = layer.length?.let { fitted(it * width, width) }
                    // The arm that runs the length of the flag, so down the display.
                    val left = (width - thick) / 2
                    val top = long?.let { startOf(it, layer.centre, height) } ?: 0
                    for (y in top until (top + (long ?: height)).coerceAtMost(height)) {
                        for (x in left until left + thick) if (y >= 0) pixels[y * width + x] = layer.colour
                    }
                    // The arm that crosses it.
                    val first = startOf(thick, layer.centre, height)
                    val from = long?.let { (width - it) / 2 } ?: 0
                    for (y in first.coerceAtLeast(0) until (first + thick).coerceAtMost(height)) {
                        for (x in from until from + (long ?: width)) pixels[y * width + x] = layer.colour
                    }
                }
            }
        }
        return pixels
    }

    /**
     * [size] in LEDs as a whole number that can sit exactly in the middle of [within] LEDs:
     * even if that's even, odd if it's odd.
     */
    private fun fitted(size: Float, within: Int): Int {
        val nearest = size.roundToInt()
        val fits = when {
            (nearest - within) % 2 == 0 -> nearest
            size >= nearest -> nearest + 1
            else -> nearest - 1
        }
        return fits.coerceIn(if (within % 2 == 0) 2 else 1, within)
    }

    /**
     * The first of [size] rows centred as nearly as whole rows allow on [centre] of the way
     * down [height] rows. Sizes that are both even (or both odd) get the same centre, which is
     * what keeps a cross inside a cross lined up.
     */
    private fun startOf(size: Int, centre: Float, height: Int): Int {
        val twice = 2f * centre * height // twice the centre, so half-rows are whole numbers
        var middle = floor(twice).toInt()
        if ((middle - size) % 2 != 0) middle -= 1
        if (twice - middle > 1f) middle += 2
        return (middle - size) / 2
    }
}
