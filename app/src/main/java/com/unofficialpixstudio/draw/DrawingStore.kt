package com.unofficialpixstudio.draw

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/**
 * The user's saved drawings: one file per drawing in [directory], named by its id.
 *
 * Plain java.io so it's unit-testable. Saves write a temporary file and rename it into place,
 * so a crash mid-save never leaves a half-written drawing. Blocking; call off the main thread,
 * one call at a time.
 */
class DrawingStore(private val directory: File) {

    /** Every readable saved drawing, most recently changed first. Corrupt files are skipped. */
    fun list(): List<SavedDrawing> =
        directory.listFiles { file -> file.isFile && file.name.endsWith(EXTENSION) }.orEmpty()
            .mapNotNull { file -> read(file, file.name.removeSuffix(EXTENSION)) }
            .sortedWith(compareByDescending<SavedDrawing> { it.updatedAtMillis }.thenBy { it.name })

    fun load(id: String): SavedDrawing? = read(fileFor(id), id)

    /** Creates or replaces the drawing with [SavedDrawing.id]. */
    fun save(saved: SavedDrawing) {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Can't create $directory")
        val target = fileFor(saved.id)
        val temp = File(directory, target.name + ".tmp")
        try {
            FileOutputStream(temp).use { file ->
                val out = BufferedOutputStream(file)
                DrawingCodec.writeSaved(saved, out)
                out.flush()
                file.fd.sync()
            }
            if (!temp.renameTo(target)) throw IOException("Couldn't replace ${target.name}")
        } finally {
            temp.delete()
        }
    }

    /** Deletes the drawing; false if there was none with that id. */
    fun delete(id: String): Boolean = fileFor(id).delete()

    fun newId(): String = UUID.randomUUID().toString()

    private fun read(file: File, id: String): SavedDrawing? =
        if (!file.isFile) {
            null
        } else {
            try {
                file.inputStream().buffered().use { DrawingCodec.readSaved(it, id) }
            } catch (e: IOException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
        }

    private fun fileFor(id: String): File {
        // Ids come from newId(); never let one reach outside the directory.
        require(ID_PATTERN.matches(id)) { "Bad drawing id" }
        return File(directory, id + EXTENSION)
    }

    private companion object {
        const val EXTENSION = ".pixp"
        val ID_PATTERN = Regex("[A-Za-z0-9-]{1,64}")
    }
}
