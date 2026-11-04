package io.github.andreygladkov.inkmessages.thread

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Telephony
import android.text.TextUtils
import android.text.format.Formatter
import android.util.Size
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.inkPrefs
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkButtonRow
import io.github.andreygladkov.ink.ui.InkPagedList
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.ink.ui.InkWindow
import io.github.andreygladkov.ink.ui.iconButton
import io.github.andreygladkov.inkmessages.Labels
import io.github.andreygladkov.inkmessages.MessageDates
import io.github.andreygladkov.inkmessages.R
import io.github.andreygladkov.inkmessages.compose.ComposeActivity
import io.github.andreygladkov.inkmessages.contacts.Blocking
import io.github.andreygladkov.inkmessages.contacts.ContactDirectory
import io.github.andreygladkov.inkmessages.contacts.Participant
import io.github.andreygladkov.inkmessages.data.Attachment
import io.github.andreygladkov.inkmessages.data.Loader
import io.github.andreygladkov.inkmessages.data.Message
import io.github.andreygladkov.inkmessages.data.MessageEdits
import io.github.andreygladkov.inkmessages.data.PendingMms
import io.github.andreygladkov.inkmessages.data.SendStatus
import io.github.andreygladkov.inkmessages.data.TelephonyStore
import io.github.andreygladkov.inkmessages.mms.Attachments
import io.github.andreygladkov.inkmessages.mms.MmsDownloader
import io.github.andreygladkov.inkmessages.mms.MmsStore
import io.github.andreygladkov.inkmessages.mms.VCards
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
    private lateinit var callButton: InkButton
    private lateinit var menuSlot: FrameLayout
    private lateinit var actionSlot: FrameLayout
    private var participants: List<Participant> = emptyList()
    private var isBlocked = false
    private var selected: Message? = null
    private var highlighted: Long? = null
    private val rows = HashMap<Long, BubbleRow>()
    private var backCallback: Any? = null
    private var activeSims: List<Sim> = emptyList()
    private var shown: List<Message>? = null
    private var imageSizes: Map<Long, Size?> = emptyMap()
    private var contactNames: Map<Long, String?> = emptyMap()
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
        menuSlot = FrameLayout(this).apply { visibility = View.GONE }
        actionSlot = FrameLayout(this).apply { visibility = View.GONE }
        highlighted = intent.getLongExtra(EXTRA_FOCUS, -1).takeIf { it >= 0 }
        val prefilled = intent.getStringExtra(EXTRA_BODY)
        composeBar.field.setText(prefilled ?: drafts.get(threadId))
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(header())
            addView(menuSlot, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(line())
            addView(FrameLayout(this@ThreadActivity).apply {
                addView(list)
                addView(empty)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(composeBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(actionSlot, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
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

    // Android 11–12 (the A9 runs 11) deliver back only here; API 33+ goes through the callback in watchBack.
    @SuppressLint("GestureBackNavigation")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (selected != null) clearSelection() else @Suppress("DEPRECATION") super.onBackPressed()
    }

    private fun load() {
        val store = TelephonyStore(contentResolver)
        val contacts = ContactDirectory(contentResolver)
        Loader.executor.execute {
            val loaded = runCatching {
                val people = store.threadAddresses()[threadId].orEmpty().map(contacts::participant)
                people to store.thread(threadId)
            }.getOrDefault(emptyList<Participant>() to emptyList())
            val images = loaded.second.flatMap(Message::attachments).filter { it.kind == Attachment.Kind.IMAGE }
            val sizes = images.associate { it.partId to runCatching { MmsImageView.bounds(contentResolver, it) }.getOrNull() }
            val cards = loaded.second.flatMap(Message::attachments).filter { it.kind == Attachment.Kind.CONTACT }
            val names = cards.associate { card ->
                card.partId to runCatching { contentResolver.openInputStream(card.uri)?.use { VCards.displayName(it.reader().readText()) } }.getOrNull()
            }
            val available = sims.active()
            val blocked = loaded.first.singleOrNull()?.let { Blocking(this).isBlocked(it.address) } ?: false
            main.post {
                if (isDestroyed) return@post
                activeSims = available
                isBlocked = blocked
                imageSizes = sizes
                contactNames = names
                show(loaded.first, loaded.second)
            }
        }
    }

    private fun show(people: List<Participant>, messages: List<Message>) {
        participants = people
        title.text = labels.title(people)
        callButton.visibility = if (recipient != null) View.VISIBLE else View.GONE
        if (menuSlot.visibility == View.VISIBLE) showMenu()
        if (selected == null) updateComposeBar()
        if (SmsRole.isDefault(this) && messages.any(Message::isUnread)) ThreadState.markRead(this, threadId)
        if (messages == shown) return
        val first = shown == null
        val atNewest = first || list.page == 0
        shown = messages
        if (selected != null && messages.none { it.key == selected?.key }) clearSelection()
        val now = System.currentTimeMillis()
        val items = ThreadItems.build(messages, dates::dayOf)
        val focus = highlighted.takeIf { first }?.let { key -> items.indexOfFirst { it.id == key }.takeIf { it >= 0 } }
        val position = focus ?: if (atNewest) items.lastIndex.coerceAtLeast(0) else list.firstPosition.coerceAtMost(items.lastIndex.coerceAtLeast(0))
        rows.clear()
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
            val pending = message.pending
            val row = if (pending != null) {
                BubbleRow(this, false, getString(R.string.mms_pending, Formatter.formatShortFileSize(this, pending.sizeBytes)), downloadFooter(message, pending))
            } else {
                BubbleRow(this, message.isOutgoing, message.text, footer(message, item.showTime), message.attachments.map { attachmentView(it, message.isOutgoing) })
            }
            rows[message.key] = row
            row.isHighlighted = message.key == highlighted
            row.setOnMessageLongClick { select(message) }
            row
        }
    }

    /** The contact's card, or a new contact for a number not saved yet; Ink Phone handles both. */
    private fun openContact() {
        val single = participants.singleOrNull() ?: return
        val intent = single.contactUri?.let { Intent(Intent.ACTION_VIEW, it) }
            ?: recipient?.let { Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI).putExtra(ContactsContract.Intents.Insert.PHONE, it) }
            ?: return
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Unit
        }
    }

    private fun showMenu() {
        val isDefault = SmsRole.isDefault(this)
        val unread = InkButton(this).apply {
            setText(R.string.mark_unread)
            isEnabled = isDefault && shown.orEmpty().any { !it.isOutgoing && it.pending == null }
            setOnClickListener { markUnread() }
        }
        val single = participants.singleOrNull()
        val block = InkButton(this).apply {
            setText(if (isBlocked) R.string.unblock else R.string.block)
            isEnabled = isDefault && single != null
            setOnClickListener { toggleBlock(single?.address ?: return@setOnClickListener) }
        }
        val delete = InkButton(this).apply {
            setText(R.string.delete_thread)
            isEnabled = isDefault
            confirmBeforeAction(R.string.delete_confirm) { deleteThread() }
        }
        menuSlot.removeAllViews()
        menuSlot.addView(buttonRow(unread, block, delete))
        menuSlot.visibility = View.VISIBLE
    }

    private fun toggleMenu() {
        if (menuSlot.visibility == View.VISIBLE) menuSlot.visibility = View.GONE else showMenu()
    }

    private fun markUnread() {
        val last = shown.orEmpty().lastOrNull { !it.isOutgoing && it.pending == null } ?: return
        val app = applicationContext
        Loader.executor.execute { MessageEdits(app.contentResolver).markUnread(last) }
        finish()
    }

    private fun toggleBlock(address: String) {
        val app = applicationContext
        val block = !isBlocked
        Loader.executor.execute { if (block) Blocking(app).block(address) else Blocking(app).unblock(address) }
        if (block) finish() else {
            isBlocked = false
            showMenu()
        }
    }

    private fun deleteThread() {
        val app = applicationContext
        drafts.set(threadId, "")
        Loader.executor.execute { MessageEdits(app.contentResolver).deleteThread(threadId) }
        finish()
    }

    private fun select(message: Message) {
        selected = message
        highlighted = message.key
        rows.forEach { (key, row) -> row.isHighlighted = key == message.key }
        menuSlot.visibility = View.GONE
        if (composeBar.visibility == View.VISIBLE) drafts.set(threadId, composeBar.field.text.toString())
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(composeBar.windowToken, 0)
        composeBar.visibility = View.GONE
        val isDefault = SmsRole.isDefault(this)
        val text = message.text.takeIf(String::isNotEmpty)
        val copy = InkButton(this).apply {
            setText(R.string.copy)
            isEnabled = text != null
            setOnClickListener {
                getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(null, text))
                clearSelection()
            }
        }
        val forward = InkButton(this).apply {
            setText(R.string.forward)
            isEnabled = text != null && isDefault
            setOnClickListener {
                startActivity(Intent(this@ThreadActivity, ComposeActivity::class.java).putExtra(Intent.EXTRA_TEXT, text))
                clearSelection()
            }
        }
        val delete = InkButton(this).apply {
            setText(R.string.delete)
            isEnabled = isDefault
            confirmBeforeAction(R.string.delete_message_confirm) {
                val app = applicationContext
                Loader.executor.execute { MessageEdits(app.contentResolver).delete(message) }
                clearSelection()
            }
        }
        val cancel = InkButton(this).apply {
            setText(R.string.cancel)
            setOnClickListener { clearSelection() }
        }
        actionSlot.removeAllViews()
        actionSlot.addView(buttonRow(copy, forward, delete, cancel))
        actionSlot.visibility = View.VISIBLE
        watchBack(true)
    }

    private fun clearSelection() {
        selected = null
        highlighted = null
        rows.values.forEach { it.isHighlighted = false }
        actionSlot.visibility = View.GONE
        actionSlot.removeAllViews()
        updateComposeBar()
        composeBar.field.setText(drafts.get(threadId))
        watchBack(false)
    }

    /** Back first leaves the selection; on API 33+ only a registered callback sees it. */
    private fun watchBack(enabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val dispatcher = onBackInvokedDispatcher
        (backCallback as? OnBackInvokedCallback)?.let(dispatcher::unregisterOnBackInvokedCallback)
        backCallback = if (enabled) OnBackInvokedCallback { clearSelection() }.also { dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, it) } else null
    }

    private fun buttonRow(vararg buttons: InkButton) = InkButtonRow(this, dp(ROW_GAP_DP), InkStyle.SECONDARY_SCALE).apply {
        setPadding(dp(ROW_GAP_DP), dp(ROW_GAP_DP), dp(ROW_GAP_DP), dp(ROW_GAP_DP))
        buttons.forEach(::addButton)
    }

    private fun attachmentView(attachment: Attachment, isOutgoing: Boolean): View {
        val size = imageSizes[attachment.partId]
        if (attachment.kind == Attachment.Kind.IMAGE && size != null) {
            return MmsImageView(this, attachment, size, BubbleRow.borderDp(isOutgoing)).apply {
                contentDescription = getString(R.string.attachment_image)
                setOnClickListener { startActivity(ImageViewerActivity.intent(this@ThreadActivity, attachment)) }
            }
        }
        return InkButton(this).apply {
            text = attachmentLabel(attachment)
            setOnClickListener {
                if (attachment.kind == Attachment.Kind.IMAGE) {
                    startActivity(ImageViewerActivity.intent(this@ThreadActivity, attachment))
                } else {
                    Attachments.open(this@ThreadActivity, attachment) { setText(R.string.no_app) }
                }
            }
        }
    }

    private fun attachmentLabel(attachment: Attachment): String {
        val kind = getString(
            when (attachment.kind) {
                Attachment.Kind.IMAGE -> R.string.open_image
                Attachment.Kind.CONTACT -> R.string.open_contact
                Attachment.Kind.AUDIO -> R.string.open_audio
                Attachment.Kind.VIDEO -> R.string.open_video
                Attachment.Kind.OTHER -> R.string.open_file
            },
        )
        val name = contactNames[attachment.partId] ?: attachment.name?.substringBeforeLast('.')?.takeIf(String::isNotBlank)
        return if (name == null) kind else getString(R.string.attachment_named, kind, name)
    }

    private fun downloadFooter(message: Message, pending: PendingMms): View {
        if (MmsDownloader.isActive(message.id)) return secondary(getString(R.string.mms_downloading))
        return InkButton(this).apply {
            setText(if (pending.failed) R.string.mms_download_failed else R.string.mms_download)
            textScale = InkStyle.SECONDARY_SCALE
            minHeight = 0
            paddingDp = FAILED_PADDING_DP
            isEnabled = SmsRole.isDefault(this@ThreadActivity)
            setOnClickListener {
                isEnabled = false
                setText(R.string.mms_downloading)
                val app = applicationContext
                Loader.executor.execute { MmsStore(app).notification(message.id)?.let { MmsDownloader.start(app, it, automatic = false) } }
            }
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
            setOnClickListener { openContact() }
        }
        addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        callButton = iconButton(this@ThreadActivity, R.drawable.ic_call, getString(R.string.call)).apply {
            visibility = View.GONE
            setOnClickListener { recipient?.let { startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", it, null))) } }
        }
        addView(callButton)
        addView(iconButton(this@ThreadActivity, R.drawable.ic_more, getString(R.string.more)).apply { setOnClickListener { toggleMenu() } })
    }

    private fun dp(value: Float) = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val EXTRA_THREAD_ID = "thread_id"
        private const val EXTRA_BODY = "body"
        private const val EXTRA_FOCUS = "focus"
        private const val ROW_GAP_DP = 8f
        private const val RELOAD_DEBOUNCE_MS = 500L
        private const val HEADER_PADDING_H_DP = 12f
        private const val HEADER_GAP_DP = 12f
        private const val HEADER_TITLE_GAP_DP = 4f
        private const val FAILED_PADDING_DP = 6f

        fun intent(context: Context, threadId: Long, body: String? = null, focus: Message? = null): Intent =
            Intent(context, ThreadActivity::class.java).putExtra(EXTRA_THREAD_ID, threadId).apply {
                if (body != null) putExtra(EXTRA_BODY, body)
                if (focus != null) putExtra(EXTRA_FOCUS, focus.key)
            }
    }
}
