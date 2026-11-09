package io.github.andreygladkov.inklauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.Gravity
import android.view.RoundedCorner
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.ui.DottedDrawable
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.inklauncher.R
import kotlin.math.roundToInt

/**
 * A panel over the bottom of the screen in place of a dialog: the screen above stays as it was, so the panel
 * is the only area the e-ink display redraws. It closes without animation, which the panel would only smear.
 */
class ActionSheet(context: Context, title: String?, private val onClose: () -> Unit) : FrameLayout(context) {
    private val style = InkStyle.of(context)
    private val density = resources.displayMetrics.density
    private val gap = (GAP_DP * density).roundToInt()
    private val padding = (PADDING_DP * density).roundToInt()
    private val panel = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        isClickable = true
        setPadding(0, 0, 0, padding)
    }
    private var afterContent = false

    init {
        isClickable = true
        setOnClickListener { onClose() }
        panel.addView(CloseArrow(context).apply {
            contentDescription = context.getString(R.string.close)
            setOnClickListener { onClose() }
        }, LinearLayout.LayoutParams((ARROW_TARGET_WIDTH_DP * density).roundToInt(), (ARROW_TARGET_HEIGHT_DP * density).roundToInt())
            .apply { gravity = Gravity.CENTER_HORIZONTAL })
        if (title != null) {
            panel.addView(InkTextView(context).apply {
                text = title
                isBold = true
                maxLines = 2
                setPadding(padding, 0, padding, gap)
            })
        }
        addView(panel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        panel.background = SheetBackground(style, screenCornerPx(), density)
    }

    /** The sheet's top corners repeat the screen's own, so the panel reads as a part of the screen. */
    private fun screenCornerPx(): Float {
        val reported = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            rootWindowInsets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius ?: 0
        } else {
            0
        }
        return if (reported > 0) reported.toFloat() else A9_SCREEN_CORNER_DP * density
    }

    fun action(label: String, onClick: () -> Unit): SheetAction = SheetAction(context, padding).apply {
        text = label
        setOnClickListener { onClick() }
    }.also { row ->
        if (panel.getChildAt(panel.childCount - 1) is SheetAction) {
            val dots = DottedDrawable(context)
            panel.addView(View(context).apply { background = dots },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dots.intrinsicHeight).apply {
                    marginStart = padding
                    marginEnd = padding
                })
        }
        panel.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            if (afterContent) topMargin = gap
        })
        afterContent = false
    }

    fun content(view: View) {
        panel.addView(view, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = gap
            marginStart = padding
            marginEnd = padding
        })
        afterContent = true
    }

    class CloseArrow(context: Context) : InkButton(context) {
        private val density = resources.displayMetrics.density
        private val stroke = Paint().apply {
            isAntiAlias = false
            style = Paint.Style.STROKE
            strokeWidth = ARROW_STROKE_DP * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val path = Path()

        init {
            isBordered = false
            isFillRounded = true
            minHeight = 0
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            val arrowWidth = ARROW_WIDTH_DP * density
            val depth = ARROW_DEPTH_DP * density
            val left = (w - arrowWidth) / 2
            val top = ((h - depth) / 2).roundToInt().toFloat()
            path.reset()
            path.moveTo(left, top)
            path.lineTo(left + arrowWidth / 2, top + depth)
            path.lineTo(left + arrowWidth, top)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            stroke.color = textColor
            canvas.drawPath(path, stroke)
        }
    }

    private class SheetBackground(private val ink: InkStyle, cornerPx: Float, density: Float) : Drawable() {
        private val radii = floatArrayOf(cornerPx, cornerPx, cornerPx, cornerPx, 0f, 0f, 0f, 0f)
        private val linePx = (InkStyle.LINE_DP * density).roundToInt().coerceAtLeast(1)
        private val paint = Paint().apply { isAntiAlias = false }
        private val edge = Path()
        private val inside = Path()

        override fun onBoundsChange(bounds: Rect) {
            edge.reset()
            edge.addRoundRect(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat(), radii, Path.Direction.CW)
            inside.reset()
            inside.addRoundRect(bounds.left.toFloat(), (bounds.top + linePx).toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat(), radii, Path.Direction.CW)
        }

        override fun draw(canvas: Canvas) {
            paint.color = ink.foreground
            canvas.drawPath(edge, paint)
            paint.color = ink.background
            canvas.drawPath(inside, paint)
        }

        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: ColorFilter?) = Unit

        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    private companion object {
        const val PADDING_DP = 16f
        const val GAP_DP = 8f
        const val ARROW_TARGET_WIDTH_DP = 72f
        const val ARROW_TARGET_HEIGHT_DP = 40f
        const val ARROW_WIDTH_DP = 36f
        const val ARROW_DEPTH_DP = 6f
        const val ARROW_STROKE_DP = 2.5f

        // Hisense ships no rounded-corner config, so the A9 reports a radius of 0; 24 dp is measured on its panel.
        const val A9_SCREEN_CORNER_DP = 24f
    }
}
