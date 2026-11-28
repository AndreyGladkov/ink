package io.github.andreygladkov.ink.ui

import android.content.Context
import android.text.format.DateFormat
import java.time.format.DateTimeFormatter
import java.util.Locale

object DatePatterns {
    fun pattern(locale: Locale, skeleton: String): String = withoutFlexibleDayPeriods(DateFormat.getBestDateTimePattern(locale, skeleton))

    fun formatter(locale: Locale, skeleton: String): DateTimeFormatter = DateTimeFormatter.ofPattern(pattern(locale, skeleton), locale)

    fun timePattern(context: Context, locale: Locale = context.resources.configuration.locales[0]): String =
        pattern(locale, if (DateFormat.is24HourFormat(context)) "Hm" else "hm")

    fun time(context: Context, locale: Locale = context.resources.configuration.locales[0]): DateTimeFormatter =
        DateTimeFormatter.ofPattern(timePattern(context, locale), locale)

    // ICU answers the 12-hour skeleton of some locales with flexible day periods ("Bh:mm" for zh-Hant, "b" for noon and midnight).
    // java.time learned 'B' only in JDK 16 and never 'b'; Android before 14 ships the older java.time, so ofPattern would throw there.
    internal fun withoutFlexibleDayPeriods(pattern: String): String = buildString {
        var quoted = false
        var previousWasPeriod = false
        for (char in pattern) {
            if (char == '\'') quoted = !quoted
            val isPeriod = !quoted && (char == 'B' || char == 'b')
            if (!isPeriod) append(char) else if (!previousWasPeriod) append('a')
            previousWasPeriod = isPeriod
        }
    }
}
