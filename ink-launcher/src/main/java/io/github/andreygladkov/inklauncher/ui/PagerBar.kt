package io.github.andreygladkov.inklauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.eink.StemFit
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.inklauncher.R
import kotlin.math.roundToInt
import io.github.andreygladkov.ink.R as InkR

class PagerBar(context: Context, onPrev: () -> Unit, onNext: () -> Unit, onDots: (() -> Unit)? = null) : LinearLayout(context) {
    private val prev = arrow(R.drawable.ic_chevron_left, InkR.string.page_prev, onPrev)
    private val next = arrow(R.drawable.ic_chevron_right, InkR.string.page_next, onNext)
    private val dots = PageDots(context).apply {
        if (onDots != null) {
            contentDescription = context.getString(R.string.all_apps)
            setOnClickListener { onDots() }
        }
    }
    private val hint = InkTextView(context).apply {
        textScale = InkStyle.SECONDARY_SCALE
        gravity = Gravity.CENTER
        maxLines = 2
    }
    private val cancel = InkButton(context).apply {
        setText(R.string.cancel)
        textScale = InkStyle.SECONDARY_SCALE
        isBordered = false
    }
    private val moving = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        visibility = GONE
        addView(hint, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(cancel, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(prev, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(FrameLayout(context).apply {
            addView(dots, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(moving, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        addView(next, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    fun show(page: Int, pageCount: Int, hasPrev: Boolean, hasNext: Boolean, prevBadge: String = "", prevState: String? = null) {
        prev.visibility = if (hasPrev) VISIBLE else INVISIBLE
        next.visibility = if (hasNext) VISIBLE else INVISIBLE
        if (prev.text.toString() != prevBadge) prev.text = prevBadge
        if (prev.stateDescription?.toString() != prevState) prev.stateDescription = prevState
        dots.show(page, pageCount)
    }

    fun showMoving(text: String?, onCancel: () -> Unit) {
        moving.visibility = if (text == null) GONE else VISIBLE
        dots.visibility = if (text == null) VISIBLE else GONE
        hint.text = text
        cancel.setOnClickListener { onCancel() }
    }

    private fun arrow(icon: Int, description: Int, onClick: () -> Unit) = InkButton(context).apply {
        this.icon = icon
        iconPosition = InkButton.IconPosition.START
        contentDescription = context.getString(description)
        isBordered = false
        maxLines = 1
        textScale = InkStyle.SECONDARY_SCALE
        setOnClickListener { onClick() }
    }

    /** Centred on the whole bar, not on the room between the arrows, so a badge widening the left arrow leaves the dots in place. */
    private inner class PageDots(context: Context) : View(context) {
        private val inkStyle = InkStyle.of(context)
        private val density = resources.displayMetrics.density
        private val fill = Paint().apply {
            isAntiAlias = false
            color = inkStyle.foreground
        }
        private val text = Paint().apply {
            isAntiAlias = false
            color = inkStyle.foreground
            textAlign = Paint.Align.CENTER
        }
        private var page = 0
        private var count = 1

        fun show(page: Int, count: Int) {
            if (page == this.page && count == this.count) return
            this.page = page
            this.count = count
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            if (count <= 1 && !isClickable) return
            val centerY = height / 2f
            val centerX = this@PagerBar.width / 2 - (parent as View).left - left
            if (count > MAX_DOTS) {
                text.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, inkStyle.textSizeSp * InkStyle.SECONDARY_SCALE, resources.displayMetrics)
                text.typeface = StemFit.typeface(text.textSize, inkStyle.weight)
                canvas.drawText("${page + 1} / $count", centerX.toFloat(), (centerY - (text.ascent() + text.descent()) / 2).roundToInt().toFloat(), text)
                return
            }
            val dot = (DOT_DP * density).roundToInt()
            val small = (SMALL_DOT_DP * density).roundToInt()
            val step = (STEP_DP * density).roundToInt()
            val first = centerX - (count - 1) * step / 2
            for (i in 0 until count) {
                val radius = if (i == page) dot / 2f else small / 2f
                canvas.drawCircle((first + i * step).toFloat(), centerY.roundToInt().toFloat(), radius, fill)
            }
        }
    }

    private companion object {
        const val MAX_DOTS = 9
        const val DOT_DP = 10f
        const val SMALL_DOT_DP = 4f
        const val STEP_DP = 20f
    }
}
