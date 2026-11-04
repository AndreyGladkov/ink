package io.github.andreygladkov.inkmessages

import io.github.andreygladkov.inkmessages.contacts.Participant
import io.github.andreygladkov.inkmessages.conversations.Conversation
import io.github.andreygladkov.inkmessages.data.Message
import kotlin.test.Test
import kotlin.test.assertEquals

class CategoryTest {
    private val stranger = Participant("+79990000000")
    private val bank = Participant("Kaspi")
    private val friend = Participant("+79004445566", "Петя", isContact = true)
    private val mother = Participant("+79001112233", "Мама", isContact = true, isStarred = true)

    private fun categoriesOf(vararg participants: Participant): Set<Category> {
        val conversation = Conversation(1, participants.toList(), Message(1, false, 1, 0, "", isOutgoing = false, isUnread = false), 0)
        return Category.entries.filter { it.includes(conversation) }.toSet()
    }

    @Test fun starredContactIsInFavoritesContactsAndAll() {
        assertEquals(setOf(Category.FAVORITES, Category.CONTACTS, Category.ALL), categoriesOf(mother))
    }

    @Test fun contactIsInContactsAndAll() {
        assertEquals(setOf(Category.CONTACTS, Category.ALL), categoriesOf(friend))
    }

    @Test fun numbersAndSendersOutsideContactsAreOther() {
        assertEquals(setOf(Category.OTHER, Category.ALL), categoriesOf(stranger))
        assertEquals(setOf(Category.OTHER, Category.ALL), categoriesOf(bank))
    }

    @Test fun groupWithOneContactCountsAsContacts() {
        assertEquals(setOf(Category.CONTACTS, Category.ALL), categoriesOf(stranger, friend))
        assertEquals(setOf(Category.FAVORITES, Category.CONTACTS, Category.ALL), categoriesOf(stranger, mother))
    }
}
