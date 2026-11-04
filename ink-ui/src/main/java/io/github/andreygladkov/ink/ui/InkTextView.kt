package io.github.andreygladkov.ink.ui

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.TextView
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.eink.MonochromeEmoji
import io.github.andreygladkov.ink.eink.StemFit

open class InkTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : TextView(context, attrs) {
    var style: InkStyle = InkStyle.of(context)
        set(value) {
            field = value
            applyStyle()
        }

    var textScale: Float = 1f
        set(value) {
            field = value
            applyStyle()
        }

    var isBold: Boolean = false
        set(value) {
            field = value
            applyStyle()
        }

    protected open val textColor: Int get() = style.foreground

    // TextView's constructor already calls overridable state callbacks, before this class's fields exist.
    private var isStyled = false

    init {
        isStyled = true
        includeFontPadding = false
        applyStyle()
    }

    private fun applyStyle() {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, style.textSizeSp * textScale)
        paint.isAntiAlias = false
        val weight = if (isBold) maxOf(style.weight, InkStyle.BOLD_WEIGHT) else style.weight
        typeface = StemFit.typeface(textSize, weight)
        refreshTextColor()
    }

    override fun setText(text: CharSequence?, type: BufferType?) {
        super.setText(if (isStyled) MonochromeEmoji.apply(context, text) else text, type)
    }

    protected fun refreshTextColor() {
        if (!isStyled) return
        setTextColor(textColor)
        setHintTextColor(textColor)
    }
}
