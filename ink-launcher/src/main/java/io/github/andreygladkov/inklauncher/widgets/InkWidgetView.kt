package io.github.andreygladkov.inklauncher.widgets

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.MotionEvent
import android.view.ViewConfiguration
import io.github.andreygladkov.ink.haptics.HapticChannel
import io.github.andreygladkov.ink.haptics.Haptics
import kotlin.math.abs

/** A widget's own views take every touch, so a long press for the launcher's menu is watched for on the way down. */
class InkWidgetView(context: Context, private val onLongPress: () -> Unit) : AppWidgetHostView(context) {
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var fired = false
    private val longPress = Runnable {
        fired = true
        Haptics.of(context).perform(HapticChannel.BUTTONS)
        onLongPress()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                fired = false
                downX = event.x
                downY = event.y
                postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> if (abs(event.x - downX) > slop || abs(event.y - downY) > slop) removeCallbacks(longPress)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> removeCallbacks(longPress)
        }
        if (fired) {
            if (event.actionMasked != MotionEvent.ACTION_DOWN) cancelChildren(event)
            return true
        }
        // Keeping the gesture even when no widget view wants it lets its end cancel the long press.
        return super.dispatchTouchEvent(event) || event.actionMasked == MotionEvent.ACTION_DOWN
    }

    private fun cancelChildren(event: MotionEvent) {
        val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
        super.dispatchTouchEvent(cancel)
        cancel.recycle()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return
        val density = resources.displayMetrics.density
        val widthDp = w / density
        val heightDp = h / density
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp, heightDp)))
        } else {
            @Suppress("DEPRECATION")
            updateAppWidgetSize(Bundle(), widthDp.toInt(), heightDp.toInt(), widthDp.toInt(), heightDp.toInt())
        }
    }
}
