package io.github.andreygladkov.inkcalendar.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/** A vertical swipe steps a whole page with no scrolling in between, which on e-ink would leave a trail of redraws. */
class SwipeFrame(context: Context, private val onStep: (Int) -> Unit) : FrameLayout(context) {
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val distance = SWIPE_DP * resources.displayMetrics.density
    private var downX = 0f
    private var downY = 0f

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> remember(event)
            MotionEvent.ACTION_MOVE -> return isVertical(event, slop.toFloat())
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> remember(event)
            MotionEvent.ACTION_UP -> if (isVertical(event, distance)) onStep(if (event.y < downY) 1 else -1)
        }
        return true
    }

    private fun remember(event: MotionEvent) {
        downX = event.x
        downY = event.y
    }

    private fun isVertical(event: MotionEvent, least: Float): Boolean {
        val dy = abs(event.y - downY)
        return dy > least && dy > abs(event.x - downX)
    }

    private companion object {
        const val SWIPE_DP = 48f
    }
}
