package io.github.andreygladkov.inkphone.calllog

import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CallLogModelTest {
    private val zone = ZoneId.of("Asia/Almaty")

    private fun at(day: Int, hour: Int, minute: Int = 0, month: Int = 10) =
        LocalDateTime.of(2026, month, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun entry(id: Long, number: String, millis: Long, type: CallType = CallType.INCOMING) =
        CallLogEntry(id, number, null, type, millis, 0)

    @Test fun groupsConsecutiveCallsFromSameNumberOnSameDay() {
        val groups = CallLogGrouping.group(
            listOf(
                entry(1, "+77011112233", at(31, 18), CallType.MISSED),
                entry(2, "87011112233", at(31, 17), CallType.MISSED),
                entry(3, "+77011112233", at(31, 9)),
                entry(4, "+77020000000", at(31, 8)),
                entry(5, "+77011112233", at(31, 7)),
                entry(6, "+77011112233", at(30, 23)),
            ),
            zone,
        )
        assertEquals(listOf(3, 1, 1, 1), groups.map { it.count })
        assertEquals(1L, groups.first().latest.id)
    }

    @Test fun hiddenNumbersGroupOnlyWithEachOther() {
        assertTrue(CallLogGrouping.sameNumber("", ""))
        assertFalse(CallLogGrouping.sameNumber("", "+77011112233"))
        assertTrue(CallLogGrouping.sameNumber("+7 (701) 111-22-33", "8 701 111 22 33"))
    }

    @Test fun formatsTimeRelativeToNow() {
        val format = CallTimeFormat("вчера", zone)
        val now = at(31, 20)
        assertEquals("14:32", format.format(at(31, 14, 32), now))
        assertEquals("вчера 23:05", format.format(at(30, 23, 5), now))
        assertEquals("12.10 09:00", format.format(at(12, 9), now))
        assertEquals("31.12.25 10:00", format.format(LocalDateTime.of(2025, 12, 31, 10, 0).atZone(zone).toInstant().toEpochMilli(), now))
    }
}
