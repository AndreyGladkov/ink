package io.github.andreygladkov.inklauncher.ui

import android.text.TextUtils
import android.text.format.DateUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkPagedList
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.ink.ui.iconButton
import io.github.andreygladkov.inklauncher.HomeActivity
import io.github.andreygladkov.inklauncher.R
import io.github.andreygladkov.inklauncher.notify.InkNotification
import io.github.andreygladkov.inklauncher.notify.NotificationFeed
import io.github.andreygladkov.inklauncher.notify.NotificationGroups
import kotlin.math.roundToInt

class NotificationsScreen(private val activity: HomeActivity) : LinearLayout(activity), SideScreen {
    private sealed interface Row {
        data class App(val packageName: String) : Row
        data class Item(val notification: InkNotification) : Row
    }

    private val density = resources.displayMetrics.density
    private val padding = (PADDING_DP * density).roundToInt()
    private val list = InkPagedList(activity)
    private val clearAll = InkButton(activity).apply {
        setText(R.string.clear_all)
        textScale = InkStyle.SECONDARY_SCALE
        isBordered = false
        setOnClickListener { NotificationFeed.dismissAll() }
    }
    private val message = InkTextView(activity).apply { gravity = Gravity.CENTER }
    private val action = InkButton(activity).apply {
        setText(R.string.grant_notification_access)
        setOnClickListener { NotificationFeed.requestAccess(activity) }
    }
    private val state = LinearLayout(activity).apply {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(padding, padding, padding, padding)
        addView(message, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(action, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = padding
        })
    }
    private var rows: List<Row> = emptyList()

    init {
        orientation = VERTICAL
        addView(screenHeader(activity, activity.getString(R.string.notifications), null, clearAll))
        list.addFooterView(footerButton(activity, R.drawable.ic_chevron_right, R.string.home) { activity.showHome() })
        addView(FrameLayout(activity).apply {
            addView(list)
            addView(state)
        }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        refresh()
    }

    override fun refresh() {
        val granted = NotificationFeed.isAccessGranted(activity)
        val groups = NotificationGroups.of(NotificationFeed.notifications)
        val anchor = rows.getOrNull(list.firstPosition)
        rows = groups.flatMap { group -> listOf(Row.App(group.packageName)) + group.notifications.map { Row.Item(it) } }
        list.setAdapter(adapter(), rows.indexOf(anchor).coerceAtLeast(0))
        clearAll.visibility = if (rows.any { it is Row.Item && it.notification.isClearable }) VISIBLE else INVISIBLE
        state.visibility = if (granted && rows.isNotEmpty()) GONE else VISIBLE
        message.setText(if (granted) R.string.notifications_empty else R.string.notification_access_needed)
        action.visibility = if (granted) GONE else VISIBLE
    }

    private fun adapter() = object : InkPagedList.Adapter {
        override val count = rows.size
        override fun itemId(position: Int) = rows[position].hashCode().toLong()
        override fun createView(position: Int): View = when (val row = rows[position]) {
            is Row.App -> InkTextView(activity).apply {
                text = activity.catalog.appLabel(row.packageName)
                textScale = InkStyle.SECONDARY_SCALE
                isBold = true
                maxLines = 1
                setPadding(padding, padding, padding, padding / 2)
            }
            is Row.Item -> item(row.notification)
        }
    }

    private fun item(notification: InkNotification) = LinearLayout(activity).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(LinearLayout(activity).apply {
            orientation = VERTICAL
            isClickable = true
            setPadding(padding, padding / 2, 0, padding / 2)
            setOnClickListener { NotificationFeed.open(activity, notification) }
            if (notification.title.isNotEmpty()) addView(line(notification.title, bold = true, lines = 2))
            if (notification.text.isNotEmpty()) addView(line(notification.text, bold = false, lines = TEXT_LINES))
            addView(line(time(notification.postedAt), bold = false, lines = 1).apply { textScale = InkStyle.SECONDARY_SCALE })
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        if (notification.isClearable) {
            addView(iconButton(activity, R.drawable.ic_close, activity.getString(R.string.dismiss)).apply {
                setOnClickListener { NotificationFeed.dismiss(notification) }
            })
        }
    }

    private fun line(value: String, bold: Boolean, lines: Int) = InkTextView(activity).apply {
        text = value
        isBold = bold
        maxLines = lines
        ellipsize = TextUtils.TruncateAt.END
    }

    private fun time(at: Long): String {
        val flags = if (DateUtils.isToday(at)) DateUtils.FORMAT_SHOW_TIME
        else DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or DateUtils.FORMAT_SHOW_TIME
        return DateUtils.formatDateTime(activity, at, flags)
    }

    private companion object {
        const val PADDING_DP = 16f
        const val TEXT_LINES = 3
    }
}
