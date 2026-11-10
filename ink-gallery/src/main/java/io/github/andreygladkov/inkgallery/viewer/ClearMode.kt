package io.github.andreygladkov.inkgallery.viewer

import android.content.Context
import androidx.core.content.edit
import io.github.andreygladkov.ink.inkPrefs

/** Whether a photo and its details are shown with the A9 panel in CLEAR, where it shows real greys. */
object ClearMode {
    const val PREF = "a9_clear_for_photos"

    fun isWanted(context: Context) = context.inkPrefs().getBoolean(PREF, true)

    fun setWanted(context: Context, wanted: Boolean) = context.inkPrefs().edit { putBoolean(PREF, wanted) }
}
