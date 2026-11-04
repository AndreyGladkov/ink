package io.github.andreygladkov.inkmessages.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.content.edit
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.inkPrefs
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.ink.ui.InkWindow
import io.github.andreygladkov.ink.ui.addSection
import io.github.andreygladkov.ink.ui.iconButton
import io.github.andreygladkov.inkmessages.Category
import io.github.andreygladkov.inkmessages.R
import io.github.andreygladkov.inkmessages.StartCategory
import kotlin.math.roundToInt

class SettingsActivity : Activity() {
    private val density by lazy { resources.displayMetrics.density }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = dp(PADDING_DP)
            setPadding(padding, 0, padding, padding)
        }
        body.addSection(getString(R.string.settings_start_category))
        addStartChoices(body)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(header())
            addView(ScrollView(this@SettingsActivity).apply { addView(body) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        InkWindow.setContent(this, root)
    }

    private fun addStartChoices(body: LinearLayout) {
        val prefs = inkPrefs()
        val options = Category.entries.map { it.name to getString(it.title) } + (StartCategory.LAST_OPENED to getString(R.string.start_last))
        val current = prefs.getString(StartCategory.PREF, Category.ALL.name)
        val buttons = options.map { (value, label) ->
            InkButton(this).apply {
                text = label
                isSelected = value == current
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
            } to value
        }
        buttons.forEach { (button, value) ->
            button.setOnClickListener {
                prefs.edit { putString(StartCategory.PREF, value) }
                buttons.forEach { (other, otherValue) -> other.isSelected = otherValue == value }
            }
            body.addView(button, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(CHOICE_GAP_DP)
            })
        }
    }

    private fun header() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(iconButton(this@SettingsActivity, R.drawable.ic_arrow_back, getString(R.string.back)).apply { setOnClickListener { finish() } })
        addView(InkTextView(this@SettingsActivity).apply {
            setText(R.string.settings)
            textScale = InkStyle.TITLE_SCALE
            isBold = true
        })
    }

    private fun dp(value: Float) = (value * density).roundToInt()

    companion object {
        private const val PADDING_DP = 16f
        private const val CHOICE_GAP_DP = 8f

        fun intent(context: Context) = Intent(context, SettingsActivity::class.java)
    }
}
