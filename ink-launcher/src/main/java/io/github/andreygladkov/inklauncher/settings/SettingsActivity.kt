package io.github.andreygladkov.inklauncher.settings

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import io.github.andreygladkov.ink.haptics.HapticChannel
import io.github.andreygladkov.ink.inkPrefs
import io.github.andreygladkov.ink.ui.InkChoiceRow
import io.github.andreygladkov.ink.ui.InkPagedActivity
import io.github.andreygladkov.ink.ui.InkPagedItems
import io.github.andreygladkov.ink.ui.InkSettings
import io.github.andreygladkov.inklauncher.LauncherPrefs
import io.github.andreygladkov.inklauncher.R
import io.github.andreygladkov.inklauncher.home.GridSpec
import io.github.andreygladkov.inklauncher.home.LayoutStore
import io.github.andreygladkov.inklauncher.icons.IconPack
import io.github.andreygladkov.inklauncher.notify.NotificationBadge
import io.github.andreygladkov.inklauncher.notify.NotificationFeed
import io.github.andreygladkov.ink.R as InkR

class SettingsActivity : InkPagedActivity() {
    private val store by lazy { LayoutStore(this) }

    override fun headerTitle(): String = getString(R.string.settings)

    override fun outsideState() = Triple(
        getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME),
        NotificationFeed.isAccessGranted(this),
        IconPack.installed(this).map { it.packageName },
    )

    override fun InkPagedItems.build() {
        addLauncherRole()
        addGrid()
        addIcons()
        addNotifications()
        section(getString(InkR.string.settings_display))
        listOf(InkSettings::textSize, InkSettings::weight, InkSettings::cornerRadius, InkSettings::colors, InkSettings::lists).forEach { item { it(this@SettingsActivity) } }
        InkSettings.addHaptics(this@SettingsActivity, this, listOf(HapticChannel.BUTTONS to getString(InkR.string.settings_haptic_buttons)))
        InkSettings.addA9(this@SettingsActivity, this)
        InkSettings.addAbout(this@SettingsActivity, this, AboutActivity::class.java)
    }

    private fun InkPagedItems.addLauncherRole() {
        val roles = getSystemService(RoleManager::class.java)
        if (!roles.isRoleAvailable(RoleManager.ROLE_HOME) || roles.isRoleHeld(RoleManager.ROLE_HOME)) return
        section(getString(R.string.settings_default))
        item {
            wideButton(getString(R.string.make_default)) {
                @Suppress("DEPRECATION")
                startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), REQUEST_HOME_ROLE)
            }
        }
    }

    private fun InkPagedItems.addGrid() {
        val spec = store.load()?.spec ?: GridSpec()
        section(getString(R.string.settings_grid))
        item { choice(R.string.grid_columns, GridSpec.COLUMNS.toList(), spec.columns) { setSpec(GridSpec.fitted(it, spec.rows, spec.pinnedTop, spec.pinnedBottom)) } }
        item { choice(R.string.grid_rows, GridSpec.ROWS.toList(), spec.rows) { setSpec(GridSpec.fitted(spec.columns, it, spec.pinnedTop, spec.pinnedBottom)) } }
        item {
            choice(R.string.grid_pinned_top, (0..spec.rows - 1 - spec.pinnedBottom).toList(), spec.pinnedTop) {
                setSpec(GridSpec.fitted(spec.columns, spec.rows, it, spec.pinnedBottom))
            }
        }
        item {
            choice(R.string.grid_pinned_bottom, GridSpec.PINNED_BOTTOM.filter { it <= spec.rows - 1 }, spec.pinnedBottom) {
                setSpec(GridSpec.fitted(spec.columns, spec.rows, spec.pinnedTop, it))
            }
        }
        item {
            InkSettings.onOff(this@SettingsActivity, R.string.settings_labels, inkPrefs().getBoolean(LauncherPrefs.LABELS, true)) { shown ->
                inkPrefs().edit { putBoolean(LauncherPrefs.LABELS, shown) }
            }
        }
    }

    private fun setSpec(spec: GridSpec) {
        store.update { it.withSpec(spec) }
        rebuild()
    }

    private fun InkPagedItems.addIcons() {
        val packs = IconPack.installed(this@SettingsActivity)
        section(getString(R.string.settings_icons))
        item {
            InkSettings.onOff(this@SettingsActivity, R.string.icon_frame, inkPrefs().getBoolean(LauncherPrefs.ICON_FRAME, false)) { framed ->
                inkPrefs().edit { putBoolean(LauncherPrefs.ICON_FRAME, framed) }
            }
        }
        if (packs.isEmpty()) {
            item { hint(getString(R.string.no_icon_packs)) }
            item { wideButton(getString(R.string.find_icon_packs)) { IconPack.search(this@SettingsActivity) } }
            return
        }
        item { hint(getString(R.string.icon_pack)) }
        val selected = inkPrefs().getString(LauncherPrefs.ICON_PACK, null)?.takeIf { pack -> packs.any { it.packageName == pack } }
        val options = listOf<Pair<String?, String>>(null to getString(R.string.icon_pack_none)) + packs.map { it.packageName to it.label }
        options.forEach { (pack, label) ->
            item {
                wideButton(label) {
                    inkPrefs().edit { if (pack == null) remove(LauncherPrefs.ICON_PACK) else putString(LauncherPrefs.ICON_PACK, pack) }
                    rebuild()
                }.apply { isSelected = pack == selected }
            }
        }
        if (selected != null) {
            val key = LauncherPrefs.packInvertKey(selected)
            item {
                InkSettings.onOff(this@SettingsActivity, R.string.icon_pack_invert, inkPrefs().getBoolean(key, false)) { invert ->
                    inkPrefs().edit { putBoolean(key, invert) }
                }
            }
            item { hint(getString(R.string.icon_pack_invert_hint)) }
        }
    }

    private fun InkPagedItems.addNotifications() {
        section(getString(R.string.notifications))
        val granted = NotificationFeed.isAccessGranted(this@SettingsActivity)
        item { hint(getString(if (granted) R.string.notification_access_on else R.string.notification_access_needed)) }
        item {
            wideButton(getString(if (granted) R.string.notification_access_change else R.string.grant_notification_access)) {
                NotificationFeed.requestAccess(this@SettingsActivity)
            }
        }
        if (!granted) return
        val badges = NotificationBadge.entries.map { it to getString(it.title) }
        item {
            InkChoiceRow(this@SettingsActivity, getString(R.string.notification_badge), badges, NotificationBadge.of(inkPrefs())) {
                NotificationBadge.save(inkPrefs(), it)
            }
        }
    }

    private fun choice(title: Int, values: List<Int>, selected: Int, onChoose: (Int) -> Unit) =
        InkChoiceRow(this, getString(title), values.map { it to it.toString() }, selected) { onChoose(it) }

    private fun hint(text: String) = InkSettings.hint(this, text)

    private fun wideButton(label: String, onClick: () -> Unit) = InkSettings.rowButton(this, label, onClick)

    companion object {
        private const val REQUEST_HOME_ROLE = 1

        fun intent(context: Context) = Intent(context, SettingsActivity::class.java)
    }
}
