package io.github.andreygladkov.inkgallery.settings

import io.github.andreygladkov.ink.ui.InkAboutActivity
import io.github.andreygladkov.inkgallery.R

class AboutActivity : InkAboutActivity() {
    override fun notes() = listOf(getString(R.string.about_license), getString(R.string.about_privacy))
}
