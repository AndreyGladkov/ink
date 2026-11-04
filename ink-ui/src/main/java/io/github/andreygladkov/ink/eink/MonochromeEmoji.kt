package io.github.andreygladkov.ink.eink

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.fonts.Font
import android.graphics.fonts.FontFamily
import android.icu.lang.UCharacter
import android.icu.lang.UProperty
import android.icu.text.BreakIterator
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ReplacementSpan
import android.text.style.TypefaceSpan
import android.util.LruCache
import androidx.core.graphics.createBitmap

/**
 * Color emoji become grey dithering on the panel. Emoji present in the outline Noto Emoji font are drawn with it,
 * so they follow the text's color and antialiasing; the rest are system emoji thresholded to two tones.
 */
object MonochromeEmoji {
    private const val FONT_ASSET = "fonts/NotoEmoji.ttf"
    private const val PRESENTATION_SELECTOR = "️"
    private const val KEYCAP = 0x20E3
    private const val ALPHA_THRESHOLD = 128
    private const val LUMA_THRESHOLD = 128

    private var typeface: Typeface? = null
    private var strictTypeface: Typeface? = null
    private val glyphPresence = HashMap<String, Boolean>()
    private val thresholded = LruCache<String, Bitmap>(64)

    fun apply(context: Context, text: CharSequence?): CharSequence? {
        if (text.isNullOrEmpty() || text.none { Character.isSurrogate(it) || it.code in 0x2000..0x33FF }) return text
        val clusters = emojiClusters(text.toString())
        if (clusters.isEmpty()) return text
        val result = SpannableStringBuilder(text)
        for ((start, end) in clusters.asReversed()) {
            val cluster = result.substring(start, end)
            val outline = cluster.replace(PRESENTATION_SELECTOR, "")
            if (outline.isNotEmpty() && hasOutlineGlyph(context, outline)) {
                result.replace(start, end, outline)
                result.setSpan(TypefaceSpan(typeface(context)), start, start + outline.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else {
                result.setSpan(ThresholdedSpan(cluster), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return result
    }

    internal fun emojiClusters(text: String): List<Pair<Int, Int>> {
        val clusters = ArrayList<Pair<Int, Int>>()
        val iterator = BreakIterator.getCharacterInstance().apply { setText(text) }
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            if (isEmoji(text, start, end)) clusters += start to end
            start = end
            end = iterator.next()
        }
        return clusters
    }

    private fun isEmoji(text: String, start: Int, end: Int): Boolean {
        val first = text.codePointAt(start)
        if (UCharacter.hasBinaryProperty(first, UProperty.EMOJI_PRESENTATION)) return true
        if (UCharacter.hasBinaryProperty(first, UProperty.REGIONAL_INDICATOR)) return true
        val cluster = text.substring(start, end)
        return UCharacter.hasBinaryProperty(first, UProperty.EMOJI) && (PRESENTATION_SELECTOR in cluster || cluster.codePoints().anyMatch { it == KEYCAP })
    }

    private fun typeface(context: Context): Typeface =
        typeface ?: Typeface.createFromAsset(context.assets, FONT_ASSET).also { typeface = it }

    // A typeface from an asset silently falls back to the system color emoji font,
    // so glyph presence can only be checked on a typeface built without system fallback.
    private fun hasOutlineGlyph(context: Context, text: String): Boolean = synchronized(glyphPresence) {
        glyphPresence.getOrPut(text) {
            val strict = strictTypeface ?: Typeface.CustomFallbackBuilder(
                FontFamily.Builder(Font.Builder(context.assets, FONT_ASSET).build()).build(),
            ).build().also { strictTypeface = it }
            Paint().apply { typeface = strict }.hasGlyph(text)
        }
    }

    private class ThresholdedSpan(private val emoji: String) : ReplacementSpan() {
        override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int =
            colorPaint(paint).measureText(emoji).toInt()

        override fun draw(canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
            val color = colorPaint(paint)
            val bounds = Rect().also { color.getTextBounds(emoji, 0, emoji.length, it) }
            if (bounds.isEmpty) return
            val key = "$emoji|${color.textSize}|${paint.color}"
            val bitmap = thresholded.get(key) ?: render(color, bounds, paint.color).also { thresholded.put(key, it) }
            canvas.drawBitmap(bitmap, x + bounds.left, (y + bounds.top).toFloat(), null)
        }

        private fun colorPaint(paint: Paint) = Paint(paint).apply {
            isAntiAlias = true
            typeface = Typeface.DEFAULT
        }

        private fun render(paint: Paint, bounds: Rect, color: Int): Bitmap {
            val bitmap = createBitmap(bounds.width(), bounds.height())
            Canvas(bitmap).drawText(emoji, -bounds.left.toFloat(), -bounds.top.toFloat(), paint)
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val opaque = color or 0xFF000000.toInt()
            for (i in pixels.indices) {
                val p = pixels[i]
                val luma = (76 * Color.red(p) + 150 * Color.green(p) + 30 * Color.blue(p)) shr 8
                pixels[i] = if (Color.alpha(p) >= ALPHA_THRESHOLD && luma < LUMA_THRESHOLD) opaque else Color.TRANSPARENT
            }
            bitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            bitmap.density = Bitmap.DENSITY_NONE
            return bitmap
        }
    }
}
