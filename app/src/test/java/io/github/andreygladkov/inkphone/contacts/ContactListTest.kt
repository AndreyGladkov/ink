package io.github.andreygladkov.inkphone.contacts

import kotlin.test.Test
import kotlin.test.assertEquals

class ContactListTest {
    private val google = AccountRef("com.google", "me@gmail.com")
    private val telegram = AccountRef("org.telegram.messenger", "123")

    private fun contact(
        id: Long,
        name: String,
        alternative: String = name,
        hasPhone: Boolean = true,
        digits: List<String> = emptyList(),
        accounts: Set<AccountRef> = setOf(google),
    ) = ContactSummary(id, "key$id", name, alternative, false, hasPhone, digits, accounts)

    private fun names(entries: List<ContactEntry>) = entries.map { it.displayName }

    @Test fun cyrillicComesBeforeLatinBeforeOther() {
        val list = ContactList.build(
            listOf(contact(1, "Zed"), contact(2, "+7 701 000"), contact(3, "Яна"), contact(4, "Anna"), contact(5, "Борис")),
            ContactListOptions(),
        )
        assertEquals(listOf("Борис", "Яна", "Anna", "Zed", "+7 701 000"), names(list))
    }

    @Test fun yoIsSortedAndIndexedWithYe() {
        val list = ContactList.build(listOf(contact(1, "Ёжиков"), contact(2, "Ежов"), contact(3, "Жанна"), contact(4, "Дина")), ContactListOptions())
        assertEquals(listOf("Дина", "Ёжиков", "Ежов", "Жанна"), names(list))
        assertEquals(listOf("Д", "Е", "Ж"), ContactList.letters(list))
    }

    @Test fun indexLetters() {
        assertEquals("Й", ContactList.letter("Йошкар"))
        assertEquals("E", ContactList.letter("Émile"))
        assertEquals("#", ContactList.letter("112"))
        assertEquals("#", ContactList.letter(""))
        assertEquals("А", ContactList.letter("  «Аптека»"))
    }

    @Test fun onlyContactsWithPhoneByDefault() {
        val list = ContactList.build(listOf(contact(1, "Анна"), contact(2, "Борис", hasPhone = false)), ContactListOptions())
        assertEquals(listOf("Анна"), names(list))
        val all = ContactList.build(listOf(contact(1, "Анна"), contact(2, "Борис", hasPhone = false)), ContactListOptions(onlyWithPhone = false))
        assertEquals(listOf("Анна", "Борис"), names(all))
    }

    @Test fun contactIsHiddenOnlyWhenAllItsAccountsAreHidden() {
        val options = ContactListOptions(hiddenAccounts = setOf(telegram))
        val list = ContactList.build(
            listOf(
                contact(1, "Анна", accounts = setOf(telegram)),
                contact(2, "Борис", accounts = setOf(telegram, google)),
                contact(3, "Вера", accounts = emptySet()),
            ),
            options,
        )
        assertEquals(listOf("Борис", "Вера"), names(list))
    }

    @Test fun familyFirstUsesAlternativeName() {
        val contacts = listOf(contact(1, "Иван Петров", "Петров, Иван"), contact(2, "Анна", ""))
        assertEquals(listOf("Анна", "Петров, Иван"), names(ContactList.build(contacts, ContactListOptions(nameOrder = NameOrder.FAMILY_FIRST))))
    }

    @Test fun searchMatchesWordPrefixesInAnyOrder() {
        val list = ContactList.build(
            listOf(contact(1, "Иван Петров"), contact(2, "Пётр Иванов"), contact(3, "Анна"), contact(4, "Алёна")),
            ContactListOptions(),
        )
        assertEquals(listOf("Иван Петров"), names(ContactList.search(list, "петро ив")))
        assertEquals(listOf("Иван Петров", "Пётр Иванов"), names(ContactList.search(list, "ИВ")))
        assertEquals(listOf("Алёна"), names(ContactList.search(list, "алена")))
        assertEquals(list, ContactList.search(list, "  "))
    }

    @Test fun searchMatchesPhoneDigits() {
        val list = ContactList.build(
            listOf(contact(1, "Анна", digits = listOf("77011234567")), contact(2, "Борис 2", digits = listOf("77079990000"))),
            ContactListOptions(),
        )
        assertEquals(listOf("Анна"), names(ContactList.search(list, "+7 701 123")))
        assertEquals(listOf("Борис 2"), names(ContactList.search(list, "борис 2")))
        assertEquals(emptyList(), names(ContactList.search(list, "анна 7701")))
    }

    @Test fun accountRefRoundTrip() {
        listOf(google, AccountRef(null, null), AccountRef("type", null)).forEach {
            assertEquals(it, AccountRef.decode(it.encode()))
        }
    }
}
