package io.github.andreygladkov.inkphone.ui

import android.view.View
import android.widget.LinearLayout
import io.github.andreygladkov.inkphone.eink.InkStyle
import kotlin.math.roundToInt

private const val SECTION_GAP_DP = 20f
private const val TITLE_GAP_DP = 6f

fun LinearLayout.addSection(title: String) {
    val density = resources.displayMetrics.density
    addView(InkTextView(context).apply {
        text = title
        textScale = InkStyle.SECONDARY_SCALE
        isBold = true
        setPadding(0, (SECTION_GAP_DP * density).roundToInt(), 0, (TITLE_GAP_DP * density).roundToInt())
    })
    addView(View(context).apply { setBackgroundColor(InkStyle.of(context).foreground) },
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (InkStyle.LINE_DP * density).roundToInt().coerceAtLeast(1)))
}
