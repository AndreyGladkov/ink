package io.github.andreygladkov.inkphone.contacts

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import io.github.andreygladkov.inkphone.R
import io.github.andreygladkov.inkphone.TabScreen
import io.github.andreygladkov.inkphone.eink.InkStyle
import io.github.andreygladkov.inkphone.inkPrefs
import io.github.andreygladkov.inkphone.settings.SettingsActivity
import io.github.andreygladkov.inkphone.ui.InkButton
import io.github.andreygladkov.inkphone.ui.InkEditText
import io.github.andreygladkov.inkphone.ui.InkPagedList
import io.github.andreygladkov.inkphone.ui.InkTextView
import io.github.andreygladkov.inkphone.ui.LetterIndexView
import io.github.andreygladkov.inkphone.ui.PermissionGate
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class ContactsScreen(private val activity: Activity) : LinearLayout(activity), TabScreen {
    private val style = InkStyle.of(activity)
    private val repository = ContactsRepository(activity.contentResolver)
    private val permission = PermissionGate(activity, Manifest.permission.READ_CONTACTS, REQUEST_CONTACTS)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val density = resources.displayMetrics.density

    private val search = InkEditText(activity).apply {
        setHint(R.string.search_hint)
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = applyQuery()
        })
    }
    private val clear = InkButton(activity).apply {
        icon = R.drawable.ic_close
        contentDescription = context.getString(R.string.clear_search)
        isBordered = false
        visibility = INVISIBLE
        setOnClickListener { search.text = null }
    }
    private val list = InkPagedList(activity)
    private val index = LetterIndexView(activity) { letter -> jumpTo(letter) }
    private val message = InkTextView(activity).apply { gravity = Gravity.CENTER }
    private val action = InkButton(activity).apply { visibility = GONE }
    private val state = LinearLayout(activity).apply {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        visibility = GONE
        setBackgroundColor(style.background)
        val padding = (PADDING_DP * density).roundToInt()
        setPadding(padding, padding, padding, padding)
        addView(message, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(action, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = padding
        })
    }

    private var entries: List<ContactEntry> = emptyList()
    private var shown: List<ContactEntry> = emptyList()
    private var loaded = false
    private var shownSignature: List<Pair<ContactSummary, String>> = emptyList()
    private val reload = Runnable { load() }
    private val observer = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean) {
            mainHandler.removeCallbacks(reload)
            mainHandler.postDelayed(reload, RELOAD_DEBOUNCE_MS)
        }
    }

    init {
        orientation = VERTICAL
        addView(searchBar(), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(View(activity).apply { setBackgroundColor(style.foreground) },
            LayoutParams(LayoutParams.MATCH_PARENT, (LINE_DP * density).roundToInt().coerceAtLeast(1)))
        addView(FrameLayout(activity).apply {
            addView(LinearLayout(activity).apply {
                orientation = HORIZONTAL
                addView(list, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
                addView(index, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
            })
            addView(state)
        }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        list.onPageShown = { first -> index.current = shown.getOrNull(first)?.letter }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refresh()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        activity.contentResolver.unregisterContentObserver(observer)
        mainHandler.removeCallbacks(reload)
    }

    override fun onResume() = refresh()

    override fun onPermissionsResult() = refresh()

    private fun refresh() {
        if (!permission.isGranted) {
            showPermissionRequest()
            return
        }
        activity.contentResolver.unregisterContentObserver(observer)
        activity.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer)
        load()
    }

    private fun load() {
        val options = ContactListOptions.from(activity.inkPrefs())
        loader.execute {
            val built = ContactList.build(repository.loadSummaries(), options)
            val signature = built.map { it.contact to it.displayName }
            mainHandler.post {
                if (loaded && signature == shownSignature) return@post
                shownSignature = signature
                entries = built
                loaded = true
                applyQuery()
            }
        }
    }

    private fun applyQuery() {
        val query = search.text.toString()
        clear.visibility = if (query.isEmpty()) INVISIBLE else VISIBLE
        if (!loaded) return
        val anchorId = shown.getOrNull(list.firstPosition)?.contact?.id
        val previous = shown
        shown = ContactList.search(entries, query)
        val position = if (previous.size == shown.size) shown.indexOfFirst { it.contact.id == anchorId }.coerceAtLeast(0) else 0
        index.letters = ContactList.letters(shown)
        list.setAdapter(adapter(shown), position)
        when {
            shown.isNotEmpty() -> state.visibility = GONE
            query.isNotEmpty() -> showMessage(R.string.search_empty)
            else -> showMessage(R.string.contacts_empty)
        }
    }

    private fun jumpTo(letter: String) {
        val position = shown.indexOfFirst { it.letter == letter }
        if (position >= 0) list.showPageStartingAt(position)
    }

    private fun adapter(items: List<ContactEntry>) = object : InkPagedList.Adapter {
        override val count = items.size
        override fun itemId(position: Int) = items[position].contact.id
        override fun createView(position: Int): View = InkButton(activity).apply {
            val entry = items[position]
            text = entry.displayName
            isBordered = false
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setOnClickListener { activity.startActivity(ContactCardActivity.intent(activity, entry.contact.id)) }
        }
    }

    private fun searchBar() = LinearLayout(activity).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(InkButton(activity).apply {
            icon = R.drawable.ic_search
            isBordered = false
            isClickable = false
            isFocusable = false
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        })
        addView(search, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(clear)
        addView(InkButton(activity).apply {
            icon = R.drawable.ic_add
            contentDescription = context.getString(R.string.contact_new)
            isBordered = false
            setOnClickListener { activity.startActivity(ContactEditActivity.intentForNew(activity)) }
        })
        addView(InkButton(activity).apply {
            icon = R.drawable.ic_settings
            contentDescription = context.getString(R.string.settings)
            isBordered = false
            setOnClickListener { activity.startActivity(Intent(activity, SettingsActivity::class.java)) }
        })
    }

    private fun showMessage(text: Int) {
        state.visibility = VISIBLE
        message.setText(text)
        action.visibility = GONE
    }

    private fun showPermissionRequest() {
        state.visibility = VISIBLE
        message.setText(R.string.contacts_permission_rationale)
        action.visibility = VISIBLE
        permission.bind(action)
    }


    companion object {
        const val REQUEST_CONTACTS = 1
        private const val PADDING_DP = 24f
        private const val LINE_DP = 2f
        private const val RELOAD_DEBOUNCE_MS = 500L
        private val loader = Executors.newSingleThreadExecutor()
    }
}
