package com.unofficialpixstudio.image

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** The last picture this phone sent to a backpack, kept so the app can show it after restart. */
class SentPicture(
    /** Bluetooth address of the backpack it was sent to. */
    val address: String,
    val width: Int,
    val height: Int,
    /** 0xRRGGBB, row-major. */
    val pixels: IntArray,
    val sentAtMillis: Long,
) {
    init {
        require(width > 0 && height > 0 && pixels.size == width * height) { "Pixels don't match size" }
    }
}

/**
 * Small binary format for [SentPicture]:
 * magic "PIXS", version, address (UTF), width, height, sent-at, then 3 bytes (R, G, B) per pixel.
 */
object SentPictureCodec {
    private const val MAGIC = 0x50495853 // "PIXS"
    private const val VERSION = 1
    private const val MAX_PIXELS = 4096

    fun write(picture: SentPicture, output: OutputStream) {
        val out = DataOutputStream(output)
        out.writeInt(MAGIC)
        out.writeByte(VERSION)
        out.writeUTF(picture.address)
        out.writeShort(picture.width)
        out.writeShort(picture.height)
        out.writeLong(picture.sentAtMillis)
        for (p in picture.pixels) {
            out.writeByte(p shr 16)
            out.writeByte(p shr 8)
            out.writeByte(p)
        }
        out.flush()
    }

    /** Reads a picture, or throws [IOException] if the data isn't one (wrong format/corrupt). */
    fun read(input: InputStream): SentPicture {
        val data = DataInputStream(input)
        if (data.readInt() != MAGIC) throw IOException("Not a saved picture")
        if (data.readUnsignedByte() != VERSION) throw IOException("Unsupported saved picture version")
        val address = data.readUTF()
        val width = data.readUnsignedShort()
        val height = data.readUnsignedShort()
        val sentAt = data.readLong()
        if (width == 0 || height == 0 || width * height > MAX_PIXELS) throw IOException("Bad picture size")
        val pixels = IntArray(width * height) {
            (data.readUnsignedByte() shl 16) or (data.readUnsignedByte() shl 8) or data.readUnsignedByte()
        }
        return SentPicture(address, width, height, pixels, sentAt)
    }
}
