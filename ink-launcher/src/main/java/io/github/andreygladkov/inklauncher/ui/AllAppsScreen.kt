package io.github.andreygladkov.inklauncher.ui

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import io.github.andreygladkov.ink.search.SearchQuery
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.ui.InkPagedList
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.ink.ui.LetterIndexView
import io.github.andreygladkov.inklauncher.HomeActivity
import io.github.andreygladkov.inklauncher.R
import io.github.andreygladkov.inklauncher.apps.LauncherApp
import io.github.andreygladkov.inklauncher.home.Spot

/** Every launchable app by name, searchable. With a [target] cell a tap puts the app there instead of opening it. */
class AllAppsScreen(private val activity: HomeActivity, private val target: Spot?) : LinearLayout(activity), SideScreen {
    private val list = InkPagedList(activity)
    private val index = LetterIndexView(activity) { letter -> jumpTo(letter) }
    private val search = SearchHeader(activity, activity.getString(if (target == null) R.string.all_apps else R.string.pick_app),
        R.string.search_apps, null) { show(keepPage = false) }
    private val empty = nothingFound(activity)
    private val style = InkStyle.of(activity)
    private var installed: List<LauncherApp> = emptyList()
    private var apps: List<LauncherApp> = emptyList()

    init {
        orientation = VERTICAL
        addView(search)
        list.addFooterView(footerButton(activity, R.drawable.ic_chevron_left, R.string.home) { activity.showHome() }, atStart = true)
        list.setAside(index)
        list.onPageShown = { first -> index.current = apps.getOrNull(first)?.let(::letter) }
        addView(FrameLayout(activity).apply {
            addView(list)
            addView(empty, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        refresh()
    }

    val isSearching: Boolean get() = search.isSearching

    override fun refresh() {
        installed = activity.catalog.apps()
        show(keepPage = true)
    }

    override fun onBack(): Boolean = search.close()

    private fun show(keepPage: Boolean) {
        val anchor = apps.getOrNull(list.firstPosition)?.key
        val query = search.query
        apps = if (query.isEmpty) installed else installed.filter { query.matches(SearchQuery.tokens(it.label)) }
        index.letters = apps.map(::letter).distinct()
        list.setAdapter(adapter(), if (keepPage) apps.indexOfFirst { it.key == anchor }.coerceAtLeast(0) else 0)
        empty.visibility = if (apps.isEmpty()) VISIBLE else GONE
    }

    private fun jumpTo(letter: String) {
        val position = apps.indexOfFirst { letter(it) == letter }
        if (position >= 0) list.showPageStartingAt(position)
    }

    private fun letter(app: LauncherApp) = LetterIndexView.letterOf(app.label)

    private fun adapter() = object : InkPagedList.Adapter {
        override val count = apps.size
        override fun itemId(position: Int) = apps[position].key.hashCode().toLong()
        override fun createView(position: Int): View = apps[position].let { app ->
            AppRow(activity, app.label) { size -> activity.icons.app(app.key, null, size, style, app.label) }
        }.apply {
            val app = apps[position]
            setOnClickListener {
                search.hideKeyboard()
                if (target == null) activity.launch(app.key) else activity.placeApp(app.key, target)
            }
            setOnLongClickListener {
                if (target == null) activity.menus.listedApp(app)
                target == null
            }
        }
    }
}

fun nothingFound(activity: HomeActivity) = InkTextView(activity).apply {
    setText(R.string.nothing_found)
    gravity = Gravity.CENTER
    visibility = View.GONE
}
