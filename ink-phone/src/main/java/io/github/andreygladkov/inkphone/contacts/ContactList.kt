package io.github.andreygladkov.inkphone.contacts

import io.github.andreygladkov.ink.search.SearchQuery
import java.text.Collator
import java.util.Locale

class ContactEntry(val contact: ContactSummary, val displayName: String) {
    val letter: String = ContactList.letter(displayName)
    internal val bucket: Int = ContactList.bucket(displayName)
    internal val searchTokens: List<String> by lazy {
        (ContactList.tokens(contact.name) + ContactList.tokens(contact.nameAlternative)).distinct()
    }
}

object ContactList {
    const val OTHER_LETTER = "#"
    private const val CYRILLIC = 0
    private const val LATIN = 1
    private const val OTHER = 2

    fun build(contacts: List<ContactSummary>, options: ContactListOptions): List<ContactEntry> {
        val collator = Collator.getInstance(Locale.forLanguageTag("ru"))
        return contacts.asSequence()
            .filter { !options.onlyWithPhone || it.hasPhone }
            .filter { it.accounts.isEmpty() || !options.hiddenAccounts.containsAll(it.accounts) }
            .map { ContactEntry(it, displayName(it, options.nameOrder)) }
            .sortedWith(compareBy<ContactEntry> { it.bucket }.thenComparator { a, b -> collator.compare(a.displayName, b.displayName) })
            .toList()
    }

    fun search(entries: List<ContactEntry>, query: String): List<ContactEntry> {
        val search = SearchQuery(query)
        if (search.isEmpty) return entries
        return entries.filter { search.matches(it.searchTokens, it.contact.numberDigits) }
    }

    fun letters(entries: List<ContactEntry>): List<String> = entries.map { it.letter }.distinct()

    internal fun displayName(contact: ContactSummary, order: NameOrder): String {
        val preferred = if (order == NameOrder.FAMILY_FIRST) contact.nameAlternative else contact.name
        return preferred.ifBlank { contact.name }
    }

    internal fun bucket(name: String): Int {
        val first = name.firstOrNull { it.isLetterOrDigit() } ?: return OTHER
        if (!first.isLetter()) return OTHER
        return when (Character.UnicodeScript.of(first.code)) {
            Character.UnicodeScript.CYRILLIC -> CYRILLIC
            Character.UnicodeScript.LATIN -> LATIN
            else -> OTHER
        }
    }

    internal fun letter(name: String): String {
        val first = name.firstOrNull { it.isLetter() }?.uppercaseChar()
        return when (bucket(name)) {
            CYRILLIC -> if (first == 'Ё') "Е" else first.toString()
            LATIN -> SearchQuery.stripMarks(first.toString())
            else -> OTHER_LETTER
        }
    }

    internal fun tokens(text: String): List<String> = SearchQuery.tokens(text)
}
