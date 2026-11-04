package io.github.andreygladkov.inkmessages.thread

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.Gravity
import android.widget.LinearLayout
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkTextView
import kotlin.math.roundToInt

class BubbleView(context: Context, borderDp: Float) : InkTextView(context) {
    private val density = resources.displayMetrics.density
    private val borderPx = (borderDp * density).roundToInt().coerceAtLeast(1)
    private val fill = Paint().apply { isAntiAlias = false }

    init {
        val horizontal = (PADDING_H_DP * density).roundToInt() + borderPx
        val vertical = (PADDING_V_DP * density).roundToInt() + borderPx
        setPadding(horizontal, vertical, horizontal, vertical)
    }

    override fun onDraw(canvas: Canvas) {
        val radius = style.cornerRadiusDp * density
        val b = borderPx.toFloat()
        fill.color = style.foreground
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, fill)
        fill.color = style.background
        val inner = (radius - b).coerceAtLeast(0f)
        canvas.drawRoundRect(b, b, width - b, height - b, inner, inner, fill)
        super.onDraw(canvas)
    }

    private companion object {
        const val PADDING_H_DP = 10f
        const val PADDING_V_DP = 8f
    }
}

class BubbleRow(context: Context, isOutgoing: Boolean, body: CharSequence, time: String?) : LinearLayout(context) {
    private val density = resources.displayMetrics.density
    private val bubble = BubbleView(context, if (isOutgoing) OUTGOING_BORDER_DP else INCOMING_BORDER_DP).apply { text = body }

    init {
        orientation = VERTICAL
        gravity = if (isOutgoing) Gravity.END else Gravity.START
        val horizontal = (InkButton.PADDING_DP * density).roundToInt()
        val vertical = (ROW_PADDING_DP * density).roundToInt()
        setPadding(horizontal, vertical, horizontal, vertical)
        addView(bubble, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        if (time != null) {
            addView(InkTextView(context).apply {
                text = time
                textScale = InkStyle.SECONDARY_SCALE
                setPadding(0, (TIME_GAP_DP * density).roundToInt(), 0, 0)
            }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val max = (available * MAX_WIDTH_FRACTION).roundToInt()
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED && bubble.maxWidth != max) bubble.maxWidth = max
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private companion object {
        const val INCOMING_BORDER_DP = 1f
        const val OUTGOING_BORDER_DP = 2f
        const val ROW_PADDING_DP = 4f
        const val TIME_GAP_DP = 2f
        const val MAX_WIDTH_FRACTION = 0.85f
    }
}
