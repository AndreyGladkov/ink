package io.github.andreygladkov.inkmessages.search

import io.github.andreygladkov.ink.search.SearchQuery
import io.github.andreygladkov.inkmessages.conversations.Conversation
import io.github.andreygladkov.inkmessages.data.Message
import java.util.Locale

data class MessageMatch(val conversation: Conversation, val message: Message, val snippet: String)

data class SearchResults(val conversations: List<Conversation>, val messages: List<MessageMatch>) {
    val isEmpty: Boolean get() = conversations.isEmpty() && messages.isEmpty()
}

/** Searches conversations by name or number and messages by their text, newest first. */
class MessageSearch(
    private val conversations: List<Conversation>,
    messages: List<Message>,
    private val title: (Conversation) -> String,
) {
    private val byThread = conversations.associateBy(Conversation::threadId)
    private val searchable = messages.filter { it.text.isNotBlank() && it.threadId in byThread }.sortedByDescending(Message::date)
    private val normalized = searchable.map { normalize(it.text) }

    fun find(text: String): SearchResults {
        val query = SearchQuery(text)
        if (query.isEmpty) return SearchResults(emptyList(), emptyList())
        val matchingThreads = conversations.filter { conversation ->
            query.matches(SearchQuery.tokens(title(conversation)), conversation.participants.map { it.address.filter(Char::isDigit) })
        }
        val needle = normalize(text.trim())
        val matchingMessages = if (needle.length < MIN_MESSAGE_QUERY) emptyList() else searchable.indices.asSequence()
            .filter { normalized[it].contains(needle) }
            .take(MAX_MESSAGES)
            .map { index ->
                val message = searchable[index]
                MessageMatch(byThread.getValue(message.threadId), message, snippet(message.text, normalized[index].indexOf(needle)))
            }
            .toList()
        return SearchResults(matchingThreads, matchingMessages)
    }

    companion object {
        private const val MIN_MESSAGE_QUERY = 2
        private const val MAX_MESSAGES = 100
        private const val CONTEXT_BEFORE = 24
        private const val SNIPPET_LENGTH = 120
        private const val WORD_REACH = 16

        /** Same folding as name search, keeping one character per character so match positions carry over. */
        fun normalize(text: String): String = SearchQuery.stripMarks(text.lowercase(Locale.ROOT).replace('ё', 'е'))

        /** The text around the match, starting at a word boundary so the line does not open mid-word. */
        fun snippet(text: String, matchAt: Int): String {
            if (matchAt <= CONTEXT_BEFORE) return flatten(text).take(SNIPPET_LENGTH)
            val rough = matchAt - CONTEXT_BEFORE
            val space = text.substring(0, rough).indexOfLast(Char::isWhitespace).takeIf { it >= rough - WORD_REACH }
            return "…" + flatten(text.substring(if (space != null) space + 1 else rough)).take(SNIPPET_LENGTH)
        }

        private fun flatten(text: String) = text.trim().replace(Regex("""\s+"""), " ")
    }
}
