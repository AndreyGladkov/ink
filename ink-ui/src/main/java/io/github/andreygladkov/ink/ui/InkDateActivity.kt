package io.github.andreygladkov.ink.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import io.github.andreygladkov.ink.R
import java.time.LocalDate
import java.time.YearMonth

/** A month at a time with arrows instead of a scrolling picker, which would leave a trail of redraws on e-ink. */
class InkDateActivity : Activity() {
    private val locale get() = resources.configuration.locales[0]
    private lateinit var today: LocalDate
    private var selected: LocalDate? = null
    private lateinit var month: YearMonth
    private lateinit var monthLabel: InkTextView
    private lateinit var grid: MonthGrid

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        today = LocalDate.ofEpochDay(intent.getLongExtra(EXTRA_TODAY, LocalDate.now().toEpochDay()))
        selected = chosen(intent)
        month = YearMonth.from(selected ?: today)
        grid = MonthGrid(this).apply { onDay = ::choose }
        InkWindow.setContent(this, LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(InkHeader.of(this@InkDateActivity, intent.getStringExtra(EXTRA_TITLE).orEmpty(), ::finish))
            addView(monthRow(), wide())
            addView(grid, wide())
        })
        showMonth()
    }

    private fun monthRow() = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(iconButton(this@InkDateActivity, R.drawable.ic_chevron_left, getString(R.string.prev_month)).apply { setOnClickListener { step(-1) } })
        monthLabel = InkTextView(this@InkDateActivity).apply {
            gravity = Gravity.CENTER
            isBold = true
        }
        addView(monthLabel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(iconButton(this@InkDateActivity, R.drawable.ic_chevron_right, getString(R.string.next_month)).apply { setOnClickListener { step(1) } })
    }

    private fun step(months: Long) {
        month = month.plusMonths(months)
        showMonth()
    }

    private fun showMonth() {
        monthLabel.text = MonthGrid.title(month, locale)
        grid.show(month, today, selected)
    }

    private fun choose(date: LocalDate) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_DATE, date.toEpochDay()))
        finish()
    }

    private fun wide() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

    companion object {
        private const val EXTRA_DATE = "date"
        private const val EXTRA_TODAY = "today"
        private const val EXTRA_TITLE = "title"

        fun intent(context: Context, title: String, date: LocalDate?, today: LocalDate): Intent =
            Intent(context, InkDateActivity::class.java)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_TODAY, today.toEpochDay())
                .apply { date?.let { putExtra(EXTRA_DATE, it.toEpochDay()) } }

        fun chosen(data: Intent?): LocalDate? = data?.takeIf { it.hasExtra(EXTRA_DATE) }?.let { LocalDate.ofEpochDay(it.getLongExtra(EXTRA_DATE, 0)) }
    }
}
