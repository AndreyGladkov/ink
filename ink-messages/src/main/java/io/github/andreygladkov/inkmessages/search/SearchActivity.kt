package io.github.andreygladkov.inkmessages.search

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.ui.InkEditText
import io.github.andreygladkov.ink.ui.InkHeader
import io.github.andreygladkov.ink.ui.InkPagedList
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.ink.ui.InkWindow
import io.github.andreygladkov.inkmessages.Labels
import io.github.andreygladkov.inkmessages.MessageDates
import io.github.andreygladkov.inkmessages.R
import io.github.andreygladkov.inkmessages.contacts.Blocking
import io.github.andreygladkov.inkmessages.contacts.ContactDirectory
import io.github.andreygladkov.inkmessages.conversations.Conversation
import io.github.andreygladkov.inkmessages.conversations.ConversationRepository
import io.github.andreygladkov.inkmessages.conversations.ConversationRow
import io.github.andreygladkov.inkmessages.data.Loader
import io.github.andreygladkov.inkmessages.data.TelephonyStore
import io.github.andreygladkov.inkmessages.thread.ThreadActivity
import kotlin.math.roundToInt

class SearchActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val labels by lazy { Labels(this) }
    private val dates by lazy { MessageDates.of(this) }
    private lateinit var field: InkEditText
    private lateinit var list: InkPagedList
    private lateinit var nothing: InkTextView
    private var search: MessageSearch? = null
    private val update = Runnable { showResults() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        field = InkEditText(this).apply {
            isBordered = true
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            contentDescription = getString(R.string.search)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    main.removeCallbacks(update)
                    main.postDelayed(update, TYPING_PAUSE_MS)
                }
            })
        }
        list = InkPagedList(this)
        nothing = InkTextView(this).apply {
            gravity = Gravity.CENTER
            setText(R.string.search_nothing)
            visibility = View.GONE
            setBackgroundColor(InkStyle.of(this@SearchActivity).background)
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(header())
            addView(FrameLayout(this@SearchActivity).apply {
                addView(list)
                addView(nothing)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        InkWindow.setContent(this, root)
        field.requestFocus()
        load()
    }

    private fun load() {
        val store = TelephonyStore(contentResolver)
        val repository = ConversationRepository(store, ContactDirectory(contentResolver), Blocking(this)::isBlocked)
        Loader.executor.execute {
            val loaded = runCatching {
                val conversations = repository.load()
                MessageSearch(conversations, store.withContent(store.headers())) { labels.title(it.participants) }
            }.getOrNull()
            main.post {
                if (isDestroyed) return@post
                search = loaded
                showResults()
            }
        }
    }

    private fun showResults() {
        val results = search?.find(field.text.toString()) ?: return
        val now = System.currentTimeMillis()
        val items = buildList {
            if (results.conversations.isNotEmpty()) add(Item.Section(R.string.search_conversations))
            results.conversations.forEach { add(Item.Thread(it)) }
            if (results.messages.isNotEmpty()) add(Item.Section(R.string.search_messages))
            results.messages.forEach { add(Item.Found(it)) }
        }
        list.setAdapter(object : InkPagedList.Adapter {
            override val count = items.size
            override fun itemId(position: Int) = position.toLong()
            override fun createView(position: Int): View = view(items[position], now)
        }, 0)
        nothing.visibility = if (results.isEmpty && field.text.isNotBlank()) View.VISIBLE else View.GONE
    }

    private fun view(item: Item, now: Long): View = when (item) {
        is Item.Section -> section(getString(item.title))
        is Item.Thread -> row(item.conversation, item.conversation.last.date, labels.preview(item.conversation.last), now) {
            startActivity(ThreadActivity.intent(this, item.conversation.threadId))
        }
        is Item.Found -> row(item.match.conversation, item.match.message.date, item.match.snippet, now) {
            startActivity(ThreadActivity.intent(this, item.match.conversation.threadId, focus = item.match.message))
        }
    }

    private fun row(conversation: Conversation, date: Long, text: String, now: Long, open: () -> Unit) = ConversationRow(this).apply {
        title.text = labels.title(conversation.participants)
        this.date.text = dates.listLabel(date, now)
        preview.text = text
        setOnClickListener { open() }
    }

    /** No rule under the title: the list's own divider follows it. */
    private fun section(title: String) = InkTextView(this).apply {
        text = title
        textScale = InkStyle.SECONDARY_SCALE
        isBold = true
        setPadding(dp(PADDING_DP), dp(SECTION_GAP_DP), dp(PADDING_DP), dp(TITLE_GAP_DP))
    }

    private fun header() = InkHeader.of(this, FrameLayout(this).apply {
        setPadding(0, 0, dp(PADDING_DP), 0)
        addView(field)
    }, InkHeader.back(this, ::finish))

    private fun line() = View(this).apply {
        setBackgroundColor(InkStyle.of(this@SearchActivity).foreground)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(InkStyle.LINE_DP).coerceAtLeast(1))
    }

    private fun dp(value: Float) = (value * resources.displayMetrics.density).roundToInt()

    private sealed interface Item {
        data class Section(val title: Int) : Item
        data class Thread(val conversation: Conversation) : Item
        data class Found(val match: MessageMatch) : Item
    }

    companion object {
        /** Results wait for a pause in typing, since every redraw costs the e-ink panel a refresh. */
        private const val TYPING_PAUSE_MS = 400L
        private const val PADDING_DP = 12f
        private const val SECTION_GAP_DP = 12f
        private const val TITLE_GAP_DP = 4f

        fun intent(context: Context) = Intent(context, SearchActivity::class.java)
    }
}
