package io.github.andreygladkov.inkphone.eink

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap

object MonochromeIcons {
    private const val ALPHA_THRESHOLD = 128

    private data class Key(@param:DrawableRes val res: Int, val sizePx: Int, @param:ColorInt val color: Int)
    private val cache = HashMap<Key, Bitmap>()

    fun bitmap(context: Context, @DrawableRes res: Int, sizePx: Int, @ColorInt color: Int): Bitmap =
        synchronized(cache) { cache.getOrPut(Key(res, sizePx, color)) { render(context, res, sizePx, color) } }

    // Vector drawables are always rasterized with antialiasing, so the only way to get a
    // pure two-tone icon is to rasterize it and threshold the coverage afterwards.
    private fun render(context: Context, @DrawableRes res: Int, sizePx: Int, @ColorInt color: Int): Bitmap {
        val icon = requireNotNull(ContextCompat.getDrawable(context, res)).mutate()
        val bitmap = createBitmap(sizePx, sizePx)
        icon.setBounds(0, 0, sizePx, sizePx)
        icon.draw(Canvas(bitmap))
        val pixels = IntArray(sizePx * sizePx)
        bitmap.getPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
        val opaque = color or 0xFF000000.toInt()
        for (i in pixels.indices) pixels[i] = if (Color.alpha(pixels[i]) >= ALPHA_THRESHOLD) opaque else Color.TRANSPARENT
        bitmap.setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
        // Already sized in pixels: a canvas of another density would rescale it with filtering and add grey edges.
        bitmap.density = Bitmap.DENSITY_NONE
        return bitmap
    }
}
