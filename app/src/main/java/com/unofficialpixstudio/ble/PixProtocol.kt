package com.unofficialpixstudio.ble

import com.unofficialpixstudio.image.PixelArt
import java.util.UUID

/*
 * PIX backpack protocol, as documented by the MIT-licensed pix-backpack-go project
 * (https://github.com/temamagic/pix-backpack-go, docs/protocol/README.md). That project verified
 * it on firmware 2.5.1 / hardware 3.2; other firmware (e.g. 2.4.0) is unverified.
 *
 * Commands are single writes to the RPC characteristic (0101):
 *     call_id:u8 | opcode:u8 | payload...
 * call_id increments per command and wraps at 256. There's no length, checksum or handshake,
 * and no reply beyond the GATT write succeeding.
 *
 * Deliberately not implemented: opcode 05 (save to flash), 09 (restart) and the OTA
 * characteristics 0107–0109.
 */

object PixUuids {
    private fun pix(shortId: String): UUID =
        UUID.fromString("0000$shortId-e984-11e7-b78e-ffd6fcc34510")

    val RPC: UUID = pix("0101")
    val WIDTH: UUID = pix("0103")
    val HEIGHT: UUID = pix("0104")
    val BRIGHTNESS: UUID = pix("0106")
}

object PixBrightness {
    /**
     * Highest brightness the app sets. With the full-colour test pattern the backpack flickered
     * (frames dimming or dropping out) constantly above ~95% and every 7–9 s at 90%, most
     * likely because the LEDs draw more current than the supply can deliver. 85% was steady.
     * Images with more white may need less than this.
     */
    const val MAX_SAFE_PERCENT = 85

    /** LED load of the test pattern, the image [MAX_SAFE_PERCENT] was measured with. */
    private val referenceLoad: Double by lazy { PixelArt.relativeLoad(pixTestPattern(16, 20)) }

    /**
     * Highest brightness that should stay steady for [pixels], assuming LED current scales with
     * image load × brightness: brighter (whiter) images than the test pattern get a lower
     * limit; darker ones still never exceed [MAX_SAFE_PERCENT].
     */
    fun maxSafeFor(pixels: IntArray): Int {
        val load = PixelArt.relativeLoad(pixels)
        if (load <= referenceLoad) return MAX_SAFE_PERCENT
        return (MAX_SAFE_PERCENT * referenceLoad / load).toInt().coerceIn(1, MAX_SAFE_PERCENT)
    }

    /** 0–100 % to the raw byte for characteristic 0106. */
    fun percentToRaw(percent: Int): Int = percent.coerceIn(0, 100) * 255 / 100

    /**
     * Rounds (not floors) so every percent survives a write/read-back round trip: 85% is
     * written as 216 (216.75 floored), and 216 must read back as 85, not 84.
     */
    fun rawToPercent(raw: Int): Int = ((raw and 0xFF) * 100 + 127) / 255
}

object PixMode {
    const val NONE = 0
    const val ANIMATION = 1
}

object PixDirection {
    const val NORMAL = 0
    const val ALTERNATE = 1
    const val NORMAL_STOP = 2
    const val ALTERNATE_STOP = 3
}

/**
 * Frames to show on the display. Each frame is [width] × [height] pixels as 0xRRGGBB ints,
 * row-major (row 0 is the top). All frames share one palette of at most 256 colours.
 */
class PixAnimation(
    val width: Int,
    val height: Int,
    val frames: List<IntArray>,
    val frameDurationsMs: List<Int>,
    val direction: Int = PixDirection.NORMAL,
) {
    init {
        require(width > 0 && height > 0) { "Display size must be positive" }
        require(width * height <= MAX_PIXELS) { "Frames over $MAX_PIXELS pixels need offset writes" }
        require(frames.size in 1..MAX_FRAMES) { "Need 1–$MAX_FRAMES frames, got ${frames.size}" }
        require(frames.all { it.size == width * height }) { "Every frame must be ${width}x$height" }
        require(frameDurationsMs.size == frames.size) { "Need one duration per frame" }
        require(frameDurationsMs.all { it in 0..0xFFFF }) { "Durations must fit in 16 bits" }
    }

    companion object {
        /** Frame count is sent as one byte; pix-backpack-go caps uploads at 254. */
        const val MAX_FRAMES = 254

        /** Largest frame that fits one write: 512-byte value limit minus the 6-byte header. */
        const val MAX_PIXELS = 506
    }
}

/** Builds RPC packets for characteristic 0101. Keeps the rolling call_id. */
class PixCommandEncoder {
    private var callId = 0

    private fun command(opcode: Int, payload: ByteArray): ByteArray {
        val packet = byteArrayOf(callId.toByte(), opcode.toByte()) + payload
        callId = (callId + 1) and 0xFF
        return packet
    }

    /** Opcode 07. [config] follows the mode byte (empty for NONE). */
    fun setMode(mode: Int, config: ByteArray = ByteArray(0)): ByteArray =
        command(OP_SET_MODE, u16le(0) + byteArrayOf(mode.toByte()) + config)

    /** Opcode 02. */
    fun frameCount(count: Int): ByteArray {
        require(count in 0..255) { "Frame count is one byte" }
        return command(OP_FRAME_COUNT, byteArrayOf(count.toByte()))
    }

    /** Opcode 01. [colors] are 0xRRGGBB, written to palette slots starting at [start]. */
    fun palette(start: Int, colors: List<Int>): ByteArray {
        require(start in 0..255 && start + colors.size <= 256) { "Palette has 256 slots" }
        require(colors.size <= MAX_PALETTE_COLORS_PER_PACKET) { "At most 160 colours per packet" }
        val rgb = ByteArray(colors.size * 3)
        colors.forEachIndexed { i, color ->
            rgb[i * 3] = (color shr 16).toByte()
            rgb[i * 3 + 1] = (color shr 8).toByte()
            rgb[i * 3 + 2] = color.toByte()
        }
        return command(OP_PALETTE, byteArrayOf(start.toByte()) + rgb)
    }

    /** Opcode 00. [indices] are palette indices, one byte per pixel, starting at pixel [offset]. */
    fun frame(index: Int, offset: Int, indices: ByteArray): ByteArray =
        command(OP_FRAME, u16le(index) + u16le(offset) + indices)

    /** Opcode 03. Per-frame durations in milliseconds. */
    fun durations(offset: Int, durationsMs: List<Int>): ByteArray =
        command(OP_DURATIONS, u16le(offset) + durationsMs.fold(ByteArray(0)) { acc, ms -> acc + u16le(ms) })

    /** Opcode 04. One of [PixDirection]. */
    fun direction(direction: Int): ByteArray = command(OP_DIRECTION, byteArrayOf(direction.toByte()))

    /**
     * Full upload sequence for showing [animation] (RAM only, not saved to flash):
     * mode NONE, reset frame count, palette, frames, durations, direction, frame count.
     */
    fun uploadAnimation(animation: PixAnimation): List<ByteArray> {
        // Palette in order of first appearance across all frames.
        val paletteIndex = LinkedHashMap<Int, Int>()
        for (frame in animation.frames) {
            for (pixel in frame) paletteIndex.getOrPut(pixel and 0xFFFFFF) { paletteIndex.size }
        }
        require(paletteIndex.size <= 256) {
            "Animation uses ${paletteIndex.size} colours; the display palette holds 256"
        }
        val colors = paletteIndex.keys.toList()

        val packets = mutableListOf<ByteArray>()
        packets += setMode(PixMode.NONE)
        packets += frameCount(0)
        colors.chunked(MAX_PALETTE_COLORS_PER_PACKET).forEachIndexed { chunk, chunkColors ->
            packets += palette(chunk * MAX_PALETTE_COLORS_PER_PACKET, chunkColors)
        }
        animation.frames.forEachIndexed { i, frame ->
            val indices = ByteArray(frame.size) { p -> paletteIndex.getValue(frame[p] and 0xFFFFFF).toByte() }
            packets += frame(index = i, offset = 0, indices = indices)
        }
        packets += durations(offset = 0, durationsMs = animation.frameDurationsMs)
        packets += direction(animation.direction)
        packets += frameCount(animation.frames.size)
        return packets
    }

    private fun u16le(value: Int): ByteArray = byteArrayOf(value.toByte(), (value shr 8).toByte())

    private companion object {
        const val OP_FRAME = 0x00
        const val OP_PALETTE = 0x01
        const val OP_FRAME_COUNT = 0x02
        const val OP_DURATIONS = 0x03
        const val OP_DIRECTION = 0x04
        const val OP_SET_MODE = 0x07
        const val MAX_PALETTE_COLORS_PER_PACKET = 160
    }
}

/**
 * Orientation test image: rainbow rows from red (top) to violet (bottom), a white border, and a
 * white 3×3 block in the top-left corner so you can tell which way up the frame is drawn.
 */
fun pixTestPattern(width: Int, height: Int): IntArray = IntArray(width * height) { i ->
    val x = i % width
    val y = i / width
    when {
        x < 3 && y < 3 -> 0xFFFFFF
        x == 0 || y == 0 || x == width - 1 || y == height - 1 -> 0xFFFFFF
        else -> hueToRgb(300f * y / (height - 1).coerceAtLeast(1))
    }
}

/** Fully saturated, full-value colour for [hue] in degrees. */
private fun hueToRgb(hue: Float): Int {
    val h = (hue % 360f) / 60f
    val x = ((1 - kotlin.math.abs(h % 2 - 1)) * 255).toInt()
    val (r, g, b) = when (h.toInt()) {
        0 -> Triple(255, x, 0)
        1 -> Triple(x, 255, 0)
        2 -> Triple(0, 255, x)
        3 -> Triple(0, x, 255)
        4 -> Triple(x, 0, 255)
        else -> Triple(255, 0, x)
    }
    return (r shl 16) or (g shl 8) or b
}
