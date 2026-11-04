package io.github.andreygladkov.inkmessages.thread

import android.app.Activity
import android.content.ContentUris
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
import io.github.andreygladkov.ink.inkPrefs
import io.github.andreygladkov.ink.ui.InkButton
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
import io.github.andreygladkov.inkmessages.data.SendStatus
import io.github.andreygladkov.inkmessages.data.TelephonyStore
import io.github.andreygladkov.inkmessages.notify.MessageNotifications
import io.github.andreygladkov.inkmessages.sms.Drafts
import io.github.andreygladkov.inkmessages.sms.Sim
import io.github.andreygladkov.inkmessages.sms.Sims
import io.github.andreygladkov.inkmessages.sms.SmsRole
import io.github.andreygladkov.inkmessages.sms.SmsSender
import io.github.andreygladkov.inkmessages.sms.ThreadState
import kotlin.math.roundToInt

class ThreadActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val labels by lazy { Labels(this) }
    private val dates by lazy { MessageDates.of(this) }
    private val drafts by lazy { Drafts(inkPrefs()) }
    private val sims by lazy { Sims(this) }
    private val threadId by lazy { intent.getLongExtra(EXTRA_THREAD_ID, -1) }
    private lateinit var title: InkTextView
    private lateinit var list: InkPagedList
    private lateinit var empty: InkTextView
    private lateinit var composeBar: ComposeBar
    private var participants: List<Participant> = emptyList()
    private var activeSims: List<Sim> = emptyList()
    private var shown: List<Message>? = null
    private val reload = Runnable { load() }
    private val observer = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) {
            main.removeCallbacks(reload)
            main.postDelayed(reload, RELOAD_DEBOUNCE_MS)
        }
    }

    private val recipient: String? get() = participants.singleOrNull()?.address?.takeIf(ContactDirectory::isPhoneNumber)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        list = InkPagedList(this, anchorEnd = true, dividers = false)
        empty = InkTextView(this).apply {
            gravity = Gravity.CENTER
            setText(R.string.empty_thread)
            visibility = View.GONE
            setBackgroundColor(InkStyle.of(this@ThreadActivity).background)
        }
        composeBar = ComposeBar(this, ::send).apply { visibility = View.GONE }
        val prefilled = intent.getStringExtra(EXTRA_BODY)
        composeBar.field.setText(prefilled ?: drafts.get(threadId))
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(header())
            addView(line())
            addView(FrameLayout(this@ThreadActivity).apply {
                addView(list)
                addView(empty)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(composeBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        InkWindow.setContent(this, root)
    }

    override fun onResume() {
        super.onResume()
        ThreadState.visibleThreadId = threadId
        MessageNotifications.cancel(this, threadId)
        contentResolver.registerContentObserver(Telephony.MmsSms.CONTENT_URI, true, observer)
        contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
        contentResolver.registerContentObserver(Telephony.Mms.CONTENT_URI, true, observer)
        load()
    }

    override fun onPause() {
        super.onPause()
        if (ThreadState.visibleThreadId == threadId) ThreadState.visibleThreadId = null
        contentResolver.unregisterContentObserver(observer)
        main.removeCallbacks(reload)
        if (composeBar.visibility == View.VISIBLE) drafts.set(threadId, composeBar.field.text.toString())
    }

    private fun load() {
        val store = TelephonyStore(contentResolver)
        val contacts = ContactDirectory(contentResolver)
        Loader.executor.execute {
            val loaded = runCatching {
                val people = store.threadAddresses()[threadId].orEmpty().map(contacts::participant)
                people to store.thread(threadId)
            }.getOrDefault(emptyList<Participant>() to emptyList())
            val available = sims.active()
            main.post {
                if (isDestroyed) return@post
                activeSims = available
                show(loaded.first, loaded.second)
            }
        }
    }

    private fun show(people: List<Participant>, messages: List<Message>) {
        participants = people
        title.text = labels.title(people)
        updateComposeBar()
        if (SmsRole.isDefault(this) && messages.any(Message::isUnread)) ThreadState.markRead(this, threadId)
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

    private fun updateComposeBar() {
        val canSend = SmsRole.isDefault(this) && recipient != null
        composeBar.visibility = if (canSend) View.VISIBLE else View.GONE
        if (!canSend) return
        composeBar.setSims(activeSims, sims.forThread(inkPrefs(), threadId, activeSims)) { sims.remember(inkPrefs(), threadId, it) }
    }

    private fun send(text: String) {
        val address = recipient ?: return
        val subId = composeBar.sim?.subId ?: sims.defaultSubId()
        val app = applicationContext
        composeBar.clear()
        drafts.set(threadId, "")
        Loader.executor.execute { SmsSender.send(app, address, text, subId) }
    }

    private fun retry(message: Message) {
        val address = recipient ?: return
        val app = applicationContext
        Loader.executor.execute {
            SmsSender.retry(app, ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, message.id), address, message.text, message.subId)
        }
    }

    private fun view(item: ThreadItem, now: Long): View = when (item) {
        is ThreadItem.Day -> dayHeader(dates.dayHeader(item.day, now))
        is ThreadItem.Bubble -> {
            val message = item.message
            val body = (labels.attachments(message) + listOf(message.text).filter(String::isNotEmpty)).joinToString("\n")
            BubbleRow(this, message.isOutgoing, body, footer(message, item.showTime))
        }
    }

    private fun footer(message: Message, showTime: Boolean): View? = when (message.status) {
        SendStatus.FAILED -> InkButton(this).apply {
            setText(R.string.send_failed_retry)
            textScale = InkStyle.SECONDARY_SCALE
            minHeight = 0
            paddingDp = FAILED_PADDING_DP
            isEnabled = !message.isMms && recipient != null && SmsRole.isDefault(this@ThreadActivity)
            setOnClickListener { retry(message) }
        }
        SendStatus.SENDING -> secondary(getString(R.string.sending))
        SendStatus.NONE -> if (showTime) secondary(timeLabel(message)) else null
    }

    private fun timeLabel(message: Message): String {
        val time = dates.time(message.date)
        val sim = activeSims.takeIf { it.size > 1 && !message.isOutgoing }?.find { it.subId == message.subId } ?: return time
        return getString(R.string.time_with_sim, time, sim.slot + 1)
    }

    private fun secondary(text: String) = InkTextView(this).apply {
        this.text = text
        textScale = InkStyle.SECONDARY_SCALE
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
        addView(line())
    }

    private fun line() = View(this).apply {
        setBackgroundColor(InkStyle.of(this@ThreadActivity).foreground)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(InkStyle.LINE_DP).coerceAtLeast(1))
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
        private const val EXTRA_BODY = "body"
        private const val RELOAD_DEBOUNCE_MS = 500L
        private const val HEADER_PADDING_H_DP = 12f
        private const val HEADER_GAP_DP = 12f
        private const val HEADER_TITLE_GAP_DP = 4f
        private const val FAILED_PADDING_DP = 6f

        fun intent(context: Context, threadId: Long, body: String? = null): Intent =
            Intent(context, ThreadActivity::class.java).putExtra(EXTRA_THREAD_ID, threadId).apply { if (body != null) putExtra(EXTRA_BODY, body) }
    }
}
