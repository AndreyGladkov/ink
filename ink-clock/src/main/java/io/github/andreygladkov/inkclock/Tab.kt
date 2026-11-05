package io.github.andreygladkov.inkclock

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.andreygladkov.ink.ui.InkTab

enum class Tab(@StringRes override val title: Int, @DrawableRes override val icon: Int) : InkTab {
    ALARMS(R.string.tab_alarms, R.drawable.ic_tab_alarm),
}
