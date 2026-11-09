package io.github.andreygladkov.inklauncher.screentime

import io.github.andreygladkov.inklauncher.screentime.UsageKind.BACKGROUND
import io.github.andreygladkov.inklauncher.screentime.UsageKind.FOREGROUND
import io.github.andreygladkov.inklauncher.screentime.UsageKind.SCREEN_OFF
import io.github.andreygladkov.inklauncher.screentime.UsageKind.UNLOCK
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class ScreenTimeTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val today = LocalDate.of(2026, 11, 8)
    private val days = ScreenTime.days(today, 2, zone)
    private val reader = "org.koreader.launcher"
    private val maps = "org.maps"
    private val launcher = "io.github.andreygladkov.inklauncher"

    @Test fun eachDayRunsFromOneLocalMidnightToTheNext() {
        assertEquals(listOf(at(8, 0, 0), at(7, 0, 0)), days.map { it.start })
        assertEquals(at(9, 0, 0), days.first().end)
        val shortDay = ScreenTime.days(LocalDate.of(2026, 3, 29), 1, zone).single()
        assertEquals(23 * HOUR, shortDay.end - shortDay.start)
    }

    @Test fun aMarkedAppDriftsAndEverythingElseIsOnPurpose() {
        val report = tally(
            event(8, 10, 0, FOREGROUND, reader),
            event(8, 10, 40, BACKGROUND, reader),
            event(8, 10, 40, FOREGROUND, launcher),
            event(8, 10, 41, BACKGROUND, launcher),
            event(8, 10, 41, FOREGROUND, maps),
            event(8, 10, 56, BACKGROUND, maps),
            until = at(8, 12, 0),
        )
        assertEquals(DayUsage(days[0], 40 * MINUTE, 16 * MINUTE, 0), report.days[0])
        assertEquals(mapOf(reader to 40 * MINUTE, launcher to MINUTE, maps to 15 * MINUTE), report.byApp)
    }

    @Test fun anUncountedAppIsListedButLeftOutOfTheDay() {
        val report = ScreenTime.tally(
            UsageLog(at(6, 18, 0), at(8, 12, 0), listOf(
                event(8, 10, 0, FOREGROUND, launcher),
                event(8, 10, 5, BACKGROUND, launcher),
                event(8, 10, 5, FOREGROUND, maps),
                event(8, 10, 20, BACKGROUND, maps),
            )),
            days,
            AppCategories(emptyMap(), uncountedByDefault = setOf(launcher)),
        )
        assertEquals(DayUsage(days[0], 0, 15 * MINUTE, 0), report.days[0])
        assertEquals(mapOf(launcher to 5 * MINUTE, maps to 15 * MINUTE), report.byApp)
    }

    @Test fun aChosenCategoryOverridesTheDefault() {
        val categories = AppCategories(mapOf(launcher to AppCategory.ON_PURPOSE, maps to AppCategory.UNCOUNTED), uncountedByDefault = setOf(launcher))
        assertEquals(listOf(AppCategory.ON_PURPOSE, AppCategory.UNCOUNTED, AppCategory.ON_PURPOSE), listOf(launcher, maps, reader).map { categories[it] })
    }

    @Test fun aSessionOverMidnightIsSplitBetweenTheDays() {
        val report = tally(
            event(7, 23, 30, FOREGROUND, reader),
            event(8, 0, 20, BACKGROUND, reader),
            until = at(8, 12, 0),
        )
        assertEquals(listOf(20 * MINUTE, 30 * MINUTE), report.days.map { it.drifting })
        assertEquals(50 * MINUTE, report.byApp[reader])
    }

    @Test fun movingBetweenScreensOfOneAppKeepsCountingIt() {
        val report = tally(
            event(8, 9, 0, FOREGROUND, maps),
            event(8, 9, 5, BACKGROUND, maps),
            event(8, 9, 5, FOREGROUND, maps),
            event(8, 9, 10, FOREGROUND, maps),
            event(8, 9, 30, BACKGROUND, maps),
            until = at(8, 12, 0),
        )
        assertEquals(30 * MINUTE, report.days[0].onPurpose)
    }

    @Test fun theAppInFrontNowCountsUntilNow() {
        val report = tally(event(8, 11, 45, FOREGROUND, reader), until = at(8, 12, 0))
        assertEquals(15 * MINUTE, report.days[0].drifting)
    }

    @Test fun anAppThatLeavesFirstWasInFrontSinceTheLogBegan() {
        val report = tally(
            event(8, 0, 10, BACKGROUND, reader),
            event(8, 0, 10, FOREGROUND, launcher),
            event(8, 0, 11, BACKGROUND, launcher),
            since = at(7, 23, 0),
            until = at(8, 12, 0),
        )
        assertEquals(listOf(10 * MINUTE, 60 * MINUTE), report.days.map { it.drifting })
        assertEquals(MINUTE, report.days[0].onPurpose)
    }

    @Test fun anAppLeavingJustAfterHomeArrivesWasInFrontSinceTheLogBegan() {
        val report = tally(
            event(8, 0, 40, FOREGROUND, launcher),
            event(8, 0, 40, BACKGROUND, reader),
            event(8, 0, 41, BACKGROUND, launcher),
            since = at(7, 23, 0),
            until = at(8, 12, 0),
        )
        assertEquals(listOf(40 * MINUTE, 60 * MINUTE), report.days.map { it.drifting })
        assertEquals(MINUTE, report.days[0].onPurpose)
    }

    @Test fun anAppLeavingAfterItWasSeenInFrontIsNotCountedAgain() {
        val report = tally(
            event(8, 9, 0, FOREGROUND, reader),
            event(8, 9, 10, FOREGROUND, maps),
            event(8, 9, 10, BACKGROUND, reader),
            event(8, 9, 20, BACKGROUND, maps),
            until = at(8, 12, 0),
        )
        assertEquals(10 * MINUTE, report.days[0].drifting)
        assertEquals(10 * MINUTE, report.days[0].onPurpose)
    }

    @Test fun theScreenGoingOffEndsTheSession() {
        val report = tally(
            event(8, 9, 0, FOREGROUND, reader),
            event(8, 9, 10, SCREEN_OFF),
            event(8, 9, 10, BACKGROUND, reader),
            event(8, 9, 50, UNLOCK),
            event(8, 9, 50, FOREGROUND, reader),
            event(8, 10, 0, BACKGROUND, reader),
            until = at(8, 12, 0),
        )
        assertEquals(20 * MINUTE, report.days[0].drifting)
    }

    @Test fun unlocksAreCountedOnTheDayTheyHappen() {
        val report = tally(
            event(7, 6, 0, UNLOCK),
            event(7, 23, 59, UNLOCK),
            event(8, 0, 1, UNLOCK),
            until = at(8, 12, 0),
        )
        assertEquals(listOf(1, 2), report.days.map { it.unlocks })
        assertEquals(DayUsage(days[0], 0, 0, 1), report.days[0])
    }

    private fun tally(vararg events: UsageEvent, since: Long = at(6, 18, 0), until: Long) =
        ScreenTime.tally(UsageLog(since, until, events.toList()), days, AppCategories(mapOf(reader to AppCategory.DRIFTING), uncountedByDefault = emptySet()))

    private fun event(day: Int, hour: Int, minute: Int, kind: UsageKind, packageName: String? = null) =
        UsageEvent(at(day, hour, minute), kind, packageName)

    private fun at(day: Int, hour: Int, minute: Int) = LocalDateTime.of(2026, 11, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
    }
}
