package io.github.andreygladkov.inkmessages.thread

import io.github.andreygladkov.inkmessages.data.Message
import java.time.LocalDate

sealed interface ThreadItem {
    val id: Long

    data class Day(val day: LocalDate) : ThreadItem {
        override val id: Long get() = -1 - day.toEpochDay()
    }

    data class Bubble(val message: Message, val showTime: Boolean) : ThreadItem {
        override val id: Long get() = message.key
    }
}

object ThreadItems {
    const val RUN_GAP_MS = 5 * 60_000L

    fun build(messages: List<Message>, dayOf: (Long) -> LocalDate): List<ThreadItem> = buildList {
        messages.forEachIndexed { index, message ->
            val day = dayOf(message.date)
            if (index == 0 || dayOf(messages[index - 1].date) != day) add(ThreadItem.Day(day))
            val next = messages.getOrNull(index + 1)
            val endsRun = next == null || next.isOutgoing != message.isOutgoing ||
                next.date - message.date >= RUN_GAP_MS || dayOf(next.date) != day
            add(ThreadItem.Bubble(message, endsRun))
        }
    }
}
