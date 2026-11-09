package io.github.andreygladkov.inklauncher.notify

import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.core.content.edit
import io.github.andreygladkov.inklauncher.LauncherPrefs
import io.github.andreygladkov.inklauncher.R

enum class NotificationBadge(@StringRes val title: Int) {
    NONE(R.string.notification_badge_none),
    NUMBER(R.string.notification_badge_number),
    DOT(R.string.notification_badge_dot);

    fun label(count: Int): String = if (count <= 0) "" else when (this) {
        NONE -> ""
        NUMBER -> count.toString()
        DOT -> DOT_MARK
    }

    companion object {
        private const val DOT_MARK = "●"

        fun of(prefs: SharedPreferences): NotificationBadge {
            val saved = prefs.getString(LauncherPrefs.NOTIFICATION_BADGE, null)
            return entries.firstOrNull { it.name == saved } ?: NUMBER
        }

        fun save(prefs: SharedPreferences, badge: NotificationBadge) =
            prefs.edit { putString(LauncherPrefs.NOTIFICATION_BADGE, badge.name) }
    }
}
