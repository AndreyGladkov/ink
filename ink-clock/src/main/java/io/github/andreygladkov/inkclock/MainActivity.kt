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
import io.github.andreygladkov.inkclock.settings.SettingsActivity
import kotlin.math.roundToInt
import io.github.andreygladkov.ink.R as InkR

class MainActivity : Activity() {
    private val notificationGate by lazy { PermissionGate(this, Manifest.permission.POST_NOTIFICATIONS, REQUEST_NOTIFICATIONS) }
    private lateinit var screens: Map<Tab, View>
    private lateinit var banner: LinearLayout
    private lateinit var bannerText: InkTextView
    private lateinit var bannerAction: InkButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        screens = mapOf(Tab.ALARMS to AlarmListScreen(this))
        val content = FrameLayout(this).apply { screens.values.forEach { addView(it.apply { visibility = View.INVISIBLE }) } }
        val tabBar = InkTabBar(this, Tab.entries, inkPrefs().getBoolean(InkTabBar.PREF_LABELS, true), ::showTab)
        InkWindow.setContent(this, LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(topBar(), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(banner(), LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(content, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
            addView(tabBar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        })
        tabBar.select(Tab.ALARMS)
    }

    override fun onResume() {
        super.onResume()
        showBanner()
        tabScreens().forEach { it.onResume() }
    }

    override fun onPause() {
        super.onPause()
        tabScreens().forEach { it.onPause() }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_NOTIFICATIONS) showBanner()
    }

    private fun tabScreens() = screens.values.filterIsInstance<TabScreen>()

    private fun showTab(tab: Tab) {
        screens.forEach { (t, screen) -> screen.visibility = if (t == tab) View.VISIBLE else View.INVISIBLE }
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
        addView(InkButton(this@MainActivity).apply {
            setText(R.string.new_alarm)
            icon = R.drawable.ic_add
            iconPosition = InkButton.IconPosition.START
            isBordered = false
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setOnClickListener { startActivity(AlarmEditActivity.create(this@MainActivity)) }
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        addView(iconButton(this@MainActivity, R.drawable.ic_settings, getString(R.string.settings)).apply {
            setOnClickListener { startActivity(SettingsActivity.intent(this@MainActivity)) }
        })
    }

    private fun dp(value: Float) = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val REQUEST_NOTIFICATIONS = 1
        private const val BANNER_PADDING_DP = 12f

        fun intent(context: Context): Intent = Intent(context, MainActivity::class.java)
    }
}
