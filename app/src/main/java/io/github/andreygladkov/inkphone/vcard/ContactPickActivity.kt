package io.github.andreygladkov.inkphone.vcard

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import io.github.andreygladkov.inkphone.R
import io.github.andreygladkov.inkphone.canReadContacts
import io.github.andreygladkov.inkphone.contacts.ContactEntry
import io.github.andreygladkov.inkphone.contacts.ContactList
import io.github.andreygladkov.inkphone.contacts.ContactListOptions
import io.github.andreygladkov.inkphone.contacts.ContactsRepository
import io.github.andreygladkov.inkphone.eink.InkStyle
import io.github.andreygladkov.inkphone.inkPrefs
import io.github.andreygladkov.inkphone.ui.InkButton
import io.github.andreygladkov.inkphone.ui.InkEditText
import io.github.andreygladkov.inkphone.ui.InkPagedList
import io.github.andreygladkov.inkphone.ui.InkTextView
import io.github.andreygladkov.inkphone.ui.InkWindow
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class ContactPickActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val density get() = resources.displayMetrics.density
    private val selected = LinkedHashSet<Long>()
    private var all: List<ContactEntry> = emptyList()
    private lateinit var list: InkPagedList
    private lateinit var search: InkEditText
    private lateinit var done: InkButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.getLongArrayExtra(EXTRA_SELECTED)?.let { selected += it.toList() }
        val gap = dp(GAP_DP)
        list = InkPagedList(this)
        search = InkEditText(this).apply {
            setHint(R.string.search_hint)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) = showList()
            })
        }
        done = InkButton(this).apply {
            setOnClickListener {
                setResult(RESULT_OK, Intent().putExtra(EXTRA_SELECTED, selected.toLongArray()))
                finish()
            }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(InkButton(context).apply {
                    icon = R.drawable.ic_arrow_back
                    contentDescription = getString(R.string.back)
                    isBordered = false
                    setOnClickListener { finish() }
                })
                addView(InkTextView(context).apply {
                    setText(R.string.export_selected)
                    textScale = TITLE_SCALE
                    isBold = true
                })
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(InkButton(context).apply {
                    icon = R.drawable.ic_search
                    isBordered = false
                    isClickable = false
                    isFocusable = false
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                })
                addView(search, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            })
            addView(View(context).apply { setBackgroundColor(InkStyle.of(context).foreground) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(LINE_DP).coerceAtLeast(1)))
            addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(done, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(gap, gap, gap, gap)
            })
        }
        InkWindow.setContent(this, root)
        updateDone()
        if (canReadContacts()) load() else requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), REQUEST_READ)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_READ) return
        if (canReadContacts()) load() else finish()
    }

    private fun load() {
        val repository = ContactsRepository(contentResolver)
        val options = ContactListOptions.from(inkPrefs()).copy(onlyWithPhone = false, hiddenAccounts = emptySet())
        loader.execute {
            val entries = runCatching { ContactList.build(repository.loadSummaries(), options) }.getOrDefault(emptyList())
            main.post {
                if (isDestroyed) return@post
                all = entries
                showList()
            }
        }
    }

    private fun showList() {
        val shown = ContactList.search(all, search.text.toString())
        list.setAdapter(object : InkPagedList.Adapter {
            override val count = shown.size
            override fun itemId(position: Int) = shown[position].contact.id
            override fun createView(position: Int): View = row(shown[position])
        })
    }

    private fun row(entry: ContactEntry): View = InkButton(this).apply {
        text = entry.displayName
        isBordered = false
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        iconPosition = InkButton.IconPosition.START
        iconSizeDp = CHECK_DP
        fun refresh() {
            val checked = entry.contact.id in selected
            icon = if (checked) R.drawable.ic_check_box else R.drawable.ic_check_box_blank
            isBold = checked
        }
        refresh()
        setOnClickListener {
            if (!selected.add(entry.contact.id)) selected.remove(entry.contact.id)
            refresh()
            updateDone()
        }
    }

    private fun updateDone() {
        done.text = getString(R.string.pick_done, selected.size)
    }

    private fun dp(value: Float) = (value * density).roundToInt()

    companion object {
        const val EXTRA_SELECTED = "selected"
        private const val GAP_DP = 8f
        private const val REQUEST_READ = 91
        private const val LINE_DP = 2f
        private const val CHECK_DP = 28f
        private const val TITLE_SCALE = 1.2f
        private val loader = Executors.newSingleThreadExecutor()

        fun intent(context: Context, selected: Set<Long>): Intent =
            Intent(context, ContactPickActivity::class.java).putExtra(EXTRA_SELECTED, selected.toLongArray())
    }
}
