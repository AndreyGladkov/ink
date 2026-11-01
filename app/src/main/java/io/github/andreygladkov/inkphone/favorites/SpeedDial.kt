package io.github.andreygladkov.inkphone.favorites

import android.content.SharedPreferences
import androidx.core.content.edit
import io.github.andreygladkov.inkphone.calllog.CallLogGrouping

class SpeedDial(private val prefs: SharedPreferences) {
    fun numberFor(key: Char): String? = if (key in KEYS) prefs.getString(prefKey(key), null) else null

    fun assignments(): Map<Char, String> = KEYS.mapNotNull { key -> numberFor(key)?.let { key to it } }.toMap()

    fun keyFor(numbers: List<String>): Char? =
        assignments().entries.firstOrNull { (_, assigned) -> numbers.any { CallLogGrouping.sameNumber(it, assigned) } }?.key

    fun assign(key: Char?, number: String, numbersOfContact: List<String>) {
        prefs.edit {
            assignments().filterValues { assigned -> numbersOfContact.any { CallLogGrouping.sameNumber(it, assigned) } }
                .keys.forEach { remove(prefKey(it)) }
            if (key != null && key in KEYS) putString(prefKey(key), number)
        }
    }

    private fun prefKey(key: Char) = "speed_dial_$key"

    companion object {
        val KEYS = ('2'..'9').toList()
    }
}
