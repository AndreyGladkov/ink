package io.github.andreygladkov.inkgallery.grid

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.eink.StemFit
import io.github.andreygladkov.ink.ui.InkPagedList
import io.github.andreygladkov.inkgallery.R
import io.github.andreygladkov.inkgallery.media.Item
import io.github.andreygladkov.inkgallery.media.Thumbnails
import io.github.andreygladkov.inkgallery.media.formatDuration
import kotlin.math.roundToInt

/**
 * Photos and videos as square thumbnails, three to a row, a page at a time. A video is marked with a play sign and
 * its length. Thumbnails of the next page are fetched while this one is looked at, so turning the page draws them at
 * once. A long press starts picking several items; while some are picked a tap picks or drops one instead of
 * opening it, and the picks stay across pages.
 */
class PhotoGrid(private val context: Context, private val onOpen: (Item) -> Unit) {
    val view = InkPagedList(context, dividers = false)
    var photos: List<Item> = emptyList()
        private set
    var onSelectionChanged: (() -> Unit)? = null
    private val selected = LinkedHashSet<Uri>()

    val isSelecting: Boolean get() = selected.isNotEmpty()

    val selection: List<Item> get() = photos.filter { it.uri in selected }

    private val gapPx = (GAP_DP * context.resources.displayMetrics.density).roundToInt()
    private val inkStyle = InkStyle.of(context)
    private val markPaint = Paint().apply {
        isAntiAlias = false
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, inkStyle.textSizeSp * InkStyle.SECONDARY_SCALE, context.resources.displayMetrics)
        typeface = StemFit.typeface(textSize, InkStyle.BOLD_WEIGHT)
    }
    private val markPadding = MARK_PADDING_DP * context.resources.displayMetrics.density
    private val playSign = Path()
    private val checkMark = Path()
    private var isFilled = false
    private val rowHeights = HashMap<Int, Int>()
    private val rows = object : InkPagedList.Adapter {
        override val count: Int get() = (photos.size + COLUMNS - 1) / COLUMNS
        override fun itemId(position: Int) = position.toLong()
        override fun createView(position: Int) = row(position)
        override fun uniformHeight(widthPx: Int) = rowHeights.getOrPut(widthPx) { heightOf(row(0), widthPx) }
    }

    /** After the shown page is laid out, so its own thumbnails are asked for before the next page's. */
    init {
        view.onPageShown = { view.post(::fetchNextPage) }
    }

    /**
     * Keeps the shown page on the photo it began with, unless the grid was at its start, where new photos appear.
     * The same photos again change nothing, so the panel is not redrawn for them.
     */
    fun setPhotos(photos: List<Item>) {
        if (isFilled && photos == this.photos) return
        isFilled = true
        if (selected.retainAll(photos.mapTo(HashSet()) { it.uri })) onSelectionChanged?.invoke()
        val first = view.firstPosition.takeIf { it > 0 }?.let { this.photos.getOrNull(it * COLUMNS)?.uri }
        this.photos = photos
        val row = first?.let { uri -> photos.indexOfFirst { it.uri == uri } }?.takeIf { it >= 0 }?.div(COLUMNS) ?: 0
        view.replaceAdapter(rows, row)
    }

    fun clearSelection() {
        if (selected.isEmpty()) return
        selected.clear()
        redrawCells(view)
        onSelectionChanged?.invoke()
    }

    private fun toggle(item: Item, cell: View) {
        if (!selected.remove(item.uri)) selected += item.uri
        cell.invalidate()
        onSelectionChanged?.invoke()
    }

    private fun redrawCells(parent: View) {
        if (parent is ThumbnailCell) parent.invalidate()
        if (parent is ViewGroup) for (i in 0 until parent.childCount) redrawCells(parent.getChildAt(i))
    }

    private fun row(position: Int) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(gapPx / 2, 0, gapPx / 2, 0)
        for (column in 0 until COLUMNS) {
            val photo = photos.getOrNull(position * COLUMNS + column)
            addView(ThumbnailCell(context, photo), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    /**
     * One thumbnail size for every column: a row's spare pixel goes to one of its cells, and a size of its own would
     * miss the thumbnails fetched ahead for the others.
     */
    private fun cellSide(rowWidth: Int) = (rowWidth - gapPx / 2 * 2) / COLUMNS - gapPx / 2 * 2

    private fun heightOf(row: View, widthPx: Int): Int {
        row.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        return row.measuredHeight
    }

    private fun fetchNextPage() {
        val side = cellSide(view.width)
        if (side <= 0) return
        val next = view.positionsOf(view.page + 1) ?: return
        photos.subList((next.first * COLUMNS).coerceAtMost(photos.size), ((next.last + 1) * COLUMNS).coerceAtMost(photos.size))
            .forEach { Thumbnails.load(context.contentResolver, it, side) }
    }

    private inner class ThumbnailCell(context: Context, private val photo: Item?) : View(context) {
        private var thumbnail: Bitmap? = null

        init {
            setPadding(gapPx / 2, gapPx / 2, gapPx / 2, gapPx / 2)
            if (photo != null) {
                contentDescription = if (photo.isVideo) context.getString(R.string.video_described, photo.name, length(photo)) else photo.name
                setOnClickListener { if (isSelecting) toggle(photo, this) else onOpen(photo) }
                setOnLongClickListener {
                    toggle(photo, this)
                    true
                }
            }
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            setMeasuredDimension(width, width)
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            val side = cellSide((parent as View).width)
            if (photo == null || side <= 0) return
            thumbnail = Thumbnails.cached(photo, side)
            if (thumbnail == null) Thumbnails.load(context.contentResolver, photo, side) { loaded ->
                if (cellSide((parent as View).width) != side) return@load
                thumbnail = loaded
                invalidate()
            }
        }

        override fun onDraw(canvas: Canvas) {
            thumbnail?.let { shown ->
                canvas.drawBitmap(shown, (paddingLeft + (width - paddingLeft - paddingRight - shown.width) / 2).toFloat(), paddingTop.toFloat(), null)
            }
            if (photo?.isVideo == true) drawVideoMark(canvas, length(photo))
            if (photo != null && photo.uri in selected) drawPicked(canvas)
        }

        override fun isSelected() = photo != null && photo.uri in selected

        /** A thick frame and a check in the corner: a grey tint barely shows on e-ink. */
        private fun drawPicked(canvas: Canvas) {
            val left = paddingLeft.toFloat()
            val top = paddingTop.toFloat()
            val right = (width - paddingRight).toFloat()
            val bottom = (height - paddingBottom).toFloat()
            val frame = PICKED_FRAME_DP * resources.displayMetrics.density
            markPaint.color = inkStyle.foreground
            canvas.drawRect(left, top, right, top + frame, markPaint)
            canvas.drawRect(left, bottom - frame, right, bottom, markPaint)
            canvas.drawRect(left, top, left + frame, bottom, markPaint)
            canvas.drawRect(right - frame, top, right, bottom, markPaint)
            val box = PICKED_BOX_DP * resources.displayMetrics.density
            canvas.drawRect(right - box, top, right, top + box, markPaint)
            markPaint.color = inkStyle.background
            markPaint.strokeWidth = box / 8
            markPaint.style = Paint.Style.STROKE
            checkMark.rewind()
            checkMark.moveTo(right - box * 0.78f, top + box * 0.52f)
            checkMark.lineTo(right - box * 0.58f, top + box * 0.72f)
            checkMark.lineTo(right - box * 0.22f, top + box * 0.3f)
            canvas.drawPath(checkMark, markPaint)
            markPaint.style = Paint.Style.FILL
        }

        private fun drawVideoMark(canvas: Canvas, length: String) {
            val sign = markPaint.textSize * PLAY_SIGN_SCALE
            val plateHeight = markPaint.textSize + 2 * markPadding
            val plateWidth = markPadding + sign + markPadding + (if (length.isEmpty()) 0f else markPaint.measureText(length) + markPadding)
            val left = paddingLeft.toFloat()
            val bottom = (height - paddingBottom).toFloat()
            val top = bottom - plateHeight
            markPaint.color = inkStyle.foreground
            canvas.drawRect(left, top, left + plateWidth, bottom, markPaint)
            markPaint.color = inkStyle.background
            val signTop = top + (plateHeight - sign) / 2
            playSign.rewind()
            playSign.moveTo(left + markPadding, signTop)
            playSign.lineTo(left + markPadding + sign, signTop + sign / 2)
            playSign.lineTo(left + markPadding, signTop + sign)
            playSign.close()
            canvas.drawPath(playSign, markPaint)
            val baseline = top + (plateHeight - markPaint.ascent() - markPaint.descent()) / 2
            canvas.drawText(length, left + 2 * markPadding + sign, baseline.roundToInt().toFloat(), markPaint)
        }
    }

    private fun length(item: Item) = item.durationMs?.let(::formatDuration).orEmpty()

    companion object {
        const val COLUMNS = 3
        private const val GAP_DP = 4f
        private const val MARK_PADDING_DP = 4f
        private const val PLAY_SIGN_SCALE = 0.8f
        private const val PICKED_FRAME_DP = 4f
        private const val PICKED_BOX_DP = 28f
    }
}
