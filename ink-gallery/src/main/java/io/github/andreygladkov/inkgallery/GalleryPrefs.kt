package io.github.andreygladkov.inkgallery

import android.content.Context
import androidx.core.content.edit
import io.github.andreygladkov.ink.inkPrefs

object GalleryPrefs {
    private const val PREF_ROTATE_LANDSCAPE = "rotate_landscape"

    fun rotatesLandscape(context: Context) = context.inkPrefs().getBoolean(PREF_ROTATE_LANDSCAPE, false)

    fun setRotatesLandscape(context: Context, rotates: Boolean) = context.inkPrefs().edit { putBoolean(PREF_ROTATE_LANDSCAPE, rotates) }
}
