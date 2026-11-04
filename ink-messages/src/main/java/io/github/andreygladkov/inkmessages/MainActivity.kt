package io.github.andreygladkov.inkmessages

import android.Manifest
import android.app.Activity
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.content.edit
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.inkPrefs
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkTabBar
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.ink.ui.InkWindow
import io.github.andreygladkov.ink.ui.PermissionGate
import io.github.andreygladkov.ink.ui.iconButton
import io.github.andreygladkov.inkmessages.contacts.ContactDirectory
import io.github.andreygladkov.inkmessages.conversations.Conversation
import io.github.andreygladkov.inkmessages.conversations.ConversationListScreen
import io.github.andreygladkov.inkmessages.conversations.ConversationRepository
import io.github.andreygladkov.inkmessages.data.Loader
import io.github.andreygladkov.inkmessages.data.TelephonyStore
import io.github.andreygladkov.inkmessages.settings.SettingsActivity
import io.github.andreygladkov.inkmessages.thread.ThreadActivity
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val gate by lazy { PermissionGate(this, listOf(Manifest.permission.READ_SMS, Manifest.permission.READ_CONTACTS), REQUEST_PERMISSIONS) }
    private lateinit var tabBar: InkTabBar<Category>
    private lateinit var screens: Map<Category, ConversationListScreen>
    private lateinit var state: LinearLayout
    private lateinit var stateAction: InkButton
    private var conversations: List<Conversation>? = null
    private val reload = Runnable { load() }
    private val observer = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) {
            main.removeCallbacks(reload)
            main.postDelayed(reload, RELOAD_DEBOUNCE_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val labels = Labels(this)
        val dates = MessageDates.of(this)
        screens = Category.entries.associateWith { category ->
            ConversationListScreen(this, category, labels, dates) { startActivity(ThreadActivity.intent(this, it.threadId)) }
        }
        val content = FrameLayout(this).apply {
            screens.values.forEach { addView(it.apply { visibility = View.INVISIBLE }) }
            addView(stateView())
        }
        tabBar = InkTabBar(this, Category.entries, inkPrefs().getBoolean(InkTabBar.PREF_LABELS, true), ::showCategory)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(topBar(), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(content, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            addView(tabBar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        InkWindow.setContent(this, root)
        tabBar.select(StartCategory.resolve(inkPrefs()))
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onPause() {
        super.onPause()
        contentResolver.unregisterContentObserver(observer)
        main.removeCallbacks(reload)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) refresh()
    }

    private fun refresh() {
        if (!gate.isGranted) {
            state.visibility = View.VISIBLE
            gate.bind(stateAction)
            return
        }
        state.visibility = View.GONE
        contentResolver.unregisterContentObserver(observer)
        contentResolver.registerContentObserver(Telephony.MmsSms.CONTENT_URI, true, observer)
        contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
        contentResolver.registerContentObserver(Telephony.Mms.CONTENT_URI, true, observer)
        load()
    }

    private fun load() {
        val repository = ConversationRepository(TelephonyStore(contentResolver), ContactDirectory(contentResolver))
        Loader.executor.execute {
            val loaded = runCatching { repository.load() }.getOrDefault(emptyList())
            main.post { if (!isDestroyed) show(loaded) }
        }
    }

    private fun show(loaded: List<Conversation>) {
        if (loaded == conversations) return
        conversations = loaded
        val now = System.currentTimeMillis()
        screens.values.forEach { it.show(loaded, now) }
        Category.entries.forEach { category -> tabBar.setBadge(category, loaded.count { it.unread > 0 && category.includes(it) }) }
    }

    private fun showCategory(category: Category) {
        inkPrefs().edit { putString(StartCategory.PREF_LAST_OPENED, category.name) }
        // Hidden lists stay INVISIBLE rather than GONE so they are still laid out and their pages are ready.
        screens.forEach { (c, screen) -> screen.visibility = if (c == category) View.VISIBLE else View.INVISIBLE }
    }

    private fun topBar() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
        addView(iconButton(this@MainActivity, R.drawable.ic_settings, getString(R.string.settings)).apply {
            setOnClickListener { startActivity(SettingsActivity.intent(this@MainActivity)) }
        })
    }

    private fun stateView(): View {
        val padding = (STATE_PADDING_DP * resources.displayMetrics.density).roundToInt()
        stateAction = InkButton(this)
        state = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            setBackgroundColor(InkStyle.of(this@MainActivity).background)
            setPadding(padding, padding, padding, padding)
            addView(InkTextView(this@MainActivity).apply {
                gravity = Gravity.CENTER
                setText(R.string.permission_rationale)
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(stateAction, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = padding
            })
        }
        return state
    }

    private companion object {
        const val REQUEST_PERMISSIONS = 1
        const val RELOAD_DEBOUNCE_MS = 500L
        const val STATE_PADDING_DP = 24f
    }
}
