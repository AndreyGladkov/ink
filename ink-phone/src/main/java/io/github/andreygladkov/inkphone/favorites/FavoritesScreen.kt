package io.github.andreygladkov.inkphone.favorites

import android.Manifest
import android.app.Activity
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.inkPrefs
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkPagedList
import io.github.andreygladkov.ink.ui.InkSegments
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.ink.ui.PermissionGate
import io.github.andreygladkov.ink.ui.twoLines
import io.github.andreygladkov.inkphone.R
import io.github.andreygladkov.inkphone.TabScreen
import io.github.andreygladkov.inkphone.call.ContactLookup
import io.github.andreygladkov.inkphone.call.messageAndCallButtons
import io.github.andreygladkov.inkphone.contacts.ContactCardActivity
import io.github.andreygladkov.inkphone.contacts.ContactList
import io.github.andreygladkov.inkphone.contacts.ContactListOptions
import io.github.andreygladkov.inkphone.contacts.ContactsRepository
import io.github.andreygladkov.inkphone.contacts.ContactsScreen
import io.github.andreygladkov.inkphone.settings.settingsButton
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class FavoritesScreen(private val activity: Activity) : LinearLayout(activity), TabScreen {
    private data class Row(val key: Long, val title: String, val subtitle: String, val number: String?, val contactId: Long?)

    private val main = Handler(Looper.getMainLooper())
    private val density = resources.displayMetrics.density
    private val permission = PermissionGate(activity, Manifest.permission.READ_CONTACTS, ContactsScreen.REQUEST_CONTACTS)
    private val list = InkPagedList(activity)
    private val message = InkTextView(activity).apply { gravity = Gravity.CENTER }
    private val action = InkButton(activity).apply { visibility = GONE }
    private val state = LinearLayout(activity).apply {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        visibility = GONE
        setBackgroundColor(InkStyle.of(activity).background)
        val padding = dp(PADDING_DP)
        setPadding(padding, padding, padding, padding)
        addView(message, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(action, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = padding
        })
    }
    private var showSpeedDial = false
    private var shownRows: List<Row>? = null
    private val reload = Runnable { load() }
    private val observer = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) {
            main.removeCallbacks(reload)
            main.postDelayed(reload, RELOAD_DEBOUNCE_MS)
        }
    }

    init {
        orientation = VERTICAL
        addView(LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(InkSegments(activity, listOf(R.string.favorites_tab_favorites, R.string.favorites_tab_speed_dial)) { index ->
                showSpeedDial = index == 1
                load()
            }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(settingsButton(activity))
        })
        addView(View(activity).apply { setBackgroundColor(InkStyle.of(activity).foreground) },
            LayoutParams(LayoutParams.MATCH_PARENT, dp(InkStyle.LINE_DP).coerceAtLeast(1)))
        addView(FrameLayout(activity).apply {
            addView(list)
            addView(state)
        }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refresh()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        activity.contentResolver.unregisterContentObserver(observer)
        main.removeCallbacks(reload)
    }

    override fun onResume() = refresh()

    override fun onPermissionsResult() = refresh()

    private fun refresh() {
        if (!permission.isGranted) {
            state.visibility = VISIBLE
            message.setText(R.string.contacts_permission_rationale)
            action.visibility = VISIBLE
            permission.bind(action)
            return
        }
        activity.contentResolver.unregisterContentObserver(observer)
        activity.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer)
        load()
    }

    private fun load() {
        val speedDial = showSpeedDial
        val prefs = activity.inkPrefs()
        val repository = ContactsRepository(activity.contentResolver)
        loader.execute {
            val rows = if (speedDial) speedDialRows(SpeedDial(prefs)) else favoriteRows(repository, ContactListOptions.from(prefs))
            main.post { if (speedDial == showSpeedDial && rows != shownRows) show(rows) }
        }
    }

    private fun favoriteRows(repository: ContactsRepository, options: ContactListOptions): List<Row> =
        ContactList.build(repository.loadSummaries().filter { it.starred }, options.copy(onlyWithPhone = false)).map { entry ->
            val number = entry.contact.numbers.firstOrNull()
            Row(entry.contact.id, entry.displayName, number.orEmpty(), number, entry.contact.id)
        }

    private fun speedDialRows(speedDial: SpeedDial): List<Row> = SpeedDial.KEYS.map { key ->
        val number = speedDial.numberFor(key)
        val match = number?.let { ContactLookup.find(activity, it) }
        val title = "$key · " + (match?.name ?: number ?: activity.getString(R.string.speed_dial_unassigned))
        Row(key.code.toLong(), title, if (match != null) number.orEmpty() else "", number, match?.contactId)
    }

    private fun show(rows: List<Row>) {
        shownRows = rows
        list.setAdapter(object : InkPagedList.Adapter {
            override val count = rows.size
            override fun itemId(position: Int) = rows[position].key
            override fun createView(position: Int): View = row(rows[position])
        })
        if (rows.isEmpty()) {
            state.visibility = VISIBLE
            message.setText(R.string.favorites_empty)
            action.visibility = GONE
        } else {
            state.visibility = GONE
        }
    }

    private fun row(row: Row) = LinearLayout(activity).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(InkButton(activity).apply {
            text = if (row.subtitle.isEmpty()) row.title else twoLines(row.title, row.subtitle)
            isBordered = false
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(paddingLeft, dp(InkPagedList.ROW_PADDING_DP), paddingRight, dp(InkPagedList.ROW_PADDING_DP))
            setOnClickListener {
                when {
                    row.contactId != null -> activity.startActivity(ContactCardActivity.intent(activity, row.contactId))
                    row.number != null -> activity.startActivity(ContactCardActivity.intentForNumber(activity, row.number))
                }
            }
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        if (row.number != null) {
            addView(messageAndCallButtons(activity, row.number), LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(GAP_DP) })
        }
    }

    private fun dp(value: Float) = (value * density).roundToInt()

    private companion object {
        const val PADDING_DP = 24f
        const val GAP_DP = 8f
        const val RELOAD_DEBOUNCE_MS = 500L
        val loader = Executors.newSingleThreadExecutor()
    }
}
