package io.github.andreygladkov.inkclock.alarm

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import android.widget.Toast
import io.github.andreygladkov.inkclock.MainActivity
import io.github.andreygladkov.inkclock.R
import java.time.DayOfWeek
import java.util.Calendar

/** Entry point for other apps' AlarmClock intents; it shows no window of its own. */
class SetAlarmActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when (intent.action) {
            AlarmClock.ACTION_SET_ALARM -> setAlarm(intent)
            AlarmClock.ACTION_SHOW_ALARMS -> startActivity(MainActivity.intent(this))
            AlarmClock.ACTION_SNOOZE_ALARM -> RingService.ringing?.let { RingService.snooze(it.id) }
            AlarmClock.ACTION_DISMISS_ALARM -> RingService.ringing?.let { RingService.dismiss(it.id) }
        }
        finish()
    }

    private fun setAlarm(intent: Intent) {
        val request = AlarmRequest.from(intent)
        if (request.hour == null || !request.skipUi) {
            startActivity(AlarmEditActivity.create(this, request.hour, request.minute, request.label))
            return
        }
        val store = AlarmStore.of(this)
        val alarm = request.toAlarm(store.all().firstOrNull { request.matches(it) }?.id ?: store.nextId())
        AlarmActions.save(this, alarm)
        Toast.makeText(this, getString(R.string.alarm_set_for, AlarmLabels(this).time(alarm.hour, alarm.minute)), Toast.LENGTH_SHORT).show()
    }
}

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
