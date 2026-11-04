package io.github.andreygladkov.inkphone.calllog

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class CallType { INCOMING, OUTGOING, MISSED, REJECTED, BLOCKED, VOICEMAIL, OTHER }

data class CallLogEntry(
    val id: Long,
    val number: String,
    val name: String?,
    val type: CallType,
    val dateMillis: Long,
    val durationSeconds: Long,
) {
    val digits: String = number.filter(Char::isDigit)
}

data class CallLogGroup(val entries: List<CallLogEntry>) {
    val latest: CallLogEntry get() = entries.first()
    val count: Int get() = entries.size
}

object CallLogGrouping {
    const val COMPARED_DIGITS = 10

    fun group(entries: List<CallLogEntry>, zone: ZoneId): List<CallLogGroup> {
        val groups = ArrayList<MutableList<CallLogEntry>>()
        for (entry in entries) {
            val current = groups.lastOrNull()
            if (current != null && sameNumber(current.first(), entry) && sameDay(current.first(), entry, zone)) {
                current += entry
            } else {
                groups += mutableListOf(entry)
            }
        }
        return groups.map(::CallLogGroup)
    }

    fun sameNumber(a: String, b: String): Boolean {
        val left = a.filter(Char::isDigit)
        val right = b.filter(Char::isDigit)
        if (left.isEmpty() || right.isEmpty()) return left == right && a == b
        return left.takeLast(COMPARED_DIGITS) == right.takeLast(COMPARED_DIGITS)
    }

    private fun sameNumber(a: CallLogEntry, b: CallLogEntry) = sameNumber(a.number, b.number)

    private fun sameDay(a: CallLogEntry, b: CallLogEntry, zone: ZoneId) = day(a.dateMillis, zone) == day(b.dateMillis, zone)

    private fun day(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
}

class CallTimeFormat(private val yesterday: String, private val zone: ZoneId) {
    private val time = DateTimeFormatter.ofPattern("HH:mm")
    private val date = DateTimeFormatter.ofPattern("dd.MM")
    private val fullDate = DateTimeFormatter.ofPattern("dd.MM.yy")

    fun format(millis: Long, nowMillis: Long): String {
        val moment = Instant.ofEpochMilli(millis).atZone(zone)
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val day = moment.toLocalDate()
        val clock = moment.format(time)
        return when {
            day == today -> clock
            day == today.minusDays(1) -> "$yesterday $clock"
            day.year == today.year -> "${moment.format(date)} $clock"
            else -> "${moment.format(fullDate)} $clock"
        }
    }
}
