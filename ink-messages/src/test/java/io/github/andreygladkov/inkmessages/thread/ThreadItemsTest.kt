package io.github.andreygladkov.inkmessages.thread

import io.github.andreygladkov.inkmessages.data.Message
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ThreadItemsTest {
    private val minute = 60_000L
    private val day = 24 * 60 * minute

    private fun message(id: Long, date: Long, outgoing: Boolean = false) =
        Message(id, false, 1, date, "m$id", isOutgoing = outgoing, isUnread = false)

    private fun dayOf(date: Long): LocalDate = LocalDate.ofEpochDay(date / day)

    @Test fun timeIsShownOnlyAtTheEndOfARunAndDaysGetHeaders() {
        val messages = listOf(
            message(1, 10 * 60 * minute),
            message(2, 10 * 60 * minute + 2 * minute),
            message(3, 10 * 60 * minute + 3 * minute, outgoing = true),
            message(4, 10 * 60 * minute + 20 * minute),
            message(5, day + 9 * 60 * minute),
        )
        val items = ThreadItems.build(messages, ::dayOf)
        assertEquals(
            listOf("day 0", "1", "2 time", "3 time", "4 time", "day 1", "5 time"),
            items.map {
                when (it) {
                    is ThreadItem.Day -> "day ${it.day.toEpochDay()}"
                    is ThreadItem.Bubble -> "${it.message.id}" + if (it.showTime) " time" else ""
                }
            },
        )
    }

    @Test fun messagesFiveMinutesApartStartANewRun() {
        val items = ThreadItems.build(listOf(message(1, 0), message(2, ThreadItems.RUN_GAP_MS)), ::dayOf)
        assertEquals(listOf(true, true), items.filterIsInstance<ThreadItem.Bubble>().map { it.showTime })
    }

    @Test fun headerIdsNeverCollideWithMessageIds() {
        val items = ThreadItems.build(listOf(message(0, 0), message(1, day)), ::dayOf)
        assertEquals(items.size, items.map { it.id }.toSet().size)
    }
}
