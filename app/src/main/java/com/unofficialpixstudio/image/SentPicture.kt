package com.unofficialpixstudio.image

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * The last thing this phone sent to a backpack, kept so the app can show it after restart:
 * a still picture (one frame) or an animation (several).
 */
class SentPicture(
    /** Bluetooth address of the backpack it was sent to. */
    val address: String,
    val width: Int,
    val height: Int,
    /** Each frame is 0xRRGGBB, row-major. */
    val frames: List<IntArray>,
    /** Milliseconds each frame is shown for. Only matters for animations. */
    val frameMs: Int,
    val sentAtMillis: Long,
) {
    /** A still picture. */
    constructor(address: String, width: Int, height: Int, pixels: IntArray, sentAtMillis: Long) :
        this(address, width, height, listOf(pixels), 0, sentAtMillis)

    init {
        require(width > 0 && height > 0) { "Size must be positive" }
        require(frames.isNotEmpty() && frames.all { it.size == width * height }) { "Pixels don't match size" }
        require(frameMs in 0..0xFFFF) { "Frame time must fit in 16 bits" }
    }

    /** The still picture, or an animation's first frame. */
    val pixels: IntArray get() = frames.first()

    val isAnimation: Boolean get() = frames.size > 1
}

/**
 * Small binary format for [SentPicture]:
 * magic "PIXS", version, address (UTF), width, height, sent-at, frame time, frame count, then
 * 3 bytes (R, G, B) per pixel of each frame. Version 1 files (a single picture, with no frame
 * time or count) are still read.
 */
object SentPictureCodec {
    private const val MAGIC = 0x50495853 // "PIXS"
    private const val VERSION = 2
    private const val MAX_PIXELS = 4096
    private const val MAX_FRAMES = 255

    fun write(picture: SentPicture, output: OutputStream) {
        require(picture.frames.size <= MAX_FRAMES) { "Too many frames" }
        val out = DataOutputStream(output)
        out.writeInt(MAGIC)
        out.writeByte(VERSION)
        out.writeUTF(picture.address)
        out.writeShort(picture.width)
        out.writeShort(picture.height)
        out.writeLong(picture.sentAtMillis)
        out.writeShort(picture.frameMs)
        out.writeShort(picture.frames.size)
        for (frame in picture.frames) {
            for (p in frame) {
                out.writeByte(p shr 16)
                out.writeByte(p shr 8)
                out.writeByte(p)
            }
        }
        out.flush()
    }

    /** Reads a picture, or throws [IOException] if the data isn't one (wrong format/corrupt). */
    fun read(input: InputStream): SentPicture {
        val data = DataInputStream(input)
        if (data.readInt() != MAGIC) throw IOException("Not a saved picture")
        val version = data.readUnsignedByte()
        if (version !in 1..VERSION) throw IOException("Unsupported saved picture version")
        val address = data.readUTF()
        val width = data.readUnsignedShort()
        val height = data.readUnsignedShort()
        val sentAt = data.readLong()
        if (width == 0 || height == 0 || width * height > MAX_PIXELS) throw IOException("Bad picture size")
        val frameMs = if (version >= 2) data.readUnsignedShort() else 0
        val frameCount = if (version >= 2) data.readUnsignedShort() else 1
        if (frameCount !in 1..MAX_FRAMES) throw IOException("Bad frame count")
        val frames = List(frameCount) {
            IntArray(width * height) {
                (data.readUnsignedByte() shl 16) or (data.readUnsignedByte() shl 8) or data.readUnsignedByte()
            }
        }
        return SentPicture(address, width, height, frames, frameMs, sentAt)
    }
}
