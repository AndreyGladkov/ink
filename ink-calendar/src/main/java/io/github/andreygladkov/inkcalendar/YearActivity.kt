package io.github.andreygladkov.inkcalendar

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import io.github.andreygladkov.ink.ui.InkHeader
import io.github.andreygladkov.ink.ui.InkWindow
import io.github.andreygladkov.inkcalendar.events.CalendarClock
import io.github.andreygladkov.inkcalendar.ui.MiniMonthView
import io.github.andreygladkov.inkcalendar.ui.SwipeFrame
import java.time.YearMonth
import io.github.andreygladkov.ink.R as InkR

class YearActivity : Activity() {
    private var year = 0
    private lateinit var header: LinearLayout
    private lateinit var months: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        year = savedInstanceState?.getInt(EXTRA_YEAR) ?: intent.getIntExtra(EXTRA_YEAR, CalendarClock.today().year)
        months = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        header = InkHeader.of(this, InkHeader.title(this, ""), InkHeader.back(this, ::finish),
            InkHeader.button(this, InkR.drawable.ic_chevron_left, getString(R.string.prev_year)) { step(-1) },
            InkHeader.button(this, InkR.drawable.ic_chevron_right, getString(R.string.next_year)) { step(1) })
        InkWindow.setContent(this, LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(header)
            addView(SwipeFrame(this@YearActivity, ::step).apply { addView(months) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        })
        show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(EXTRA_YEAR, year)
    }

    private fun step(years: Int) {
        year += years
        show()
    }

    private fun show() {
        InkHeader.setTitle(header, year.toString())
        val today = CalendarClock.today()
        months.removeAllViews()
        (1..MONTHS).chunked(COLUMNS).forEach { row ->
            months.addView(LinearLayout(this).apply {
                row.forEach { number ->
                    addView(MiniMonthView(this@YearActivity, YearMonth.of(year, number), today).apply {
                        setOnClickListener { choose(month) }
                    }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
                }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    private fun choose(month: YearMonth) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_MONTH, month.toString()))
        finish()
    }

    companion object {
        private const val EXTRA_YEAR = "year"
        private const val EXTRA_MONTH = "month"
        private const val MONTHS = 12
        private const val COLUMNS = 3

        fun intent(context: Context, year: Int): Intent = Intent(context, YearActivity::class.java).putExtra(EXTRA_YEAR, year)

        fun chosen(data: Intent?): YearMonth? = data?.getStringExtra(EXTRA_MONTH)?.let(YearMonth::parse)
    }
}
