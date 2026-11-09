package io.github.andreygladkov.inkweather.forecast

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

data class Forecast(
    val fetchedAt: Instant,
    val zone: ZoneId,
    val hours: List<Hour>,
    val days: List<Day>,
    val source: Source,
) {
    data class Hour(
        val instant: Instant,
        val time: LocalDateTime,
        val temperature: Double,
        val feelsLike: Double?,
        val symbol: Symbol,
        val precipitationMm: Double?,
        val pressureHpa: Double?,
        val windSpeed: Double?,
        val windDegrees: Double?,
    )

    data class Day(val date: LocalDate, val symbol: Symbol, val max: Double, val min: Double, val precipitationMm: Double)

    fun localTime(instant: Instant): LocalDateTime = LocalDateTime.ofInstant(instant, zone)

    fun age(instant: Instant): Duration = Duration.between(fetchedAt, instant)

    /** The hour under way, or the first one when the forecast starts later; nothing once the forecast has run out. */
    fun current(instant: Instant): Hour? {
        val begun = hours.lastOrNull { !it.instant.isAfter(instant) } ?: return hours.firstOrNull()
        return begun.takeIf { Duration.between(it.instant, instant) < LONGEST_STEP }
    }

    /**
     * Counted from the first step after [instant] in absolute time, not from the next whole local hour: in zones
     * offset by half an hour every step falls on :30, and around a clock change local hours repeat or go missing.
     * Picked by time rather than by index, so a step the service left out does not shift the ones after it. A shown
     * step's precipitation adds up all its hours, so rain between two shown hours is not lost.
     */
    fun hoursAfter(instant: Instant, count: Int, stepHours: Long): List<Hour> {
        val first = hours.firstOrNull { it.instant.isAfter(instant) } ?: return emptyList()
        val byInstant = hours.associateBy { it.instant }
        val step = Duration.ofHours(stepHours)
        return (0 until count).mapNotNull { index ->
            val start = first.instant.plus(step.multipliedBy(index.toLong()))
            val end = start.plus(step)
            val fallen = hours.filter { !it.instant.isBefore(start) && it.instant.isBefore(end) }.mapNotNull { it.precipitationMm }
            byInstant[start]?.copy(precipitationMm = fallen.takeIf { it.isNotEmpty() }?.sum())
        }
    }

    fun daysFrom(instant: Instant, count: Int): List<Day> {
        val today = localTime(instant).toLocalDate()
        return days.filter { !it.date.isBefore(today) }.take(count)
    }

    private companion object {
        val LONGEST_STEP: Duration = Duration.ofHours(6)
    }
}
