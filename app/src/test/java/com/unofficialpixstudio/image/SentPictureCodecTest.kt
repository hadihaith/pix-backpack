package com.unofficialpixstudio.image

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class SentPictureCodecTest {

    @Test
    fun roundTrip() {
        val pixels = IntArray(16 * 20) { (it * 0x030507) and 0xFFFFFF }
        val original = SentPicture("24:6F:28:D1:92:3A", 16, 20, pixels, sentAtMillis = 1_762_800_000_000)

        val bytes = ByteArrayOutputStream().also { SentPictureCodec.write(original, it) }.toByteArray()
        val restored = SentPictureCodec.read(ByteArrayInputStream(bytes))

        assertEquals(original.address, restored.address)
        assertEquals(16, restored.width)
        assertEquals(20, restored.height)
        assertEquals(original.sentAtMillis, restored.sentAtMillis)
        assertArrayEquals(pixels, restored.pixels)
    }

    @Test(expected = IOException::class)
    fun rejectsOtherData() {
        SentPictureCodec.read(ByteArrayInputStream("not a picture".toByteArray()))
    }

    @Test(expected = IOException::class)
    fun rejectsTruncatedData() {
        val picture = SentPicture("AA:BB", 2, 2, IntArray(4), 0)
        val bytes = ByteArrayOutputStream().also { SentPictureCodec.write(picture, it) }.toByteArray()
        SentPictureCodec.read(ByteArrayInputStream(bytes.copyOf(bytes.size - 2)))
    }
}
