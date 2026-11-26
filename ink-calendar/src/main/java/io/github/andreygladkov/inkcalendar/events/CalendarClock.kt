package io.github.andreygladkov.inkcalendar.events

import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object CalendarClock {
    var fixed: Clock? = null

    /** A system clock keeps the time zone it was made in, so it is made anew to follow the phone into another zone. */
    val clock: Clock get() = fixed ?: Clock.systemDefaultZone()
    val zone: ZoneId get() = clock.zone

    fun now(): Instant = clock.instant()

    fun today(): LocalDate = LocalDate.now(clock)
}
