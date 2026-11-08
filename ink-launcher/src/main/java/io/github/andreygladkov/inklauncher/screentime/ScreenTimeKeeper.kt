package io.github.andreygladkov.inklauncher.screentime

import android.content.Context
import androidx.core.content.edit
import io.github.andreygladkov.ink.inkPrefs
import io.github.andreygladkov.inklauncher.LauncherPrefs
import java.time.Clock
import java.time.LocalDate

class UsagePeriod(private val days: List<Span>, private val log: UsageLog) {
    fun report(drifting: Set<String>): ScreenTimeReport = ScreenTime.tally(log, days, drifting)
}

/** The drifting marks are kept by package, so every launcher entry of an app shares its mark. */
class ScreenTimeKeeper(context: Context, private val clock: () -> Clock) {
    private val access = UsageAccess(context)
    private val prefs = context.inkPrefs()

    val isGranted: Boolean get() = access.isGranted()

    fun requestAccess() = access.request()

    val drifting: Set<String> get() = prefs.getStringSet(LauncherPrefs.DRIFTING_APPS, null).orEmpty().toSet()

    fun isDrifting(packageName: String) = packageName in drifting

    fun setDrifting(packageName: String, value: Boolean) {
        val next = if (value) drifting + packageName else drifting - packageName
        prefs.edit { putStringSet(LauncherPrefs.DRIFTING_APPS, next) }
    }

    fun period(dayCount: Int): UsagePeriod? {
        if (!isGranted) return null
        val clock = clock()
        val days = ScreenTime.days(LocalDate.now(clock), dayCount, clock.zone)
        return UsagePeriod(days, access.log(days.minOf { it.start } - LOOKBACK_MS, clock.millis()))
    }

    private companion object {
        // An app opened before midnight is found by the event that brought it to the front.
        const val LOOKBACK_MS = 6 * 60 * 60 * 1000L
    }
}
