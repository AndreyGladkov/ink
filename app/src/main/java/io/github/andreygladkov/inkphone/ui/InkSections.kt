package io.github.andreygladkov.inkphone.ui

import android.view.View
import android.widget.LinearLayout
import io.github.andreygladkov.inkphone.eink.InkStyle
import kotlin.math.roundToInt

private const val SECTION_GAP_DP = 20f
private const val TITLE_GAP_DP = 6f
private const val LINE_DP = 2f
private const val SECTION_TITLE_SCALE = 0.8f

fun LinearLayout.addSection(title: String) {
    val density = resources.displayMetrics.density
    addView(InkTextView(context).apply {
        text = title
        textScale = SECTION_TITLE_SCALE
        isBold = true
        setPadding(0, (SECTION_GAP_DP * density).roundToInt(), 0, (TITLE_GAP_DP * density).roundToInt())
    })
    addView(View(context).apply { setBackgroundColor(InkStyle.of(context).foreground) },
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (LINE_DP * density).roundToInt().coerceAtLeast(1)))
}
