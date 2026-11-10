package io.github.andreygladkov.inkgallery.viewer

import android.graphics.Matrix
import android.graphics.RectF
import android.util.Size
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Where a photo lies on screen, as a matrix from the photo's pixels to the view's. The photo is shown whole-pixel
 * sized and placed, so a picture decoded at [decodedSize] lands on the screen one to one, without resampling.
 */
object Viewport {
    const val ZOOM = 2f

    /**
     * The whole photo, enlarged only when [enlarges]; with [rotateLandscape] a wide photo is turned to lie along the
     * tall screen.
     */
    fun fit(photo: Size, view: Size, rotateLandscape: Boolean, enlarges: Boolean = false): Matrix {
        val turned = rotateLandscape && photo.width > photo.height
        val across = if (turned) photo.height else photo.width
        val along = if (turned) photo.width else photo.height
        val fill = min(view.width.toFloat() / across, view.height.toFloat() / along)
        val scale = if (enlarges) fill else min(1f, fill)
        val width = max(1, (across * scale).roundToInt())
        val height = max(1, (along * scale).roundToInt())
        return Matrix().apply {
            if (turned) {
                postRotate(90f)
                postTranslate(photo.height.toFloat(), 0f)
            }
            postScale(width.toFloat() / across, height.toFloat() / along)
            postTranslate(((view.width - width) / 2).toFloat(), ((view.height - height) / 2).toFloat())
        }
    }

    fun zoomed(fitted: Matrix, x: Float, y: Float, photo: Size, view: Size): Matrix =
        Matrix(fitted).apply { postScale(ZOOM, ZOOM, x, y) }.also { clamp(it, photo, view) }

    fun panned(current: Matrix, dx: Float, dy: Float, photo: Size, view: Size): Matrix =
        Matrix(current).apply { postTranslate(dx, dy) }.also { clamp(it, photo, view) }

    /** The size to decode the photo at for [matrix], never above the photo's own. */
    fun decodedSize(matrix: Matrix, photo: Size): Size {
        val edges = floatArrayOf(photo.width.toFloat(), 0f, 0f, photo.height.toFloat())
        matrix.mapVectors(edges)
        val width = hypot(edges[0], edges[1]).roundToInt().coerceIn(1, photo.width)
        val height = hypot(edges[2], edges[3]).roundToInt().coerceIn(1, photo.height)
        return Size(width, height)
    }

    fun bounds(matrix: Matrix, photo: Size) = RectF(0f, 0f, photo.width.toFloat(), photo.height.toFloat()).also(matrix::mapRect)

    /** A photo larger than the view on an axis covers it without a gap; a smaller one stays centred. */
    private fun clamp(matrix: Matrix, photo: Size, view: Size) {
        val shown = bounds(matrix, photo)
        matrix.postTranslate(shift(shown.left, shown.right, view.width), shift(shown.top, shown.bottom, view.height))
    }

    private fun shift(start: Float, end: Float, viewLength: Int): Float {
        val length = end - start
        val newStart = when {
            length <= viewLength -> (viewLength - length) / 2
            start > 0 -> 0f
            end < viewLength -> viewLength - length
            else -> start
        }
        return newStart.roundToInt() - start
    }
}
