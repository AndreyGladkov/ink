package io.github.andreygladkov.ink.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.InsetDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.LinearLayout
import io.github.andreygladkov.ink.R
import io.github.andreygladkov.ink.eink.A9Panel
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.inkPrefs
import kotlin.math.abs
import kotlin.math.roundToInt

/** With [anchorEnd] the list opens on its last, full page and the up button leads to earlier items, like a chat. */
class InkPagedList(
    context: Context,
    private val anchorEnd: Boolean = false,
    private val dividers: Boolean = true,
) : LinearLayout(context) {
    interface Adapter {
        val count: Int
        fun itemId(position: Int): Long
        fun createView(position: Int): View
    }

    var onPageShown: ((firstPosition: Int) -> Unit)? = null

    private val dividerInsetPx = (InkButton.PADDING_DP * resources.displayMetrics.density).roundToInt()
    private val scrolls = context.inkPrefs().getBoolean(PREF_SCROLL, false)
    private val scrollList: ListView? = if (scrolls) scrollList(context) else null

    private val dividerPx = if (dividers) DottedDrawable(context).intrinsicHeight else 0
    private val body = LinearLayout(context).apply { orientation = HORIZONTAL }
    private val pageView = PageView(context)
    private val towardsTop = if (anchorEnd) 1 else -1
    private val prev = pagerButton(R.drawable.ic_page_up, R.string.page_prev) { flip(page + towardsTop) }
    private val next = pagerButton(R.drawable.ic_page_down, R.string.page_next) { flip(page - towardsTop) }
    private val label = InkTextView(context).apply {
        gravity = Gravity.CENTER
        textScale = InkStyle.SECONDARY_SCALE
    }
    private val heights = HashMap<Long, Int>()
    private var heightsWidth = -1
    private var adapter: Adapter? = null
    private var breaker: PageLayout? = null
    private var pendingPosition = 0
    private val measureRest = object : Runnable {
        override fun run() {
            val current = breaker ?: return
            current.measure(BACKGROUND_CHUNK)
            // A pause between small chunks keeps touches and frames from queueing behind the measuring.
            if (current.isComplete) updateFooter() else postDelayed(this, BACKGROUND_PAUSE_MS)
        }
    }

    var page = 0
        private set

    val pageCount: Int
        get() {
            val current = breaker ?: return 1
            if (!current.isComplete) {
                current.measure(Int.MAX_VALUE)
                updateFooter()
            }
            return current.knownPages
        }

    val firstPosition get() = scrollList?.firstVisiblePosition ?: breaker?.start(page) ?: 0

    init {
        orientation = VERTICAL
        body.addView(scrollList ?: pageView, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        if (scrollList == null) addPager()
    }

    fun setAside(view: View, params: LayoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)) {
        body.addView(view, params)
    }

    private fun addPager() {
        addView(LinearLayout(context).apply {
            orientation = HORIZONTAL
            addView(prev, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
            addView(label, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
            addView(next, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun setAdapter(adapter: Adapter, position: Int = 0) {
        this.adapter = adapter
        scrollList?.let { list ->
            list.adapter = ScrollAdapter(adapter)
            list.setSelection(position)
            onPageShown?.invoke(position)
            return
        }
        heights.clear()
        pendingPosition = position
        if (repaginate()) showPageOf(pendingPosition)
    }

    fun showPageOf(position: Int) {
        scrollList?.let { return it.setSelection(position) }
        val current = breaker ?: return
        showPage(current.pageOf(position))
    }

    fun showPageStartingAt(position: Int) {
        scrollList?.let { return it.setSelection(position) }
        if (repaginate(forcedStart = position)) showPageOf(position)
    }

    private fun flip(index: Int) {
        val before = page
        showPage(index)
        if (page != before) A9Panel.onPageFlipped(context)
    }

    fun showPage(index: Int) {
        val adapter = adapter ?: return
        val current = breaker ?: return
        current.end(index.coerceAtLeast(0))
        page = index.coerceIn(0, current.knownPages - 1)
        val start = current.start(page)
        pendingPosition = start
        pageView.removeAllViews()
        for (position in start until current.end(page)) {
            pageView.addView(adapter.createView(position), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        updateFooter()
        onPageShown?.invoke(start)
        removeCallbacks(measureRest)
        if (!current.isComplete) postDelayed(measureRest, BACKGROUND_PAUSE_MS)
    }

    private fun updateFooter() {
        val current = breaker ?: return
        val count = adapter?.count ?: 0
        val hasFirst = page > 0
        val hasLast = page < current.knownPages - 1
        prev.visibility = if (if (anchorEnd) hasLast else hasFirst) VISIBLE else INVISIBLE
        next.visibility = if (if (anchorEnd) hasFirst else hasLast) VISIBLE else INVISIBLE
        label.text = when {
            count == 0 -> ""
            anchorEnd && !current.isComplete -> ""
            anchorEnd -> context.getString(R.string.page_of, current.knownPages - page, current.knownPages)
            current.isComplete -> context.getString(R.string.page_of, page + 1, current.knownPages)
            else -> context.getString(R.string.page_of_unknown, page + 1)
        }
    }

    private fun repaginate(forcedStart: Int? = null): Boolean {
        val adapter = adapter ?: return false
        val width = pageView.width
        val height = pageView.height
        if (width == 0 || height == 0) return false
        if (width != heightsWidth) {
            heights.clear()
            heightsWidth = width
        }
        val heightOf = { position: Int ->
            heights.getOrPut(adapter.itemId(position)) { measureHeight(adapter.createView(position), width) }
        }
        breaker = if (anchorEnd) EndAnchoredPageBreaker(adapter.count, height, dividerPx, heightOf)
        else PageBreaker(adapter.count, height, dividerPx, forcedStart, heightOf)
        return true
    }

    private fun measureHeight(view: View, width: Int): Int {
        view.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        return view.measuredHeight
    }

    private fun pagerButton(icon: Int, description: Int, onClick: () -> Unit) = InkButton(context).apply {
        this.icon = icon
        contentDescription = context.getString(description)
        isBordered = false
        visibility = INVISIBLE
        setOnClickListener { onClick() }
    }

    private fun scrollList(context: Context) = ListView(context).apply {
        divider = if (dividers) divider(context) else null
        dividerHeight = dividerPx
        selector = ColorDrawable(Color.TRANSPARENT)
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        isStackFromBottom = anchorEnd
        setOnScrollListener(object : AbsListView.OnScrollListener {
            private var lastFirst = -1

            override fun onScrollStateChanged(view: AbsListView, scrollState: Int) = Unit

            override fun onScroll(view: AbsListView, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {
                if (firstVisibleItem == lastFirst) return
                lastFirst = firstVisibleItem
                onPageShown?.invoke(firstVisibleItem)
            }
        })
    }

    private fun divider(context: Context) = InsetDrawable(DottedDrawable(context), dividerInsetPx, 0, dividerInsetPx, 0)

    private class ScrollAdapter(private val items: Adapter) : BaseAdapter() {
        override fun getCount() = items.count
        override fun getItem(position: Int): Any = position
        override fun getItemId(position: Int) = items.itemId(position)
        override fun hasStableIds() = true
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View = items.createView(position)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(measureRest)
    }

    private inner class PageView(context: Context) : LinearLayout(context) {
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop * 2
        private var downX = 0f
        private var downY = 0f
        private var swiping = false

        init {
            orientation = VERTICAL
            if (anchorEnd) gravity = Gravity.BOTTOM
            if (dividers) {
                showDividers = SHOW_DIVIDER_MIDDLE
                dividerDrawable = divider(context)
            }
        }

        override fun shouldDelayChildPressedState() = true

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            post { if (repaginate()) showPageOf(pendingPosition) }
        }

        override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
            track(event)
            return swiping
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            track(event)
            if (event.actionMasked == MotionEvent.ACTION_UP && swiping) flip(if (event.y < downY) page - towardsTop else page + towardsTop)
            return true
        }

        private fun track(event: MotionEvent) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    swiping = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.y - downY
                    if (abs(dy) > touchSlop && abs(dy) > abs(event.x - downX)) swiping = true
                }
            }
        }
    }

    companion object {
        const val PREF_SCROLL = "lists_scroll"
        const val ROW_PADDING_DP = 8f
        private const val BACKGROUND_CHUNK = 3
        private const val BACKGROUND_PAUSE_MS = 16L
    }
}
