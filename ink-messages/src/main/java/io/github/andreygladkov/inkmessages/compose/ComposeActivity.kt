package io.github.andreygladkov.inkmessages.compose

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkEditText
import io.github.andreygladkov.ink.ui.InkPagedList
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.ink.ui.InkWindow
import io.github.andreygladkov.ink.ui.iconButton
import io.github.andreygladkov.ink.ui.twoLines
import io.github.andreygladkov.inkmessages.Labels
import io.github.andreygladkov.inkmessages.R
import io.github.andreygladkov.inkmessages.contacts.PhoneBook
import io.github.andreygladkov.inkmessages.contacts.PhoneEntry
import io.github.andreygladkov.inkmessages.data.Loader
import io.github.andreygladkov.inkmessages.thread.ThreadActivity
import kotlin.math.roundToInt

class ComposeActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val labels by lazy { Labels(this) }
    private lateinit var recipient: InkEditText
    private lateinit var list: InkPagedList
    private var phones: List<PhoneEntry> = emptyList()
    private var body: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        body = bodyOf(intent)
        recipientOf(intent)?.let { address ->
            openThread(address)
            return
        }
        recipient = InkEditText(this).apply {
            isBordered = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_PERSON_NAME
            imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setOnEditorActionListener { _, _, _ ->
                PhoneBook.typedNumber(text.toString())?.let(::openThread)
                true
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) = showMatches()
            })
        }
        list = InkPagedList(this)
        val padding = dp(PADDING_DP)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(header())
            addView(InkTextView(this@ComposeActivity).apply {
                setText(R.string.compose_to)
                setPadding(padding, 0, padding, dp(LABEL_GAP_DP))
            })
            addView(recipient, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(padding, 0, padding, padding)
            })
            addView(View(this@ComposeActivity).apply { setBackgroundColor(InkStyle.of(this@ComposeActivity).foreground) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(InkStyle.LINE_DP).coerceAtLeast(1)))
            addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        InkWindow.setContent(this, root)
        recipient.requestFocus()
        val book = PhoneBook(contentResolver, resources)
        Loader.executor.execute {
            val loaded = book.load()
            main.post {
                if (isDestroyed) return@post
                phones = loaded
                showMatches()
            }
        }
    }

    private fun showMatches() {
        val query = recipient.text.toString()
        val typed = PhoneBook.typedNumber(query)
        val matches = PhoneBook.search(phones, query)
        val rows = listOfNotNull(typed?.let { Row(getString(R.string.compose_to_number, labels.address(it)), it) }) +
            matches.map { Row(twoLines(it.name, "${it.type} ${labels.address(it.number)}"), it.number) }
        list.setAdapter(object : InkPagedList.Adapter {
            override val count = rows.size
            override fun itemId(position: Int) = rows[position].hashCode().toLong()
            override fun createView(position: Int): View = row(rows[position])
        })
    }

    private data class Row(val text: CharSequence, val address: String)

    private fun row(row: Row) = InkButton(this).apply {
        text = row.text
        isBordered = false
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setPadding(paddingLeft, dp(InkPagedList.ROW_PADDING_DP), paddingRight, dp(InkPagedList.ROW_PADDING_DP))
        setOnClickListener { openThread(row.address) }
    }

    private fun openThread(address: String) {
        val app = applicationContext
        Loader.executor.execute {
            val threadId = runCatching { Telephony.Threads.getOrCreateThreadId(app, address) }.getOrNull()
            main.post {
                if (threadId != null) startActivity(ThreadActivity.intent(this, threadId, body))
                finish()
            }
        }
    }

    private fun header() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(iconButton(this@ComposeActivity, R.drawable.ic_arrow_back, getString(R.string.back)).apply { setOnClickListener { finish() } })
        addView(InkTextView(this@ComposeActivity).apply {
            setText(R.string.compose_title)
            textScale = InkStyle.TITLE_SCALE
            isBold = true
        })
    }

    private fun dp(value: Float) = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val PADDING_DP = 12f
        private const val LABEL_GAP_DP = 4f
        private val SCHEMES = setOf("sms", "smsto", "mms", "mmsto")

        fun recipientOf(intent: Intent?): String? {
            val data = intent?.data?.takeIf { it.scheme in SCHEMES } ?: return null
            return Uri.decode(data.schemeSpecificPart.substringBefore('?'))
                .split(',', ';').map(String::trim).firstOrNull(String::isNotEmpty)
        }

        fun bodyOf(intent: Intent?): String? {
            intent ?: return null
            val fromQuery = intent.data?.schemeSpecificPart?.substringAfter('?', "")
                ?.split('&')?.firstOrNull { it.startsWith("body=") }?.removePrefix("body=")?.let(Uri::decode)
            return intent.getStringExtra("sms_body") ?: intent.getStringExtra(Intent.EXTRA_TEXT) ?: fromQuery
        }
    }
}
