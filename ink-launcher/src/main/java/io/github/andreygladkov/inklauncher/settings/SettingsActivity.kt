package io.github.andreygladkov.inklauncher.settings

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.content.edit
import io.github.andreygladkov.ink.eink.InkStyle
import io.github.andreygladkov.ink.haptics.HapticChannel
import io.github.andreygladkov.ink.inkPrefs
import io.github.andreygladkov.ink.ui.InkButton
import io.github.andreygladkov.ink.ui.InkChoiceRow
import io.github.andreygladkov.ink.ui.InkSettings
import io.github.andreygladkov.ink.ui.InkTextView
import io.github.andreygladkov.ink.ui.InkWindow
import io.github.andreygladkov.ink.ui.addSection
import io.github.andreygladkov.ink.ui.iconButton
import io.github.andreygladkov.inklauncher.LauncherPrefs
import io.github.andreygladkov.inklauncher.R
import io.github.andreygladkov.inklauncher.home.GridSpec
import io.github.andreygladkov.inklauncher.home.LayoutStore
import io.github.andreygladkov.inklauncher.icons.IconPack
import io.github.andreygladkov.inklauncher.notify.NotificationBadge
import io.github.andreygladkov.inklauncher.notify.NotificationFeed
import kotlin.math.roundToInt
import io.github.andreygladkov.ink.R as InkR

class SettingsActivity : Activity() {
    private lateinit var body: LinearLayout
    private val store by lazy { LayoutStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = dp(PADDING_DP)
            setPadding(padding, 0, padding, padding)
        }
        InkWindow.setContent(this, LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(header())
            addView(ScrollView(this@SettingsActivity).apply {
                isVerticalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(body)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        })
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun build() {
        body.removeAllViews()
        addLauncherRole()
        addGrid()
        addIcons()
        addNotifications()
        body.addSection(getString(InkR.string.settings_display))
        listOf(InkSettings::textSize, InkSettings::weight, InkSettings::cornerRadius, InkSettings::colors, InkSettings::lists).forEach { body.addView(it(this)) }
        InkSettings.addHaptics(this, body, listOf(HapticChannel.BUTTONS to getString(InkR.string.settings_haptic_buttons)))
        InkSettings.addA9(this, body)
        body.addSection(getString(R.string.about))
        body.addView(wideButton(getString(R.string.about)) { startActivity(AboutActivity.intent(this)) })
    }

    private fun addLauncherRole() {
        val roles = getSystemService(RoleManager::class.java)
        if (!roles.isRoleAvailable(RoleManager.ROLE_HOME) || roles.isRoleHeld(RoleManager.ROLE_HOME)) return
        body.addSection(getString(R.string.settings_default))
        body.addView(wideButton(getString(R.string.make_default)) {
            @Suppress("DEPRECATION")
            startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), REQUEST_HOME_ROLE)
        })
    }

    private fun addGrid() {
        val spec = store.load()?.spec ?: GridSpec()
        body.addSection(getString(R.string.settings_grid))
        body.addView(choice(R.string.grid_columns, GridSpec.COLUMNS.toList(), spec.columns) { setSpec(GridSpec.fitted(it, spec.rows, spec.pinnedTop, spec.pinnedBottom)) })
        body.addView(choice(R.string.grid_rows, GridSpec.ROWS.toList(), spec.rows) { setSpec(GridSpec.fitted(spec.columns, it, spec.pinnedTop, spec.pinnedBottom)) })
        body.addView(choice(R.string.grid_pinned_top, (0..spec.rows - 1 - spec.pinnedBottom).toList(), spec.pinnedTop) {
            setSpec(GridSpec.fitted(spec.columns, spec.rows, it, spec.pinnedBottom))
        })
        body.addView(choice(R.string.grid_pinned_bottom, GridSpec.PINNED_BOTTOM.filter { it <= spec.rows - 1 }, spec.pinnedBottom) {
            setSpec(GridSpec.fitted(spec.columns, spec.rows, spec.pinnedTop, it))
        })
        body.addView(InkSettings.onOff(this, R.string.settings_labels, inkPrefs().getBoolean(LauncherPrefs.LABELS, true)) { shown ->
            inkPrefs().edit { putBoolean(LauncherPrefs.LABELS, shown) }
        })
    }

    private fun setSpec(spec: GridSpec) {
        store.update { it.withSpec(spec) }
        build()
    }

    private fun addIcons() {
        val packs = IconPack.installed(this)
        body.addSection(getString(R.string.settings_icons))
        body.addView(InkSettings.onOff(this, R.string.icon_frame, inkPrefs().getBoolean(LauncherPrefs.ICON_FRAME, false)) { framed ->
            inkPrefs().edit { putBoolean(LauncherPrefs.ICON_FRAME, framed) }
        })
        if (packs.isEmpty()) {
            body.addView(hint(getString(R.string.no_icon_packs)))
            body.addView(wideButton(getString(R.string.find_icon_packs)) { IconPack.search(this) })
            return
        }
        body.addView(hint(getString(R.string.icon_pack)))
        val selected = inkPrefs().getString(LauncherPrefs.ICON_PACK, null)?.takeIf { pack -> packs.any { it.packageName == pack } }
        val options = listOf<Pair<String?, String>>(null to getString(R.string.icon_pack_none)) + packs.map { it.packageName to it.label }
        options.forEach { (pack, label) ->
            body.addView(wideButton(label) {
                inkPrefs().edit { if (pack == null) remove(LauncherPrefs.ICON_PACK) else putString(LauncherPrefs.ICON_PACK, pack) }
                build()
            }.apply { isSelected = pack == selected })
        }
        if (selected != null) {
            val key = LauncherPrefs.packInvertKey(selected)
            body.addView(InkSettings.onOff(this, R.string.icon_pack_invert, inkPrefs().getBoolean(key, false)) { invert ->
                inkPrefs().edit { putBoolean(key, invert) }
            })
            body.addView(hint(getString(R.string.icon_pack_invert_hint)))
        }
    }

    private fun addNotifications() {
        body.addSection(getString(R.string.notifications))
        val granted = NotificationFeed.isAccessGranted(this)
        body.addView(hint(getString(if (granted) R.string.notification_access_on else R.string.notification_access_needed)))
        body.addView(wideButton(getString(if (granted) R.string.notification_access_change else R.string.grant_notification_access)) {
            NotificationFeed.requestAccess(this)
        })
        if (!granted) return
        val badges = NotificationBadge.entries.map { it to getString(it.title) }
        body.addView(InkChoiceRow(this, getString(R.string.notification_badge), badges, NotificationBadge.of(inkPrefs())) {
            NotificationBadge.save(inkPrefs(), it)
        })
    }

    private fun choice(title: Int, values: List<Int>, selected: Int, onChoose: (Int) -> Unit) =
        InkChoiceRow(this, getString(title), values.map { it to it.toString() }, selected) { onChoose(it) }

    private fun hint(text: String) = InkTextView(this).apply {
        this.text = text
        textScale = InkStyle.SECONDARY_SCALE
        setPadding(0, dp(GAP_DP), 0, 0)
    }

    private fun wideButton(label: String, onClick: () -> Unit) = InkButton(this).apply {
        text = label
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(GAP_DP) }
    }

    private fun header() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(iconButton(this@SettingsActivity, R.drawable.ic_arrow_back, getString(R.string.back)).apply { setOnClickListener { finish() } })
        addView(InkTextView(this@SettingsActivity).apply {
            setText(R.string.settings)
            textScale = InkStyle.TITLE_SCALE
            isBold = true
        })
    }

    private fun dp(value: Float) = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val PADDING_DP = 16f
        private const val GAP_DP = 8f
        private const val REQUEST_HOME_ROLE = 1

        fun intent(context: Context) = Intent(context, SettingsActivity::class.java)
    }
}
