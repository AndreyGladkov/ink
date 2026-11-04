package io.github.andreygladkov.inkphone.ui

import android.content.Context
import androidx.annotation.DrawableRes
import kotlin.math.roundToInt

private const val ICON_DP = 24f

fun iconButton(context: Context, @DrawableRes icon: Int, description: String) = InkButton(context).apply {
    this.icon = icon
    iconSizeDp = ICON_DP
    minWidth = (InkButton.MIN_HEIGHT_DP * resources.displayMetrics.density).roundToInt()
    contentDescription = description
    isBordered = false
}
