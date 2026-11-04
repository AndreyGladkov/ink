package io.github.andreygladkov.inkmessages.conversations

import io.github.andreygladkov.inkmessages.contacts.ContactDirectory
import io.github.andreygladkov.inkmessages.data.Message
import io.github.andreygladkov.inkmessages.data.TelephonyStore

class ConversationRepository(
    private val store: TelephonyStore,
    private val contacts: ContactDirectory,
    private val isBlocked: (String) -> Boolean = { false },
) {
    fun load(): List<Conversation> {
        val addresses = store.threadAddresses()
        val byThread = store.headers().groupBy(Message::threadId)
        val latest = byThread.mapValues { (_, messages) -> messages.maxWith(compareBy(Message::date, Message::key)) }
        val withText = store.withContent(latest.values.toList()).associateBy(Message::threadId)
        val hidden = addresses.filterValues { it.isNotEmpty() && it.all(isBlocked) }.keys
        return withText.filterKeys { it !in hidden }.map { (threadId, last) ->
            Conversation(
                threadId = threadId,
                participants = addresses[threadId].orEmpty().map(contacts::participant),
                last = last,
                unread = byThread.getValue(threadId).count(Message::isUnread),
            )
        }.sortedWith(compareByDescending<Conversation> { it.last.date }.thenByDescending { it.threadId })
    }
}
