package io.github.andreygladkov.inkphone.vcard

import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImportPlanTest {
    private val mama = ExistingContact(1, "Мама", listOf("+7 927 505-75-04"), listOf("mama@example.com"))
    private val ivan = ExistingContact(2, "Иван Петров", listOf("+7 701 111 22 33"))

    private fun card(name: String, vararg numbers: String) = CardData(formattedName = name, phones = numbers.map { TypedValue(it, Phone.TYPE_MOBILE) })

    @Test fun matchesByNumberInAnyFormatThenByName() {
        val items = ImportPlan.match(
            listOf(card("Мамочка", "89275057504"), card("Петров Иван"), card("Новый", "+7 700 000 00 00"), card("Без номера")),
            listOf(mama, ivan),
        )
        assertEquals(listOf(1L, 2L, null, null), items.map { it.match?.id })
    }

    @Test fun mergeKeepsOnlyNewNumbersAndEmails() {
        val incoming = CardData(
            formattedName = "Мама",
            phones = listOf(TypedValue("8 927 505 75 04", Phone.TYPE_MOBILE), TypedValue("+7 727 300 00 00", Phone.TYPE_HOME)),
            emails = listOf(TypedValue("MAMA@example.com", Email.TYPE_HOME), TypedValue("new@example.com", Email.TYPE_HOME)),
        )
        val missing = ImportPlan.missingParts(incoming, mama.numbers, mama.emails)
        assertEquals(listOf("+7 727 300 00 00"), missing.phones.map { it.value })
        assertEquals(listOf("new@example.com"), missing.emails.map { it.value })
    }

    @Test fun emptyNamesDoNotMatchEachOther() {
        val nameless = ExistingContact(3, "", emptyList())
        assertNull(ImportPlan.match(listOf(CardData()), listOf(nameless)).single().match)
    }
}
