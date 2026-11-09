package io.github.andreygladkov.inkcalculator.settings

import android.content.Context
import android.content.Intent
import io.github.andreygladkov.ink.haptics.HapticChannel
import io.github.andreygladkov.ink.ui.InkPagedActivity
import io.github.andreygladkov.ink.ui.InkPagedItems
import io.github.andreygladkov.ink.ui.InkSettings
import io.github.andreygladkov.inkcalculator.R
import io.github.andreygladkov.ink.R as InkR

class SettingsActivity : InkPagedActivity() {
    override fun headerTitle(): String = getString(R.string.settings)

    override fun InkPagedItems.build() {
        val activity = this@SettingsActivity
        section(getString(InkR.string.settings_display))
        listOf(InkSettings::textSize, InkSettings::weight, InkSettings::cornerRadius, InkSettings::colors).forEach { item { it(activity) } }
        InkSettings.addHaptics(activity, this, listOf(
            HapticChannel.KEYS to getString(R.string.settings_haptic_keys),
            HapticChannel.BUTTONS to getString(InkR.string.settings_haptic_buttons),
        ))
        InkSettings.addAbout(activity, this, AboutActivity::class.java)
    }

    companion object {
        fun intent(context: Context) = Intent(context, SettingsActivity::class.java)
    }
}
