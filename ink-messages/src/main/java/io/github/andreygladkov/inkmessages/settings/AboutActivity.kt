package io.github.andreygladkov.inkmessages.settings

import io.github.andreygladkov.ink.ui.InkAboutActivity
import io.github.andreygladkov.ink.ui.InkPagedItems
import io.github.andreygladkov.inkmessages.R

class AboutActivity : InkAboutActivity() {
    override fun notes() = listOf(getString(R.string.about_license), getString(R.string.about_privacy))

    override fun InkPagedItems.addOwnComponents() {
        license(getString(R.string.about_mmslib), APACHE)
    }
}
