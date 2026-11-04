package io.github.andreygladkov.inkmessages.search

import io.github.andreygladkov.inkmessages.contacts.Participant
import io.github.andreygladkov.inkmessages.conversations.Conversation
import io.github.andreygladkov.inkmessages.data.Message
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MessageSearchTest {
    private val mom = conversation(1, Participant("+79001112233", "Мама Лёля"))
    private val bank = conversation(2, Participant("Tinkoff"))
    private val messages = listOf(
        message(1, 1, 100, "Купи сыр и хлеб"),
        message(2, 1, 300, "Ещё молоко"),
        message(3, 2, 200, "Ваш код 1234. Никому не сообщайте"),
        message(4, 9, 400, "Сыр из чужой переписки"),
    )
    private val search = MessageSearch(listOf(mom, bank), messages) { conversation -> conversation.participants.joinToString { it.name ?: it.address } }

    @Test fun findsConversationsByNameIgnoringCaseAndYo() {
        assertEquals(listOf(mom), search.find("леля").conversations)
    }

    @Test fun findsConversationsByNumberDigits() {
        assertEquals(listOf(mom), search.find("1112233").conversations)
    }

    @Test fun findsMessagesByTextNewestFirstOnlyInListedConversations() {
        val found = search.find("сыр").messages
        assertEquals(listOf(1L), found.map { it.message.id })
        assertEquals(mom, found.single().conversation)
    }

    @Test fun ignoresYoInMessages() {
        assertEquals(listOf(2L), search.find("еще").messages.map { it.message.id })
    }

    @Test fun anEmptyQueryFindsNothing() {
        assertTrue(search.find("  ").isEmpty)
    }

    @Test fun aSingleLetterSearchesNamesButNotMessages() {
        val results = search.find("м")
        assertEquals(listOf(mom), results.conversations)
        assertTrue(results.messages.isEmpty())
    }

    @Test fun snippetStartsAtAWordBeforeALateMatch() {
        val text = "Сегодня вечером в восемь соберёмся у бабушки на даче, не забудь привезти сыр и хлеб"
        assertEquals("…даче, не забудь привезти сыр и хлеб", MessageSearch.snippet(text, MessageSearch.normalize(text).indexOf("сыр")))
    }

    @Test fun snippetKeepsAnEarlyMatchFromTheStartOnOneLine() {
        assertEquals("Купи сыр и хлеб", MessageSearch.snippet("Купи  сыр\nи хлеб", 6))
    }

    private fun conversation(threadId: Long, participant: Participant) =
        Conversation(threadId, listOf(participant), message(0, threadId, 0, ""), unread = 0)

    private fun message(id: Long, threadId: Long, date: Long, text: String) =
        Message(id = id, isMms = false, threadId = threadId, date = date, text = text, isOutgoing = false, isUnread = false)
}
