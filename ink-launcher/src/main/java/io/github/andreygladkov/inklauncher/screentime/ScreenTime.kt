package io.github.andreygladkov.inklauncher.screentime

import java.time.LocalDate
import java.time.ZoneId

enum class UsageKind { FOREGROUND, BACKGROUND, SCREEN_OFF, UNLOCK }

data class UsageEvent(val at: Long, val kind: UsageKind, val packageName: String? = null)

/** What the system recorded from [since] until [until], oldest event first. */
data class UsageLog(val since: Long, val until: Long, val events: List<UsageEvent>)

data class Span(val start: Long, val end: Long)

data class DayUsage(val day: Span, val drifting: Long, val onPurpose: Long, val unlocks: Int)

data class ScreenTimeReport(val days: List<DayUsage>, val byApp: Map<String, Long>)

object ScreenTime {
    /** Newest first, each from one local midnight to the next, so a day around a clock change is not 24 hours. */
    fun days(today: LocalDate, count: Int, zone: ZoneId): List<Span> = (0 until count).map { back ->
        val date = today.minusDays(back.toLong())
        Span(date.atStartOfDay(zone).toInstant().toEpochMilli(), date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
    }

    fun tally(log: UsageLog, days: List<Span>, categories: AppCategories): ScreenTimeReport {
        val sessions = sessions(log)
        val byApp = HashMap<String, Long>()
        val usage = days.map { day ->
            var driftingMs = 0L
            var onPurposeMs = 0L
            sessions.forEach { session ->
                val overlap = minOf(session.end, day.end) - maxOf(session.start, day.start)
                if (overlap <= 0) return@forEach
                when (categories[session.packageName]) {
                    AppCategory.DRIFTING -> driftingMs += overlap
                    AppCategory.ON_PURPOSE -> onPurposeMs += overlap
                    AppCategory.UNCOUNTED -> Unit
                }
                byApp.merge(session.packageName, overlap, Long::plus)
            }
            val unlocks = log.events.count { it.kind == UsageKind.UNLOCK && it.at >= day.start && it.at < day.end }
            DayUsage(day, driftingMs, onPurposeMs, unlocks)
        }
        return ScreenTimeReport(usage, byApp)
    }

    private data class Session(val packageName: String, val start: Long, val end: Long)

    private fun sessions(log: UsageLog): List<Session> {
        val sessions = ArrayList<Session>()
        val seen = HashSet<String>()
        var current: String? = null
        var start = log.since
        val changes = log.events.filter { it.kind != UsageKind.UNLOCK }
        val firstChange = changes.firstOrNull()?.at ?: log.until
        fun close(at: Long) {
            current?.let { if (at > start) sessions += Session(it, start, at) }
            current = null
        }
        changes.forEach { event ->
            when (event.kind) {
                UsageKind.FOREGROUND -> {
                    close(event.at)
                    current = event.packageName
                    start = event.at
                }
                // An app first seen leaving was in front when the log begins, until the front first changed: the
                // home activity resumes while the app is still pausing (resumeWhilePausing), so its leaving can come
                // just after the launcher's arrival.
                UsageKind.BACKGROUND -> {
                    val app = event.packageName
                    if (app == current) close(event.at)
                    else if (app != null && app !in seen) sessions += Session(app, log.since, firstChange)
                }
                UsageKind.SCREEN_OFF -> close(event.at)
                UsageKind.UNLOCK -> Unit
            }
            event.packageName?.let(seen::add)
        }
        close(log.until)
        return sessions
    }
}
