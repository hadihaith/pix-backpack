package com.unofficialpixstudio.draw

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

class DrawingStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun drawing(color: Int) = Drawing(2, 2, IntArray(4) { color })

    private fun storeDir() = File(folder.root, "drawings")

    @Test
    fun savesListsAndLoads() {
        val store = DrawingStore(storeDir())
        assertEquals(emptyList<SavedDrawing>(), store.list()) // directory doesn't exist yet

        val id = store.newId()
        store.save(SavedDrawing(id, "Rocket", 1_000, drawing(0xFF0000)))

        val loaded = store.load(id)!!
        assertEquals("Rocket", loaded.name)
        assertEquals(1_000, loaded.updatedAtMillis)
        assertArrayEquals(IntArray(4) { 0xFF0000 }, loaded.drawing.pixels)
        assertEquals(listOf(id), store.list().map { it.id })
    }

    @Test
    fun listsNewestFirst() {
        val store = DrawingStore(storeDir())
        store.save(SavedDrawing("old", "Old", 1_000, drawing(1)))
        store.save(SavedDrawing("new", "New", 3_000, drawing(2)))
        store.save(SavedDrawing("mid", "Mid", 2_000, drawing(3)))
        assertEquals(listOf("new", "mid", "old"), store.list().map { it.id })
    }

    @Test
    fun saveReplacesExisting() {
        val store = DrawingStore(storeDir())
        store.save(SavedDrawing("a", "First", 1_000, drawing(1)))
        store.save(SavedDrawing("a", "Second", 2_000, drawing(2)))

        val all = store.list()
        assertEquals(1, all.size)
        assertEquals("Second", all.single().name)
        assertArrayEquals(IntArray(4) { 2 }, all.single().drawing.pixels)
        // No temporary files left behind.
        assertEquals(listOf("a.pixp"), storeDir().list()!!.toList())
    }

    @Test
    fun deletes() {
        val store = DrawingStore(storeDir())
        store.save(SavedDrawing("a", "A", 1_000, drawing(1)))
        assertTrue(store.delete("a"))
        assertFalse(store.delete("a"))
        assertNull(store.load("a"))
        assertEquals(emptyList<SavedDrawing>(), store.list())
    }

    @Test
    fun skipsCorruptFiles() {
        val store = DrawingStore(storeDir())
        store.save(SavedDrawing("good", "Good", 1_000, drawing(1)))
        File(storeDir(), "bad.pixp").writeText("not a drawing")
        assertEquals(listOf("good"), store.list().map { it.id })
        assertNull(store.load("bad"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsIdsThatEscapeTheDirectory() {
        DrawingStore(storeDir()).load("../secret")
    }

    @Test
    fun savedCodecRoundTripKeepsUnicodeNames() {
        val original = SavedDrawing("x", "Ünïcødé ✨ heart", 1_762_800_000_000, drawing(0x123456))
        val bytes = ByteArrayOutputStream().also { DrawingCodec.writeSaved(original, it) }.toByteArray()
        val restored = DrawingCodec.readSaved(ByteArrayInputStream(bytes), "x")
        assertEquals(original.name, restored.name)
        assertEquals(original.updatedAtMillis, restored.updatedAtMillis)
        assertArrayEquals(original.drawing.pixels, restored.drawing.pixels)
    }

    @Test(expected = IOException::class)
    fun savedCodecRejectsDraftFormat() {
        // The in-progress drawing file must not be mistaken for a saved drawing.
        val bytes = ByteArrayOutputStream().also { DrawingCodec.write(drawing(1), it) }.toByteArray()
        DrawingCodec.readSaved(ByteArrayInputStream(bytes), "x")
    }
}
