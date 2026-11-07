package io.github.andreygladkov.inklauncher.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/** Takes a horizontal swipe from its children and reports it once the finger lifts: pages change in one step. */
class SwipeFrame(context: Context, private val onSwipe: (towardsLeft: Boolean) -> Unit) : FrameLayout(context) {
    private val slop = ViewConfiguration.get(context).scaledTouchSlop * 2
    private var downX = 0f
    private var downY = 0f
    private var swiping = false

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        track(event)
        return swiping
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        track(event)
        if (event.actionMasked == MotionEvent.ACTION_UP && swiping) {
            swiping = false
            onSwipe(event.x < downX)
        }
        return true
    }

    private fun track(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                swiping = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                if (abs(dx) > slop && abs(dx) > abs(event.y - downY)) swiping = true
            }
        }
    }
}
