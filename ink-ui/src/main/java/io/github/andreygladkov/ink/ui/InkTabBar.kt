package io.github.andreygladkov.ink.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.andreygladkov.ink.eink.InkStyle
import kotlin.math.roundToInt

interface InkTab {
    @get:StringRes val title: Int
    @get:DrawableRes val icon: Int
}

class InkTabBar<T : InkTab>(
    context: Context,
    tabs: List<T>,
    private val showLabels: Boolean,
    private val onSelect: (T) -> Unit,
) : LinearLayout(context) {
    private val inkStyle = InkStyle.of(context)
    private val line = Paint().apply {
        isAntiAlias = false
        color = inkStyle.foreground
    }
    private val linePx = (InkStyle.LINE_DP * resources.displayMetrics.density).roundToInt().coerceAtLeast(1)
    private val buttons = tabs.associateWith { tab ->
        InkButton(context).apply {
            paddingDp = TAB_PADDING_DP
            icon = tab.icon
            iconSizeDp = TAB_ICON_DP
            if (showLabels) {
                setText(tab.title)
                maxLines = 1
                val padding = (LABEL_PADDING_DP * resources.displayMetrics.density).roundToInt()
                setPadding(padding, paddingTop, padding, paddingBottom)
            } else {
                contentDescription = context.getString(tab.title)
            }
            isBordered = false
            setOnClickListener { select(tab, afterRedraw = true) }
        }
    }

    private var fittedForWidth = -1

    var selected: T? = null
        private set

    init {
        orientation = HORIZONTAL
        setWillNotDraw(false)
        setPadding(0, linePx, 0, 0)
        buttons.values.forEach { addView(it, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)) }
    }

    fun setBadge(tab: T, count: Int) {
        buttons.getValue(tab).badge = count.takeIf { it > 0 }?.toString()
    }

    fun select(tab: T, afterRedraw: Boolean = false) {
        if (tab == selected) return
        selected = tab
        buttons.forEach { (t, button) -> button.isSelected = t == tab }
        // The animation callback runs before this frame's traversal and the post after it, so the new
        // selection reaches the panel before the tab's content is built.
        if (afterRedraw) postOnAnimation { post { if (selected == tab) onSelect(tab) } } else onSelect(tab)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (showLabels) fitLabels(MeasureSpec.getSize(widthMeasureSpec) / buttons.size)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun fitLabels(tabWidthPx: Int) {
        if (tabWidthPx <= 0 || tabWidthPx == fittedForWidth) return
        fittedForWidth = tabWidthPx
        val scale = fittedLabelScale(buttons.values, tabWidthPx, InkStyle.SECONDARY_SCALE)
        buttons.values.forEach { it.textScale = scale }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), linePx.toFloat(), line)
    }

    companion object {
        const val PREF_LABELS = "tab_labels"
        private const val LABEL_PADDING_DP = 4f
        private const val TAB_PADDING_DP = 8f
        private const val TAB_ICON_DP = 24f
    }
}
