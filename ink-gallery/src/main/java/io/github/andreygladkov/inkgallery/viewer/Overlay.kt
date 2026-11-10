package io.github.andreygladkov.inkgallery.viewer

import android.content.Context
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.ui.InkButton
import kotlin.math.roundToInt

/** The bars laid over a full-screen photo or video, hidden until a tap shows them. */
object Overlay {
    private const val ICON_DP = 24f
    private const val ACTION_PADDING_DP = 4f

    /** Takes the taps that land on it, so they do not reach the photo or video below. */
    fun bar(context: Context, content: View) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(InkStyle.of(context).background)
        visibility = View.GONE
        isClickable = true
        addView(content, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    fun action(context: Context, @DrawableRes icon: Int, @StringRes label: Int, onClick: () -> Unit) = InkButton(context).apply {
        if (icon != 0) {
            this.icon = icon
            iconSizeDp = ICON_DP
        }
        setText(label)
        isBordered = false
        val padding = (ACTION_PADDING_DP * resources.displayMetrics.density).roundToInt()
        setPadding(padding, paddingTop, padding, paddingBottom)
        setOnClickListener { onClick() }
    }

    /** Shows the bars when hidden and hides them when shown; true when they are shown now. */
    fun toggle(bars: List<View>): Boolean {
        val shown = !bars.first().isVisible
        bars.forEach { it.isVisible = shown }
        return shown
    }
}
