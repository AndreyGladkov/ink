package io.github.andreygladkov.inkweather.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import io.github.andreygladkov.ink.haptics.HapticChannel
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkChoiceRow
import io.github.andreygladkov.ink.ui.InkHeader
import io.github.andreygladkov.ink.ui.InkSettings
import io.github.andreygladkov.ink.ui.InkWindow
import io.github.andreygladkov.ink.ui.addSection
import io.github.andreygladkov.inkweather.R
import io.github.andreygladkov.inkweather.forecast.Source
import io.github.andreygladkov.inkweather.forecast.Weather
import io.github.andreygladkov.inkweather.widget.RefreshJob
import kotlin.math.roundToInt
import io.github.andreygladkov.ink.R as InkR

class SettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = dp(PADDING_DP)
            setPadding(padding, 0, padding, padding)
        }
        body.addSection(getString(R.string.settings_forecast))
        body.addView(InkChoiceRow(this, getString(R.string.settings_source), Source.entries.map { it to it.title }, Source.of(this), onChoose = ::choose))
        body.addSection(getString(InkR.string.settings_display))
        listOf(InkSettings::textSize, InkSettings::weight, InkSettings::lists, InkSettings::cornerRadius, InkSettings::colors).forEach { body.addView(it(this)) }
        InkSettings.addHaptics(this, body, listOf(HapticChannel.BUTTONS to getString(InkR.string.settings_haptic_buttons)))
        body.addSection(getString(R.string.about))
        body.addView(InkButton(this).apply {
            setText(R.string.about)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setOnClickListener { startActivity(AboutActivity.intent(this@SettingsActivity)) }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(GAP_DP) })
        InkWindow.setContent(this, LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(header())
            addView(ScrollView(this@SettingsActivity).apply {
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(body)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        })
    }

    /** Widgets show the new source's forecast at once instead of waiting up to an hour for the refresh job. */
    private fun choose(source: Source) {
        if (source == Source.of(this)) return
        Source.choose(this, source)
        val context = applicationContext
        Weather.fetches.execute { RefreshJob.refreshWidgets(context) }
    }

    private fun header() = InkHeader.of(this, getString(InkR.string.settings), ::finish)

    private fun dp(value: Float) = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val PADDING_DP = 16f
        private const val GAP_DP = 8f

        fun intent(context: Context) = Intent(context, SettingsActivity::class.java)
    }
}
