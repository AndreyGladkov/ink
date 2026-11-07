package io.github.andreygladkov.inklauncher.ui

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import io.github.andreygladkov.ink.haptics.HapticChannel
import io.github.andreygladkov.ink.haptics.Haptics

/** Lays children out on equal cells; touches that no child takes are reported as taps on the cell under them. */
class CellGrid(context: Context, val columns: Int, val rows: Int) : ViewGroup(context) {
    class Params(val column: Int, val row: Int, val columnSpan: Int = 1, val rowSpan: Int = 1) : LayoutParams(MATCH_PARENT, MATCH_PARENT)

    var onCellTap: ((column: Int, row: Int) -> Unit)? = null
    var onCellLongPress: ((column: Int, row: Int) -> Unit)? = null

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

    override fun checkLayoutParams(p: LayoutParams?) = p is Params

    private fun x(column: Int, width: Int) = column * width / columns

    private fun y(row: Int, height: Int) = row * height / rows

    private fun cellAtTouch(): Pair<Int, Int>? {
        if (width == 0 || height == 0) return null
        val column = (downX * columns / width).toInt()
        val row = (downY * rows / height).toInt()
        return if (column in 0 until columns && row in 0 until rows) column to row else null
    }
}
