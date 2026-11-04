package io.github.andreygladkov.inkmessages.thread

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkTextView
import kotlin.math.roundToInt

class BubbleView(context: Context, borderDp: Float) : InkTextView(context) {
    private val density = resources.displayMetrics.density
    private val borderPx = (borderDp * density).roundToInt().coerceAtLeast(1)
    private val fill = Paint().apply { isAntiAlias = false }

    /** Drawn into the padding, so selecting a message never moves anything on the page. */
    var isHighlighted = false
        set(value) {
            field = value
            invalidate()
        }

    init {
        val horizontal = (PADDING_H_DP * density).roundToInt() + borderPx
        val vertical = (PADDING_V_DP * density).roundToInt() + borderPx
        setPadding(horizontal, vertical, horizontal, vertical)
    }

    override fun onDraw(canvas: Canvas) {
        val radius = style.cornerRadiusDp * density
        val b = (if (isHighlighted) highlightPx(density) else borderPx).toFloat()
        fill.color = style.foreground
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, fill)
        fill.color = style.background
        val inner = (radius - b).coerceAtLeast(0f)
        canvas.drawRoundRect(b, b, width - b, height - b, inner, inner, fill)
        super.onDraw(canvas)
    }

    companion object {
        private const val PADDING_H_DP = 10f
        private const val PADDING_V_DP = 8f
        private const val HIGHLIGHT_DP = 3f

        fun highlightPx(density: Float) = (HIGHLIGHT_DP * density).roundToInt()
    }
}

/** A message: its pictures and attachment buttons first, then the text bubble, then an optional footer. */
class BubbleRow(context: Context, isOutgoing: Boolean, body: CharSequence, footer: View?, private val attachments: List<View> = emptyList()) : LinearLayout(context) {
    private val density = resources.displayMetrics.density
    private val bubble = BubbleView(context, borderDp(isOutgoing)).apply { text = body }
    private val images = attachments.filterIsInstance<MmsImageView>()

    var isHighlighted = false
        set(value) {
            field = value
            bubble.isHighlighted = value
            images.forEach { it.isHighlighted = value }
        }

    /** Children with their own tap handling swallow long presses, so each of them forwards it. */
    fun setOnMessageLongClick(action: () -> Unit) {
        val listener = OnLongClickListener {
            action()
            true
        }
        setOnLongClickListener(listener)
        bubble.setOnLongClickListener(listener)
        attachments.forEach { it.setOnLongClickListener(listener) }
    }

    init {
        orientation = VERTICAL
        gravity = if (isOutgoing) Gravity.END else Gravity.START
        val horizontal = (InkButton.PADDING_DP * density).roundToInt()
        val vertical = (ROW_PADDING_DP * density).roundToInt()
        setPadding(horizontal, vertical, horizontal, vertical)
        attachments.forEachIndexed { index, view ->
            val gap = if (index > 0) (ATTACHMENT_GAP_DP * density).roundToInt() else 0
            addView(view, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = gap })
        }
        if (body.isNotEmpty() || attachments.isEmpty()) {
            val gap = if (attachments.isNotEmpty()) (ATTACHMENT_GAP_DP * density).roundToInt() else 0
            addView(bubble, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = gap })
        }
        if (footer != null) {
            addView(footer, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = (FOOTER_GAP_DP * density).roundToInt() })
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val max = (available * MAX_WIDTH_FRACTION).roundToInt()
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            if (bubble.maxWidth != max) bubble.maxWidth = max
            images.forEach { it.maxWidthPx = max }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    companion object {
        private const val INCOMING_BORDER_DP = 1f
        private const val OUTGOING_BORDER_DP = 2f
        private const val ATTACHMENT_GAP_DP = 4f
        private const val ROW_PADDING_DP = 4f
        private const val FOOTER_GAP_DP = 2f
        private const val MAX_WIDTH_FRACTION = 0.85f

        fun borderDp(isOutgoing: Boolean) = if (isOutgoing) OUTGOING_BORDER_DP else INCOMING_BORDER_DP
    }
}
