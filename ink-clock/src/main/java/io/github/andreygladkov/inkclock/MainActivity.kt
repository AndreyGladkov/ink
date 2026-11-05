package io.github.andreygladkov.inkclock

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.content.edit
import androidx.core.view.isVisible
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.inkPrefs
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkTabBar
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.ink.ui.InkWindow
import io.github.andreygladkov.ink.ui.PermissionGate
import io.github.andreygladkov.ink.ui.iconButton
import io.github.andreygladkov.inkclock.alarm.AlarmEditActivity
import io.github.andreygladkov.inkclock.alarm.AlarmListScreen
import io.github.andreygladkov.inkclock.pomodoro.PomodoroScreen
import io.github.andreygladkov.inkclock.ring.RingActivity
import io.github.andreygladkov.inkclock.ring.RingService
import io.github.andreygladkov.inkclock.settings.SettingsActivity
import io.github.andreygladkov.inkclock.stopwatch.StopwatchScreen
import io.github.andreygladkov.inkclock.timer.TimerScreen
import kotlin.math.roundToInt
import io.github.andreygladkov.ink.R as InkR

class MainActivity : Activity() {
    private val notificationGate by lazy { PermissionGate(this, Manifest.permission.POST_NOTIFICATIONS, REQUEST_NOTIFICATIONS) }
    private lateinit var screens: Map<Tab, View>
    private lateinit var tabBar: InkTabBar<Tab>
    private lateinit var newAlarm: InkButton
    private var isInForeground = false
    // While the screen is on and unlocked the system shows a full-screen notification as a heads-up only;
    // an app in the foreground may open the ring screen itself.
    private val openRingScreen: () -> Unit = { if (RingService.ringing != null) startActivity(RingActivity.intent(this)) }
    private lateinit var banner: LinearLayout
    private lateinit var bannerText: InkTextView
    private lateinit var bannerAction: InkButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screens = mapOf(Tab.ALARMS to AlarmListScreen(this), Tab.TIMER to TimerScreen(this), Tab.STOPWATCH to StopwatchScreen(this),
            Tab.POMODORO to PomodoroScreen(this))
        val content = FrameLayout(this).apply { screens.values.forEach { addView(it.apply { visibility = View.INVISIBLE }) } }
        tabBar = InkTabBar(this, Tab.entries, inkPrefs().getBoolean(InkTabBar.PREF_LABELS, true), ::showTab)
        InkWindow.setContent(this, LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(topBar(), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(banner(), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(content, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            addView(tabBar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        })
        tabBar.select(requestedTab(intent) ?: lastTab())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        requestedTab(intent)?.let { tabBar.select(it) }
    }

    override fun onResume() {
        super.onResume()
        isInForeground = true
        showBanner()
        visibleScreen()?.onResume()
        RingService.addListener(openRingScreen)
        openRingScreen()
    }

    override fun onPause() {
        super.onPause()
        isInForeground = false
        visibleScreen()?.onPause()
        RingService.removeListener(openRingScreen)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATIONS) showBanner()
    }

    private fun visibleScreen() = tabBar.selected?.let { screens[it] } as? TabScreen

    private fun requestedTab(intent: Intent?) = intent?.getStringExtra(EXTRA_TAB)?.let { name -> Tab.entries.firstOrNull { it.name == name } }

    private fun lastTab() = inkPrefs().getString(PREF_LAST_TAB, null)?.let { name -> Tab.entries.firstOrNull { it.name == name } } ?: Tab.ALARMS

    private fun showTab(tab: Tab) {
        inkPrefs().edit { putString(PREF_LAST_TAB, tab.name) }
        newAlarm.visibility = if (tab == Tab.ALARMS) View.VISIBLE else View.INVISIBLE
        screens.forEach { (t, screen) ->
            val shown = t == tab
            if (!shown && screen.isVisible) (screen as? TabScreen)?.onPause()
            screen.visibility = if (shown) View.VISIBLE else View.INVISIBLE
        }
        if (isInForeground) visibleScreen()?.onResume()
    }

    /** Without notifications, or the right to show them full screen, an alarm rings with no screen to stop it from. */
    private fun showBanner() {
        val fullScreenDenied = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            !getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        val notificationsDenied = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationGate.isGranted
        when {
            notificationsDenied -> {
                bannerText.setText(R.string.notifications_needed)
                notificationGate.bind(bannerAction)
            }
            fullScreenDenied -> {
                bannerText.setText(R.string.full_screen_needed)
                bannerAction.setText(InkR.string.open_settings)
                bannerAction.setOnClickListener {
                    startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.fromParts("package", packageName, null)))
                }
            }
            else -> {
                banner.visibility = View.GONE
                return
            }
        }
        banner.visibility = View.VISIBLE
    }

    private fun banner(): View {
        val padding = dp(BANNER_PADDING_DP)
        bannerText = InkTextView(this).apply { textScale = InkStyle.SECONDARY_SCALE }
        bannerAction = InkButton(this)
        banner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(padding, 0, padding, padding)
            addView(bannerText)
            addView(bannerAction, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = padding })
        }
        return banner
    }

    private fun topBar() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
        newAlarm = InkButton(this@MainActivity).apply {
            setText(R.string.new_alarm)
            icon = R.drawable.ic_add
            iconPosition = InkButton.IconPosition.START
            isBordered = false
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setOnClickListener { startActivity(AlarmEditActivity.create(this@MainActivity)) }
        }
        addView(newAlarm, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        addView(iconButton(this@MainActivity, R.drawable.ic_settings, getString(R.string.settings)).apply {
            setOnClickListener { startActivity(SettingsActivity.intent(this@MainActivity)) }
        })
    }

    private fun dp(value: Float) = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val REQUEST_NOTIFICATIONS = 1
        private const val BANNER_PADDING_DP = 12f
        private const val EXTRA_TAB = "tab"
        private const val PREF_LAST_TAB = "last_tab"

        fun intent(context: Context, tab: Tab? = null): Intent = Intent(context, MainActivity::class.java).apply {
            tab?.let { putExtra(EXTRA_TAB, it.name) }
        }
    }
}
