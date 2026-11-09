package io.github.andreygladkov.inkmessages

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
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
import io.github.andreygladkov.ink.ui.BackToStart
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkHeader
import io.github.andreygladkov.ink.ui.InkTabBar
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.ink.ui.InkWindow
import io.github.andreygladkov.ink.ui.PermissionGate
import io.github.andreygladkov.ink.ui.iconButton
import io.github.andreygladkov.inkmessages.compose.ComposeActivity
import io.github.andreygladkov.inkmessages.contacts.Blocking
import io.github.andreygladkov.inkmessages.contacts.ContactDirectory
import io.github.andreygladkov.inkmessages.conversations.Conversation
import io.github.andreygladkov.inkmessages.conversations.ConversationListScreen
import io.github.andreygladkov.inkmessages.conversations.ConversationRepository
import io.github.andreygladkov.inkmessages.data.Loader
import io.github.andreygladkov.inkmessages.data.TelephonyStore
import io.github.andreygladkov.inkmessages.search.SearchActivity
import io.github.andreygladkov.inkmessages.settings.SettingsActivity
import io.github.andreygladkov.inkmessages.sms.Drafts
import io.github.andreygladkov.inkmessages.sms.SmsRole
import io.github.andreygladkov.inkmessages.thread.ThreadActivity
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val gate by lazy { PermissionGate(this, listOf(Manifest.permission.READ_SMS, Manifest.permission.READ_CONTACTS), REQUEST_PERMISSIONS) }
    private lateinit var tabBar: InkTabBar<Category>
    private lateinit var screens: Map<Category, ConversationListScreen>
    private lateinit var state: LinearLayout
    private lateinit var stateAction: InkButton
    private lateinit var roleBanner: View
    private lateinit var composeButton: InkButton
    private lateinit var topLine: View
    private var conversations: List<Conversation>? = null
    private var drafts: Map<Long, String>? = null
    private val reload = Runnable { load() }
    private val observer = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) {
            main.removeCallbacks(reload)
            main.postDelayed(reload, RELOAD_DEBOUNCE_MS)
        }
    }

    private var startCategory = Category.entries.first()
    private val backToStart = BackToStart(this) { tabBar.select(startCategory) }

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
            topLine = InkHeader.line(this@MainActivity)
            addView(topLine)
            addView(roleBanner(), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(content, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            addView(tabBar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        InkWindow.setContent(this, root)
        startCategory = StartCategory.resolve(inkPrefs())
        tabBar.select(startCategory)
    }

    @SuppressLint("GestureBackNavigation")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!backToStart.onBackPressed()) @Suppress("DEPRECATION") super.onBackPressed()
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

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SmsRole.REQUEST_ROLE && SmsRole.isDefault(this)) requestDefaultAppPermissions()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) refresh()
    }

    private fun refresh() {
        val isDefault = SmsRole.isDefault(this)
        roleBanner.visibility = if (isDefault) View.GONE else View.VISIBLE
        composeButton.visibility = if (isDefault) View.VISIBLE else View.GONE
        topLine.visibility = if (isDefault) View.VISIBLE else View.INVISIBLE
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
        val repository = ConversationRepository(TelephonyStore(contentResolver), ContactDirectory(contentResolver), Blocking(this)::isBlocked)
        Loader.executor.execute {
            val loaded = runCatching { repository.load() }.getOrDefault(emptyList())
            main.post { if (!isDestroyed) show(loaded) }
        }
    }

    private fun show(loaded: List<Conversation>) {
        val currentDrafts = Drafts(inkPrefs()).all()
        if (loaded == conversations && currentDrafts == drafts) return
        conversations = loaded
        drafts = currentDrafts
        val now = System.currentTimeMillis()
        screens.values.forEach { it.show(loaded, now, currentDrafts) }
        Category.entries.forEach { category -> tabBar.setBadge(category, loaded.count { it.unread > 0 && category.includes(it) }) }
    }

    private fun showCategory(category: Category) {
        inkPrefs().edit { putString(StartCategory.PREF_LAST_OPENED, category.name) }
        // Hidden lists stay INVISIBLE rather than GONE so they are still laid out and their pages are ready.
        screens.forEach { (c, screen) -> screen.visibility = if (c == category) View.VISIBLE else View.INVISIBLE }
        backToStart.isAway = category != startCategory
    }

    private fun requestDefaultAppPermissions() {
        val wanted = buildList {
            add(Manifest.permission.READ_CONTACTS)
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isNotEmpty()) requestPermissions(wanted.toTypedArray(), REQUEST_PERMISSIONS)
    }

    private fun roleBanner(): View {
        val padding = (BANNER_PADDING_DP * resources.displayMetrics.density).roundToInt()
        roleBanner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, 0, padding, padding)
            addView(InkTextView(this@MainActivity).apply {
                setText(R.string.not_default_banner)
                textScale = InkStyle.SECONDARY_SCALE
            })
            addView(InkButton(this@MainActivity).apply {
                setText(R.string.make_default)
                setOnClickListener { SmsRole.request(this@MainActivity) }
            }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = padding })
        }
        return roleBanner
    }

    private fun topBar() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
        composeButton = InkButton(this@MainActivity).apply {
            setText(R.string.compose)
            icon = R.drawable.ic_compose
            iconPosition = InkButton.IconPosition.START
            isBordered = false
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setOnClickListener { startActivity(Intent(this@MainActivity, ComposeActivity::class.java)) }
        }
        addView(composeButton, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        addView(iconButton(this@MainActivity, R.drawable.ic_search, getString(R.string.search)).apply {
            setOnClickListener { startActivity(SearchActivity.intent(this@MainActivity)) }
        })
        addView(InkHeader.settings(this@MainActivity) { startActivity(SettingsActivity.intent(this@MainActivity)) })
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
        const val BANNER_PADDING_DP = 12f
    }
}
