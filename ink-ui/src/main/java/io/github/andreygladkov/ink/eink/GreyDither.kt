package io.github.andreygladkov.ink.eink

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Brings a picture down to the 16 greys the A9 panel shows in its CLEAR mode, choosing between the two nearest
 * greys by a blue noise threshold, so smooth tones do not break into bands. In the other modes the panel's own
 * controller dithers every frame, and this only adds grain. The panel's greys are closer to perceptual than to
 * linear light, so values are quantized as they are, without linearizing (see a9/dithering.md).
 */
object GreyDither {
    const val LEVELS = 16
    const val STEP = 255 / (LEVELS - 1)

    // The integer weights the A9 vendor composer itself uses to turn a frame into grey.
    private const val RED = 76
    private const val GREEN = 150
    private const val BLUE = 30

    fun quantize(bitmap: Bitmap) {
        require(bitmap.isMutable)
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val thresholds = BlueNoise.thresholds
        for (y in 0 until height) {
            val noiseRow = (y and NOISE_MASK) * BlueNoise.SIZE
            val row = y * width
            for (x in 0 until width) {
                val threshold = thresholds[noiseRow + (x and NOISE_MASK)].toInt() and 0xFF
                val grey = level(luminance(pixels[row + x]), threshold) * STEP
                pixels[row + x] = Color.rgb(grey, grey, grey)
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    internal fun level(luma: Int, threshold: Int): Int =
        ((luma * (LEVELS - 1) * 512 + (2 * threshold + 1) * 255) / (255 * 512)).coerceIn(0, LEVELS - 1)

    fun luminance(argb: Int): Int =
        Dither.overPage((RED * Color.red(argb) + GREEN * Color.green(argb) + BLUE * Color.blue(argb)) shr 8, Color.alpha(argb))

    private const val NOISE_MASK = BlueNoise.SIZE - 1
}
