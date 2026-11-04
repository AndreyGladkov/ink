package io.github.andreygladkov.ink.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.text.InputType
import android.util.TypedValue
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.eink.StemFit
import kotlin.math.roundToInt

class InkEditText(context: Context) : EditText(context) {
    private val style = InkStyle.of(context)
    private val density = resources.displayMetrics.density
    private val fill = Paint().apply { isAntiAlias = false }

    var isBordered = false
        set(value) {
            field = value
            val padding = if (value) (FIELD_PADDING_DP * density).roundToInt() else 0
            setPadding(padding, padding, padding, padding)
            invalidate()
        }

    init {
        background = null
        includeFontPadding = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, style.textSizeSp)
        paint.isAntiAlias = false
        typeface = StemFit.typeface(textSize, style.weight)
        setTextColor(style.foreground)
        setHintTextColor(style.foreground)
        // A blinking cursor repaints the panel twice a second for as long as the field has focus.
        isCursorVisible = false
        // Selection would be drawn as a foreground-colored block over foreground-colored text.
        isLongClickable = false
        isSingleLine = true
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (isBordered) drawBorder(canvas)
        super.onDraw(canvas)
    }

    // Without a cursor the only sign of the field being typed into is a heavier frame.
    private fun drawBorder(canvas: Canvas) {
        val border = ((if (isFocused) FOCUSED_BORDER_DP else BORDER_DP) * density).roundToInt().coerceAtLeast(1).toFloat()
        val radius = style.cornerRadiusDp * density
        val left = scrollX.toFloat()
        val top = scrollY.toFloat()
        val right = left + width
        val bottom = top + height
        fill.color = style.foreground
        canvas.drawRoundRect(left, top, right, bottom, radius, radius, fill)
        fill.color = style.background
        val inner = (radius - border).coerceAtLeast(0f)
        canvas.drawRoundRect(left + border, top + border, right - border, bottom - border, inner, inner, fill)
    }

    private companion object {
        const val BORDER_DP = 1f
        const val FOCUSED_BORDER_DP = 3f
        const val FIELD_PADDING_DP = 12f
    }
}
