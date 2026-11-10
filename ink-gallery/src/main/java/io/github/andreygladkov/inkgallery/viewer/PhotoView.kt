package io.github.andreygladkov.inkgallery.viewer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.net.Uri
import android.util.Size
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.inkgallery.Background
import io.github.andreygladkov.inkgallery.media.Item
import java.util.Collections
import kotlin.math.abs
import kotlin.math.hypot

/**
 * One photo on the whole view. A frame is prepared off the main thread and replaces the shown one only when
 * complete, so the panel redraws once per photo. Taps on the outer thirds and horizontal swipes turn photos;
 * a tap in the middle shows the controls. A double tap in the middle zooms in around it; while zoomed a drag
 * moves the photo once the finger lifts, and a double tap anywhere zooms back out.
 */
class PhotoView(context: Context) : View(context) {
    var onFlip: ((step: Int) -> Unit)? = null
    var onToggleControls: (() -> Unit)? = null
    var onShown: ((readable: Boolean) -> Unit)? = null
    var rotatesLandscape = false
    var dithers = false

    private val background = InkStyle.of(context).background
    private val swipeSlop = ViewConfiguration.get(context).scaledTouchSlop * 2
    private var photo: Item? = null
    private var upcoming: Item? = null
    private var shown: Prepared? = null
    private var source: Decoded? = null
    private var frame: Bitmap? = null
    private var current: Matrix? = null
    private var generation = 0
    private var downX = 0f
    private var downY = 0f
    private val ready: MutableMap<Uri, Prepared> = Collections.synchronizedMap(LinkedHashMap())

    var isZoomed = false
        private set

    private val taps = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (!isZoomed && side(e.x) != 0) onFlip?.invoke(side(e.x))
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (isZoomed || side(e.x) == 0) onToggleControls?.invoke()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            when {
                isZoomed -> zoomOut()
                side(e.x) == 0 -> zoomIn(e.x, e.y)
                else -> onFlip?.invoke(side(e.x))
            }
            return true
        }
    })

    fun show(photo: Item, upcoming: Item?) {
        this.photo = photo
        this.upcoming = upcoming
        isZoomed = false
        current = null
        val view = viewSize() ?: return
        val request = ++generation
        val key = key(photo, view)
        Background.frames.execute {
            val prepared = runCatching { ready.remove(photo.uri)?.takeIf { it.key == key } ?: prepare(photo, key) }.getOrNull()
            post {
                if (request != generation) return@post
                if (prepared != null) display(prepared) else clear()
                onShown?.invoke(prepared != null)
                prepareUpcoming(view)
            }
        }
    }

    private fun display(prepared: Prepared) {
        shown = prepared
        source = prepared.decoded
        frame = prepared.frame
        invalidate()
    }

    private fun clear() {
        shown = null
        source = null
        frame = null
        invalidate()
    }

    private fun prepareUpcoming(view: Size) {
        val next = upcoming ?: return
        val key = key(next, view)
        Background.frames.execute {
            if (ready[next.uri]?.key == key) return@execute
            val prepared = runCatching { prepare(next, key) }.getOrNull() ?: return@execute
            synchronized(ready) {
                ready[next.uri] = prepared
                while (ready.size > READY_FRAMES) ready.remove(ready.keys.first())
            }
        }
    }

    private fun prepare(photo: Item, key: Key): Prepared {
        val decoded = decode(photo) { Viewport.decodedSize(Viewport.fit(it, key.view, key.rotates), it) }
        val fitted = Viewport.fit(decoded.photo, key.view, key.rotates)
        return Prepared(key, decoded, fitted, Frames.render(decoded, fitted, key.view, background, key.dithers))
    }

    /** While the next photo is still being prepared, the one on screen is no longer the one to zoom. */
    private fun shownNow(): Prepared? = shown?.takeIf { it.key.uri == photo?.uri }

    private fun zoomIn(x: Float, y: Float) {
        val prepared = shownNow() ?: return
        val view = viewSize() ?: return
        isZoomed = true
        redraw(Viewport.zoomed(prepared.fitted, x, y, prepared.decoded.photo, view), view)
    }

    private fun zoomOut() {
        isZoomed = false
        current = null
        generation++
        shown?.let(::display)
    }

    private fun pan(dx: Float, dy: Float) {
        val prepared = shownNow() ?: return
        val view = viewSize() ?: return
        redraw(Viewport.panned(current ?: return, dx, dy, prepared.decoded.photo, view), view)
    }

    private fun redraw(matrix: Matrix, view: Size) {
        val photo = photo ?: return
        val from = source ?: return
        current = matrix
        val request = ++generation
        Background.frames.execute {
            val (sharper, zoomed) = runCatching {
                val needed = Viewport.decodedSize(matrix, from.photo)
                val sharper = if (from.bitmap.width >= needed.width && from.bitmap.height >= needed.height) from
                else runCatching { decode(photo) { needed } }.getOrDefault(from)
                sharper to Frames.render(sharper, matrix, view, background, dithers)
            }.getOrNull() ?: return@execute
            post {
                if (request != generation) return@post
                source = sharper
                frame = zoomed
                invalidate()
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        photo?.let { show(it, upcoming) }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(background)
        frame?.let { canvas.drawBitmap(it, 0f, 0f, null) }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        taps.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (isZoomed) {
                    if (hypot(dx, dy) > swipeSlop) pan(dx, dy)
                } else if (abs(dx) > swipeSlop && abs(dx) > abs(dy)) {
                    onFlip?.invoke(if (dx < 0) 1 else -1)
                }
            }
        }
        return true
    }

    private fun side(x: Float) = when {
        x < width / 3f -> -1
        x > width * 2 / 3f -> 1
        else -> 0
    }

    private fun viewSize() = if (width > 0 && height > 0) Size(width, height) else null

    private fun decode(item: Item, sizeFor: (Size) -> Size) =
        if (item.isVideo) Frames.decodeVideoFrame(context, item.uri, sizeFor) else Frames.decode(context.contentResolver, item.uri, sizeFor)

    private fun key(photo: Item, view: Size) = Key(photo.uri, view, rotatesLandscape, dithers)

    private data class Key(val uri: Uri, val view: Size, val rotates: Boolean, val dithers: Boolean)

    private class Prepared(val key: Key, val decoded: Decoded, val fitted: Matrix, val frame: Bitmap)

    private companion object {
        const val READY_FRAMES = 2
    }
}
