package io.github.andreygladkov.ink.ui

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import io.github.andreygladkov.ink.R
import io.github.andreygladkov.ink.eink.InkStyle
import kotlin.math.roundToInt

object InkHeader {
    const val MIN_HEIGHT_DP = 56f
    private const val TITLE_PADDING_DP = 16f
    private const val TITLE_TAG = "ink_header_title"

    fun of(context: Context, title: String, onBack: (() -> Unit)?, vararg actions: View): LinearLayout =
        of(context, title(context, title), onBack?.let { back(context, it) }, *actions)

    /** [center] takes the width left by [leading] and [trailing]; without [leading] it starts at the title inset. */
    fun of(context: Context, center: View, leading: View?, vararg trailing: View): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val density = resources.displayMetrics.density
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            if (leading != null) addView(leading) else setPadding((TITLE_PADDING_DP * density).roundToInt(), 0, 0, 0)
            addView(center, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            trailing.forEach { addView(it) }
            minimumHeight = (MIN_HEIGHT_DP * density).roundToInt()
        })
        addView(line(context))
    }

    fun line(context: Context) = View(context).apply {
        setBackgroundColor(InkStyle.of(context).foreground)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
            (InkStyle.LINE_DP * resources.displayMetrics.density).roundToInt().coerceAtLeast(1))
    }

    fun title(context: Context, text: String) = InkTextView(context).apply {
        this.text = text
        tag = TITLE_TAG
        textScale = InkStyle.TITLE_SCALE
        isBold = true
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }

    fun setTitle(header: View, text: String) {
        header.findViewWithTag<InkTextView>(TITLE_TAG)?.text = text
    }

    fun back(context: Context, onBack: () -> Unit) = button(context, R.drawable.ic_arrow_back, context.getString(R.string.back), onBack)

    fun settings(context: Context, onOpen: () -> Unit) = button(context, R.drawable.ic_settings, context.getString(R.string.settings), onOpen)

    fun button(context: Context, @DrawableRes icon: Int, description: String, onClick: () -> Unit) =
        iconButton(context, icon, description).apply { setOnClickListener { onClick() } }

    /** A primary action at the start of a main screen's header, where a nested screen has its back arrow. */
    fun action(context: Context, @DrawableRes icon: Int, label: String, onClick: () -> Unit) = InkButton(context).apply {
        text = label
        this.icon = icon
        iconPosition = InkButton.IconPosition.START
        isBordered = false
        maxLines = 1
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setOnClickListener { onClick() }
    }
}
