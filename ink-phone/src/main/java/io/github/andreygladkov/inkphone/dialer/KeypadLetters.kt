package io.github.andreygladkov.inkphone.dialer

import android.content.SharedPreferences
import java.util.Locale

enum class KeypadLetters {
    AUTO, LATIN, LATIN_CYRILLIC;

    fun showsCyrillic(systemLocales: List<Locale>, names: List<String>): Boolean = when (this) {
        LATIN -> false
        LATIN_CYRILLIC -> true
        AUTO -> systemLocales.any { it.language == RUSSIAN } || names.any(::hasCyrillic)
    }

    companion object {
        const val PREF = "dialer_keypad_letters"
        private const val RUSSIAN = "ru"

        fun from(prefs: SharedPreferences) = prefs.getString(PREF, null)?.let { name -> entries.find { it.name == name } } ?: AUTO

        private fun hasCyrillic(name: String) = name.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.CYRILLIC }
    }
}
