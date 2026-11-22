package io.github.andreygladkov.inklauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.haptics.HapticChannel
import io.github.andreygladkov.ink.haptics.Haptics
import kotlin.math.roundToInt

/** Lays children out on equal cells; touches that no child takes are reported as taps on the cell under them. */
class CellGrid(context: Context, val columns: Int, val rows: Int) : ViewGroup(context) {
    class Params(val column: Int, val row: Int, val columnSpan: Int = 1, val rowSpan: Int = 1) : LayoutParams(MATCH_PARENT, MATCH_PARENT)

    data class Area(val column: Int, val row: Int, val width: Int = 1, val height: Int = 1)

    var onCellTap: ((column: Int, row: Int) -> Unit)? = null
    var onCellLongPress: ((column: Int, row: Int) -> Unit)? = null

    var marked: List<Area> = emptyList()
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    var framed: View? = null
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val ink = Paint().apply {
        isAntiAlias = false
        color = InkStyle.of(context).foreground
    }
    private var downX = 0f
    private var downY = 0f

    init {
        setOnClickListener { cellAtTouch()?.let { (column, row) -> onCellTap?.invoke(column, row) } }
        setOnLongClickListener {
            val cell = cellAtTouch()
            val handler = onCellLongPress
            if (cell == null || handler == null) return@setOnLongClickListener false
            Haptics.of(context).perform(HapticChannel.BUTTONS)
            handler(cell.first, cell.second)
            true
        }
    }

    fun add(view: View, column: Int, row: Int, columnSpan: Int = 1, rowSpan: Int = 1) = addView(view, Params(column, row, columnSpan, rowSpan))

    val cellWidth: Int get() = if (width == 0) 0 else width / columns

    val cellHeight: Int get() = if (height == 0) 0 else height / rows

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            downX = event.x
            downY = event.y
        }
        return super.dispatchTouchEvent(event)
    }

    override fun shouldDelayChildPressedState() = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(width, height)
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val params = child.layoutParams as Params
            child.measure(
                MeasureSpec.makeMeasureSpec(x(params.column + params.columnSpan, width) - x(params.column, width), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(y(params.row + params.rowSpan, height) - y(params.row, height), MeasureSpec.EXACTLY),
            )
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val params = child.layoutParams as Params
            child.layout(x(params.column, width), y(params.row, height), x(params.column + params.columnSpan, width), y(params.row + params.rowSpan, height))
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        marked.forEach { drawMark(canvas, it) }
        framed?.takeIf { it.parent == this }?.let { drawFrame(canvas, it.left, it.top, it.right, it.bottom) }
    }

    private fun drawMark(canvas: Canvas, area: Area) {
        val left = x(area.column, width)
        val top = y(area.row, height)
        val right = x(area.column + area.width, width)
        val bottom = y(area.row + area.height, height)
        drawCorners(canvas, left, top, right, bottom)
        if (area.width > 1 || area.height > 1) drawPlus(canvas, (left + right) / 2, (top + bottom) / 2)
    }

    private fun drawPlus(canvas: Canvas, centerX: Int, centerY: Int) {
        val half = dp(PLUS_DP) / 2
        val line = dp(PLUS_LINE_DP)
        rect(canvas, centerX - half, centerY - line / 2, centerX + half, centerY - line / 2 + line)
        rect(canvas, centerX - line / 2, centerY - half, centerX - line / 2 + line, centerY + half)
    }

    private fun drawCorners(canvas: Canvas, left: Int, top: Int, right: Int, bottom: Int) {
        val inset = dp(MARK_INSET_DP)
        val line = dp(InkStyle.LINE_DP)
        val arm = minOf(dp(MARK_ARM_DP), (right - left) / 3, (bottom - top) / 3)
        val l = left + inset
        val t = top + inset
        val r = right - inset
        val b = bottom - inset
        listOf(l to t, r - arm to t, l to b - line, r - arm to b - line).forEach { (x, y) -> rect(canvas, x, y, x + arm, y + line) }
        listOf(l to t, r - line to t, l to b - arm, r - line to b - arm).forEach { (x, y) -> rect(canvas, x, y, x + line, y + arm) }
    }

    private fun drawFrame(canvas: Canvas, left: Int, top: Int, right: Int, bottom: Int) {
        val line = dp(FRAME_DP)
        rect(canvas, left, top, right, top + line)
        rect(canvas, left, bottom - line, right, bottom)
        rect(canvas, left, top, left + line, bottom)
        rect(canvas, right - line, top, right, bottom)
    }

    private fun rect(canvas: Canvas, left: Int, top: Int, right: Int, bottom: Int) =
        canvas.drawRect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), ink)

    private fun dp(value: Float) = (value * density).roundToInt()

    override fun checkLayoutParams(p: LayoutParams?) = p is Params

    private fun x(column: Int, width: Int) = column * width / columns

    private fun y(row: Int, height: Int) = row * height / rows

    private fun cellAtTouch(): Pair<Int, Int>? {
        if (width == 0 || height == 0) return null
        val column = (downX * columns / width).toInt()
        val row = (downY * rows / height).toInt()
        return if (column in 0 until columns && row in 0 until rows) column to row else null
    }

    private companion object {
        const val MARK_INSET_DP = 6f
        const val MARK_ARM_DP = 12f
        const val FRAME_DP = 4f
        const val PLUS_DP = 28f
        const val PLUS_LINE_DP = 3f
    }
}
