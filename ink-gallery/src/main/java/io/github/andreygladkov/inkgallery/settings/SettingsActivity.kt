package io.github.andreygladkov.inkgallery.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.annotation.RequiresApi
import io.github.andreygladkov.ink.haptics.HapticChannel
import io.github.andreygladkov.ink.ui.InkChoiceRow
import io.github.andreygladkov.ink.ui.InkPagedActivity
import io.github.andreygladkov.ink.ui.InkPagedItems
import io.github.andreygladkov.ink.ui.InkSettings
import io.github.andreygladkov.inkgallery.GalleryPrefs
import io.github.andreygladkov.inkgallery.R
import io.github.andreygladkov.inkgallery.viewer.ClearMode
import io.github.andreygladkov.inkgallery.viewer.PhotoDither
import io.github.andreygladkov.ink.R as InkR

class SettingsActivity : InkPagedActivity() {
    override fun headerTitle(): String = getString(InkR.string.settings)

    override fun outsideState() = canManageMedia()

    override fun InkPagedItems.build() {
        val activity = this@SettingsActivity
        section(getString(R.string.settings_viewer))
        item { InkSettings.onOff(activity, R.string.settings_rotate_landscape, GalleryPrefs.rotatesLandscape(activity)) { GalleryPrefs.setRotatesLandscape(activity, it) } }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) addDeletion()
        section(getString(InkR.string.settings_display))
        listOf(InkSettings::textSize, InkSettings::weight, InkSettings::lists, InkSettings::cornerRadius, InkSettings::colors, InkSettings::tabLabels)
            .forEach { item { it(activity) } }
        InkSettings.addHaptics(activity, this, listOf(HapticChannel.BUTTONS to getString(InkR.string.settings_haptic_buttons)))
        InkSettings.addA9(activity, this, listOf(
            { InkSettings.onOff(activity, R.string.settings_clear_for_photos, ClearMode.isWanted(activity)) { ClearMode.setWanted(activity, it) } },
            {
                val options = listOf(PhotoDither.AUTO to getString(R.string.dither_auto), PhotoDither.ON to getString(InkR.string.on), PhotoDither.OFF to getString(InkR.string.off))
                InkChoiceRow(activity, getString(R.string.settings_photo_dither), options, PhotoDither.of(activity)) { PhotoDither.choose(activity, it) }
            },
        ))
        InkSettings.addAbout(activity, this, AboutActivity::class.java)
    }

    /** With the "manage media" right the system deletes a photo without asking again in its own dialog. */
    @RequiresApi(Build.VERSION_CODES.S)
    private fun InkPagedItems.addDeletion() {
        val activity = this@SettingsActivity
        section(getString(R.string.settings_deletion))
        item { InkSettings.hint(activity, getString(if (canManageMedia()) R.string.manage_media_granted else R.string.manage_media_hint)) }
        item {
            InkSettings.rowButton(activity, getString(R.string.manage_media_open)) {
                startActivity(Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA, Uri.fromParts("package", packageName, null)))
            }
        }
    }

    private fun canManageMedia() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && MediaStore.canManageMedia(this)

    companion object {
        fun intent(context: Context) = Intent(context, SettingsActivity::class.java)
    }
}
