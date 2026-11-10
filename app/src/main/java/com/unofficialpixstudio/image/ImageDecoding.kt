package com.unofficialpixstudio.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.max

/** A picked image, decoded small enough to crop interactively. */
class SourceImage(
    val bitmap: ImageBitmap,
    /** ARGB pixels, row-major. */
    val pixels: IntArray,
    val width: Int,
    val height: Int,
)

/**
 * Decodes [uri] with its longest side at most [maxDimension] px. The display is only 16×20,
 * so this keeps memory low without visibly affecting the result.
 */
suspend fun loadSourceImage(context: Context, uri: Uri, maxDimension: Int = 1024): SourceImage =
    withContext(Dispatchers.IO) {
        val decoded = decodeBitmap(context, uri, maxDimension)
        val bitmap = if (decoded.config == Bitmap.Config.ARGB_8888) {
            decoded
        } else {
            decoded.copy(Bitmap.Config.ARGB_8888, false) ?: throw IOException("Unsupported image format")
        }
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        SourceImage(bitmap.asImageBitmap(), pixels, bitmap.width, bitmap.height)
    }

private fun decodeBitmap(context: Context, uri: Uri, maxDimension: Int): Bitmap {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        // ImageDecoder also applies EXIF rotation, so phone photos come out the right way up.
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE // getPixels needs a CPU bitmap.
            val scale = max(info.size.width, info.size.height) / maxDimension.toFloat()
            if (scale > 1f) {
                decoder.setTargetSize(
                    (info.size.width / scale).toInt().coerceAtLeast(1),
                    (info.size.height / scale).toInt().coerceAtLeast(1),
                )
            }
        }
    }

    // Android 8.x: BitmapFactory with power-of-two downsampling. (Ignores EXIF rotation.)
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sampleSize = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= maxDimension) sampleSize *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: throw IOException("Couldn't read the image")
}
