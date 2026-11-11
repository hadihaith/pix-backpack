package com.unofficialpixstudio.draw

import android.app.Application
import android.content.Context
import android.util.AtomicFile
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

enum class DrawTool { Pen, Eraser, Fill, Picker }

/** What the Draw tab shows. [pixels] is a fresh copy each time the drawing changes. */
class DrawCanvas(
    val width: Int,
    val height: Int,
    val pixels: IntArray,
    val canUndo: Boolean,
    val canRedo: Boolean,
) {
    val isBlank: Boolean get() = pixels.all { it == 0 }

    fun matches(drawing: Drawing): Boolean =
        width == drawing.width && height == drawing.height && pixels.contentEquals(drawing.pixels)
}

/** The saved drawing being edited. */
class OpenProject(val id: String, val name: String)

/**
 * Holds the drawing so it survives rotation and switching tabs, and keeps it on disk so it's
 * still there after the app restarts, saved or not. Tool, colour and recent colours are
 * remembered too.
 *
 * Drawings can be saved by name as projects ([savedDrawings]); the one being edited is
 * [project], and [hasUnsavedChanges] says whether the canvas differs from its saved copy.
 *
 * Touches arrive as [press], [drag]/[dragOutside] and [release] in grid cells; what they do
 * depends on the current tool.
 */
class DrawViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val draftFile = AtomicFile(File(application.filesDir, DRAFT_FILE))
    private val store = DrawingStore(File(application.filesDir, DRAWINGS_DIR))
    /** All file work runs here, one job at a time, so saves land in the order they were made. */
    private val io = Dispatchers.IO.limitedParallelism(1)
    private val editor = PixelEditor(DEFAULT_WIDTH, DEFAULT_HEIGHT)
    private var draftJob: Job? = null

    private val _canvas = MutableStateFlow(currentCanvas())
    val canvas: StateFlow<DrawCanvas> = _canvas.asStateFlow()

    private val _tool = MutableStateFlow(DrawTool.Pen)
    val tool: StateFlow<DrawTool> = _tool.asStateFlow()

    private val _color = MutableStateFlow(prefs.getInt(KEY_COLOR, DrawPalette.presets[1].rgb))
    /** Colour for the pen and fill, 0xRRGGBB. */
    val color: StateFlow<Int> = _color.asStateFlow()

    private val _mirror = MutableStateFlow(false)
    /** When on, everything drawn is also drawn reflected left–right. */
    val mirror: StateFlow<Boolean> = _mirror.asStateFlow()

    private val _recentColors = MutableStateFlow(loadRecentColors())
    /** Custom and picked colours that aren't presets, newest first. */
    val recentColors: StateFlow<List<Int>> = _recentColors.asStateFlow()

    private val _project = MutableStateFlow<OpenProject?>(null)
    /** The saved drawing being edited, or null for a new one that hasn't been saved. */
    val project: StateFlow<OpenProject?> = _project.asStateFlow()

    /** [project] as last saved, to tell whether the canvas has changed since. */
    private val savedCopy = MutableStateFlow<Drawing?>(null)

    /** True if there's something on the canvas that saving would keep. */
    val hasUnsavedChanges: StateFlow<Boolean> =
        combine(_canvas, _project, savedCopy) { canvas, project, saved ->
            if (project == null) !canvas.isBlank else saved == null || !canvas.matches(saved)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _savedDrawings = MutableStateFlow<List<SavedDrawing>?>(null)
    /** The user's saved drawings, newest first; null until read from storage. */
    val savedDrawings: StateFlow<List<SavedDrawing>?> = _savedDrawings.asStateFlow()

    private val _saveFailed = MutableStateFlow(false)
    /** True after a save, rename or delete failed, until the next one succeeds. */
    val saveFailed: StateFlow<Boolean> = _saveFailed.asStateFlow()

    init {
        viewModelScope.launch {
            val openId = prefs.getString(KEY_PROJECT, null)
            val (draft, saved) = withContext(io) {
                val draft = runCatching { draftFile.openRead().use(DrawingCodec::read) }.getOrNull()
                draft to store.list()
            }
            // Don't replace anything drawn while this was loading.
            if (draft != null && editor.isBlank && !editor.canUndo) {
                editor.reset(draft.width, draft.height, draft.pixels)
                publish(save = false)
            }
            _savedDrawings.value = saved
            link(saved.firstOrNull { it.id == openId })
        }
    }

    fun press(x: Int, y: Int) {
        when (_tool.value) {
            DrawTool.Pen, DrawTool.Eraser -> {
                editor.beginStroke()
                editor.strokeTo(x, y, strokeColor(), _mirror.value)
                publish(save = false)
            }
            DrawTool.Fill -> {
                editor.fill(x, y, _color.value, _mirror.value)
                publish()
            }
            DrawTool.Picker -> pick(x, y)
        }
    }

    fun drag(x: Int, y: Int) {
        when (_tool.value) {
            DrawTool.Pen, DrawTool.Eraser -> if (editor.isStroking) {
                editor.strokeTo(x, y, strokeColor(), _mirror.value)
                publish(save = false)
            }
            DrawTool.Picker -> pick(x, y)
            DrawTool.Fill -> Unit
        }
    }

    fun dragOutside() = editor.liftPen()

    fun release() {
        if (editor.isStroking) {
            editor.endStroke()
            publish()
        }
        // The eyedropper is a one-off: go back to drawing with what was picked.
        if (_tool.value == DrawTool.Picker) _tool.value = DrawTool.Pen
    }

    fun selectTool(tool: DrawTool) {
        _tool.value = tool
    }

    fun toggleMirror() {
        _mirror.value = !_mirror.value
    }

    /** Picks a colour to draw with; switches from the eraser/eyedropper back to the pen. */
    fun selectColor(rgb: Int) {
        val color = rgb and 0xFFFFFF
        _color.value = color
        prefs.edit { putInt(KEY_COLOR, color) }
        if (_tool.value == DrawTool.Eraser || _tool.value == DrawTool.Picker) _tool.value = DrawTool.Pen
    }

    /** A colour mixed in the colour picker: selected and kept in the recent colours. */
    fun selectCustomColor(rgb: Int) {
        rememberColor(rgb and 0xFFFFFF)
        selectColor(rgb)
    }

    fun undo() {
        if (editor.undo()) publish()
    }

    fun redo() {
        if (editor.redo()) publish()
    }

    fun clear() {
        editor.clear()
        publish()
    }

    /** Saves over the open project. Does nothing for a new drawing: that needs [saveNew]. */
    fun save() {
        val open = _project.value ?: return
        write(open.id, open.name)
    }

    /** Saves the drawing as a new project called [name] and keeps editing that. */
    fun saveNew(name: String) = write(store.newId(), name)

    /** A name for the next new drawing that no saved one has yet: "Drawing 1", "Drawing 2", … */
    fun suggestedName(): String {
        val used = _savedDrawings.value.orEmpty().mapTo(HashSet()) { it.name }
        return generateSequence(1) { it + 1 }.map { "Drawing $it" }.first { it !in used }
    }

    /** Opens a saved drawing in place of the current one (whose unsaved changes are lost). */
    fun open(saved: SavedDrawing) {
        val drawing = saved.drawing
        editor.reset(drawing.width, drawing.height, drawing.pixels)
        link(saved)
        publish(saveDraftNow = true)
    }

    /** Starts a blank, unsaved drawing (the current one's unsaved changes are lost). */
    fun newDrawing(width: Int, height: Int) {
        editor.reset(width, height)
        link(null)
        publish(saveDraftNow = true)
    }

    fun rename(id: String, name: String) {
        val newName = name.trim()
        val renameOpen = { if (_project.value?.id == id) _project.value = OpenProject(id, newName) }
        updateStore(onSuccess = renameOpen) {
            val saved = store.load(id) ?: return@updateStore
            store.save(SavedDrawing(id, newName, saved.updatedAtMillis, saved.drawing))
        }
    }

    /** Saves a copy of [saved] next to it; the open drawing doesn't change. */
    fun duplicate(saved: SavedDrawing) = updateStore {
        store.save(SavedDrawing(store.newId(), "${saved.name} copy", System.currentTimeMillis(), saved.drawing))
    }

    /** Deletes a saved drawing. If it's the one open, the canvas starts over blank. */
    fun delete(id: String) {
        if (_project.value?.id == id) newDrawing(editor.width, editor.height)
        updateStore { store.delete(id) }
    }

    /** Replaces the drawing with [pixels] (e.g. the last picture sent), undoably. */
    fun load(pixels: IntArray, width: Int, height: Int) {
        editor.replace(width, height, pixels)
        publish()
    }

    /** Resizes the drawing to the backpack's display, keeping it centred. Undoable. */
    fun resize(width: Int, height: Int) {
        editor.resize(width, height)
        publish()
    }

    /** Follows the display size while there's nothing drawn, so a blank canvas always fits. */
    fun fitBlankCanvasTo(width: Int, height: Int) {
        if (editor.isBlank && (editor.width != width || editor.height != height)) {
            editor.reset(width, height)
            publish()
        }
    }

    private fun strokeColor(): Int = if (_tool.value == DrawTool.Eraser) 0 else _color.value

    /** Eyedropper: takes the colour under the finger. Unlit LEDs are skipped. */
    private fun pick(x: Int, y: Int) {
        val picked = editor.pixelAt(x, y)
        if (picked == 0) return
        _color.value = picked
        prefs.edit { putInt(KEY_COLOR, picked) }
        if (!DrawPalette.isPreset(picked)) rememberColor(picked)
    }

    private fun rememberColor(rgb: Int) {
        if (DrawPalette.isPreset(rgb)) return
        val recent = (listOf(rgb) + _recentColors.value.filter { it != rgb }).take(MAX_RECENT_COLORS)
        _recentColors.value = recent
        prefs.edit { putString(KEY_RECENT, recent.joinToString(",") { Integer.toHexString(it) }) }
    }

    private fun loadRecentColors(): List<Int> =
        prefs.getString(KEY_RECENT, null).orEmpty()
            .split(',')
            .mapNotNull { it.toIntOrNull(16) }
            .take(MAX_RECENT_COLORS)

    /** Saves the canvas as project [id] called [name], then edits that project. */
    private fun write(id: String, name: String) {
        val drawing = Drawing(editor.width, editor.height, editor.pixels)
        val saved = SavedDrawing(id, name.trim(), System.currentTimeMillis(), drawing)
        updateStore(onSuccess = { link(saved) }) { store.save(saved) }
    }

    /** Runs [change] on the saved drawings, then refreshes the list (and reports failure). */
    private fun updateStore(onSuccess: () -> Unit = {}, change: () -> Unit) {
        viewModelScope.launch {
            val (ok, list) = withContext(io) {
                val ok = try {
                    change()
                    true
                } catch (e: IOException) {
                    false
                }
                ok to store.list()
            }
            _saveFailed.value = !ok
            if (ok) onSuccess()
            _savedDrawings.value = list
        }
    }

    /** Makes [saved] the open project (null: none), remembered across restarts. */
    private fun link(saved: SavedDrawing?) {
        _project.value = saved?.let { OpenProject(it.id, it.name) }
        savedCopy.value = saved?.drawing
        prefs.edit { if (saved == null) remove(KEY_PROJECT) else putString(KEY_PROJECT, saved.id) }
    }

    private fun currentCanvas() =
        DrawCanvas(editor.width, editor.height, editor.pixels, editor.canUndo, editor.canRedo)

    /**
     * Shows the editor's current state; with [save], also keeps it on disk shortly after
     * (straight away with [saveDraftNow], e.g. when switching drawings).
     */
    private fun publish(save: Boolean = true, saveDraftNow: Boolean = false) {
        _canvas.value = currentCanvas()
        if (save) saveDraft(if (saveDraftNow) 0 else DRAFT_DELAY_MS)
    }

    /** Writes the canvas after [delayMs], so a quick run of edits writes the file once. */
    private fun saveDraft(delayMs: Long) {
        val drawing = Drawing(editor.width, editor.height, editor.pixels)
        draftJob?.cancel()
        draftJob = viewModelScope.launch(io) {
            delay(delayMs)
            val stream = try {
                draftFile.startWrite()
            } catch (e: IOException) {
                return@launch
            }
            try {
                DrawingCodec.write(drawing, stream)
                draftFile.finishWrite(stream)
            } catch (e: IOException) {
                draftFile.failWrite(stream)
            }
        }
    }

    private companion object {
        const val PREFS_NAME = "draw"
        const val KEY_COLOR = "color"
        const val KEY_RECENT = "recent_colors"
        const val KEY_PROJECT = "open_project"
        const val DRAFT_FILE = "drawing.bin"
        const val DRAWINGS_DIR = "drawings"
        const val DEFAULT_WIDTH = 16
        const val DEFAULT_HEIGHT = 20
        const val MAX_RECENT_COLORS = 8
        const val DRAFT_DELAY_MS = 400L
    }
}
