package io.github.andreygladkov.inkphone

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.view.View
import android.view.WindowInsets
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.content.edit
import io.github.andreygladkov.inkphone.call.CallLauncher
import io.github.andreygladkov.inkphone.calllog.CallLogRepository
import io.github.andreygladkov.inkphone.calllog.CallLogScreen
import io.github.andreygladkov.inkphone.contacts.ContactsScreen
import io.github.andreygladkov.inkphone.dialer.DialerScreen
import io.github.andreygladkov.inkphone.eink.InkStyle
import io.github.andreygladkov.inkphone.favorites.FavoritesScreen
import io.github.andreygladkov.inkphone.ui.InkPagedList
import io.github.andreygladkov.inkphone.ui.InkTabBar
import io.github.andreygladkov.inkphone.ui.InkWindow
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var content: FrameLayout
    private lateinit var tabBar: InkTabBar
    private val main = Handler(Looper.getMainLooper())
    private var screen: View? = null
    private val screens = HashMap<Tab, View>()
    private var appearance: List<Any>? = null
    private var pendingNumber: String? = null
    private val callLogObserver = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) = updateMissedBadge()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        content = FrameLayout(this)
        tabBar = InkTabBar(this, inkPrefs().getBoolean(InkTabBar.PREF_LABELS, true), ::showTab).apply {
            setOnApplyWindowInsetsListener { bar, insets ->
                bar.visibility = if (insets.isVisible(WindowInsets.Type.ime())) View.GONE else View.VISIBLE
                insets
            }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(content, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            addView(tabBar, LinearLayout.LayoutParams(MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        InkWindow.setContent(this, root)
        appearance = currentAppearance()
        tabBar.select(tabFor(intent))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val tab = tabFor(intent)
        val number = pendingNumber
        if (tab == tabBar.selected) {
            if (number != null) (screen as? DialerScreen)?.setNumber(number)
            pendingNumber = null
        } else {
            tabBar.select(tab)
        }
    }

    override fun onResume() {
        super.onResume()
        if (currentAppearance() != appearance) {
            recreate()
            return
        }
        screens.values.forEach { (it as? TabScreen)?.onResume() }
        (screen as? TabScreen)?.onShown()
        if (CallLauncher.isDefaultDialer(this)) requestCallPermissions()
        if (hasCallLogAccess()) {
            contentResolver.unregisterContentObserver(callLogObserver)
            contentResolver.registerContentObserver(CallLog.Calls.CONTENT_URI, true, callLogObserver)
        }
        updateMissedBadge()
    }

    override fun onPause() {
        super.onPause()
        contentResolver.unregisterContentObserver(callLogObserver)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        CallLauncher.onPermissionsResult(this, requestCode, grantResults)
        (screen as? TabScreen)?.onPermissionsResult()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CallLauncher.REQUEST_DIALER_ROLE && CallLauncher.isDefaultDialer(this)) requestCallPermissions(force = true)
    }

    private fun requestCallPermissions(force: Boolean = false) {
        if (!force && inkPrefs().getBoolean(PREF_CALL_PERMISSIONS_ASKED, false)) return
        val wanted = buildList {
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isEmpty()) return
        inkPrefs().edit { putBoolean(PREF_CALL_PERMISSIONS_ASKED, true) }
        requestPermissions(wanted.toTypedArray(), REQUEST_CALL_PERMISSIONS)
    }

    private fun currentAppearance(): List<Any> = listOf(
        InkStyle.of(this),
        inkPrefs().getBoolean(InkTabBar.PREF_LABELS, true),
        inkPrefs().getBoolean(InkPagedList.PREF_SCROLL, false),
        DialerScreen.numberScale(inkPrefs()),
    )

    private fun tabFor(intent: Intent?): Tab {
        val dialed = intent?.takeIf { it.action == Intent.ACTION_DIAL || it.action == Intent.ACTION_VIEW }?.data?.schemeSpecificPart
        if (intent?.action == Intent.ACTION_DIAL || dialed != null) {
            pendingNumber = dialed
            return Tab.DIALER
        }
        intent?.getStringExtra(EXTRA_TAB)?.let { name -> Tab.entries.find { it.name == name } }?.let { return it }
        return inkPrefs().getString(PREF_LAST_TAB, null)?.let { name -> Tab.entries.find { it.name == name } } ?: Tab.DIALER
    }

    private fun showTab(tab: Tab) {
        inkPrefs().edit { putString(PREF_LAST_TAB, tab.name) }
        val view = ensureScreen(tab)
        // Hidden tabs stay INVISIBLE rather than GONE so they are still laid out and their pages are ready.
        screens.forEach { (t, v) -> v.visibility = if (t == tab) View.VISIBLE else View.INVISIBLE }
        screen = view
        (view as? TabScreen)?.onShown()
        pendingNumber?.let { number -> (view as? DialerScreen)?.setNumber(number) }
        pendingNumber = null
        content.postOnAnimation { content.post(::prepareNextScreen) }
    }

    private fun ensureScreen(tab: Tab): View = screens.getOrPut(tab) {
        createScreen(tab).also {
            it.visibility = View.INVISIBLE
            content.addView(it)
        }
    }

    private fun prepareNextScreen() {
        if (isDestroyed) return
        val missing = Tab.entries.firstOrNull { it !in screens } ?: return
        ensureScreen(missing)
        content.postOnAnimation { content.post(::prepareNextScreen) }
    }

    private fun createScreen(tab: Tab): View = when (tab) {
        Tab.CONTACTS -> ContactsScreen(this)
        Tab.CALL_LOG -> CallLogScreen(this, ::updateMissedBadge)
        Tab.DIALER -> DialerScreen(this)
        Tab.FAVORITES -> FavoritesScreen(this)
    }

    private fun updateMissedBadge() {
        if (!hasCallLogAccess()) return
        val repository = CallLogRepository(contentResolver)
        loader.execute {
            val count = runCatching { repository.unreadMissedCount() }.getOrDefault(0)
            main.post { if (!isDestroyed) tabBar.setBadge(Tab.CALL_LOG, count) }
        }
    }

    private fun hasCallLogAccess() = checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

    companion object {
        private const val PREF_LAST_TAB = "last_tab"
        private const val PREF_CALL_PERMISSIONS_ASKED = "call_permissions_asked"
        private const val EXTRA_TAB = "tab"
        private const val REQUEST_CALL_PERMISSIONS = 43
        private val loader = Executors.newSingleThreadExecutor()

        fun intent(context: Context, tab: Tab): Intent =
            Intent(context, MainActivity::class.java).putExtra(EXTRA_TAB, tab.name).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
