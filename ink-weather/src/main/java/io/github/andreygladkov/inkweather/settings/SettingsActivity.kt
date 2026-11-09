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
import io.github.andreygladkov.ink.ui.InkHeader
import io.github.andreygladkov.ink.ui.InkSettings
import io.github.andreygladkov.ink.ui.InkWindow
import io.github.andreygladkov.ink.ui.addSection
import io.github.andreygladkov.inkweather.R
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

    private fun header() = InkHeader.of(this, getString(InkR.string.settings), ::finish)

    private fun dp(value: Float) = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val PADDING_DP = 16f
        private const val GAP_DP = 8f

        fun intent(context: Context) = Intent(context, SettingsActivity::class.java)
    }
}
