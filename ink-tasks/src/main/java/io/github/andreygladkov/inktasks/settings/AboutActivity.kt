package io.github.andreygladkov.inktasks.settings

import io.github.andreygladkov.ink.ui.InkAboutActivity
import io.github.andreygladkov.inktasks.R

class AboutActivity : InkAboutActivity() {
    override fun notes() = listOf(getString(R.string.about_license), getString(R.string.about_privacy))
}
