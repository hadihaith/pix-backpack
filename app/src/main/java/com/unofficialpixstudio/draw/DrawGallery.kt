package com.unofficialpixstudio.draw

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.unofficialpixstudio.ui.components.LedDisplay

/**
 * My drawings, the Draw tab's first screen: a tile to start a new drawing, the drawing in
 * progress if it hasn't been saved yet ([unsavedNewDrawing]), then every saved drawing as a
 * little LED preview. Tap one to open it; its ⋮ menu renames, duplicates or deletes it.
 * [drawings] is null while still loading.
 */
@Composable
fun DrawGallery(
    drawings: List<SavedDrawing>?,
    openId: String?,
    openHasUnsavedChanges: Boolean,
    unsavedNewDrawing: DrawCanvas?,
    onContinue: () -> Unit,
    onNew: () -> Unit,
    onOpen: (SavedDrawing) -> Unit,
    onRename: (SavedDrawing) -> Unit,
    onDuplicate: (SavedDrawing) -> Unit,
    onDelete: (SavedDrawing) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text("My drawings", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(16.dp))

        if (drawings == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "new") {
                Tile(
                    title = "New drawing",
                    subtitle = "Start blank",
                    onClick = onNew,
                    preview = {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(40.dp),
                            )
                        }
                    },
                )
            }
            if (unsavedNewDrawing != null) {
                item(key = "unsaved") {
                    Tile(
                        title = "Unsaved drawing",
                        subtitle = "Tap to keep drawing",
                        subtitleColor = MaterialTheme.colorScheme.tertiary,
                        highlighted = true,
                        onClick = onContinue,
                        preview = {
                            LedDisplay(unsavedNewDrawing.pixels, unsavedNewDrawing.width, unsavedNewDrawing.height)
                        },
                    )
                }
            }
            items(drawings, key = { it.id }) { saved ->
                val isOpen = saved.id == openId
                Tile(
                    title = saved.name,
                    subtitle = when {
                        isOpen && openHasUnsavedChanges -> "Open · unsaved changes"
                        isOpen -> "Open now"
                        else -> whenSaved(saved.updatedAtMillis)
                    },
                    subtitleColor = when {
                        isOpen && openHasUnsavedChanges -> MaterialTheme.colorScheme.tertiary
                        isOpen -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    highlighted = isOpen,
                    onClick = { onOpen(saved) },
                    preview = { LedDisplay(saved.drawing.pixels, saved.drawing.width, saved.drawing.height) },
                    menu = {
                        TileMenu(
                            name = saved.name,
                            onRename = { onRename(saved) },
                            onDuplicate = { onDuplicate(saved) },
                            onDelete = { onDelete(saved) },
                        )
                    },
                )
            }
            if (drawings.isEmpty()) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Tap New drawing to start. Drawings you save will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}

/** A card with a preview on top and a title/subtitle (and optional ⋮ [menu]) below. */
@Composable
private fun Tile(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    preview: @Composable () -> Unit,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    highlighted: Boolean = false,
    menu: (@Composable () -> Unit)? = null,
) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = if (highlighted) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(Modifier.padding(12.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(TILE_ASPECT), contentAlignment = Alignment.Center) {
                preview()
            }
            // At least as tall as the ⋮ button, so tiles with and without a menu line up.
            Row(Modifier.padding(top = 8.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = subtitleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                menu?.invoke()
            }
        }
    }
}

@Composable
private fun TileMenu(name: String, onRename: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Options for $name")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Rename") }, onClick = { open = false; onRename() })
            DropdownMenuItem(text = { Text("Duplicate") }, onClick = { open = false; onDuplicate() })
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                onClick = { open = false; onDelete() },
            )
        }
    }
}

/** Asks for a drawing's name, starting from [initial] (selected, so typing replaces it). */
@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var field by remember { mutableStateOf(TextFieldValue(initial, selection = TextRange(0, initial.length))) }
    val name = field.text.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocus() }
            OutlinedTextField(
                value = field,
                onValueChange = { if (it.text.length <= MAX_NAME_LENGTH) field = it },
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotEmpty()) onConfirm(name) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotEmpty()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Yes/no question for something that loses work; the confirm button is shown in red. */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    dismissLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } },
    )
}

/** "Just now" for the last minute (instead of "0 minutes ago"), then "5 minutes ago", "Yesterday", … */
private fun whenSaved(millis: Long): String {
    val now = System.currentTimeMillis()
    return if (now - millis < DateUtils.MINUTE_IN_MILLIS) {
        "Just now"
    } else {
        DateUtils.getRelativeTimeSpanString(millis, now, DateUtils.MINUTE_IN_MILLIS).toString()
    }
}

private const val TILE_ASPECT = 0.8f
private const val MAX_NAME_LENGTH = 40
