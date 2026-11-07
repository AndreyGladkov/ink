package io.github.andreygladkov.inklauncher.icons

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import io.github.andreygladkov.ink.eink.Dither
import io.github.andreygladkov.ink.eink.MonochromeIcons
import kotlin.math.roundToInt

/** Every icon is a mask: black where ink goes, transparent elsewhere, tinted with the style's color when drawn. */
object InkIcons {
    private const val TEXT_START_TO_SIZE = 0.55f
    private const val TEXT_MAX_WIDTH_TO_SIZE = 0.72f
    private const val TEXT_STEP = 0.95f

    fun dithered(drawable: Drawable, sizePx: Int, invert: Boolean = false): Bitmap = dithered(draw(drawable, sizePx), invert)

    fun dithered(source: Bitmap, invert: Boolean = false): Bitmap {
        val pixels = source.pixels()
        val black = Dither.atkinson(IntArray(pixels.size) { luminance(pixels[it], invert) }, source.width, source.height)
        return mask(source.width, source.height) { black[it] }
    }

    /**
     * The themed-icon layer apps ship since Android 13. Like the other adaptive layers it is drawn on a 108-unit
     * canvas of which only the middle 72 units are meant to be seen.
     */
    fun monochromeLayer(drawable: Drawable?, sizePx: Int): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val layer = (drawable as? AdaptiveIconDrawable)?.monochrome ?: return null
        val bleed = sizePx / 4
        val bitmap = createBitmap(sizePx, sizePx)
        layer.setBounds(-bleed, -bleed, sizePx + bleed, sizePx + bleed)
        layer.setTint(Color.BLACK)
        layer.draw(Canvas(bitmap))
        return MonochromeIcons.silhouette(bitmap)
    }

    /** A picture keeps its proportions and is centred in the square. */
    fun picture(source: Bitmap, sizePx: Int): Bitmap {
        val ratio = minOf(sizePx.toFloat() / source.width, sizePx.toFloat() / source.height)
        val width = (source.width * ratio).roundToInt().coerceIn(1, sizePx)
        val height = (source.height * ratio).roundToInt().coerceIn(1, sizePx)
        val scaled = source.scale(width, height)
        val square = createBitmap(sizePx, sizePx)
        Canvas(square).drawBitmap(scaled, ((sizePx - width) / 2).toFloat(), ((sizePx - height) / 2).toFloat(), null)
        return dithered(square)
    }

    fun text(text: String, sizePx: Int, typeface: (Float) -> Typeface, linePx: Int, radiusPx: Float): Bitmap {
        val bitmap = frame(sizePx, linePx, radiusPx)
        val paint = Paint().apply {
            isAntiAlias = false
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
            textSize = sizePx * TEXT_START_TO_SIZE
            this.typeface = typeface(textSize)
        }
        while (paint.measureText(text) > sizePx * TEXT_MAX_WIDTH_TO_SIZE && paint.textSize > 1f) {
            paint.textSize *= TEXT_STEP
            paint.typeface = typeface(paint.textSize)
        }
        val baseline = sizePx / 2f - (paint.ascent() + paint.descent()) / 2f
        Canvas(bitmap).drawText(text, sizePx / 2f, baseline.roundToInt().toFloat(), paint)
        return bitmap
    }

    fun framedSize(sizePx: Int, linePx: Int): Int = sizePx - 2 * frameInset(sizePx, linePx)

    fun framed(sizePx: Int, linePx: Int, radiusPx: Float, inner: Bitmap): Bitmap {
        val bitmap = frame(sizePx, linePx, radiusPx)
        Canvas(bitmap).drawBitmap(inner, ((sizePx - inner.width) / 2).toFloat(), ((sizePx - inner.height) / 2).toFloat(), null)
        return bitmap
    }

    fun folderCellSize(sizePx: Int, linePx: Int): Int = (sizePx - 2 * frameInset(sizePx, linePx) - folderGap(sizePx)) / 2

    fun folder(sizePx: Int, linePx: Int, radiusPx: Float, minis: List<Bitmap>): Bitmap {
        val bitmap = frame(sizePx, linePx, radiusPx)
        val inset = frameInset(sizePx, linePx)
        val cell = folderCellSize(sizePx, linePx)
        val step = cell + folderGap(sizePx)
        val canvas = Canvas(bitmap)
        minis.take(4).forEachIndexed { index, mini ->
            canvas.drawBitmap(mini, (inset + index % 2 * step).toFloat(), (inset + index / 2 * step).toFloat(), null)
        }
        return bitmap
    }

    fun luminance(argb: Int, invert: Boolean): Int {
        val alpha = Color.alpha(argb)
        val luma = (299 * Color.red(argb) + 587 * Color.green(argb) + 114 * Color.blue(argb)) / 1000
        val ink = if (invert) 255 - luma else luma
        return (ink * alpha + 255 * (255 - alpha)) / 255
    }

    private fun frameInset(sizePx: Int, linePx: Int) = linePx + sizePx / 10

    private fun folderGap(sizePx: Int) = sizePx / 16

    private fun frame(sizePx: Int, linePx: Int, radiusPx: Float): Bitmap {
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            isAntiAlias = false
            color = Color.BLACK
        }
        val size = sizePx.toFloat()
        canvas.drawRoundRect(0f, 0f, size, size, radiusPx, radiusPx, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        val inner = (radiusPx - linePx).coerceAtLeast(0f)
        canvas.drawRoundRect(linePx.toFloat(), linePx.toFloat(), size - linePx, size - linePx, inner, inner, paint)
        bitmap.density = Bitmap.DENSITY_NONE
        return bitmap
    }

    private fun draw(drawable: Drawable, sizePx: Int): Bitmap {
        val bitmap = createBitmap(sizePx, sizePx)
        val bounds = drawable.copyBounds()
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(Canvas(bitmap))
        drawable.bounds = bounds
        return bitmap
    }

    private fun mask(width: Int, height: Int, isInk: (Int) -> Boolean): Bitmap = createBitmap(width, height).apply {
        setPixels(IntArray(width * height) { if (isInk(it)) Color.BLACK else Color.TRANSPARENT }, 0, width, 0, 0, width, height)
        // Already sized in pixels: a canvas of another density would rescale it with filtering and add grey edges.
        density = Bitmap.DENSITY_NONE
    }

    private fun Bitmap.pixels() = IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }
}
