package io.github.andreygladkov.inktasks.task

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.widget.LinearLayout
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkHeader
import io.github.andreygladkov.ink.ui.InkWindow
import io.github.andreygladkov.ink.ui.TimeInput
import io.github.andreygladkov.inktasks.R
import java.time.LocalTime
import kotlin.math.roundToInt

class TimeActivity : Activity() {
    private lateinit var input: TimeInput

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        input = TimeInput(this, chosen(intent) ?: DEFAULT_TIME, DateFormat.is24HourFormat(this))
        val ready = InkButton(this).apply {
            setText(R.string.ready)
            setOnClickListener { choose() }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { marginEnd = dp(PADDING_DP) }
        }
        InkWindow.setContent(this, LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(InkHeader.of(this@TimeActivity, getString(R.string.reminder), ::finish, ready))
            val padding = dp(PADDING_DP)
            input.setPadding(padding, 0, padding, padding)
            addView(input,LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        })
    }

    private fun choose() {
        val time = input.time() ?: return
        setResult(RESULT_OK, Intent().putExtra(EXTRA_TIME, time.toSecondOfDay()))
        finish()
    }

    private fun dp(value: Float) = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val EXTRA_TIME = "time"
        private const val PADDING_DP = 16f
        private val DEFAULT_TIME = LocalTime.of(9, 0)

        fun intent(context: Context, time: LocalTime?): Intent =
            Intent(context, TimeActivity::class.java).apply { time?.let { putExtra(EXTRA_TIME, it.toSecondOfDay()) } }

        fun chosen(data: Intent?): LocalTime? = data?.takeIf { it.hasExtra(EXTRA_TIME) }?.let { LocalTime.ofSecondOfDay(it.getIntExtra(EXTRA_TIME, 0).toLong()) }
    }
}
