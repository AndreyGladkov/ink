package io.github.andreygladkov.inkgallery.media

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.util.Size
import androidx.core.graphics.createBitmap
import io.github.andreygladkov.inkgallery.Background
import kotlin.math.min

/** Square thumbnails cut from the middle of each picture, from the system's own thumbnail store. */
object Thumbnails {
    private const val CACHE_BYTES = 32 * 1024 * 1024
    private val main = Handler(Looper.getMainLooper())
    private val waiting = HashMap<String, MutableList<(Bitmap) -> Unit>>()
    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun cached(photo: Item, sizePx: Int): Bitmap? = cache.get(key(photo, sizePx))

    fun clear() = cache.evictAll()

    /** Calls [onLoaded] on the main thread, unless the picture cannot be read. */
    fun load(resolver: ContentResolver, photo: Item, sizePx: Int, onLoaded: (Bitmap) -> Unit = {}) {
        val key = key(photo, sizePx)
        cache.get(key)?.let { return onLoaded(it) }
        val pending = waiting[key]
        if (pending != null) {
            pending += onLoaded
            return
        }
        waiting[key] = mutableListOf(onLoaded)
        Background.thumbnails.execute {
            val square = runCatching { square(resolver, photo, sizePx) }.getOrNull()
            main.post {
                val callbacks = waiting.remove(key).orEmpty()
                if (square == null) return@post
                cache.put(key, square)
                callbacks.forEach { it(square) }
            }
        }
    }

    private fun square(resolver: ContentResolver, photo: Item, sizePx: Int): Bitmap {
        // The system fits its thumbnail inside the requested size, so asking for twice the cell keeps the short
        // side of a wide picture from being enlarged.
        val thumbnail = resolver.loadThumbnail(photo.uri, Size(sizePx * 2, sizePx * 2), null)
        val side = min(thumbnail.width, thumbnail.height)
        val crop = Rect((thumbnail.width - side) / 2, (thumbnail.height - side) / 2, 0, 0).apply {
            right = left + side
            bottom = top + side
        }
        return createBitmap(sizePx, sizePx).also {
            Canvas(it).drawBitmap(thumbnail, crop, Rect(0, 0, sizePx, sizePx), Paint(Paint.FILTER_BITMAP_FLAG))
        }
    }

    private fun key(photo: Item, sizePx: Int) = "${photo.uri}@$sizePx"
}
