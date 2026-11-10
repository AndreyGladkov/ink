package io.github.andreygladkov.inkgallery.viewer

import android.content.Context
import androidx.core.content.edit
import io.github.andreygladkov.ink.eink.A9Panel
import io.github.andreygladkov.ink.inkPrefs

/**
 * Whether photos are dithered to the A9 panel's 16 greys. It pays off only in CLEAR: in the other modes the panel
 * dithers every frame itself and this adds grain. [ON] is for a panel kept in CLEAR by hand in A9 Service, which
 * the app cannot see; [OFF] shows CLEAR's steps between greys instead of grain.
 */
enum class PhotoDither {
    AUTO, ON, OFF;

    companion object {
        private const val PREF = "a9_photo_dither"

        fun of(context: Context): PhotoDither =
            context.inkPrefs().getString(PREF, null)?.let { name -> entries.firstOrNull { it.name == name } } ?: AUTO

        fun choose(context: Context, dither: PhotoDither) = context.inkPrefs().edit { putString(PREF, dither.name) }

        fun isOn(context: Context): Boolean = A9Panel.isInstalled(context) && when (of(context)) {
            AUTO -> ClearMode.isWanted(context)
            ON -> true
            OFF -> false
        }
    }
}
