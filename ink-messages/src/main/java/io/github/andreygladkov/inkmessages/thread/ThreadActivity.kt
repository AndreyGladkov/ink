package io.github.andreygladkov.inkmessages.thread

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.ui.InkPagedList
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.ink.ui.InkWindow
import io.github.andreygladkov.ink.ui.iconButton
import io.github.andreygladkov.inkmessages.Labels
import io.github.andreygladkov.inkmessages.MessageDates
import io.github.andreygladkov.inkmessages.R
import io.github.andreygladkov.inkmessages.contacts.ContactDirectory
import io.github.andreygladkov.inkmessages.contacts.Participant
import io.github.andreygladkov.inkmessages.data.Loader
import io.github.andreygladkov.inkmessages.data.Message
import io.github.andreygladkov.inkmessages.data.TelephonyStore
import kotlin.math.roundToInt

class ThreadActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val labels by lazy { Labels(this) }
    private val dates by lazy { MessageDates.of(this) }
    private val threadId by lazy { intent.getLongExtra(EXTRA_THREAD_ID, -1) }
    private lateinit var title: InkTextView
    private lateinit var list: InkPagedList
    private lateinit var empty: InkTextView
    private var shown: List<Message>? = null
    private val reload = Runnable { load() }
    private val observer = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) {
            main.removeCallbacks(reload)
            main.postDelayed(reload, RELOAD_DEBOUNCE_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        list = InkPagedList(this, anchorEnd = true, dividers = false)
        empty = InkTextView(this).apply {
            gravity = Gravity.CENTER
            setText(R.string.empty_thread)
            visibility = View.GONE
            setBackgroundColor(InkStyle.of(this@ThreadActivity).background)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(header())
            addView(View(this@ThreadActivity).apply { setBackgroundColor(InkStyle.of(this@ThreadActivity).foreground) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(InkStyle.LINE_DP).coerceAtLeast(1)))
            addView(FrameLayout(this@ThreadActivity).apply {
                addView(list)
                addView(empty)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        InkWindow.setContent(this, root)
    }

    override fun onResume() {
        super.onResume()
        contentResolver.registerContentObserver(Telephony.MmsSms.CONTENT_URI, true, observer)
        contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
        contentResolver.registerContentObserver(Telephony.Mms.CONTENT_URI, true, observer)
        load()
    }

    override fun onPause() {
        super.onPause()
        contentResolver.unregisterContentObserver(observer)
        main.removeCallbacks(reload)
    }

    private fun load() {
        val store = TelephonyStore(contentResolver)
        val contacts = ContactDirectory(contentResolver)
        Loader.executor.execute {
            val loaded = runCatching {
                val participants = store.threadAddresses()[threadId].orEmpty().map(contacts::participant)
                participants to store.thread(threadId)
            }.getOrDefault(emptyList<Participant>() to emptyList())
            main.post { if (!isDestroyed) show(loaded.first, loaded.second) }
        }
    }

    private fun show(participants: List<Participant>, messages: List<Message>) {
        title.text = labels.title(participants)
        if (messages == shown) return
        val atNewest = shown == null || list.page == 0
        shown = messages
        val now = System.currentTimeMillis()
        val items = ThreadItems.build(messages, dates::dayOf)
        val position = if (atNewest) items.lastIndex.coerceAtLeast(0) else list.firstPosition.coerceAtMost(items.lastIndex.coerceAtLeast(0))
        list.setAdapter(object : InkPagedList.Adapter {
            override val count = items.size
            override fun itemId(position: Int) = items[position].id
            override fun createView(position: Int): View = view(items[position], now)
        }, position)
        empty.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun view(item: ThreadItem, now: Long): View = when (item) {
        is ThreadItem.Day -> dayHeader(dates.dayHeader(item.day, now))
        is ThreadItem.Bubble -> {
            val message = item.message
            val body = (labels.attachments(message) + listOf(message.text).filter(String::isNotEmpty)).joinToString("\n")
            BubbleRow(this, message.isOutgoing, body, if (item.showTime) dates.time(message.date) else null)
        }
    }

    private fun dayHeader(label: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val horizontal = dp(HEADER_PADDING_H_DP)
        setPadding(horizontal, dp(HEADER_GAP_DP), horizontal, dp(HEADER_GAP_DP / 2))
        addView(InkTextView(this@ThreadActivity).apply {
            text = label
            textScale = InkStyle.SECONDARY_SCALE
            isBold = true
            setPadding(0, 0, 0, dp(HEADER_TITLE_GAP_DP))
        })
        addView(View(this@ThreadActivity).apply { setBackgroundColor(InkStyle.of(this@ThreadActivity).foreground) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(InkStyle.LINE_DP).coerceAtLeast(1)))
    }

    private fun header() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(iconButton(this@ThreadActivity, R.drawable.ic_arrow_back, getString(R.string.back)).apply { setOnClickListener { finish() } })
        title = InkTextView(this@ThreadActivity).apply {
            isBold = true
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, 0, dp(HEADER_PADDING_H_DP), 0)
        }
        addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun dp(value: Float) = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val EXTRA_THREAD_ID = "thread_id"
        private const val RELOAD_DEBOUNCE_MS = 500L
        private const val HEADER_PADDING_H_DP = 12f
        private const val HEADER_GAP_DP = 12f
        private const val HEADER_TITLE_GAP_DP = 4f

        fun intent(context: Context, threadId: Long): Intent =
            Intent(context, ThreadActivity::class.java).putExtra(EXTRA_THREAD_ID, threadId)
    }
}
