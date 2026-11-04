package io.github.andreygladkov.inkmessages.thread

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.util.Size
import android.view.View
import io.github.andreygladkov.ink.eink.Dither
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.inkmessages.data.Attachment
import io.github.andreygladkov.inkmessages.data.Loader
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A 1-bit thumbnail of an MMS picture. Its size follows from the picture's dimensions alone, so a page
 * can be laid out before the picture is decoded.
 */
class MmsImageView(context: Context, private val attachment: Attachment, private val source: Size, borderDp: Float) : View(context) {
    private val style = InkStyle.of(context)
    private val density = resources.displayMetrics.density
    private val borderPx = (borderDp * density).roundToInt().coerceAtLeast(1)
    private val paint = Paint().apply {
        isAntiAlias = false
        isFilterBitmap = false
    }
    private var thumbnail: Bitmap? = null
    var maxWidthPx = Int.MAX_VALUE

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val offered = MeasureSpec.getSize(widthMeasureSpec).takeIf { MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED } ?: Int.MAX_VALUE
        val available = min(offered, maxWidthPx)
        val inner = fit(source, available - 2 * borderPx, (MAX_HEIGHT_DP * density).roundToInt())
        setMeasuredDimension(inner.width + 2 * borderPx, inner.height + 2 * borderPx)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val inner = Size(w - 2 * borderPx, h - 2 * borderPx)
        if (inner.width <= 0 || inner.height <= 0) return
        val key = "${attachment.partId}:${inner.width}x${inner.height}"
        thumbnail = cache.get(key)
        if (thumbnail != null) return
        val resolver = context.contentResolver
        Loader.executor.execute {
            val bitmap = runCatching { decode(resolver, attachment, inner) }.getOrNull() ?: return@execute
            cache.put(key, bitmap)
            main.post {
                if (width - 2 * borderPx == inner.width && height - 2 * borderPx == inner.height) {
                    thumbnail = bitmap
                    invalidate()
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        paint.color = style.foreground
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        paint.color = style.background
        canvas.drawRect(borderPx.toFloat(), borderPx.toFloat(), (width - borderPx).toFloat(), (height - borderPx).toFloat(), paint)
        thumbnail?.let { canvas.drawBitmap(it, borderPx.toFloat(), borderPx.toFloat(), paint) }
    }

    companion object {
        private const val MAX_HEIGHT_DP = 240f
        private const val CACHE_BYTES = 8 * 1024 * 1024
        private val main = Handler(Looper.getMainLooper())
        private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
            override fun sizeOf(key: String, value: Bitmap) = value.byteCount
        }

        /** Largest size within the bounds that keeps the picture's proportions, never enlarging it. */
        fun fit(source: Size, maxWidth: Int, maxHeight: Int): Size {
            if (source.width <= 0 || source.height <= 0) return Size(0, 0)
            val scale = min(1f, min(maxWidth.toFloat() / source.width, maxHeight.toFloat() / source.height))
            return Size(max(1, (source.width * scale).roundToInt()), max(1, (source.height * scale).roundToInt()))
        }

        fun bounds(resolver: ContentResolver, attachment: Attachment): Size? {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(attachment.uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            return Size(options.outWidth, options.outHeight).takeIf { it.width > 0 && it.height > 0 }
        }

        private fun decode(resolver: ContentResolver, attachment: Attachment, target: Size): Bitmap? {
            val bounds = bounds(resolver, attachment) ?: return null
            var sample = 1
            while (bounds.width / (sample * 2) >= target.width && bounds.height / (sample * 2) >= target.height) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = resolver.openInputStream(attachment.uri)?.use { BitmapFactory.decodeStream(it, null, options) } ?: return null
            return Dither.bitmap(decoded, target.width, target.height)
        }
    }
}
