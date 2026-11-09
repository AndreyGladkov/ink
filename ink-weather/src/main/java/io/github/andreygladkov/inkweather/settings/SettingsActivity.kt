package io.github.andreygladkov.inkweather.settings

import android.content.Context
import android.content.Intent
import io.github.andreygladkov.ink.haptics.HapticChannel
import io.github.andreygladkov.ink.ui.InkChoiceRow
import io.github.andreygladkov.ink.ui.InkPagedActivity
import io.github.andreygladkov.ink.ui.InkPagedItems
import io.github.andreygladkov.ink.ui.InkSettings
import io.github.andreygladkov.inkweather.R
import io.github.andreygladkov.inkweather.forecast.Source
import io.github.andreygladkov.inkweather.forecast.Weather
import io.github.andreygladkov.inkweather.widget.RefreshJob
import io.github.andreygladkov.ink.R as InkR

class SettingsActivity : InkPagedActivity() {
    override fun headerTitle(): String = getString(InkR.string.settings)

    override fun InkPagedItems.build() {
        val activity = this@SettingsActivity
        section(getString(R.string.settings_forecast))
        item { InkChoiceRow(activity, getString(R.string.settings_source), Source.entries.map { it to it.title }, Source.of(activity), onChoose = ::choose) }
        section(getString(InkR.string.settings_display))
        listOf(InkSettings::textSize, InkSettings::weight, InkSettings::lists, InkSettings::cornerRadius, InkSettings::colors).forEach { item { it(activity) } }
        InkSettings.addHaptics(activity, this, listOf(HapticChannel.BUTTONS to getString(InkR.string.settings_haptic_buttons)))
        InkSettings.addAbout(activity, this, AboutActivity::class.java)
    }

    /** Widgets show the new source's forecast at once instead of waiting up to an hour for the refresh job. */
    private fun choose(source: Source) {
        if (source == Source.of(this)) return
        Source.choose(this, source)
        val context = applicationContext
        Weather.fetches.execute { RefreshJob.refreshWidgets(context) }
    }

    companion object {
        fun intent(context: Context) = Intent(context, SettingsActivity::class.java)
    }
}
