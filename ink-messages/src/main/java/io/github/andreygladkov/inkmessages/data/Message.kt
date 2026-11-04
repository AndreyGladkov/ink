package io.github.andreygladkov.inkmessages.data

enum class Attachment { IMAGE, OTHER }

data class Message(
    val id: Long,
    val isMms: Boolean,
    val threadId: Long,
    val date: Long,
    val text: String,
    val isOutgoing: Boolean,
    val isUnread: Boolean,
    val attachments: List<Attachment> = emptyList(),
) {
    val key: Long get() = id * 2 + if (isMms) 1 else 0
}
