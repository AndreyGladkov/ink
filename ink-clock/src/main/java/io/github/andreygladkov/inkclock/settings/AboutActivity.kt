package io.github.andreygladkov.inkclock.settings

import io.github.andreygladkov.ink.ui.InkAboutActivity
import io.github.andreygladkov.inkclock.R

class AboutActivity : InkAboutActivity() {
    override fun notes() = listOf(getString(R.string.about_license), getString(R.string.about_privacy))
}
