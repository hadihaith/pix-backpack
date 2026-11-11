package com.unofficialpixstudio.draw

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.unofficialpixstudio.ble.BackpackViewModel
import com.unofficialpixstudio.ble.GattState
import com.unofficialpixstudio.image.colourSummary
import com.unofficialpixstudio.ui.components.PixCard
import com.unofficialpixstudio.ui.components.PixIcons
import com.unofficialpixstudio.ui.components.SendProgress
import com.unofficialpixstudio.ui.theme.LedOff
import com.unofficialpixstudio.ui.theme.LedPanel
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** A name to ask the user for, and what to do with it. */
private class NameRequest(
    val title: String,
    val initial: String,
    val confirmLabel: String,
    val onConfirm: (String) -> Unit,
)

/**
 * Draw tab: paint pixel by pixel at the backpack's resolution with a colour palette, save
 * drawings by name and send them. Opens on My drawings ([DrawGallery]); picking or starting a
 * drawing shows the editor. The drawing being edited is kept (and saved) by [DrawViewModel],
 * so it survives switching tabs.
 */
@Composable
fun DrawScreen(
    backpack: BackpackViewModel,
    modifier: Modifier = Modifier,
    draw: DrawViewModel = viewModel(),
) {
    val displaySize by backpack.displaySize.collectAsState()
    val project by draw.project.collectAsState()
    val unsaved by draw.hasUnsavedChanges.collectAsState()
    val savedDrawings by draw.savedDrawings.collectAsState()
    val canvas by draw.canvas.collectAsState()
    // Opening the tab starts on My drawings; this resets whenever the tab is left.
    var showingGallery by rememberSaveable { mutableStateOf(true) }
    var naming by remember { mutableStateOf<NameRequest?>(null) }
    var afterDiscard by remember { mutableStateOf<(() -> Unit)?>(null) }
    var deleting by remember { mutableStateOf<SavedDrawing?>(null) }

    LaunchedEffect(displaySize) { draw.fitBlankCanvasTo(displaySize.width, displaySize.height) }

    // Runs [action] now, or once the user agrees to lose the unsaved changes.
    fun unlessUnsaved(action: () -> Unit) {
        if (unsaved) afterDiscard = action else action()
    }

    fun startNew() = unlessUnsaved {
        draw.newDrawing(displaySize.width, displaySize.height)
        showingGallery = false
    }

    fun askToRename(id: String, name: String) {
        naming = NameRequest("Rename drawing", name, "Rename") { draw.rename(id, it) }
    }

    // Back from the editor returns to My drawings (and from there, to Home).
    BackHandler(enabled = !showingGallery) { showingGallery = true }

    if (showingGallery) {
        DrawGallery(
            drawings = savedDrawings,
            openId = project?.id,
            openHasUnsavedChanges = unsaved,
            unsavedNewDrawing = canvas.takeIf { project == null && !it.isBlank },
            onContinue = { showingGallery = false },
            onNew = ::startNew,
            onOpen = { saved ->
                if (saved.id == project?.id) {
                    showingGallery = false
                } else {
                    unlessUnsaved {
                        draw.open(saved)
                        showingGallery = false
                    }
                }
            },
            onRename = { askToRename(it.id, it.name) },
            onDuplicate = draw::duplicate,
            onDelete = { deleting = it },
            modifier = modifier,
        )
    } else {
        DrawEditor(
            backpack = backpack,
            draw = draw,
            project = project,
            unsaved = unsaved,
            onOpenGallery = { showingGallery = true },
            onSave = {
                if (project != null) {
                    draw.save()
                } else {
                    naming = NameRequest("Save drawing", draw.suggestedName(), "Save", draw::saveNew)
                }
            },
            onSaveCopy = { open -> naming = NameRequest("Save a copy", "${open.name} copy", "Save", draw::saveNew) },
            onRename = { open -> askToRename(open.id, open.name) },
            onNew = ::startNew,
            modifier = modifier,
        )
    }

    naming?.let { request ->
        NameDialog(
            title = request.title,
            initial = request.initial,
            confirmLabel = request.confirmLabel,
            onDismiss = { naming = null },
            onConfirm = {
                naming = null
                request.onConfirm(it)
            },
        )
    }
    afterDiscard?.let { action ->
        ConfirmDialog(
            title = "Discard changes?",
            text = project?.let { "Changes to “${it.name}” haven't been saved." } ?: "This drawing hasn't been saved.",
            confirmLabel = "Discard",
            dismissLabel = "Keep editing",
            onDismiss = { afterDiscard = null },
            onConfirm = {
                afterDiscard = null
                action()
            },
        )
    }
    deleting?.let { saved ->
        val clears = if (saved.id == project?.id) " It's open now, so the canvas will be cleared too." else ""
        ConfirmDialog(
            title = "Delete drawing?",
            text = "“${saved.name}” will be deleted from this phone.$clears This can't be undone.",
            confirmLabel = "Delete",
            dismissLabel = "Cancel",
            onDismiss = { deleting = null },
            onConfirm = {
                deleting = null
                draw.delete(saved.id)
            },
        )
    }
}

@Composable
private fun DrawEditor(
    backpack: BackpackViewModel,
    draw: DrawViewModel,
    project: OpenProject?,
    unsaved: Boolean,
    onOpenGallery: () -> Unit,
    onSave: () -> Unit,
    onSaveCopy: (OpenProject) -> Unit,
    onRename: (OpenProject) -> Unit,
    onNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connection by backpack.connectionState.collectAsState()
    val displaySize by backpack.displaySize.collectAsState()
    val busy by backpack.busy.collectAsState()
    val message by backpack.message.collectAsState()
    val sendProgress by backpack.sendProgress.collectAsState()
    val lastSent by backpack.nowShowing.collectAsState()
    val canvas by draw.canvas.collectAsState()
    val tool by draw.tool.collectAsState()
    val color by draw.color.collectAsState()
    val mirror by draw.mirror.collectAsState()
    val recentColors by draw.recentColors.collectAsState()
    val saveFailed by draw.saveFailed.collectAsState()
    var pickingColor by rememberSaveable { mutableStateOf(false) }

    val fitsDisplay = canvas.width == displaySize.width && canvas.height == displaySize.height

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onOpenGallery) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "My drawings")
            }
            DrawingTitle(
                project = project,
                unsaved = unsaved,
                blank = canvas.isBlank,
                saveFailed = saveFailed,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            IconButton(onClick = draw::undo, enabled = canvas.canUndo) {
                Icon(PixIcons.Undo, contentDescription = "Undo")
            }
            IconButton(onClick = draw::redo, enabled = canvas.canRedo) {
                Icon(PixIcons.Redo, contentDescription = "Redo")
            }
            MoreMenu(
                listOfNotNull(
                    MenuItem("New drawing", onClick = onNew),
                    project?.let { MenuItem("Save as copy") { onSaveCopy(it) } },
                    project?.let { MenuItem("Rename") { onRename(it) } },
                    MenuItem("Clear drawing", enabled = !canvas.isBlank, onClick = draw::clear),
                    lastSent?.let { picture ->
                        MenuItem("Edit last sent picture") { draw.load(picture.pixels, picture.width, picture.height) }
                    },
                )
            )
        }

        if (!fitsDisplay) {
            Spacer(Modifier.height(8.dp))
            PixCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Text(
                    "This drawing is ${canvas.width}×${canvas.height}, but your backpack shows " +
                        "${displaySize.width}×${displaySize.height}.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = { draw.resize(displaySize.width, displaySize.height) }) {
                    Text("Fit to backpack")
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            PixelCanvas(
                canvas = canvas,
                mirror = mirror,
                onPress = draw::press,
                onDrag = draw::drag,
                onDragOutside = draw::dragOutside,
                onRelease = draw::release,
                modifier = Modifier.aspectRatio(canvas.width.toFloat() / canvas.height),
            )
        }

        Spacer(Modifier.height(16.dp))
        ToolBar(tool = tool, mirror = mirror, onTool = draw::selectTool, onToggleMirror = draw::toggleMirror)

        Spacer(Modifier.height(12.dp))
        Palette(
            selected = color,
            recentColors = recentColors,
            onSelect = draw::selectColor,
            onCustom = { pickingColor = true },
        )

        Spacer(Modifier.height(16.dp))
        val ready = connection is GattState.Ready
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Always tappable: it reads "Saved" once there's nothing new, but saving again is harmless,
            // and a new drawing can be named before anything is drawn.
            val saved = project != null && !unsaved
            FilledTonalButton(
                onClick = onSave,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                if (saved) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                }
                Text(if (saved) "Saved" else "Save")
            }
            Button(
                onClick = { backpack.sendImage(canvas.pixels, canvas.width, canvas.height) },
                enabled = ready && !busy && fitsDisplay,
                modifier = Modifier.weight(1.6f),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) { Text(if (busy) "Sending…" else "Send to backpack") }
        }
        val summary = remember(canvas) { colourSummary(canvas.pixels) }
        val progress = sendProgress
        if (ready && progress != null) {
            SendProgress(progress, Modifier.padding(top = 6.dp))
        } else {
            Text(
                when {
                    !ready -> "Backpack not connected."
                    message != null -> message.orEmpty()
                    else -> summary
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (!ready) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                maxLines = 1,
            )
        }
    }

    if (pickingColor) {
        ColorPickerDialog(
            initial = color,
            onDismiss = { pickingColor = false },
            onPick = {
                draw.selectCustomColor(it)
                pickingColor = false
            },
        )
    }
}

/** Which drawing this is, and under it whether it's saved, e.g. "Rocket" / "unsaved changes". */
@Composable
private fun DrawingTitle(
    project: OpenProject?,
    unsaved: Boolean,
    blank: Boolean,
    saveFailed: Boolean,
    modifier: Modifier = Modifier,
) {
    val status = when {
        saveFailed -> "Couldn't save, try again"
        project == null && blank -> null
        project == null -> "Not saved"
        unsaved -> "Unsaved changes"
        else -> "Saved"
    }
    val statusColor = when {
        saveFailed -> MaterialTheme.colorScheme.error
        unsaved -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(modifier) {
        Text(
            project?.name ?: "New drawing",
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (status != null) {
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = statusColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private class MenuItem(val label: String, val enabled: Boolean = true, val onClick: () -> Unit)

@Composable
private fun MoreMenu(items: List<MenuItem>) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label) },
                    enabled = item.enabled,
                    onClick = {
                        open = false
                        item.onClick()
                    },
                )
            }
        }
    }
}

/**
 * The drawing as a grid of LEDs. Reports touches in grid cells: [onPress] where a finger
 * lands, [onDrag] each time it moves to another cell (or [onDragOutside] off the grid), and
 * [onRelease] when it lifts. Extra fingers are ignored.
 */
@Composable
private fun PixelCanvas(
    canvas: DrawCanvas,
    mirror: Boolean,
    onPress: (Int, Int) -> Unit,
    onDrag: (Int, Int) -> Unit,
    onDragOutside: () -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Gesture handling outlives recompositions; read the latest values through these.
    val gridWidth by rememberUpdatedState(canvas.width)
    val gridHeight by rememberUpdatedState(canvas.height)
    val press by rememberUpdatedState(onPress)
    val drag by rememberUpdatedState(onDrag)
    val dragOutside by rememberUpdatedState(onDragOutside)
    val release by rememberUpdatedState(onRelease)

    Canvas(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(LedPanel)
            .semantics { contentDescription = "Drawing, ${canvas.width} by ${canvas.height} pixels" }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val width = gridWidth
                    val height = gridHeight
                    val cell = min(size.width.toFloat() / width, size.height.toFloat() / height)
                    val originX = (size.width - cell * width) / 2
                    val originY = (size.height - cell * height) / 2
                    fun cellAt(p: Offset): IntOffset? {
                        val x = floor((p.x - originX) / cell).toInt()
                        val y = floor((p.y - originY) / cell).toInt()
                        return if (x in 0 until width && y in 0 until height) IntOffset(x, y) else null
                    }

                    val down = awaitFirstDown()
                    val start = cellAt(down.position) ?: return@awaitEachGesture
                    down.consume()
                    press(start.x, start.y)
                    try {
                        var last: IntOffset? = start
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if (!change.positionChanged()) continue
                            change.consume()
                            val now = cellAt(change.position)
                            if (now != last) {
                                if (now == null) dragOutside() else drag(now.x, now.y)
                                last = now
                            }
                        }
                    } finally {
                        release()
                    }
                }
            }
    ) {
        val width = canvas.width
        val height = canvas.height
        val cell = min(size.width / width, size.height / height)
        val originX = (size.width - cell * width) / 2
        val originY = (size.height - cell * height) / 2
        val gap = max(1f, cell * 0.1f)
        val led = Size(cell - gap, cell - gap)
        val corner = CornerRadius(cell * 0.2f)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val rgb = canvas.pixels[y * width + x]
                drawRoundRect(
                    color = if (rgb == 0) LedOff else Color(OPAQUE or rgb),
                    topLeft = Offset(originX + x * cell + gap / 2, originY + y * cell + gap / 2),
                    size = led,
                    cornerRadius = corner,
                )
            }
        }
        if (mirror) {
            val middle = originX + cell * width / 2
            drawLine(
                color = Color.White.copy(alpha = 0.55f),
                start = Offset(middle, originY),
                end = Offset(middle, originY + cell * height),
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
            )
        }
    }
}

@Composable
private fun ToolBar(tool: DrawTool, mirror: Boolean, onTool: (DrawTool) -> Unit, onToggleMirror: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.weight(4f).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DrawTool.entries.forEach { item ->
                ToolButton(
                    icon = item.icon(),
                    label = item.label(),
                    selected = tool == item,
                    modifier = Modifier
                        .weight(1f)
                        .selectable(selected = tool == item, role = Role.RadioButton, onClick = { onTool(item) }),
                )
            }
        }
        ToolButton(
            icon = PixIcons.Mirror,
            label = "Mirror",
            selected = mirror,
            modifier = Modifier
                .weight(1f)
                .toggleable(value = mirror, role = Role.Switch, onValueChange = { onToggleMirror() }),
        )
    }
}

/** Icon over a label; [modifier] carries the click/selection behaviour. */
@Composable
private fun ToolButton(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier = Modifier) {
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.medium)
            .then(modifier)
            .background(container)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = content, maxLines = 1)
    }
}

private fun DrawTool.label(): String = when (this) {
    DrawTool.Pen -> "Pen"
    DrawTool.Eraser -> "Eraser"
    DrawTool.Fill -> "Fill"
    DrawTool.Picker -> "Pick"
}

private fun DrawTool.icon(): ImageVector = when (this) {
    DrawTool.Pen -> PixIcons.Pencil
    DrawTool.Eraser -> PixIcons.Eraser
    DrawTool.Fill -> PixIcons.Bucket
    DrawTool.Picker -> PixIcons.Dropper
}

/** Preset colours, a custom-colour button, and a row of recent custom colours once there are some. */
@Composable
private fun Palette(selected: Int, recentColors: List<Int>, onSelect: (Int) -> Unit, onCustom: () -> Unit) {
    val presets = DrawPalette.presets
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            presets.take(SWATCHES_PER_ROW).forEach {
                Swatch(it.rgb, it.name, it.rgb == selected, onSelect, Modifier.weight(1f))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            presets.drop(SWATCHES_PER_ROW).take(SWATCHES_PER_ROW - 1).forEach {
                Swatch(it.rgb, it.name, it.rgb == selected, onSelect, Modifier.weight(1f))
            }
            CustomColorButton(onCustom, Modifier.weight(1f))
        }
        if (recentColors.isNotEmpty()) {
            val shown = recentColors.take(SWATCHES_PER_ROW)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                shown.forEach {
                    Swatch(it, "Colour #${"%06X".format(it)}", it == selected, onSelect, Modifier.weight(1f))
                }
                repeat(SWATCHES_PER_ROW - shown.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Swatch(rgb: Int, name: String, selected: Boolean, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val outer = RoundedCornerShape(10.dp)
    val inner = RoundedCornerShape(if (selected) 6.dp else 10.dp)
    Box(
        modifier
            .aspectRatio(1f)
            .clip(outer)
            .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(rgb) })
            .semantics { contentDescription = name }
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, outer).padding(4.dp)
                } else {
                    Modifier
                }
            )
            .clip(inner)
            .background(Color(OPAQUE or rgb))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), inner)
    )
}

@Composable
private fun CustomColorButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.sweepGradient(RAINBOW))
            .clickable(onClickLabel = "Mix a custom colour", role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Custom colour", tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

private const val OPAQUE = 0xFF000000.toInt()
private const val SWATCHES_PER_ROW = 8
private val RAINBOW = listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f).map { Color.hsv(it, 0.9f, 1f) }
