package io.github.andreygladkov.inkclock.alarm

import android.content.Intent
import android.provider.AlarmClock
import java.time.DayOfWeek
import java.util.Calendar

data class AlarmRequest(
    val hour: Int?,
    val minute: Int,
    val label: String?,
    val days: Set<DayOfWeek>,
    val vibrates: Boolean,
    val ringtone: Ringtone,
    val skipUi: Boolean,
) {
    fun matches(alarm: Alarm) = alarm.hour == hour && alarm.minute == minute && alarm.days == days && alarm.label == label.orEmpty()

    fun toAlarm(id: Int) = Alarm(id, hour ?: 0, minute, days, label.orEmpty(), ringtone = ringtone, vibrates = vibrates)

    companion object {
        fun from(intent: Intent) = AlarmRequest(
            hour = intent.getIntExtra(AlarmClock.EXTRA_HOUR, -1).takeIf { it in 0..23 },
            minute = intent.getIntExtra(AlarmClock.EXTRA_MINUTES, 0).coerceIn(0, 59),
            label = intent.getStringExtra(AlarmClock.EXTRA_MESSAGE),
            days = intent.getIntegerArrayListExtra(AlarmClock.EXTRA_DAYS).orEmpty().mapNotNull(::dayOf).toSet(),
            vibrates = intent.getBooleanExtra(AlarmClock.EXTRA_VIBRATE, true),
            ringtone = when (val ringtone = intent.getStringExtra(AlarmClock.EXTRA_RINGTONE)) {
                null -> Ringtone.Default
                AlarmClock.VALUE_RINGTONE_SILENT -> Ringtone.Silent
                else -> Ringtone.Sound(ringtone)
            },
            skipUi = intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false),
        )

        private fun dayOf(calendarDay: Int): DayOfWeek? = when (calendarDay) {
            Calendar.SUNDAY -> DayOfWeek.SUNDAY
            in Calendar.MONDAY..Calendar.SATURDAY -> DayOfWeek.of(calendarDay - 1)
            else -> null
        }
    }
}
