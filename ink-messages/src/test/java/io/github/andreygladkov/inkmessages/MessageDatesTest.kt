package io.github.andreygladkov.inkmessages

import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class MessageDatesTest {
    private val dates = MessageDates(
        today = "Сегодня",
        yesterday = "Вчера",
        yesterdayShort = "вчера",
        monthsShort = listOf("янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек"),
        monthsFull = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря"),
        dayMonth = "%1\$d %2\$s",
        dayMonthYear = "%1\$d %2\$s %3\$d",
        zone = ZoneOffset.UTC,
    )
    private val now = at(2026, 11, 4, 12, 0)

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int) =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test fun listShowsTimeTodayThenYesterdayThenDayAndMonthThenFullDate() {
        assertEquals("09:05", dates.listLabel(at(2026, 11, 4, 9, 5), now))
        assertEquals("вчера", dates.listLabel(at(2026, 11, 3, 23, 59), now))
        assertEquals("3 мар", dates.listLabel(at(2026, 3, 3, 8, 0), now))
        assertEquals("03.11.25", dates.listLabel(at(2025, 11, 3, 8, 0), now))
    }

    @Test fun dayHeadersSpellTheMonthOut() {
        assertEquals("Сегодня", dates.dayHeader(LocalDate.of(2026, 11, 4), now))
        assertEquals("Вчера", dates.dayHeader(LocalDate.of(2026, 11, 3), now))
        assertEquals("1 мая", dates.dayHeader(LocalDate.of(2026, 5, 1), now))
        assertEquals("31 декабря 2025", dates.dayHeader(LocalDate.of(2025, 12, 31), now))
    }
}
