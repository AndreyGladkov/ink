package io.github.andreygladkov.inkcalculator.ui

import android.content.Context
import android.view.ViewGroup

class BottomUpColumn(context: Context) : ViewGroup(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val childWidth = MeasureSpec.makeMeasureSpec(width - paddingLeft - paddingRight, MeasureSpec.EXACTLY)
        val childHeight = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        for (i in 0 until childCount) getChildAt(i).measure(childWidth, childHeight)
        setMeasuredDimension(width, MeasureSpec.getSize(heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        var bottom = height - paddingBottom
        for (i in childCount - 1 downTo 0) {
            val child = getChildAt(i)
            val top = bottom - child.measuredHeight
            if (top < paddingTop) {
                child.visibility = INVISIBLE
                child.layout(0, 0, 0, 0)
                bottom = paddingTop
            } else {
                child.visibility = VISIBLE
                child.layout(paddingLeft, top, paddingLeft + child.measuredWidth, bottom)
                bottom = top
            }
        }
    }
}
