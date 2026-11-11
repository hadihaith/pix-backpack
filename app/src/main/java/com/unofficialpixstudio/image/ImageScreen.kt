package com.unofficialpixstudio.image

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.unofficialpixstudio.ble.BackpackViewModel
import com.unofficialpixstudio.ble.GattState
import com.unofficialpixstudio.ble.PixBrightness
import com.unofficialpixstudio.ui.components.LedDisplay
import com.unofficialpixstudio.ui.components.PixIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val CropStateSaver = listSaver<CropState?, Float>(
    save = { state -> state?.let { listOf(it.centerX, it.centerY, it.zoom) } ?: emptyList() },
    restore = { if (it.size == 3) CropState(it[0], it[1], it[2]) else null },
)

/**
 * Picture tab: pick a photo, pinch/drag to frame it at the display's aspect ratio, preview
 * exactly what the LEDs will show, and send it. Picked image and crop survive rotation.
 */
@Composable
fun PictureScreen(viewModel: BackpackViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val connection by viewModel.connectionState.collectAsState()
    val displaySize by viewModel.displaySize.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()
    val displayWidth = displaySize.width
    val displayHeight = displaySize.height

    var uri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var crop by rememberSaveable(stateSaver = CropStateSaver) { mutableStateOf<CropState?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked ->
        if (picked != null) {
            uri = picked
            crop = null
        }
    }
    fun pickPhoto() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    // Decoding: null while loading, failure if it couldn't be read.
    val source by produceState<Result<SourceImage>?>(null, uri) {
        value = null
        val picked = uri ?: return@produceState
        value = runCatching { loadSourceImage(context, picked) }
    }
    val image = source?.getOrNull()
    val geometry = remember(image, displayWidth, displayHeight) {
        image?.let { CropGeometry(it.width, it.height, displayWidth, displayHeight) }
    }
    val cropState = crop ?: geometry?.initial()

    // What the LEDs will show. Recomputed shortly after the crop stops changing.
    val pixels by produceState<IntArray?>(null, image, cropState, geometry) {
        if (image == null || geometry == null || cropState == null) {
            value = null
            return@produceState
        }
        delay(60)
        value = withContext(Dispatchers.Default) {
            val shrunk = PixelArt.downscale(
                image.pixels, image.width, image.height,
                geometry.rect(cropState), displayWidth, displayHeight,
            )
            PixelArt.quantize(shrunk)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Picture", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
            if (uri != null) TextButton(onClick = ::pickPhoto, enabled = !busy) { Text("Choose another") }
        }
        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            when {
                uri == null -> EmptyState(onPick = ::pickPhoto)
                source == null -> CircularProgressIndicator()
                image == null -> Text(
                    "Couldn't open that image.\n${source?.exceptionOrNull()?.message.orEmpty()}",
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                geometry != null && cropState != null -> CropBox(
                    image = image,
                    geometry = geometry,
                    crop = cropState,
                    gridWidth = displayWidth,
                    gridHeight = displayHeight,
                    onCropChange = { crop = it },
                    modifier = Modifier
                        .aspectRatio(displayWidth.toFloat() / displayHeight)
                        .clip(MaterialTheme.shapes.large)
                        .border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large),
                )
            }
        }

        if (image != null) {
            Text(
                "Pinch to zoom · drag to move",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                textAlign = TextAlign.Center,
            )
        }

        pixels?.let { preview ->
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LedDisplay(preview, displayWidth, displayHeight, Modifier.width(76.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Preview", style = MaterialTheme.typography.titleMedium)
                    Text(
                        colourSummary(preview),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val ready = connection is GattState.Ready
                    Button(
                        onClick = { viewModel.sendImage(preview, displayWidth, displayHeight) },
                        enabled = ready && !busy,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 12.dp),
                    ) { Text(if (busy) "Sending…" else "Send to backpack") }
                    val status = if (!ready) "Backpack not connected." else message
                    status?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (!ready) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onPick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                PixIcons.Heart,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(56.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Turn any photo into pixel art",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Pick a photo, frame it, and see exactly how it'll look on your backpack before sending.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onPick, contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp)) {
            Text("Choose a photo")
        }
    }
}

/** The photo under a fixed frame the shape of the display, with a faint LED grid on top. */
@Composable
private fun CropBox(
    image: SourceImage,
    geometry: CropGeometry,
    crop: CropState,
    gridWidth: Int,
    gridHeight: Int,
    onCropChange: (CropState) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Gesture callbacks outlive recompositions; read the latest crop through these.
    val currentCrop by rememberUpdatedState(crop)
    val currentGeometry by rememberUpdatedState(geometry)

    Canvas(
        modifier = modifier
            .clipToBounds()
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    onCropChange(
                        currentGeometry.transform(
                            state = currentCrop,
                            boxWidth = size.width.toFloat(),
                            boxHeight = size.height.toFloat(),
                            centroidX = centroid.x,
                            centroidY = centroid.y,
                            panX = pan.x,
                            panY = pan.y,
                            zoomChange = zoom,
                        )
                    )
                }
            }
    ) {
        val rect = geometry.rect(crop)
        val scale = size.width / rect.width
        drawImage(
            image = image.bitmap,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset((-rect.left * scale).roundToInt(), (-rect.top * scale).roundToInt()),
            dstSize = IntSize((image.width * scale).roundToInt(), (image.height * scale).roundToInt()),
            filterQuality = FilterQuality.Low,
        )
        val gridColor = Color.White.copy(alpha = 0.2f)
        for (x in 1 until gridWidth) {
            val px = size.width * x / gridWidth
            drawLine(gridColor, Offset(px, 0f), Offset(px, size.height))
        }
        for (y in 1 until gridHeight) {
            val py = size.height * y / gridHeight
            drawLine(gridColor, Offset(0f, py), Offset(size.width, py))
        }
    }
}

/**
 * "12 colours", plus a note when the picture is bright enough that brightness can't go all
 * the way up (e.g. "12 colours · brightness up to 71%").
 */
fun colourSummary(pixels: IntArray): String {
    val colours = PixelArt.distinctColors(pixels)
    val text = if (colours == 1) "1 colour" else "$colours colours"
    val limit = PixBrightness.maxUserPercentFor(pixels)
    return if (limit < 100) "$text · brightness up to $limit%" else text
}
