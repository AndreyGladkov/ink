package io.github.andreygladkov.inklauncher.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkTextView
import kotlin.math.roundToInt

/**
 * A panel over the bottom of the screen in place of a dialog: the screen above stays as it was, so the panel
 * is the only area the e-ink display redraws. A tap above it closes it.
 */
class ActionSheet(context: Context, title: String?, private val onClose: () -> Unit) : FrameLayout(context) {
    private val style = InkStyle.of(context)
    private val density = resources.displayMetrics.density
    private val gap = (GAP_DP * density).roundToInt()
    private val padding = (PADDING_DP * density).roundToInt()
    private val panel = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        isClickable = true
        setBackgroundColor(style.background)
        setPadding(0, 0, 0, padding)
    }

    init {
        isClickable = true
        setOnClickListener { onClose() }
        panel.addView(View(context).apply { setBackgroundColor(style.foreground) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (InkStyle.LINE_DP * density).roundToInt().coerceAtLeast(1)))
        if (title != null) {
            panel.addView(InkTextView(context).apply {
                text = title
                isBold = true
                maxLines = 2
                setPadding(padding, gap * 2, padding, 0)
            })
        }
        addView(panel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
    }

    fun action(label: String, onClick: () -> Unit): InkButton = InkButton(context).apply {
        text = label
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        maxLines = 1
        setOnClickListener { onClick() }
    }.also { content(it) }

    fun content(view: View) {
        panel.addView(view, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = gap
            marginStart = padding
            marginEnd = padding
        })
    }

    private companion object {
        const val PADDING_DP = 16f
        const val GAP_DP = 8f
    }
}
