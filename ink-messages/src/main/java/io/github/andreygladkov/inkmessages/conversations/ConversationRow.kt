package io.github.andreygladkov.inkmessages.conversations

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.widget.LinearLayout
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.haptics.HapticChannel
import io.github.andreygladkov.ink.haptics.Haptics
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkPagedList
import io.github.andreygladkov.ink.ui.InkTextView
import kotlin.math.roundToInt

class ConversationRow(context: Context) : LinearLayout(context) {
    private val baseStyle = InkStyle.of(context)
    private val haptics by lazy { Haptics.of(context) }
    private val density = resources.displayMetrics.density

    val title = InkTextView(context).apply {
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }
    val date = InkTextView(context).apply { textScale = InkStyle.SECONDARY_SCALE }
    val preview = InkTextView(context).apply {
        textScale = InkStyle.SECONDARY_SCALE
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
    }

    var isUnread: Boolean = false
        set(value) {
            field = value
            listOf(title, date, preview).forEach { it.isBold = value }
        }

    init {
        orientation = VERTICAL
        isClickable = true
        isFocusable = true
        val horizontal = dp(InkButton.PADDING_DP)
        val vertical = dp(InkPagedList.ROW_PADDING_DP)
        setPadding(horizontal, vertical, horizontal, vertical)
        addView(LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(title, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(date, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = dp(DATE_GAP_DP) })
        })
        addView(preview, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = dp(LINE_GAP_DP) })
        applyPressed()
    }

    override fun setPressed(pressed: Boolean) {
        if (pressed && !isPressed && isClickable) haptics.perform(HapticChannel.BUTTONS)
        super.setPressed(pressed)
        applyPressed()
    }

    private fun applyPressed() {
        val style = if (isPressed) baseStyle.copy(inverted = !baseStyle.inverted) else baseStyle
        setBackgroundColor(style.background)
        listOf(title, date, preview).forEach { it.style = style }
    }

    private fun dp(value: Float) = (value * density).roundToInt()

    private companion object {
        const val DATE_GAP_DP = 8f
        const val LINE_GAP_DP = 2f
    }
}
