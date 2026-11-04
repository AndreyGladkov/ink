package io.github.andreygladkov.inkmessages.conversations

import io.github.andreygladkov.inkmessages.contacts.Participant
import io.github.andreygladkov.inkmessages.data.Message

data class Conversation(
    val threadId: Long,
    val participants: List<Participant>,
    val last: Message,
    val unread: Int,
)
