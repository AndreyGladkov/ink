package io.github.andreygladkov.inkweather.settings

import io.github.andreygladkov.ink.ui.InkAboutActivity
import io.github.andreygladkov.ink.ui.InkPagedItems
import io.github.andreygladkov.inkweather.R

class AboutActivity : InkAboutActivity() {
    override fun notes() = listOf(getString(R.string.about_license), getString(R.string.about_privacy))

    override fun InkPagedItems.addOwnComponents() {
        link(getString(R.string.about_met_no), "https://api.met.no/doc/License")
        link(getString(R.string.about_open_meteo), "https://open-meteo.com/en/license")
    }
}
