package io.github.andreygladkov.inktasks.task

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.icu.util.Calendar
import android.icu.util.ULocale
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkHeader
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.ink.ui.InkWindow
import io.github.andreygladkov.ink.ui.iconButton
import io.github.andreygladkov.inktasks.R
import io.github.andreygladkov.inktasks.store.TaskLists
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlin.math.roundToInt

/** A month at a time with arrows instead of a scrolling picker, which would leave a trail of redraws on e-ink. */
class DateActivity : Activity() {
    private val today = TaskLists.today()
    private val locale get() = resources.configuration.locales[0]

    /** A language set without a country ("ru") would start weeks on Sunday; its likely country decides instead. */
    private val firstDayOfWeek: DayOfWeek
        get() = DayOfWeek.SUNDAY.plus(Calendar.getInstance(ULocale.addLikelySubtags(ULocale.forLocale(locale))).firstDayOfWeek - 1L)
    private var selected: LocalDate? = null
    private lateinit var month: YearMonth
    private lateinit var monthLabel: InkTextView
    private val days = mutableListOf<InkButton>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selected = chosen(intent)
        month = YearMonth.from(selected ?: today)
        InkWindow.setContent(this, LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(InkHeader.of(this@DateActivity, getString(R.string.due), ::finish))
            addView(monthRow(), wide())
            addView(weekdayRow(), wide())
            repeat(WEEKS) { addView(weekRow(), wide()) }
        })
        showMonth()
    }

    private fun monthRow() = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(iconButton(this@DateActivity, R.drawable.ic_chevron_left, getString(R.string.prev_month)).apply { setOnClickListener { step(-1) } })
        monthLabel = InkTextView(this@DateActivity).apply {
            gravity = Gravity.CENTER
            isBold = true
        }
        addView(monthLabel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(iconButton(this@DateActivity, R.drawable.ic_chevron_right, getString(R.string.next_month)).apply { setOnClickListener { step(1) } })
    }

    private fun weekdayRow() = LinearLayout(this).apply {
        val first = firstDayOfWeek
        repeat(DAYS_IN_WEEK) { index ->
            addView(InkTextView(this@DateActivity).apply {
                text = first.plus(index.toLong()).getDisplayName(TextStyle.SHORT_STANDALONE, locale)
                textScale = InkStyle.SECONDARY_SCALE
                gravity = Gravity.CENTER
                setPadding(0, dp(GAP_DP), 0, dp(GAP_DP))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun weekRow() = LinearLayout(this).apply {
        repeat(DAYS_IN_WEEK) {
            val day = InkButton(this@DateActivity).apply {
                isBordered = false
                setPadding(0, paddingTop, 0, paddingBottom)
            }
            days += day
            addView(day, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    private fun step(months: Long) {
        month = month.plusMonths(months)
        showMonth()
    }

    private fun showMonth() {
        monthLabel.text = month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale)).replaceFirstChar { it.titlecase(locale) }
        val first = firstDayOfWeek
        val start = month.atDay(1).let { it.minusDays(((it.dayOfWeek.value - first.value + DAYS_IN_WEEK) % DAYS_IN_WEEK).toLong()) }
        days.forEachIndexed { index, button ->
            val date = start.plusDays(index.toLong())
            val inMonth = YearMonth.from(date) == month
            button.visibility = if (inMonth) View.VISIBLE else View.INVISIBLE
            button.text = String.format(locale, "%d", date.dayOfMonth)
            button.isSelected = date == selected
            button.isBold = date == today
            button.contentDescription = date.toString()
            button.setOnClickListener { choose(date) }
        }
    }

    private fun choose(date: LocalDate) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_DATE, date.toEpochDay()))
        finish()
    }

    private fun wide() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

    private fun dp(value: Float) = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val EXTRA_DATE = "date"
        private const val WEEKS = 6
        private const val DAYS_IN_WEEK = 7
        private const val GAP_DP = 8f

        fun intent(context: Context, date: LocalDate?): Intent =
            Intent(context, DateActivity::class.java).apply { date?.let { putExtra(EXTRA_DATE, it.toEpochDay()) } }

        fun chosen(data: Intent?): LocalDate? = data?.takeIf { it.hasExtra(EXTRA_DATE) }?.let { LocalDate.ofEpochDay(it.getLongExtra(EXTRA_DATE, 0)) }
    }
}
