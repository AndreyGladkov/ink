package io.github.andreygladkov.ink.eink

import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale

object Dither {
    private const val THRESHOLD = 128

    /**
     * Atkinson error diffusion: spreads only 6/8 of the error, which keeps highlights and shadows clean on a
     * 1-bit panel instead of speckling them the way Floyd–Steinberg does. Returns true for black pixels.
     */
    fun atkinson(luminance: IntArray, width: Int, height: Int): BooleanArray {
        require(luminance.size == width * height)
        val values = luminance.copyOf()
        val black = BooleanArray(values.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val old = values[index]
                val isBlack = old < THRESHOLD
                black[index] = isBlack
                val share = (old - if (isBlack) 0 else 255) / 8
                if (share == 0) continue
                spread(values, width, height, x + 1, y, share)
                spread(values, width, height, x + 2, y, share)
                spread(values, width, height, x - 1, y + 1, share)
                spread(values, width, height, x, y + 1, share)
                spread(values, width, height, x + 1, y + 1, share)
                spread(values, width, height, x, y + 2, share)
            }
        }
        return black
    }

    /** Scales without filtering, so no grey edges appear, then dithers to pure black and white. */
    fun bitmap(source: Bitmap, width: Int, height: Int): Bitmap {
        val scaled = if (source.width == width && source.height == height) source else source.scale(width, height, filter = false)
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)
        val black = atkinson(IntArray(pixels.size) { luminance(pixels[it]) }, width, height)
        return createBitmap(width, height).apply {
            setPixels(IntArray(pixels.size) { if (black[it]) Color.BLACK else Color.WHITE }, 0, width, 0, 0, width, height)
        }
    }

    /** Rec. 601 luma over a white background, since transparent areas of a picture show the page behind. */
    fun luminance(argb: Int): Int {
        val alpha = Color.alpha(argb)
        val luma = (299 * Color.red(argb) + 587 * Color.green(argb) + 114 * Color.blue(argb)) / 1000
        return (luma * alpha + 255 * (255 - alpha)) / 255
    }

    private fun spread(values: IntArray, width: Int, height: Int, x: Int, y: Int, share: Int) {
        if (x < 0 || x >= width || y >= height) return
        values[y * width + x] += share
    }
}
