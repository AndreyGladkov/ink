package io.github.andreygladkov.inkphone.vcard

import io.github.andreygladkov.inkphone.calllog.CallLogGrouping
import io.github.andreygladkov.inkphone.contacts.ContactList

enum class DuplicatePolicy { SKIP, MERGE, ADD }

data class ExistingContact(val id: Long, val name: String, val numbers: List<String>, val emails: List<String> = emptyList())

data class ImportItem(val card: CardData, val match: ExistingContact?)

object ImportPlan {
    fun match(cards: List<CardData>, existing: List<ExistingContact>): List<ImportItem> {
        val byName = existing.groupBy { normalizedName(it.name) }
        val byNumber = HashMap<String, ExistingContact>()
        existing.forEach { contact -> contact.numbers.mapNotNull(::numberKey).forEach { byNumber.putIfAbsent(it, contact) } }
        return cards.map { card ->
            val sameNumber = card.phones.firstNotNullOfOrNull { phone -> numberKey(phone.value)?.let(byNumber::get) }
            val sameName = normalizedName(card.displayName).takeIf { it.isNotEmpty() }?.let { byName[it]?.firstOrNull() }
            ImportItem(card, sameNumber ?: sameName)
        }
    }

    private fun numberKey(number: String): String? =
        number.filter(Char::isDigit).takeIf { it.isNotEmpty() }?.takeLast(CallLogGrouping.COMPARED_DIGITS)

    fun missingParts(card: CardData, existingNumbers: List<String>, existingEmails: List<String>): CardData = card.copy(
        phones = card.phones.filter { phone -> existingNumbers.none { CallLogGrouping.sameNumber(it, phone.value) } },
        emails = card.emails.filter { email -> existingEmails.none { it.equals(email.value, ignoreCase = true) } },
    )

    private fun normalizedName(name: String) = ContactList.tokens(name).sorted().joinToString(" ")
}
