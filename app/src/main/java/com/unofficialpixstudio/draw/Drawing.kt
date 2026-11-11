package com.unofficialpixstudio.draw

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** A drawing as saved between app launches. Pixels are 0xRRGGBB, row-major. */
class Drawing(val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(width > 0 && height > 0 && pixels.size == width * height) { "Pixels don't match size" }
    }
}

/** A drawing the user saved under a name; listed in My drawings. [id] is also its file name. */
class SavedDrawing(val id: String, val name: String, val updatedAtMillis: Long, val drawing: Drawing)

/**
 * Small binary formats, both ending in width, height, then 3 bytes (R, G, B) per pixel:
 * - the drawing being edited: magic "PIXD", version, then the pixels;
 * - a saved drawing: magic "PIXP", version, name (UTF), updated-at, then the pixels.
 */
object DrawingCodec {
    private const val DRAFT_MAGIC = 0x50495844 // "PIXD"
    private const val SAVED_MAGIC = 0x50495850 // "PIXP"
    private const val VERSION = 1
    private const val MAX_PIXELS = 4096

    fun write(drawing: Drawing, output: OutputStream) {
        val out = DataOutputStream(output)
        out.writeInt(DRAFT_MAGIC)
        out.writeByte(VERSION)
        writePixels(drawing, out)
        out.flush()
    }

    /** Reads a drawing, or throws [IOException] if the data isn't one (wrong format/corrupt). */
    fun read(input: InputStream): Drawing {
        val data = DataInputStream(input)
        readHeader(data, DRAFT_MAGIC)
        return readPixels(data)
    }

    fun writeSaved(saved: SavedDrawing, output: OutputStream) {
        val out = DataOutputStream(output)
        out.writeInt(SAVED_MAGIC)
        out.writeByte(VERSION)
        out.writeUTF(saved.name)
        out.writeLong(saved.updatedAtMillis)
        writePixels(saved.drawing, out)
        out.flush()
    }

    /** Reads a saved drawing stored under [id], or throws [IOException] if it isn't one. */
    fun readSaved(input: InputStream, id: String): SavedDrawing {
        val data = DataInputStream(input)
        readHeader(data, SAVED_MAGIC)
        val name = data.readUTF()
        val updatedAt = data.readLong()
        return SavedDrawing(id, name, updatedAt, readPixels(data))
    }

    private fun readHeader(data: DataInputStream, magic: Int) {
        if (data.readInt() != magic) throw IOException("Not a saved drawing")
        if (data.readUnsignedByte() != VERSION) throw IOException("Unsupported saved drawing version")
    }

    private fun writePixels(drawing: Drawing, out: DataOutputStream) {
        out.writeShort(drawing.width)
        out.writeShort(drawing.height)
        for (p in drawing.pixels) {
            out.writeByte(p shr 16)
            out.writeByte(p shr 8)
            out.writeByte(p)
        }
    }

    private fun readPixels(data: DataInputStream): Drawing {
        val width = data.readUnsignedShort()
        val height = data.readUnsignedShort()
        if (width == 0 || height == 0 || width * height > MAX_PIXELS) throw IOException("Bad drawing size")
        val pixels = IntArray(width * height) {
            (data.readUnsignedByte() shl 16) or (data.readUnsignedByte() shl 8) or data.readUnsignedByte()
        }
        return Drawing(width, height, pixels)
    }
}

class PaletteColor(val name: String, val rgb: Int)

/** Ready-made colours, picked to look distinct on the LEDs (dark shades just look dim there). */
object DrawPalette {
    val presets = listOf(
        PaletteColor("White", 0xFFFFFF),
        PaletteColor("Red", 0xFF0000),
        PaletteColor("Orange", 0xFF6A00),
        PaletteColor("Yellow", 0xFFD800),
        PaletteColor("Lime", 0xA0FF00),
        PaletteColor("Green", 0x00E030),
        PaletteColor("Mint", 0x00FFA0),
        PaletteColor("Cyan", 0x00FFFF),
        PaletteColor("Sky blue", 0x0090FF),
        PaletteColor("Blue", 0x0020FF),
        PaletteColor("Purple", 0x8C00FF),
        PaletteColor("Magenta", 0xFF00DC),
        PaletteColor("Pink", 0xFF6FB5),
        PaletteColor("Peach", 0xFFB27F),
        PaletteColor("Brown", 0x8B4A1C),
    )

    fun isPreset(rgb: Int): Boolean = presets.any { it.rgb == rgb }
}
