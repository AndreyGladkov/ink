package io.github.andreygladkov.inkgallery

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.andreygladkov.ink.ui.InkTab

enum class Tab(@StringRes override val title: Int, @DrawableRes override val icon: Int) : InkTab {
    PHOTOS(R.string.tab_photos, R.drawable.ic_tab_photos),
    ALBUMS(R.string.tab_albums, R.drawable.ic_tab_albums),
}
