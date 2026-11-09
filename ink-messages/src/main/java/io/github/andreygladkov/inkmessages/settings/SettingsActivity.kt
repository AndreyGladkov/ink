package io.github.andreygladkov.inkmessages.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.BlockedNumberContract.BlockedNumbers
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.content.edit
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.haptics.HapticChannel
import io.github.andreygladkov.ink.inkPrefs
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkHeader
import io.github.andreygladkov.ink.ui.InkSettings
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.ink.ui.InkWindow
import io.github.andreygladkov.ink.ui.addSection
import io.github.andreygladkov.inkmessages.Category
import io.github.andreygladkov.inkmessages.Labels
import io.github.andreygladkov.inkmessages.R
import io.github.andreygladkov.inkmessages.StartCategory
import io.github.andreygladkov.inkmessages.contacts.Blocking
import io.github.andreygladkov.inkmessages.data.Loader
import io.github.andreygladkov.inkmessages.notify.MessageNotifications
import io.github.andreygladkov.inkmessages.sms.SmsRole
import kotlin.math.roundToInt
import io.github.andreygladkov.ink.R as InkR

class SettingsActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var blocked: LinearLayout
    private lateinit var smsApp: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = dp(PADDING_DP)
            setPadding(padding, 0, padding, padding)
        }
        body.addSection(getString(R.string.settings_start_category))
        addStartChoices(body)
        body.addSection(getString(InkR.string.settings_display))
        listOf(InkSettings::textSize, InkSettings::weight, InkSettings::lists, InkSettings::cornerRadius, InkSettings::colors, InkSettings::tabLabels)
            .forEach { body.addView(it(this)) }
        InkSettings.addHaptics(this, body, listOf(HapticChannel.BUTTONS to getString(InkR.string.settings_haptic_buttons)))
        addNotifications(body)
        body.addSection(getString(R.string.settings_blocked))
        blocked = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(blocked)
        body.addSection(getString(R.string.settings_sms_app))
        smsApp = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(smsApp)
        InkSettings.addA9(this, body)
        body.addSection(getString(R.string.about))
        body.addView(wideButton(getString(R.string.about)) { startActivity(AboutActivity.intent(this)) }, rowParams())
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(header())
            addView(ScrollView(this@SettingsActivity).apply {
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(body)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        InkWindow.setContent(this, root)
    }

    override fun onResume() {
        super.onResume()
        showSmsApp()
        loadBlocked()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SmsRole.REQUEST_ROLE) showSmsApp()
    }

    private fun addStartChoices(body: LinearLayout) {
        val prefs = inkPrefs()
        val options = Category.entries.map { it.name to getString(it.title) } + (StartCategory.LAST_OPENED to getString(R.string.start_last))
        val current = prefs.getString(StartCategory.PREF, Category.ALL.name)
        val buttons = options.map { (value, label) ->
            InkButton(this).apply {
                text = label
                isSelected = value == current
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
            } to value
        }
        buttons.forEach { (button, value) ->
            button.setOnClickListener {
                prefs.edit { putString(StartCategory.PREF, value) }
                buttons.forEach { (other, otherValue) -> other.isSelected = otherValue == value }
            }
            body.addView(button, rowParams())
        }
    }

    /** Sound and vibration live in the system's per-channel settings, so each category only links there. */
    private fun addNotifications(body: LinearLayout) {
        body.addSection(getString(R.string.settings_notifications))
        body.addView(hint(R.string.notifications_hint))
        listOf(Category.FAVORITES, Category.CONTACTS, Category.OTHER).forEach { category ->
            body.addView(wideButton(getString(category.title)) {
                startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, MessageNotifications.channelId(category)))
            }, rowParams())
        }
    }

    private fun loadBlocked() {
        val app = applicationContext
        Loader.executor.execute {
            val numbers = if (Blocking(app).isAvailable()) blockedNumbers() else null
            main.post { if (!isDestroyed) showBlocked(numbers) }
        }
    }

    private fun blockedNumbers(): List<String> = runCatching {
        contentResolver.query(BlockedNumbers.CONTENT_URI, arrayOf(BlockedNumbers.COLUMN_ORIGINAL_NUMBER), null, null, null)?.use { cursor ->
            buildList { while (cursor.moveToNext()) cursor.getString(0)?.let(::add) }
        }.orEmpty()
    }.getOrDefault(emptyList())

    private fun showBlocked(numbers: List<String>?) {
        blocked.removeAllViews()
        when {
            numbers == null -> blocked.addView(hint(R.string.blocked_unavailable))
            numbers.isEmpty() -> blocked.addView(hint(R.string.blocked_none))
            else -> {
                val labels = Labels(this)
                numbers.forEach { number -> blocked.addView(blockedRow(labels.address(number), number)) }
            }
        }
    }

    private fun blockedRow(label: String, number: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(GAP_DP), 0, 0)
        addView(InkTextView(this@SettingsActivity).apply { text = label }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(InkButton(this@SettingsActivity).apply {
            setText(R.string.unblock)
            textScale = InkStyle.SECONDARY_SCALE
            setOnClickListener {
                isEnabled = false
                val app = applicationContext
                Loader.executor.execute {
                    Blocking(app).unblock(number)
                    main.post { if (!isDestroyed) loadBlocked() }
                }
            }
        })
    }

    private fun showSmsApp() {
        smsApp.removeAllViews()
        if (SmsRole.isDefault(this)) {
            smsApp.addView(hint(R.string.sms_app_yes))
        } else {
            smsApp.addView(hint(R.string.not_default_banner))
            smsApp.addView(wideButton(getString(R.string.make_default)) { SmsRole.request(this) }, rowParams())
        }
    }

    private fun hint(text: Int) = InkTextView(this).apply {
        setText(text)
        textScale = InkStyle.SECONDARY_SCALE
        setPadding(0, dp(GAP_DP), 0, 0)
    }

    private fun wideButton(label: String, onClick: () -> Unit) = InkButton(this).apply {
        text = label
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setOnClickListener { onClick() }
    }

    private fun rowParams() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        topMargin = dp(GAP_DP)
    }

    private fun header() = InkHeader.of(this, getString(R.string.settings), ::finish)

    private fun dp(value: Float) = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val PADDING_DP = 16f
        private const val GAP_DP = 8f

        fun intent(context: Context) = Intent(context, SettingsActivity::class.java)
    }
}
