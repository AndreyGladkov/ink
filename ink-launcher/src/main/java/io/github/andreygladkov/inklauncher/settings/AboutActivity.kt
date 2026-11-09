package io.github.andreygladkov.inklauncher.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.net.toUri
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkHeader
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.ink.ui.InkWindow
import io.github.andreygladkov.ink.ui.addSection
import io.github.andreygladkov.inklauncher.BuildConfig
import io.github.andreygladkov.inklauncher.R
import kotlin.math.roundToInt

class AboutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val license = intent.getStringExtra(EXTRA_LICENSE)
        val padding = dp(PADDING_DP)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, 0, padding, padding)
        }
        if (license != null) showLicense(body, license) else showAbout(body)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(InkHeader.of(this@AboutActivity, getString(R.string.about), ::finish))
            addView(ScrollView(this@AboutActivity).apply {
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(body)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        InkWindow.setContent(this, root)
    }

    private fun showAbout(body: LinearLayout) {
        body.addView(InkTextView(this).apply {
            setText(R.string.app_name)
            textScale = InkStyle.TITLE_SCALE
            isBold = true
        })
        body.addView(InkTextView(this).apply { text = getString(R.string.about_version, BuildConfig.VERSION_NAME) })
        listOf(R.string.about_license, R.string.about_privacy).forEach { text ->
            body.addView(InkTextView(this).apply {
                setText(text)
                setPadding(0, dp(GAP_DP), 0, 0)
            })
        }
        body.addView(button(getString(R.string.about_gpl)) { openLicense(GPL) })
        body.addView(button(getString(R.string.about_source)) { openSource() })
        body.addSection(getString(R.string.about_components))
        body.addView(button(getString(R.string.about_noto)) { openLicense(OFL) })
        body.addView(button(getString(R.string.about_material)) { openLicense(APACHE) })
    }

    private fun openSource() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, SOURCE_URL.toUri()))
        } catch (_: ActivityNotFoundException) {
        }
    }

    private fun showLicense(body: LinearLayout, asset: String) {
        val text = runCatching { assets.open("licenses/$asset").bufferedReader().use { it.readText() } }.getOrDefault("")
        body.addView(InkTextView(this).apply {
            this.text = text
            textScale = InkStyle.SECONDARY_SCALE
        })
    }

    private fun openLicense(asset: String) = startActivity(Intent(this, AboutActivity::class.java).putExtra(EXTRA_LICENSE, asset))

    private fun button(label: String, onClick: () -> Unit) = InkButton(this).apply {
        text = label
        textScale = InkStyle.SECONDARY_SCALE
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(GAP_DP) }
    }

    private fun dp(value: Float) = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val EXTRA_LICENSE = "license"
        private const val GPL = "GPL-3.0.txt"
        private const val OFL = "OFL-1.1.txt"
        private const val APACHE = "Apache-2.0.txt"
        private const val SOURCE_URL = "https://github.com/AndreyGladkov/ink"
        private const val PADDING_DP = 16f
        private const val GAP_DP = 8f

        fun intent(context: Context) = Intent(context, AboutActivity::class.java)
    }
}
