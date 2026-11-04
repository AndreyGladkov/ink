package io.github.andreygladkov.inkphone.call

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import io.github.andreygladkov.ink.inkPrefs
import io.github.andreygladkov.inkphone.R

/** Texts offered when declining a call with an SMS; each one the user leaves blank falls back to its default. */
class QuickReplies(private val prefs: SharedPreferences, private val defaults: List<String>) {
    fun all(): List<String> = defaults.indices.map(::get)

    fun get(index: Int): String = prefs.getString(key(index), null)?.takeIf(String::isNotBlank) ?: defaults[index]

    fun set(index: Int, text: String) = prefs.edit {
        if (text.isBlank() || text.trim() == defaults[index]) remove(key(index)) else putString(key(index), text.trim())
    }

    companion object {
        fun of(context: Context) = QuickReplies(context.inkPrefs(), context.resources.getStringArray(R.array.quick_replies).toList())

        private fun key(index: Int) = "quick_reply_$index"
    }
}
