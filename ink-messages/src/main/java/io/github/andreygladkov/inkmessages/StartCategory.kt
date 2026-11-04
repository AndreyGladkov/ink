package io.github.andreygladkov.inkmessages

import android.content.SharedPreferences

object StartCategory {
    const val PREF = "start_category"
    const val PREF_LAST_OPENED = "last_category"
    const val LAST_OPENED = "LAST"

    fun resolve(prefs: SharedPreferences): Category {
        val choice = prefs.getString(PREF, null) ?: return Category.ALL
        val name = if (choice == LAST_OPENED) prefs.getString(PREF_LAST_OPENED, null) else choice
        return Category.entries.find { it.name == name } ?: Category.ALL
    }
}
