package io.github.andreygladkov.inkmessages.contacts

import android.content.ContentResolver
import android.content.res.Resources
import android.provider.ContactsContract.CommonDataKinds.Phone
import io.github.andreygladkov.ink.search.SearchQuery

data class PhoneEntry(val name: String, val number: String, val type: String, val isStarred: Boolean) {
    val nameTokens: List<String> = SearchQuery.tokens(name)
    val digits: String = number.filter(Char::isDigit)
}

class PhoneBook(private val resolver: ContentResolver, private val resources: Resources) {
    fun load(): List<PhoneEntry> {
        val projection = arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME_PRIMARY, Phone.NUMBER, Phone.TYPE, Phone.LABEL, Phone.STARRED)
        val entries = runCatching {
            resolver.query(Phone.CONTENT_URI, projection, null, null, "${Phone.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC")?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val number = cursor.getString(2) ?: continue
                        val type = Phone.getTypeLabel(resources, cursor.getInt(3), cursor.getString(4)).toString()
                        add(cursor.getLong(0) to PhoneEntry(cursor.getString(1).orEmpty().ifBlank { number }, number, type, cursor.getInt(5) == 1))
                    }
                }
            }
        }.getOrNull().orEmpty()
        return entries.distinctBy { (contactId, entry) -> contactId to entry.digits.takeLast(10) }.map { it.second }
    }

    companion object {
        fun search(entries: List<PhoneEntry>, query: String): List<PhoneEntry> {
            val search = SearchQuery(query)
            if (search.isEmpty) return entries.sortedByDescending { it.isStarred }
            return entries.filter { search.matches(it.nameTokens, listOf(it.digits)) }
        }

        fun typedNumber(query: String): String? {
            val trimmed = query.trim()
            val digits = trimmed.filter(Char::isDigit)
            return trimmed.takeIf { digits.length >= MIN_NUMBER_DIGITS && it.all { c -> c.isDigit() || c in NUMBER_PUNCTUATION } }
        }

        private const val MIN_NUMBER_DIGITS = 3
        private const val NUMBER_PUNCTUATION = "+-() "
    }
}
