package io.github.andreygladkov.inkclock.alarm

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime

data class Alarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val days: Set<DayOfWeek> = emptySet(),
    val label: String = "",
    val isEnabled: Boolean = true,
    val ringtone: Ringtone = Ringtone.Default,
    val vibrates: Boolean = true,
    val snoozedUntil: Long? = null,
) {
    val isRepeating get() = days.isNotEmpty()

    fun nextRing(now: ZonedDateTime): ZonedDateTime? {
        if (!isEnabled) return null
        snoozedUntil?.let { return ZonedDateTime.ofInstant(Instant.ofEpochMilli(it), now.zone) }
        return nextOccurrence(now)
    }

    fun nextOccurrence(now: ZonedDateTime): ZonedDateTime {
        val time = LocalTime.of(hour, minute)
        val today = now.toLocalDate()
        return (0L..DAYS_TO_SEARCH).asSequence()
            .map { today.plusDays(it) }
            .filter { days.isEmpty() || it.dayOfWeek in days }
            // ZonedDateTime.of moves a time inside a spring-forward gap past the gap and takes the earlier offset
            // in an autumn overlap, so an alarm set inside either still rings exactly once that day.
            .map { ZonedDateTime.of(it, time, now.zone) }
            .first { it.isAfter(now) }
    }

    private companion object {
        const val DAYS_TO_SEARCH = 7L
    }
}

sealed interface Ringtone {
    data object Default : Ringtone
    data object Silent : Ringtone
    data class Sound(val uri: String) : Ringtone
}
