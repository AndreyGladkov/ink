package io.github.andreygladkov.inklauncher.ui

import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.search.SearchQuery
import io.github.andreygladkov.ink.ui.InkHeader
import io.github.andreygladkov.ink.ui.InkPagedList
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.inklauncher.HomeActivity
import io.github.andreygladkov.inklauncher.R
import io.github.andreygladkov.inklauncher.home.ScreenTimeTile
import io.github.andreygladkov.inklauncher.home.Spot
import io.github.andreygladkov.inklauncher.widgets.InkWidgetHost
import java.text.Collator
import kotlin.math.roundToInt

class WidgetPickerScreen(private val activity: HomeActivity, private val target: Spot) : LinearLayout(activity), SideScreen {
    private class Choice(val key: String, val label: String, val app: String, val columns: Int, val rows: Int, val onPick: () -> Unit)

    private val padding = (PADDING_DP * resources.displayMetrics.density).roundToInt()
    private val list = InkPagedList(activity)
    private val search = SearchHeader(activity, activity.getString(R.string.pick_widget), R.string.search_widgets,
        InkHeader.back(activity) { activity.showHome() }) { show() }
    private val empty = nothingFound(activity)
    private val all: List<Choice>
    private var choices: List<Choice> = emptyList()

    init {
        orientation = VERTICAL
        addView(search)
        addView(FrameLayout(activity).apply {
            addView(list)
            addView(empty, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        val collator = Collator.getInstance(resources.configuration.locales[0])
        val (width, height) = activity.home.cellSize()
        val columns = activity.layout.spec.columns
        val rows = activity.layout.spec.rowsOf(target.sheet)
        val own = Choice(SCREEN_TIME, activity.getString(R.string.screen_time), activity.getString(R.string.app_name),
            ScreenTimeTile.COLUMNS.coerceAtMost(columns), ScreenTimeTile.ROWS) { activity.addScreenTime(target) }
        all = listOf(own) + activity.widgets.providers()
            .map { info ->
                Choice(info.provider.flattenToString(), info.loadLabel(activity.packageManager).orEmpty(), activity.catalog.appLabel(info.provider.packageName),
                    InkWidgetHost.span(info.minWidth, width, columns), InkWidgetHost.span(info.minHeight, height, rows)) { activity.addWidget(info, target) }
            }
            .sortedWith(compareBy<Choice, String>(collator) { it.app }.thenBy(collator) { it.label })
        show()
    }

    val isSearching: Boolean get() = search.isSearching

    override fun onBack(): Boolean = search.close()

    private fun show() {
        val query = search.query
        choices = if (query.isEmpty) all else all.filter { query.matches(SearchQuery.tokens(it.label) + SearchQuery.tokens(it.app)) }
        list.setAdapter(adapter())
        empty.visibility = if (choices.isEmpty()) VISIBLE else GONE
    }

    private fun adapter() = object : InkPagedList.Adapter {
        override val count = choices.size
        override fun itemId(position: Int) = choices[position].key.hashCode().toLong()
        override fun createView(position: Int): View = LinearLayout(activity).apply {
            val choice = choices[position]
            orientation = VERTICAL
            isClickable = true
            setPadding(padding, padding / 2, padding, padding / 2)
            setOnClickListener {
                search.hideKeyboard()
                choice.onPick()
            }
            addView(InkTextView(activity).apply {
                text = choice.label
                isBold = true
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            })
            addView(InkTextView(activity).apply {
                text = activity.getString(R.string.widget_line, choice.app, choice.columns, choice.rows)
                textScale = InkStyle.SECONDARY_SCALE
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
        }
    }

    private companion object {
        const val PADDING_DP = 16f
        const val SCREEN_TIME = "screen_time"
    }
}
