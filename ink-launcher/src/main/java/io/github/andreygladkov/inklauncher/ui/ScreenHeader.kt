package io.github.andreygladkov.inklauncher.ui

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.ink.ui.iconButton
import kotlin.math.roundToInt

interface SideScreen {
    fun refresh() = Unit

    /** True when the screen used the back press itself. */
    fun onBack(): Boolean = false
}

fun screenTitle(context: Context, title: String) = InkTextView(context).apply {
    text = title
    tag = TITLE_TAG
    textScale = InkStyle.TITLE_SCALE
    isBold = true
    maxLines = 1
    ellipsize = TextUtils.TruncateAt.END
}

fun screenHeader(context: Context, title: String, leading: View?, vararg trailing: View): LinearLayout =
    screenHeader(context, screenTitle(context, title), leading, *trailing)

/** A row closed by a line: [center] takes the width left by the [leading] and [trailing] buttons at its ends. */
fun screenHeader(context: Context, center: View, leading: View?, vararg trailing: View): LinearLayout = LinearLayout(context).apply {
    orientation = LinearLayout.VERTICAL
    val density = resources.displayMetrics.density
    addView(LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val padding = (TITLE_PADDING_DP * density).roundToInt()
        if (leading != null) addView(leading) else setPadding(padding, 0, 0, 0)
        addView(center, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        trailing.forEach { addView(it) }
        minimumHeight = (MIN_HEIGHT_DP * density).roundToInt()
    })
    addView(View(context).apply { setBackgroundColor(InkStyle.of(context).foreground) },
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (InkStyle.LINE_DP * density).roundToInt().coerceAtLeast(1)))
}

fun LinearLayout.setHeaderTitle(title: String) {
    (findViewWithTag<InkTextView>(TITLE_TAG))?.text = title
}

fun headerButton(context: Context, icon: Int, description: Int, onClick: () -> Unit) =
    iconButton(context, icon, context.getString(description)).apply { setOnClickListener { onClick() } }

/** Sized like the page buttons it stands next to. */
fun footerButton(context: Context, icon: Int, description: Int, onClick: () -> Unit) = InkButton(context).apply {
    this.icon = icon
    contentDescription = context.getString(description)
    isBordered = false
    setOnClickListener { onClick() }
}

private const val TITLE_TAG = "screen_title"
private const val TITLE_PADDING_DP = 16f
private const val MIN_HEIGHT_DP = 56f
