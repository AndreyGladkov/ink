package io.github.andreygladkov.inkcalculator

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.ui.InkHeader
import io.github.andreygladkov.ink.ui.InkWindow
import io.github.andreygladkov.inkcalculator.settings.SettingsActivity

class MainActivity : Activity() {
    private lateinit var style: InkStyle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        style = InkStyle.of(this)
        InkWindow.setContent(this, LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(LinearLayout(this@MainActivity).apply {
                gravity = Gravity.END
                addView(InkHeader.settings(this@MainActivity) { startActivity(SettingsActivity.intent(this@MainActivity)) })
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(CalculatorScreen(this@MainActivity), LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        })
    }

    override fun onResume() {
        super.onResume()
        if (InkStyle.of(this) != style) recreate()
    }
}
