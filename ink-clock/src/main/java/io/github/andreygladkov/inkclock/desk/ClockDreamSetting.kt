package io.github.andreygladkov.inkclock.desk

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

object ClockDreamSetting {
    // The chosen screensaver has no public API; this is the secure setting the system screensaver picker writes,
    // a comma-separated list of flattened components.
    private const val SCREENSAVER_COMPONENTS = "screensaver_components"

    fun isSelected(context: Context): Boolean {
        val chosen = runCatching { Settings.Secure.getString(context.contentResolver, SCREENSAVER_COMPONENTS) }.getOrNull() ?: return false
        val ours = ComponentName(context, ClockDream::class.java)
        return chosen.split(',').mapNotNull(ComponentName::unflattenFromString).any { it == ours }
    }
}
