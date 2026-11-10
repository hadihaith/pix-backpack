package com.unofficialpixstudio.image

import kotlin.math.max
import kotlin.math.min

/** A rectangle in source-image pixel coordinates. */
data class CropRect(val left: Float, val top: Float, val width: Float, val height: Float)

/**
 * Where the crop window sits: its centre in source pixels and a zoom factor, where 1 is the
 * largest window of the display's aspect ratio that fits in the image. Independent of how big
 * the crop box is on screen, so it survives rotation.
 */
data class CropState(val centerX: Float, val centerY: Float, val zoom: Float)

/** Crop maths for a [srcWidth]×[srcHeight] image shown on an [outWidth]×[outHeight] display. */
class CropGeometry(
    private val srcWidth: Int,
    private val srcHeight: Int,
    private val outWidth: Int,
    outHeight: Int,
) {
    private val aspect = outWidth.toFloat() / outHeight

    /** Width of the crop window at zoom 1. */
    private val fullWidth = min(srcWidth.toFloat(), srcHeight * aspect)

    /** Zooming further would leave less than one source pixel per display pixel. */
    val maxZoom: Float = max(1f, fullWidth / outWidth)

    fun initial(): CropState = CropState(srcWidth / 2f, srcHeight / 2f, 1f)

    fun rect(state: CropState): CropRect {
        val width = fullWidth / state.zoom
        val height = width / aspect
        return CropRect(state.centerX - width / 2, state.centerY - height / 2, width, height)
    }

    /** Keeps zoom in range and the window inside the image. */
    fun clamp(state: CropState): CropState {
        val zoom = state.zoom.coerceIn(1f, maxZoom)
        val width = fullWidth / zoom
        val height = width / aspect
        return CropState(
            centerX = state.centerX.clampOrMiddle(width / 2, srcWidth - width / 2),
            centerY = state.centerY.clampOrMiddle(height / 2, srcHeight - height / 2),
            zoom = zoom,
        )
    }

    /**
     * Applies a pinch/drag made over a crop box [boxWidth] screen px wide: the image point under
     * the gesture centroid stays under it while zooming, and panning drags the image along.
     */
    fun transform(
        state: CropState,
        boxWidth: Float,
        boxHeight: Float,
        centroidX: Float,
        centroidY: Float,
        panX: Float,
        panY: Float,
        zoomChange: Float,
    ): CropState {
        val scale = boxWidth / rect(state).width // screen px per source px
        val newZoom = (state.zoom * zoomChange).coerceIn(1f, maxZoom)
        val newScale = scale * newZoom / state.zoom
        val dx = centroidX - boxWidth / 2
        val dy = centroidY - boxHeight / 2
        val anchorX = state.centerX + dx / scale
        val anchorY = state.centerY + dy / scale
        return clamp(
            CropState(
                centerX = anchorX - (dx + panX) / newScale,
                centerY = anchorY - (dy + panY) / newScale,
                zoom = newZoom,
            )
        )
    }

    // Float rounding can make lo slightly exceed hi when the window spans the whole image.
    private fun Float.clampOrMiddle(lo: Float, hi: Float): Float =
        if (lo <= hi) coerceIn(lo, hi) else (lo + hi) / 2
}
