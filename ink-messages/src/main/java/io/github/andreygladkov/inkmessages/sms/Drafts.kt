package io.github.andreygladkov.inkmessages.sms

import android.content.SharedPreferences
import androidx.core.content.edit

class Drafts(private val prefs: SharedPreferences) {
    fun get(threadId: Long): String = prefs.getString(key(threadId), null).orEmpty()

    fun set(threadId: Long, text: String) = prefs.edit {
        if (text.isBlank()) remove(key(threadId)) else putString(key(threadId), text)
    }

    fun all(): Map<Long, String> = prefs.all.mapNotNull { (key, value) ->
        val id = key.removePrefix(PREFIX).takeIf { key.startsWith(PREFIX) }?.toLongOrNull() ?: return@mapNotNull null
        (value as? String)?.let { id to it }
    }.toMap()

    private fun key(threadId: Long) = "$PREFIX$threadId"

    private companion object {
        const val PREFIX = "draft_"
    }
}
