package io.github.andreygladkov.inkweather.places

import android.app.Activity
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import io.github.andreygladkov.ink.ui.DottedDrawable
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkHeader
import io.github.andreygladkov.ink.ui.InkWindow
import io.github.andreygladkov.ink.ui.twoLines
import io.github.andreygladkov.inkweather.R
import io.github.andreygladkov.inkweather.forecast.Place
import kotlin.math.roundToInt

class PlaceList(private val activity: Activity, title: String, onAdd: () -> Unit) {
    val rows = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        dividerDrawable = DottedDrawable(activity)
        showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
    }

    init {
        val padding = dp(PADDING_DP)
        InkWindow.setContent(activity, LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(InkHeader.of(activity, title, activity::finish))
            addView(ScrollView(activity).apply {
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(rows)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(InkButton(activity).apply {
                setText(R.string.add_place)
                icon = R.drawable.ic_add
                iconPosition = InkButton.IconPosition.START
                setOnClickListener { onAdd() }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(padding, padding, padding, padding)
            })
        })
    }

    private fun dp(value: Float) = (value * activity.resources.displayMetrics.density).roundToInt()

    private companion object {
        const val PADDING_DP = 16f
    }
}

fun placeButton(context: Context, place: Place, onClick: () -> Unit) = InkButton(context).apply {
    text = if (place.area.isEmpty()) place.name else twoLines(place.name, place.area)
    isBordered = false
    gravity = Gravity.START or Gravity.CENTER_VERTICAL
    setOnClickListener { onClick() }
}
