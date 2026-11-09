package io.github.andreygladkov.inkmessages.settings

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.BlockedNumberContract.BlockedNumbers
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import androidx.core.content.edit
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.haptics.HapticChannel
import io.github.andreygladkov.ink.inkPrefs
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkPagedActivity
import io.github.andreygladkov.ink.ui.InkPagedItems
import io.github.andreygladkov.ink.ui.InkSettings
import io.github.andreygladkov.ink.ui.InkTextView
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

class SettingsActivity : InkPagedActivity() {
    private val main = Handler(Looper.getMainLooper())
    private var blocked: Blocked = Blocked.Loading

    private sealed interface Blocked {
        data object Loading : Blocked
        data object Unavailable : Blocked
        data class Numbers(val numbers: List<String>) : Blocked
    }

    override fun headerTitle(): String = getString(R.string.settings)

    override fun InkPagedItems.build() {
        val activity = this@SettingsActivity
        section(getString(R.string.settings_start_category))
        item { startChoices() }
        section(getString(InkR.string.settings_display))
        listOf(InkSettings::textSize, InkSettings::weight, InkSettings::lists, InkSettings::cornerRadius, InkSettings::colors, InkSettings::tabLabels)
            .forEach { item { it(activity) } }
        InkSettings.addHaptics(activity, this, listOf(HapticChannel.BUTTONS to getString(InkR.string.settings_haptic_buttons)))
        addNotifications()
        section(getString(R.string.settings_blocked))
        addBlocked()
        section(getString(R.string.settings_sms_app))
        addSmsApp()
        InkSettings.addA9(activity, this)
        InkSettings.addAbout(activity, this, AboutActivity::class.java)
    }

    override fun outsideState(): Any = blocked to SmsRole.isDefault(this)

    override fun onResume() {
        super.onResume()
        loadBlocked()
    }

    private fun startChoices() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val prefs = inkPrefs()
        val options = Category.entries.map { it.name to getString(it.title) } + (StartCategory.LAST_OPENED to getString(R.string.start_last))
        val current = prefs.getString(StartCategory.PREF, Category.ALL.name)
        lateinit var buttons: List<Pair<InkButton, String>>
        buttons = options.map { (value, label) ->
            InkSettings.rowButton(this@SettingsActivity, label) {
                prefs.edit { putString(StartCategory.PREF, value) }
                buttons.forEach { (other, otherValue) -> other.isSelected = otherValue == value }
            }.apply { isSelected = value == current } to value
        }
        buttons.forEach { (button, _) -> addView(button) }
    }

    /** Sound and vibration live in the system's per-channel settings, so each category only links there. */
    private fun InkPagedItems.addNotifications() {
        section(getString(R.string.settings_notifications))
        item { hint(R.string.notifications_hint) }
        listOf(Category.FAVORITES, Category.CONTACTS, Category.OTHER).forEach { category ->
            item {
                wideButton(getString(category.title)) {
                    startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        .putExtra(Settings.EXTRA_CHANNEL_ID, MessageNotifications.channelId(category)))
                }
            }
        }
    }

    private fun InkPagedItems.addBlocked() {
        when (val state = blocked) {
            Blocked.Loading -> Unit
            Blocked.Unavailable -> item { hint(R.string.blocked_unavailable) }
            is Blocked.Numbers -> if (state.numbers.isEmpty()) {
                item { hint(R.string.blocked_none) }
            } else {
                val labels = Labels(this@SettingsActivity)
                state.numbers.forEach { number -> item { blockedRow(labels.address(number), number) } }
            }
        }
    }

    private fun loadBlocked() {
        val app = applicationContext
        Loader.executor.execute {
            val numbers = if (Blocking(app).isAvailable()) blockedNumbers() else null
            main.post {
                if (isDestroyed) return@post
                blocked = numbers?.let(Blocked::Numbers) ?: Blocked.Unavailable
                rebuildIfChanged()
            }
        }
    }

    private fun blockedNumbers(): List<String> = runCatching {
        contentResolver.query(BlockedNumbers.CONTENT_URI, arrayOf(BlockedNumbers.COLUMN_ORIGINAL_NUMBER), null, null, null)?.use { cursor ->
            buildList { while (cursor.moveToNext()) cursor.getString(0)?.let(::add) }
        }.orEmpty()
    }.getOrDefault(emptyList())

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

    private fun InkPagedItems.addSmsApp() {
        if (SmsRole.isDefault(this@SettingsActivity)) {
            item { hint(R.string.sms_app_yes) }
        } else {
            item { hint(R.string.not_default_banner) }
            item { wideButton(getString(R.string.make_default)) { SmsRole.request(this@SettingsActivity) } }
        }
    }

    private fun hint(text: Int) = InkSettings.hint(this, getString(text))

    private fun wideButton(label: String, onClick: () -> Unit) = InkSettings.rowButton(this, label, onClick)

    private fun dp(value: Float) = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val GAP_DP = 8f

        fun intent(context: Context) = Intent(context, SettingsActivity::class.java)
    }
}
