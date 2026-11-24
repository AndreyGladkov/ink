package io.github.andreygladkov.inktasks.settings

import android.content.Context
import android.content.Intent
import io.github.andreygladkov.ink.haptics.HapticChannel
import io.github.andreygladkov.ink.ui.InkPagedActivity
import io.github.andreygladkov.ink.ui.InkPagedItems
import io.github.andreygladkov.ink.ui.InkSettings
import io.github.andreygladkov.inktasks.R
import io.github.andreygladkov.inktasks.store.Storage
import io.github.andreygladkov.ink.R as InkR

class SettingsActivity : InkPagedActivity() {
    private val storage by lazy { Storage(this) }
    private var failed = false

    override fun headerTitle(): String = getString(InkR.string.settings)

    override fun outsideState(): Any? = storage.chosen?.tree to failed

    override fun InkPagedItems.build() {
        val activity = this@SettingsActivity
        addStorage()
        section(getString(InkR.string.settings_display))
        listOf(InkSettings::textSize, InkSettings::weight, InkSettings::lists, InkSettings::cornerRadius, InkSettings::colors).forEach { item { it(activity) } }
        InkSettings.addHaptics(activity, this, listOf(HapticChannel.BUTTONS to getString(InkR.string.settings_haptic_buttons)))
        InkSettings.addAbout(activity, this, AboutActivity::class.java)
    }

    private fun InkPagedItems.addStorage() {
        val activity = this@SettingsActivity
        val chosen = storage.chosen
        section(getString(R.string.settings_storage))
        val place = when {
            chosen == null -> getString(R.string.storage_phone)
            !chosen.isAvailable -> getString(R.string.storage_unavailable)
            else -> getString(R.string.storage_folder, chosen.displayName.orEmpty())
        }
        item { InkSettings.hint(activity, place) }
        if (failed) item { InkSettings.hint(activity, getString(R.string.storage_failed)) }
        if (chosen == null) item { InkSettings.hint(activity, getString(R.string.storage_hint)) }
        item { InkSettings.rowButton(activity, getString(if (chosen == null) R.string.choose_folder else R.string.choose_other_folder)) { FolderPicker.open(activity) } }
        if (chosen != null) item {
            InkSettings.rowButton(activity, getString(R.string.keep_on_phone)) {
                storage.keepOnPhone()
                rebuild()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val chosen = FolderPicker.chosen(this, requestCode, resultCode, data) ?: return
        failed = !chosen && data?.data != null
        rebuild()
    }

    companion object {
        fun intent(context: Context) = Intent(context, SettingsActivity::class.java)
    }
}
