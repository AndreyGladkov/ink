package io.github.andreygladkov.inklauncher.ui

import android.text.TextUtils
import android.text.format.DateUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkHeader
import io.github.andreygladkov.ink.ui.InkPagedList
import io.github.andreygladkov.ink.ui.InkSegments
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.inklauncher.HomeActivity
import io.github.andreygladkov.inklauncher.R
import io.github.andreygladkov.inklauncher.screentime.AppCategories
import io.github.andreygladkov.inklauncher.screentime.AppCategory
import io.github.andreygladkov.inklauncher.screentime.DayUsage
import io.github.andreygladkov.inklauncher.screentime.ScreenTimeReport
import io.github.andreygladkov.inklauncher.screentime.ScreenTimeText
import io.github.andreygladkov.inklauncher.screentime.UsagePeriod
import java.text.Collator
import kotlin.math.roundToInt

class ScreenTimeScreen(private val activity: HomeActivity) : LinearLayout(activity), SideScreen {
    private enum class Tab { DAYS, APPS }

    private class AppUsage(val packageName: String, val label: String, val millis: Long, val category: AppCategory)

    private val labels = HashMap<String, String>()
    private val padding = (PADDING_DP * resources.displayMetrics.density).roundToInt()
    private val list = InkPagedList(activity)
    private val message = InkTextView(activity).apply {
        setText(R.string.screen_time_access_needed)
        gravity = Gravity.CENTER
    }
    private val access = LinearLayout(activity).apply {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(padding, padding, padding, padding)
        addView(message, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(InkButton(activity).apply {
            setText(R.string.grant_screen_time_access)
            setOnClickListener { activity.screenTime.requestAccess() }
        }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = padding
        })
    }
    private var tab = Tab.DAYS
    private var period: UsagePeriod? = null
    private var days: List<DayUsage> = emptyList()
    private var apps: List<AppUsage> = emptyList()

    init {
        orientation = VERTICAL
        addView(InkHeader.of(activity, activity.getString(R.string.screen_time), { activity.showHome() }))
        addView(InkSegments(activity, listOf(R.string.by_day, R.string.by_app)) { index ->
            tab = Tab.entries[index]
            show(keepPage = false)
        })
        addView(FrameLayout(activity).apply {
            addView(list)
            addView(access)
        }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        reload()
    }

    fun reload() {
        period = activity.screenTime.period(DAYS)
        refresh()
    }

    override fun refresh() {
        val categories = activity.screenTime.categories()
        val report = period?.report(categories)
        days = report?.days.orEmpty()
        apps = report?.let { appsOf(it, categories) }.orEmpty()
        access.visibility = if (report == null) VISIBLE else GONE
        show(keepPage = true)
    }

    private fun appsOf(report: ScreenTimeReport, categories: AppCategories): List<AppUsage> {
        val installed = activity.catalog.apps().map { it.key.packageName }.toSet()
        val shown = report.byApp.filterValues { it >= ScreenTimeText.MINUTE_MS }.keys + categories.chosenApps.filter { it in installed }
        val collator = Collator.getInstance(resources.configuration.locales[0])
        return shown.map { AppUsage(it, label(it), report.byApp[it] ?: 0, categories[it]) }
            .sortedWith(compareByDescending<AppUsage> { it.millis / ScreenTimeText.MINUTE_MS }.thenBy(collator) { it.label })
    }

    private fun label(packageName: String) = labels.getOrPut(packageName) { activity.catalog.appLabel(packageName) }

    private fun show(keepPage: Boolean) {
        list.setAdapter(if (tab == Tab.DAYS) daysAdapter() else appsAdapter(), if (keepPage) list.firstPosition else 0)
    }

    private fun daysAdapter() = object : InkPagedList.Adapter {
        override val count = days.size
        override fun itemId(position: Int) = days[position].day.start
        override fun createView(position: Int): View = days[position].let { usage ->
            column(dayName(position, usage.day.start), listOf(
                "${activity.getString(R.string.drifting)} ${duration(usage.drifting)} · ${activity.getString(R.string.on_purpose)} ${duration(usage.onPurpose)}",
                ScreenTimeText.unlocks(activity, usage.unlocks),
            ).joinToString("\n"))
        }
    }

    private fun appsAdapter() = object : InkPagedList.Adapter {
        override val count = apps.size
        override fun itemId(position: Int) = apps[position].packageName.hashCode().toLong()
        override fun createView(position: Int): View = apps[position].let { app ->
            LinearLayout(activity).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(CategoryMark(activity, app.category), LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginStart = padding })
                addView(column(app.label, appDetail(app)), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
                isClickable = true
                contentDescription = "${app.label}, ${duration(app.millis)}, ${ScreenTimeText.category(activity, app.category)}"
                setOnClickListener { activity.menus.appCategory(app.packageName, app.label) }
            }
        }
    }

    private fun appDetail(app: AppUsage): String = when (app.category) {
        AppCategory.UNCOUNTED -> "${duration(app.millis)} · ${ScreenTimeText.category(activity, app.category)}"
        AppCategory.DRIFTING, AppCategory.ON_PURPOSE -> duration(app.millis)
    }

    private fun column(title: String, detail: String) = LinearLayout(activity).apply {
        orientation = VERTICAL
        setPadding(padding, padding / 2, padding, padding / 2)
        addView(InkTextView(activity).apply {
            text = title
            isBold = true
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        addView(InkTextView(activity).apply {
            text = detail
            textScale = InkStyle.SECONDARY_SCALE
        })
    }

    private fun dayName(daysBack: Int, start: Long): String = when (daysBack) {
        0 -> activity.getString(R.string.today)
        1 -> activity.getString(R.string.yesterday)
        else -> DateUtils.formatDateTime(activity, start,
            DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_YEAR or DateUtils.FORMAT_ABBREV_ALL)
    }

    private fun duration(millis: Long) = ScreenTimeText.duration(activity, millis)

    private companion object {
        const val DAYS = 7
        const val PADDING_DP = 16f
    }
}
