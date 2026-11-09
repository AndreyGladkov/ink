package io.github.andreygladkov.inkphone.settings

import io.github.andreygladkov.ink.ui.InkAboutActivity
import io.github.andreygladkov.ink.ui.InkPagedItems
import io.github.andreygladkov.inkphone.R

class AboutActivity : InkAboutActivity() {
    override fun notes() = listOf(getString(R.string.about_license))

    override fun InkPagedItems.addOwnComponents() {
        license(getString(R.string.about_ezvcard), APACHE)
    }
}
