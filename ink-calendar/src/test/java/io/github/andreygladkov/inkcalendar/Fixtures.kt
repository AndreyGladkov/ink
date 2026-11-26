package io.github.andreygladkov.inkcalendar

import android.Manifest
import android.app.Application
import io.github.andreygladkov.inkcalendar.events.CalendarClock
import org.robolectric.Shadows.shadowOf
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

object Fixtures {
    val ZONE: ZoneId = ZoneId.of("Europe/Moscow")
    val TODAY: LocalDate = LocalDate.of(2026, 11, 26)

    fun fixNow(at: LocalDateTime = TODAY.atTime(12, 0)) {
        CalendarClock.fixed = Clock.fixed(at.atZone(ZONE).toInstant(), ZONE)
    }

    fun restoreClock() {
        CalendarClock.fixed = null
    }

    fun allow(application: Application) =
        shadowOf(application).grantPermissions(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    fun at(date: LocalDate, hour: Int, minute: Int = 0): Long = date.atTime(hour, minute).atZone(ZONE).toInstant().toEpochMilli()

    fun utcDay(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}
